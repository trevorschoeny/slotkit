package com.trevlar.slotkit.api.storage;

import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * Storage backed by a read function and a write function, so a consumer can
 * bring any external data source into MenuKit's {@link Storage} interface.
 *
 * <p>Implements plain {@link Storage}, not {@code PersistentStorage}: saving is
 * up to the external source.
 *
 * <p>Replaces the old {@code MKContainerSource} and {@code LiveContainerSource},
 * where consumers supplied their own read and write callbacks.
 */
public class VirtualStorage implements Storage {

    private final int size;
    private final IntFunction<ItemStack> getter;
    private final BiConsumer<Integer, ItemStack> setter;
    private final Runnable dirtyCallback;

    /**
     * @param size          number of slots
     * @param getter        (localIndex) -> stack at that index
     * @param setter        (localIndex, stack) -> write stack at that index
     * @param dirtyCallback called when markDirty() is invoked
     */
    public VirtualStorage(int size, IntFunction<ItemStack> getter,
                          BiConsumer<Integer, ItemStack> setter,
                          Runnable dirtyCallback) {
        this.size = size;
        this.getter = getter;
        this.setter = setter;
        this.dirtyCallback = dirtyCallback;
    }

    /** With a dirty callback that does nothing. */
    public VirtualStorage(int size, IntFunction<ItemStack> getter,
                          BiConsumer<Integer, ItemStack> setter) {
        this(size, getter, setter, () -> {});
    }

    @Override
    public ItemStack getStack(int localIndex) {
        return getter.apply(localIndex);
    }

    @Override
    public void setStack(int localIndex, ItemStack stack) {
        setter.accept(localIndex, stack);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void markDirty() {
        dirtyCallback.run();
    }
}
