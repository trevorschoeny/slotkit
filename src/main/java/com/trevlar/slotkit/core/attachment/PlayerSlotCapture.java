package com.trevlar.slotkit.core.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * What grave-mod adapters call. Covers every death-droppable player content slot
 * (the {@code PLAYER_DEATH_DROP} registry). An adapter captures the slots (reads and
 * clears them) into the grave's data and restores them on collect through this
 * class, never slot by slot. So each grave mod needs one adapter, and every
 * consumer's slots (pockets, equipment and so on) go through it.
 *
 * <p>Grave mods capture before vanilla drops the inventory (Universal Graves
 * injects ahead of {@code dropEquipment}), so {@link #captureAndClear} runs before
 * the {@code dropEquipment} fallback, which then finds the slots empty and drops
 * nothing. That avoids duplicates without any explicit suppression. The clear here
 * is the same empty write-back the fallback uses, so respawn's {@code copyOnDeath}
 * carries an empty bag.
 */
public final class PlayerSlotCapture {

    private PlayerSlotCapture() {}

    /**
     * One captured slot: its stable identity (the attachment's registered
     * {@link Identifier} and the local slot index) plus the stack. The id lets it be
     * serialized for grave mods that store NBT.
     */
    public record CapturedSlot(Identifier attachmentId, int index, ItemStack stack) {
        public static final Codec<CapturedSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("attachment").forGetter(CapturedSlot::attachmentId),
                Codec.INT.fieldOf("slot").forGetter(CapturedSlot::index),
                ItemStack.CODEC.fieldOf("stack").forGetter(CapturedSlot::stack)
        ).apply(i, CapturedSlot::new));

        /** Codec for a whole capture, which is what a grave that stores NBT keeps. */
        public static final Codec<List<CapturedSlot>> LIST_CODEC = CODEC.listOf();
    }

    /**
     * For every death-droppable player content slot on {@code player}, reads the
     * non-empty stack and clears the slot. Returns the captured slots for the
     * adapter to put in the grave. Because the slots are cleared, the fallback drop
     * finds them empty and does nothing. Server side.
     */
    public static List<CapturedSlot> captureAndClear(ServerPlayer player) {
        List<CapturedSlot> captured = new ArrayList<>();
        for (AttachmentType<ItemContainerContents> type : StorageAttachments.playerDeathDropAttachments()) {
            if (!player.hasAttached(type)) continue;
            ItemContainerContents contents = player.getAttached(type);
            if (contents == null) continue;
            int slotCount = StorageAttachments.slotCountOf(type);
            if (slotCount <= 0) continue;
            Identifier id = StorageAttachments.identifierOf(type);
            if (id == null) continue;

            NonNullList<ItemStack> items = StorageAttachments.contentsToList(contents, slotCount);
            boolean changed = false;
            for (int i = 0; i < slotCount; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;
                captured.add(new CapturedSlot(id, i, stack.copy()));
                items.set(i, ItemStack.EMPTY);
                changed = true;
            }
            if (changed) {
                player.setAttached(type, StorageAttachments.listToContents(items));
            }
        }
        return captured;
    }

    /**
     * Writes a captured stack back to its slot on grave collect. Does nothing and
     * returns false if the attachment id is unknown or the index is out of range,
     * meaning the slot layout changed since capture (the consumer reconfigured or
     * removed the attachment). The caller handles that overflow, for example by
     * dropping it.
     *
     * @return true if restored to the slot; false if it couldn't be placed
     */
    public static boolean restore(ServerPlayer player, CapturedSlot slot) {
        AttachmentType<ItemContainerContents> type = StorageAttachments.typeById(slot.attachmentId());
        if (type == null) return false;
        int slotCount = StorageAttachments.slotCountOf(type);
        if (slot.index() < 0 || slot.index() >= slotCount) return false;

        ItemContainerContents contents = player.hasAttached(type)
                ? player.getAttached(type)
                : StorageAttachments.defaultEmpty(slotCount);
        NonNullList<ItemStack> items = StorageAttachments.contentsToList(contents, slotCount);
        items.set(slot.index(), slot.stack());
        player.setAttached(type, StorageAttachments.listToContents(items));
        return true;
    }

    /**
     * Restores a whole capture. Returns the stacks that could not be placed (unknown
     * attachment or out-of-range index) so the caller can drop them.
     */
    public static List<ItemStack> restoreAll(ServerPlayer player, List<CapturedSlot> slots) {
        List<ItemStack> overflow = new ArrayList<>();
        for (CapturedSlot s : slots) {
            if (!restore(player, s)) overflow.add(s.stack());
        }
        return overflow;
    }
}
