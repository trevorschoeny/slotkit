package com.trevlar.slotkit.api.storage;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * In-memory storage that is never saved. Its contents are dropped through the usual
 * {@code dropInventory} pattern when the screen closes.
 */
public class EphemeralStorage implements Storage {

    private final NonNullList<ItemStack> items;

    public EphemeralStorage(int size) {
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Same as the constructor; reads better in builder chains. */
    public static EphemeralStorage of(int size) {
        return new EphemeralStorage(size);
    }

    @Override
    public ItemStack getStack(int localIndex) {
        return items.get(localIndex);
    }

    @Override
    public void setStack(int localIndex, ItemStack stack) {
        items.set(localIndex, stack);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public void markDirty() {
        // Nothing is saved, so nothing to mark.
    }

    /**
     * Reads all items and clears the storage in one step. Returns the items for the
     * caller to drop into the world or move elsewhere.
     */
    public NonNullList<ItemStack> drainContents() {
        NonNullList<ItemStack> collected = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            collected.set(i, items.get(i).copy());
            items.set(i, ItemStack.EMPTY);
        }
        return collected;
    }
}
