package com.trevlar.slotkit.api.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Where items live: get, set, size and markDirty.
 *
 * <p>Storage does not know who owns it, when to save, or which player it belongs
 * to. Those belong to whatever binds it, such as {@code StorageAttachment}.
 *
 * <p>When slots are built, each Storage is wrapped in vanilla's {@code Container}
 * interface ({@code StorageContainerAdapter}). Consumers see only Storage.
 *
 * <p>For persistent storage (on a block entity, a player or an item), see
 * {@code StorageAttachment}, whose factories produce Storage views backed by Fabric
 * attachments or data components that save themselves, or a session-only list
 * ({@code StorageAttachment.ephemeral}).
 */
public interface Storage {

    /** Returns the stack at the given local index. */
    ItemStack getStack(int localIndex);

    /** Sets the stack at the given local index. */
    void setStack(int localIndex, ItemStack stack);

    /** Returns the number of slots in this storage. */
    int size();

    /** Marks this storage as changed. */
    void markDirty();
}
