package com.trevlar.slotkit.state;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Slot-state bag for one owner. Holds the state of every registered channel for one
 * persistent owner: a player for player-scoped keys, or one player's slice of a block
 * entity's or entity's per-player map.
 *
 * <p>Stored as a {@link CompoundTag}, so it is plain NBT that {@code /data get} can
 * read. Structure:
 *
 * <pre>
 * {
 *   "my-mod:sort_lock": {
 *     "5": &lt;Tag encoded via the channel's Codec using NbtOps&gt;,
 *     "12": &lt;Tag ...&gt;
 *   },
 *   "other-mod:annotation": {
 *     "3": &lt;Tag ...&gt;
 *   }
 * }
 * </pre>
 *
 * <p>The bag is mutable, and callers read and write it directly. Fabric serializes
 * the backing {@link CompoundTag} through the attachment's codec. The library syncs
 * through its own packets, not {@code syncWith} auto-sync.
 */
@ApiStatus.Internal
public final class SlotStateBag {

    /** Round-trips the bag through a CompoundTag, for attachment persistence. */
    public static final Codec<SlotStateBag> CODEC =
            CompoundTag.CODEC.xmap(SlotStateBag::new, SlotStateBag::backing);

    private final CompoundTag backing;

    public SlotStateBag() {
        this(new CompoundTag());
    }

    public SlotStateBag(CompoundTag backing) {
        this.backing = backing;
    }

    /** The underlying CompoundTag. Exposed for the codec only. */
    public CompoundTag backing() {
        return backing;
    }

    /**
     * Reads the stored Tag for a channel and container-relative slot index, or
     * {@code null} if none is stored. Callers decode it with the channel's
     * {@code Codec<T>.parse(NbtOps.INSTANCE, tag)}.
     */
    public @Nullable Tag read(Identifier channelId, int containerSlotIndex) {
        if (!backing.contains(channelId.toString())) return null;
        CompoundTag channelTag = backing.getCompoundOrEmpty(channelId.toString());
        return channelTag.get(String.valueOf(containerSlotIndex));
    }

    /** Writes the Tag at the channel and slot, replacing any existing value. */
    public void write(Identifier channelId, int containerSlotIndex, Tag value) {
        String cid = channelId.toString();
        CompoundTag channelTag = backing.getCompoundOrEmpty(cid);
        channelTag.put(String.valueOf(containerSlotIndex), value);
        backing.put(cid, channelTag);
    }

    /** Removes the stored value for the channel and slot, if any. */
    public void clear(Identifier channelId, int containerSlotIndex) {
        if (!backing.contains(channelId.toString())) return;
        CompoundTag channelTag = backing.getCompoundOrEmpty(channelId.toString());
        channelTag.remove(String.valueOf(containerSlotIndex));
        if (channelTag.isEmpty()) {
            backing.remove(channelId.toString());
        } else {
            backing.put(channelId.toString(), channelTag);
        }
    }

    /** Removes every entry under the given channel id. Used by /mkverify cleanup. */
    public void clearChannel(Identifier channelId) {
        backing.remove(channelId.toString());
    }

    /** True if no channel has any entry. */
    public boolean isEmpty() {
        return backing.isEmpty();
    }
}
