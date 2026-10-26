package com.trevlar.slotkit.api.item;

import com.mojang.serialization.Codec;
import com.trevlar.slotkit.network.ModSides;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A value that rides on an item: "open", "favorite", a mark. It follows the item wherever
 * it goes (into a shulker box and back, dropped, traded), and vanilla syncs it to clients
 * with the stack, so any client drawing the item reads it. The server sets it.
 *
 * <pre>{@code
 * // Common init:
 * static final ItemState<Boolean> OPEN = ItemState.flag("mymod:open", ItemState.Saved.NO);
 *
 * // Server, when the box opens and when it closes:
 * OPEN.set(ref, true);
 * OPEN.clear(ref);
 *
 * // Either side:
 * boolean open = OPEN.is(stack);
 * }</pre>
 *
 * <p>A state is a data component SlotKit registers for the mod. A {@link Saved#YES} state
 * is saved with the item; a {@link Saved#NO} state isn't, so it's gone after a relog or a
 * restart. Only a mod declaring {@code bothSides: required} declares a state, because a
 * client without the mod can't read the stack. A stack carrying a state doesn't merge
 * with one that doesn't, as with any component: fine for unstackable items and for a
 * flag held briefly. Since 2.2.0.
 *
 * @param <T> the value's type
 */
public final class ItemState<T> {

    /** Whether the state is saved with the item. */
    public enum Saved { YES, NO }

    private final Identifier id;
    private final DataComponentType<T> type;

    private ItemState(Identifier id, DataComponentType<T> type) {
        this.id = id;
        this.type = type;
    }

    /**
     * A true-or-absent flag. Declare at common init.
     *
     * @throws IllegalStateException when {@code id}'s namespace isn't a loaded mod that
     *         declares {@code bothSides: required}
     */
    public static ItemState<Boolean> flag(String id, Saved saved) {
        return of(id, Codec.BOOL, ByteBufCodecs.BOOL, saved);
    }

    /**
     * A typed state. The codec saves it (when {@code saved} is YES), the stream codec
     * sends it. Declare at common init.
     *
     * @throws IllegalStateException when {@code id}'s namespace isn't a loaded mod that
     *         declares {@code bothSides: required}
     */
    public static <T> ItemState<T> of(String id, Codec<T> codec, StreamCodec<? super ByteBuf, T> streamCodec, Saved saved) {
        ModSides.requireBuildsSlots("ItemState", id);
        Identifier key = Identifier.parse(id);
        DataComponentType.Builder<T> b = DataComponentType.<T>builder().networkSynchronized(streamCodec);
        if (saved == Saved.YES) b.persistent(codec);
        DataComponentType<T> type = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, b.build());
        return new ItemState<>(key, type);
    }

    public Identifier id() { return id; }

    /** The value on {@code stack}, or {@code null} when it has none. */
    public @Nullable T get(ItemStack stack) { return stack.get(type); }

    /** Whether {@code stack} carries the state with a true value (for a flag) or any value. */
    public boolean is(ItemStack stack) {
        T v = stack.get(type);
        return v != null && !Boolean.FALSE.equals(v);
    }

    /** Sets the value on a stack the caller holds, a live slot's stack included. */
    public void set(ItemStack stack, T value) { stack.set(type, value); }

    /** Removes the value from {@code stack}. */
    public void clear(ItemStack stack) { stack.remove(type); }

    /**
     * Sets the value on the item {@code ref} names and writes it back up through every
     * container. Server side. Returns false, writing nothing, if the ref is stale.
     */
    public boolean set(ItemRef ref, T value) {
        ref.stack().set(type, value);
        return ref.commit();
    }

    /** Removes the value from the item {@code ref} names, written back. False if the ref is stale. */
    public boolean clear(ItemRef ref) {
        ref.stack().remove(type);
        return ref.commit();
    }

    /** The component type, for code that reads components directly. */
    public DataComponentType<T> componentType() { return type; }
}
