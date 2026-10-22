package com.trevlar.slotkit.core.attachment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Drops the contents of every block-scoped storage attachment at a block entity's
 * position before the block entity is destroyed. Called from
 * {@code com.trevlar.slotkit.mixin.SKBlockDropMixin} on block removal,
 * only when the block is actually removed and not just changing state.
 *
 * <p>This gives storage attachments the same drop-on-break behavior as a vanilla
 * container. Consumers who want the contents lost instead declare their attachment
 * with {@code StorageAttachment.custom(...)} rather than {@code blockScoped(...)},
 * since the custom path bypasses this registry.
 */
public final class BlockScopedDropHandler {

    private BlockScopedDropHandler() {}

    /**
     * For every block-scoped attachment on this block entity, drops its content as
     * item entities at the given position. Attachments with no stored content drop
     * nothing.
     *
     * <p>Called with a live {@link Level} before the block entity is destroyed, while
     * attachments are still readable. Safe to call from an {@code onRemove} HEAD
     * injection.
     */
    public static void dropAllAt(Level level, BlockPos pos, BlockEntity be) {
        if (level == null || be == null) return;
        // Server only: the server owns ItemEntity spawns, and client-side
        // block-removal prediction must not spawn ghost entities.
        if (level.isClientSide()) return;

        for (AttachmentType<ItemContainerContents> type : StorageAttachments.blockScopedAttachments()) {
            if (!be.hasAttached(type)) continue;
            ItemContainerContents contents = be.getAttached(type);
            if (contents == null) continue;

            int slotCount = StorageAttachments.slotCountOf(type);
            if (slotCount <= 0) continue;

            // ItemContainerContents trims trailing empties, so its size may be
            // less than slotCount. contentsToList pads the list back out with EMPTY.
            NonNullList<ItemStack> items =
                    StorageAttachments.contentsToList(contents, slotCount);

            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            stack);
                }
            }
        }
    }
}
