package com.trevlar.slotkit.api.item;

import net.minecraft.world.item.ItemStack;

/**
 * One item found by {@link ItemSources}, wherever it sits: a top-level slot, or inside a
 * shulker box or bundle, any number of containers deep. Writes go back up the chain of
 * containers to the top-level slot and mark its container changed.
 *
 * <p><b>Stale references never write.</b> A ref checks, before every write, that its
 * top-level slot still holds the stack it was found in and that no container on its
 * chain has changed since it was read (a click, a command, another mod). If anything
 * has, {@link #take} returns {@code EMPTY} and {@link #commit} returns {@code false},
 * with nothing written. Refs from one walk that share a container share its contents, so
 * several takes in one walk stay consistent.
 *
 * <p>Server side only, on the server thread.
 */
public interface ItemRef {

    /** Where a top-level slot is. */
    enum Source { INVENTORY, ENDER_CHEST }

    /**
     * The item. A top-level item is the live stack in its slot; a nested one is the
     * walk's working copy. It may be changed in place (a repair, a component), and then
     * {@link #commit} writes it back.
     */
    ItemStack stack();

    /**
     * Removes up to {@code count} of the item and writes the change back. Returns what
     * was removed, {@code EMPTY} when nothing was (an empty stack, or a stale ref).
     */
    ItemStack take(int count);

    /**
     * Writes an in-place change to {@link #stack()} back up the chain. Returns
     * {@code false}, writing nothing, if the ref is stale.
     */
    boolean commit();

    /** 0 for a top-level slot, 1 for an item inside a container there, and so on. */
    int depth();

    /** Whether the top-level slot is in the inventory or the ender chest. */
    Source source();
}
