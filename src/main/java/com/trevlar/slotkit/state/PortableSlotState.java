package com.trevlar.slotkit.state;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slot state that travels with an item: a portable block entity's per-player bag,
 * carried on the item it drops as (a shulker box with its marks), under one key in
 * vanilla's {@code minecraft:custom_data}.
 *
 * <h3>Why custom_data and not a component of our own</h3>
 * Fabric API refuses, before SlotKit's handshake, any client that cannot match a
 * server's modded entries in a synced registry, and data component types are one. A
 * registered component would make every SlotKit server refuse vanilla players and
 * Fabric players without SlotKit, whatever its mods declare. So SlotKit registers no
 * component, and the bag rides in custom_data, merged beside any other mod's data.
 *
 * <h3>Three rules this class holds</h3>
 * <ol>
 *   <li><b>Old saves load.</b> Containers 6.x saved the bag as the component
 *       {@code menukit:portable_slot_state}. With no such type registered, vanilla
 *       would fail the whole item and the box would vanish with its contents. So the
 *       component patch codec renames it as it reads ({@link #renamingLegacy}): the
 *       value moves under {@link #KEY} in custom_data (merged in, never replacing),
 *       and the removal form {@code !menukit:portable_slot_state} is dropped. This
 *       runs forever: old chunks, offline players and structure files keep the old
 *       form for years.</li>
 *   <li><b>Private and unforgeable on the wire.</b> custom_data syncs to clients, so
 *       {@link #installWireStrip} drops the key from custom_data's stream codec both ways: a
 *       client never reads another player's marks, and a creative client cannot write
 *       slot state by sending an item back. A creative stack never carries slot state,
 *       as with 6.x's empty network codec.</li>
 *   <li><b>A placed box owns its state.</b> Placing consumes the key (it moves into the
 *       block entity's attachment and leaves the leftover components), and breaking
 *       writes it back only when the bag holds something, so marks cleared while the
 *       box was placed do not come back ({@code SKPortableSlotStateMixin}).</li>
 * </ol>
 *
 * <p>The key is saved text and never changes.
 */
@ApiStatus.Internal
public final class PortableSlotState {

    private PortableSlotState() {}

    /** The key in custom_data, and the id of the 6.x component it replaces. Saved text: fixed. */
    public static final String KEY = "menukit:portable_slot_state";

    private static final String LEGACY_REMOVAL = "!" + KEY;
    private static final Identifier CUSTOM_DATA = Identifier.withDefaultNamespace("custom_data");

    // Item side

    /** The bag carried in {@code data}, or {@code null} when it carries none. */
    public static @Nullable PerPlayerSlotStateBag read(@Nullable CustomData data) {
        if (data == null) return null;
        Tag tag = data.copyTag().get(KEY);
        return tag instanceof CompoundTag compound ? new PerPlayerSlotStateBag(compound) : null;
    }

    /** {@code data} with a copy of {@code bag} under the key, every other key kept. */
    public static CustomData with(@Nullable CustomData data, PerPlayerSlotStateBag bag) {
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.put(KEY, bag.backing().copy());
        return CustomData.of(tag);
    }

    /** {@code data} without the key: {@code null} when nothing else is left, {@code data} itself when it had no key. */
    public static @Nullable CustomData without(@Nullable CustomData data) {
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (tag.remove(KEY) == null) return data;
        return tag.isEmpty() ? null : CustomData.of(tag);
    }

    /** {@code components} with custom_data replaced by {@code data} (removed when {@code null}). */
    public static DataComponentMap withCustomData(DataComponentMap components, @Nullable CustomData data) {
        return DataComponentMap.builder().addAll(components).set(DataComponents.CUSTOM_DATA, data).build();
    }

    // The wire

    /** custom_data as it crosses the network: the key dropped, so it never reaches or comes from a client. */
    public static CustomData stripped(CustomData data) {
        if (!data.copyTag().contains(KEY)) return data;
        CustomData without = without(data);
        return without == null ? CustomData.EMPTY : without;
    }

    /**
     * Wraps custom_data's wire codec so the key is dropped both ways: encoding (a server
     * sending to a client, or a client to a server) and decoding (whatever arrives).
     * From common init, on both sides, before any connection.
     */
    @SuppressWarnings("unchecked")
    public static void installWireStrip() {
        var type = (com.trevlar.slotkit.mixin.SKComponentTypeAccessor) (Object) DataComponents.CUSTOM_DATA;
        var wire = (net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, CustomData>)
                type.sk$streamCodec();
        type.sk$setStreamCodec(wire.map(PortableSlotState::stripped, PortableSlotState::stripped));
    }

    // Old saves

    /** The component patch codec, reading 6.x's component into custom_data first. Encoding is unchanged. */
    public static <A> Codec<A> renamingLegacy(Codec<A> patchCodec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return patchCodec.decode(ops, renameLegacy(ops, input));
            }

            @Override
            public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
                return patchCodec.encode(value, ops, prefix);
            }

            @Override
            public String toString() {
                return patchCodec + "[SlotKit: legacy portable_slot_state renamed]";
            }
        };
    }

    /**
     * A component patch's map with 6.x's component moved into custom_data: the
     * component's value goes under {@link #KEY}, merged into any custom_data already
     * there (whose own key wins if both are present), and the removal form is dropped.
     * Input that is not a map, or holds neither form, comes back unchanged.
     */
    static <T> T renameLegacy(DynamicOps<T> ops, T input) {
        MapLike<T> map = ops.getMap(input).result().orElse(null);
        if (map == null) return input;
        T legacy = map.get(KEY);
        if (legacy == null && map.get(LEGACY_REMOVAL) == null) return input;

        Map<T, T> out = new LinkedHashMap<>();
        T customKey = null;
        T customValue = null;
        for (Pair<T, T> entry : (Iterable<Pair<T, T>>) map.entries()::iterator) {
            String key = ops.getStringValue(entry.getFirst()).result().orElse("");
            if (key.equals(KEY) || key.equals(LEGACY_REMOVAL)) continue;
            if (CUSTOM_DATA.equals(Identifier.tryParse(key))) {
                customKey = entry.getFirst();
                customValue = entry.getSecond();
                continue;
            }
            out.put(entry.getFirst(), entry.getSecond());
        }
        if (legacy != null) {
            // Another mod's custom_data that does not parse is left exactly as it was
            // (the marks are dropped rather than risk that mod's data).
            CompoundTag tag = customValue == null ? new CompoundTag()
                    : CustomData.CODEC.parse(ops, customValue).result().map(CustomData::copyTag).orElse(null);
            if (tag != null) {
                if (!tag.contains(KEY)) tag.put(KEY, ops.convertTo(NbtOps.INSTANCE, legacy));
                customValue = CustomData.CODEC.encodeStart(ops, CustomData.of(tag)).result().orElse(customValue);
            }
        }
        if (customValue != null) out.put(customKey != null ? customKey : ops.createString(CUSTOM_DATA.toString()), customValue);
        return ops.createMap(out);
    }
}
