package com.trevlar.slotkit.api.reserve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a reserved slot holds out for: a ghost {@code item}, an optional count {@code cap}, and
 * which of the item's fields an incoming stack must share with it ({@code match}). With no
 * fields named, any stack of the same item matches, whatever its name, enchantments or other
 * components. Wear (damage, repair cost) never decides.
 *
 * <p>{@link #NONE} is the empty reservation: the slot takes anything. Values are immutable;
 * {@link #withCap} and {@link #withMatch} return new ones.
 *
 * @param item  the ghost, one item; {@link ItemStack#EMPTY} for {@link #NONE}
 * @param cap   the most the slot holds, or 0 for the item's own stack size
 * @param match the fields an incoming stack must share with {@code item}
 * @since 2.4.0
 */
public record Reservation(ItemStack item, int cap, Set<Match> match) {

    /** A field of the ghost an incoming stack can be asked to share. */
    public enum Match {
        /** The custom name. */
        NAME,
        /** Enchantments, and the stored enchantments of a book. */
        ENCHANTMENTS,
        /** Every other component (contents, dye, trim, potion, ...), wear aside. */
        COMPONENTS
    }

    /** No reservation: the slot takes anything. */
    public static final Reservation NONE = new Reservation(ItemStack.EMPTY, 0, Set.of());

    public Reservation {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(match, "match");
        item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        cap = Math.max(0, cap);
        match = match.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(match));
    }

    /** {@code item}, matched by type, up to its stack size. */
    public static Reservation of(ItemStack item) {
        return new Reservation(item, 0, Set.of());
    }

    /** This reservation holding at most {@code cap} (0: the item's stack size). */
    public Reservation withCap(int cap) {
        return new Reservation(item, cap, match);
    }

    /** This reservation asking an incoming stack to share {@code fields} with the ghost. */
    public Reservation withMatch(Match... fields) {
        return new Reservation(item, cap, fields.length == 0 ? Set.of() : EnumSet.copyOf(List.of(fields)));
    }

    public boolean isNone() {
        return item.isEmpty();
    }

    /** Whether {@code stack} may go in the slot: always, with no reservation. */
    public boolean matches(ItemStack stack) {
        if (isNone()) return true;
        if (!ItemStack.isSameItem(item, stack)) return false;
        if (match.isEmpty()) return true;
        Set<DataComponentType<?>> types = new HashSet<>(item.getComponents().keySet());
        types.addAll(stack.getComponents().keySet());
        for (DataComponentType<?> type : types) {
            if (judged(type) && !Objects.equals(item.get(type), stack.get(type))) return false;
        }
        return true;
    }

    /** How many of {@code stack} the slot holds: the cap when set, never more than the stack size. */
    public int limit(ItemStack stack) {
        int max = stack.getMaxStackSize();
        return cap > 0 ? Math.min(cap, max) : max;
    }

    private boolean judged(DataComponentType<?> type) {
        if (type == DataComponents.CUSTOM_NAME) return match.contains(Match.NAME);
        if (type == DataComponents.ENCHANTMENTS || type == DataComponents.STORED_ENCHANTMENTS) {
            return match.contains(Match.ENCHANTMENTS);
        }
        if (type == DataComponents.DAMAGE || type == DataComponents.REPAIR_COST) return false;
        return match.contains(Match.COMPONENTS);
    }

    // ItemStack has identity equality; two reservations of equal stacks are equal, so
    // writing NONE clears the slot's entry.
    @Override
    public boolean equals(Object o) {
        return o instanceof Reservation r && cap == r.cap && match.equals(r.match)
                && ItemStack.isSameItemSameComponents(item, r.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item.isEmpty() ? null : item.getItem(), cap, match);
    }

    // Saved text: the field names and the Match names never change.
    private static final Codec<Match> MATCH_CODEC = Codec.STRING.xmap(
            s -> Match.valueOf(s.toUpperCase(java.util.Locale.ROOT)), m -> m.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<Reservation> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(Reservation::item),
            Codec.INT.optionalFieldOf("cap", 0).forGetter(Reservation::cap),
            MATCH_CODEC.listOf().optionalFieldOf("match", List.of())
                    .forGetter(r -> List.copyOf(r.match()))
    ).apply(i, (item, cap, match) -> new Reservation(item, cap, Set.copyOf(match))));

    private static final StreamCodec<ByteBuf, Set<Match>> MATCH_STREAM = ByteBufCodecs.VAR_INT.map(
            bits -> {
                Set<Match> out = EnumSet.noneOf(Match.class);
                for (Match m : Match.values()) if ((bits & (1 << m.ordinal())) != 0) out.add(m);
                return out;
            },
            set -> set.stream().mapToInt(m -> 1 << m.ordinal()).sum());

    /** Untrusted on decode: a client names the ghost. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Reservation> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, Reservation::item,
            ByteBufCodecs.VAR_INT, Reservation::cap,
            MATCH_STREAM.cast(), Reservation::match,
            Reservation::new);
}
