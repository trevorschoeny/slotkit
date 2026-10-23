package com.trevlar.slotkit.api.item;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * One item at an {@link ItemPath}, held for as long as a menu stays open on it: a shulker
 * box in a chest, or a bundle in a shulker in a chest. Unlike an {@link ItemRef}, which a
 * single write uses up, a live item takes any number of writes, each going back up the
 * chain of containers to the top-level slot, and says when it has gone stale so the
 * menu can close. Get one from {@link ItemSources#live}.
 *
 * <pre>{@code
 * LiveItem bundle = ItemSources.live(chest, path);
 * // The mod's own storage over an item stack, kept in step with the chain:
 * Storage slots = bundle.storage(new MyBundleStorage(bundle.stack()));
 * // In the menu: stillValid(player) returns !bundle.isStale()
 * }</pre>
 *
 * <h3>The item</h3>
 * {@link #stack()} is one object for the item's whole life, so a storage built over it
 * (one that reads and sets the item's {@code minecraft:container} or
 * {@code minecraft:bundle_contents}) can hold on to it. At depth 0 it is the live stack in
 * the slot. Deeper, it is a working copy, written up the chain by {@link #commit}.
 *
 * <h3>Stale</h3>
 * The item is tied to itself, not to its place. It goes stale, for good, when the
 * top-level slot no longer holds the stack it held (the stack moved or was replaced), or,
 * for a nested item, when the item at its path is no longer the item as last read or
 * written: gone, moved, or changed by anything else (another player, a command, a mod).
 * A change elsewhere on the chain (another item in the same shulker box) is read and
 * passed over. A stale item writes nothing.
 *
 * <p><b>Close the menu on stale.</b> Have the menu's {@code stillValid} return
 * {@code !isStale()}: vanilla then refuses the player's clicks into it and closes it on
 * the next tick, so no click lands in a storage that would drop it.
 *
 * <p>Server side only, on the server thread.
 */
public final class LiveItem {

    private final Container container;
    private final int slots;                 // how many of the container's slots its source covers
    private final ItemPath path;
    private final ItemStack top;             // the top-level stack, by identity
    private final ItemStack stack;           // the item: top itself at depth 0, else a working copy
    private ItemStack known;                 // nested: the item as last read or written
    private @Nullable Object seenContainer;  // nested: the top's contents components as last read or written;
    private @Nullable Object seenBundle;     // while both are the same objects, nothing on the chain changed
    private boolean stale;

    private LiveItem(Container container, int slots, ItemPath path, ItemStack top, ItemStack stack) {
        this.container = container;
        this.slots = slots;
        this.path = path;
        this.top = top;
        this.stack = stack;
        this.known = stack.copy();
        seen();
    }

    /** Binds to the item at {@code path} now, or returns null when it isn't there. */
    static @Nullable LiveItem bind(Container container, int slots, ItemPath path) {
        ItemSources.Ref ref = ItemSources.resolve(container, slots, path);
        if (ref == null) return null;
        ItemStack top = container.getItem(path.slot());
        return new LiveItem(container, slots, path, top, path.depth() == 0 ? top : ref.stack().copy());
    }

    /** The item. The same object for as long as this is bound; see the class notes. */
    public ItemStack stack() { return stack; }

    /** Where the item is. */
    public ItemPath path() { return path; }

    /**
     * Checks the chain now: true once the item is gone, moved or changed by anything else.
     * Once stale, always stale. Cheap when nothing on the chain changed.
     */
    public boolean isStale() {
        if (stale) return true;
        if (container.getItem(path.slot()) != top) return stale = true;
        if (path.depth() == 0 || chainUnchanged()) return false;
        // Something on the chain changed: still fine if the item at the path is the one we know.
        ItemSources.Ref ref = ItemSources.resolve(container, slots, path);
        if (ref == null || !ItemStack.matches(ref.stack(), known)) return stale = true;
        seen();
        return false;
    }

    /**
     * Writes a change to {@link #stack()} back up the chain to the top-level slot and
     * marks its container changed. Returns {@code false}, writing nothing, when stale.
     */
    public boolean commit() {
        if (isStale()) return false;
        if (path.depth() == 0) {
            container.setChanged();
            return true;
        }
        ItemSources.Ref ref = ItemSources.resolve(container, slots, path);
        if (ref == null || !ref.replace(stack)) return stale = true;
        known = stack.copy();
        seen();
        // The item may now be gone from its place (emptied): the next check says so.
        return true;
    }

    /**
     * {@code over}, a storage built on {@link #stack()}, kept in step with the chain: every
     * write through it is committed up the chain, and once the item is stale reads are
     * empty and writes are dropped (the menu closes on stale; see the class notes).
     */
    public Storage storage(Storage over) {
        return new Storage() {
            @Override public ItemStack getStack(int i) {
                return isStale() ? ItemStack.EMPTY : over.getStack(i);
            }
            @Override public void setStack(int i, ItemStack s) {
                if (isStale()) return;
                over.setStack(i, s);
                commit();
            }
            @Override public int size() { return over.size(); }
            @Override public void markDirty() {
                // A stack handed out by getStack may have changed in place.
                if (isStale()) return;
                over.markDirty();
                commit();
            }
        };
    }

    private boolean chainUnchanged() {
        return top.get(DataComponents.CONTAINER) == seenContainer && top.get(DataComponents.BUNDLE_CONTENTS) == seenBundle;
    }

    private void seen() {
        seenContainer = top.get(DataComponents.CONTAINER);
        seenBundle = top.get(DataComponents.BUNDLE_CONTENTS);
    }
}
