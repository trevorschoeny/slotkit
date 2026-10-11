package com.trevlar.menukit.containers.state;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Slot-state bags for player-scoped registered slots, one per resolver id. Wraps a
 * {@link CompoundTag} whose keys are slot resolver-id strings and whose values are
 * {@link SlotStateBag}s. Attached to a {@code Player} through
 * {@link SlotStateAttachments#MODDED_PLAYER}.
 *
 * <p>Keying by resolver id keeps several slots on the same player apart. A mod that
 * registers both "pockets" and "equipment" on the inventory gets a separate bag for
 * each, so their slot indices (each {@code 0..N}) never overwrite each other. The
 * same holds across mods.
 *
 * <p>Same shape as {@link PerPlayerSlotStateBag}, but keyed by resolver id rather
 * than viewer UUID: here the owner is the player, and the key is the slot, not the
 * viewer.
 */
@ApiStatus.Internal
public final class NamespacedSlotStateBag {

    /** Round-trips the bag through a CompoundTag, for attachment persistence. */
    public static final Codec<NamespacedSlotStateBag> CODEC =
            CompoundTag.CODEC.xmap(NamespacedSlotStateBag::new, NamespacedSlotStateBag::backing);

    private final CompoundTag backing;

    public NamespacedSlotStateBag() {
        this(new CompoundTag());
    }

    public NamespacedSlotStateBag(CompoundTag backing) {
        this.backing = backing;
    }

    public CompoundTag backing() {
        return backing;
    }

    /**
     * Returns the bag for {@code resolverId}, creating an empty one if absent. The
     * returned bag writes through to the underlying CompoundTag.
     */
    public SlotStateBag getOrCreate(Identifier resolverId) {
        String key = resolverId.toString();
        CompoundTag sub = backing.getCompoundOrEmpty(key);
        backing.put(key, sub); // write back so a fresh empty sub-tag sticks
        return new SlotStateBag(sub);
    }

    /** Returns the bag for {@code resolverId}, or {@code null} if absent. */
    public @Nullable SlotStateBag get(Identifier resolverId) {
        String key = resolverId.toString();
        if (!backing.contains(key)) return null;
        return new SlotStateBag(backing.getCompoundOrEmpty(key));
    }

    public boolean isEmpty() {
        return backing.isEmpty();
    }
}
