package com.trevlar.slotkit.api.storage;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Adapts a {@link Storage} to vanilla's {@link Container} interface. Needed
 * when building slots, because {@link net.minecraft.world.inventory.Slot}'s
 * constructor takes a Container while consumers declare storage through the
 * narrower {@link Storage} interface.
 *
 * <p>{@link com.trevlar.slotkit.api.menu.CustomContainerMenu} uses it for
 * its own slots. Consumers adding slots to vanilla handlers also wrap a
 * Storage in this adapter when calling {@code addSlot} with a
 * {@link net.minecraft.world.inventory.Slot}.
 */
public class StorageContainerAdapter implements Container {

    private final Storage storage;

    public StorageContainerAdapter(Storage storage) {
        this.storage = storage;
    }

    /**
     * Returns the backing {@link Storage}. Exposed so internal code (notably
     * {@code ContainerIdentity}) can check what kind of storage it is, such as a
     * {@link KeyedStorage}, without going through {@link Container}. Consumer code
     * should almost always use the {@link Container} methods instead.
     */
    public Storage getStorage() {
        return storage;
    }

    @Override public int getContainerSize() { return storage.size(); }

    @Override
    public int getMaxStackSize() {
        return 99; // vanilla SimpleContainer default; items cap themselves
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < storage.size(); i++) {
            if (!storage.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) { return storage.getStack(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = storage.getStack(slot);
        if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack removed = current.split(amount);
        if (current.isEmpty()) storage.setStack(slot, ItemStack.EMPTY);
        else storage.setStack(slot, current);
        storage.markDirty();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = storage.getStack(slot);
        storage.setStack(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) { storage.setStack(slot, stack); }

    @Override public void setChanged() { storage.markDirty(); }
    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void clearContent() {
        for (int i = 0; i < storage.size(); i++) {
            storage.setStack(i, ItemStack.EMPTY);
        }
    }
}
