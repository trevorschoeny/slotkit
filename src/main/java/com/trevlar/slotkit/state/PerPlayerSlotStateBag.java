package com.trevlar.slotkit.state;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;

/**
 * State bag per player per owner. Wraps a {@link CompoundTag} whose keys are player
 * UUID strings (plus the reserved {@link #SHARED_KEY} for SHARED channels) and whose
 * values are {@link SlotStateBag}s. Attached to block entities and entities, so each
 * viewing player has a private set of marks on a shared container. Shared channels
 * are the exception: every viewer resolves them to one bag.
 *
 * <p>This shape was chosen so shared state needed no data rewrite: a shared channel
 * just skips the UUID layer ({@link #getOrCreateShared()}).
 */
@ApiStatus.Internal
public final class PerPlayerSlotStateBag {

    /**
     * Reserved key for the shared bag that belongs to no one player. Not a valid UUID
     * string, so it never collides with a per-player entry. It lives in the same
     * backing tag, so the block-to-item travel component carries it with the rest.
     */
    private static final String SHARED_KEY = "shared";

    /** Round-trips the bag through a CompoundTag. */
    public static final Codec<PerPlayerSlotStateBag> CODEC =
            CompoundTag.CODEC.xmap(PerPlayerSlotStateBag::new, PerPlayerSlotStateBag::backing);

    private final CompoundTag backing;

    public PerPlayerSlotStateBag() {
        this(new CompoundTag());
    }

    public PerPlayerSlotStateBag(CompoundTag backing) {
        this.backing = backing;
    }

    public CompoundTag backing() {
        return backing;
    }

    /**
     * Returns the bag for {@code playerId}, creating an empty one if absent. The
     * returned bag writes through to the underlying CompoundTag.
     */
    public SlotStateBag getOrCreate(UUID playerId) {
        String key = playerId.toString();
        CompoundTag playerTag = backing.getCompoundOrEmpty(key);
        // Write back so the sub-tag sticks even if getCompoundOrEmpty
        // returned a fresh empty tag.
        backing.put(key, playerTag);
        return new SlotStateBag(playerTag);
    }

    /**
     * Returns the shared bag, creating it if absent. SHARED channels resolve here
     * instead of {@link #getOrCreate(UUID)}, so every viewer reads and writes one
     * value. Same as {@link #getOrCreate}, under the reserved {@link #SHARED_KEY}.
     */
    public SlotStateBag getOrCreateShared() {
        CompoundTag sharedTag = backing.getCompoundOrEmpty(SHARED_KEY);
        backing.put(SHARED_KEY, sharedTag);
        return new SlotStateBag(sharedTag);
    }

    /** The shared bag, or {@code null} when none is stored. Creates nothing (reads never write). */
    public @org.jspecify.annotations.Nullable SlotStateBag getShared() {
        if (!backing.contains(SHARED_KEY)) return null;
        return new SlotStateBag(backing.getCompoundOrEmpty(SHARED_KEY));
    }

    /** Returns the bag for {@code playerId} or {@code null} if no entry exists. */
    public SlotStateBag get(UUID playerId) {
        String key = playerId.toString();
        if (!backing.contains(key)) return null;
        return new SlotStateBag(backing.getCompoundOrEmpty(key));
    }

    public boolean isEmpty() {
        return backing.isEmpty();
    }
}
