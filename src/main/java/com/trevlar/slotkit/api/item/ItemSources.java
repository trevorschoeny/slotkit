package com.trevlar.slotkit.api.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every item a player has, including the ones inside shulker boxes and bundles, to any
 * depth: one walk, with every write going back up the chain of containers to the
 * top-level slot. What to look for and what to do with it stay with the caller.
 *
 * <pre>{@code
 * // Take one totem from anywhere in scope, nearest first.
 * ItemStack used = ItemSources.take(player, scope, s -> s.has(DataComponents.DEATH_PROTECTION), 1);
 *
 * // Visit everything; return false to stop.
 * ItemSources.walk(player, scope, ref -> { ...; return true; });
 * }</pre>
 *
 * <h3>Order</h3>
 * Nearest first, level by level: the inventory's own slots (hotbar first), then every
 * item one container deep in the inventory, then two deep; then the ender chest in the
 * same order. A shallower item always comes before a deeper one, whichever container
 * it's in.
 *
 * <h3>Containers</h3>
 * A shulker box item's {@code minecraft:container} contents (written back with
 * {@code ItemContainerContents.fromItems}, so empty slots keep their places) and a
 * bundle's {@code minecraft:bundle_contents} (rebuilt in the same order;
 * {@code copyWithContents} would reverse it). Other items with contents are not walked.
 *
 * <h3>Open screens</h3>
 * A write sets a new component on the live top-level stack. Item-backed storage
 * ({@code StorageAttachment.itemContainer}, and any storage that rereads when the
 * component object changes) shows the change on its next read, so a screen showing the
 * same shulker stays in step.
 *
 * <p>Server side only, on the server thread.
 */
public final class ItemSources {

    private ItemSources() {}

    /** Visits each item in scope, nearest first, until the visitor returns false. */
    public static void walk(ServerPlayer player, ItemScope scope, ItemVisitor visitor) {
        // The inventory's 36 main slots, hotbar (0 to 8) first: that's also slot order.
        if (!walkContainer(player.getInventory(), 36, ItemRef.Source.INVENTORY, scope, visitor)) return;
        if (scope.enderChest()) {
            Container ender = player.getEnderChestInventory();
            walkContainer(ender, ender.getContainerSize(), ItemRef.Source.ENDER_CHEST, scope, visitor);
        }
    }

    /**
     * Takes {@code count} of the first item, nearest first, that {@code test} accepts.
     * Returns what was taken, {@code EMPTY} if no item matched. Takes from one stack only.
     */
    public static ItemStack take(ServerPlayer player, ItemScope scope, Predicate<ItemStack> test, int count) {
        ItemStack[] taken = { ItemStack.EMPTY };
        walk(player, scope, ref -> {
            if (!test.test(ref.stack())) return true;
            taken[0] = ref.take(count);
            return taken[0].isEmpty();               // keep going only if this one gave nothing
        });
        return taken[0];
    }

    // ── The walk ────────────────────────────────────────────────────────

    /** One top-level container, level by level. Returns false when the visitor stopped. */
    private static boolean walkContainer(Container container, int slots, ItemRef.Source source,
                                         ItemScope scope, ItemVisitor visitor) {
        List<Ref> level = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) level.add(new Ref(new Top(container, i, stack, source), null, -1, stack, 0));
        }
        while (!level.isEmpty()) {
            for (Ref ref : level) {
                if (ref.stack().isEmpty()) continue;   // emptied by an earlier take this walk
                if (!visitor.visit(ref)) return false;
            }
            if (level.get(0).depth >= scope.maxDepth()) break;
            List<Ref> next = new ArrayList<>();
            for (Ref ref : level) {
                Holder holder = Holder.open(ref, scope);
                if (holder == null) continue;
                for (int i = 0; i < holder.items.size(); i++) {
                    ItemStack inner = holder.items.get(i);
                    if (!inner.isEmpty()) next.add(new Ref(ref.top, holder, i, inner, ref.depth + 1));
                }
            }
            level = next;
        }
        return true;
    }

    /** The top-level slot a chain starts from, and the stack it held when read. */
    private record Top(Container container, int slot, ItemStack stack, ItemRef.Source source) {
        boolean stillThere() { return container.getItem(slot) == stack; }
    }

    /**
     * A shulker box or bundle being walked: its contents as a list of working copies,
     * read from its component, and the component object that list came from.
     */
    private static final class Holder {
        final Ref owner;                 // the ref of the shulker box or bundle itself
        final boolean bundle;
        final List<ItemStack> items;
        Object readFrom;                 // the component object the list was read from

        private Holder(Ref owner, boolean bundle, List<ItemStack> items, Object readFrom) {
            this.owner = owner;
            this.bundle = bundle;
            this.items = items;
            this.readFrom = readFrom;
        }

        /** The holder for {@code ref}'s item if it's a container in scope, or null. */
        static @Nullable Holder open(Ref ref, ItemScope scope) {
            ItemStack stack = ref.stack();
            if (scope.shulkers() && isShulkerBox(stack)) {
                ItemContainerContents c = stack.get(DataComponents.CONTAINER);
                if (c == null) return null;
                return new Holder(ref, false, new ArrayList<>(c.itemCopies().toList()), c);
            }
            if (scope.bundles()) {
                BundleContents c = stack.get(DataComponents.BUNDLE_CONTENTS);
                if (c == null) return null;
                return new Holder(ref, true, new ArrayList<>(c.itemCopies().toList()), c);
            }
            return null;
        }

        /** True while this holder's component is still the one its list was read from, all the way up. */
        boolean unchanged() {
            Object now = owner.stack().get(bundle ? DataComponents.BUNDLE_CONTENTS : DataComponents.CONTAINER);
            return now == readFrom && owner.unchanged();
        }

        /** Writes the list back onto the holder's item, then the holder's own chain. */
        void write() {
            ItemStack stack = owner.stack();
            if (bundle) {
                // In the same order: copyWithContents would reverse the bundle. Emptied stacks drop out.
                stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items.stream()
                        .filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList()));
                readFrom = stack.get(DataComponents.BUNDLE_CONTENTS);
            } else {
                // fromItems keeps every item in its own slot, empties included.
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
                readFrom = stack.get(DataComponents.CONTAINER);
            }
            owner.write();
        }
    }

    /** An item: a top-level slot's live stack (holder null), or entry {@code index} of a holder's list. */
    private static final class Ref implements ItemRef {
        final Top top;
        final @Nullable Holder holder;
        final int index;
        final ItemStack stack;
        final int depth;

        Ref(Top top, @Nullable Holder holder, int index, ItemStack stack, int depth) {
            this.top = top;
            this.holder = holder;
            this.index = index;
            this.stack = stack;
            this.depth = depth;
        }

        @Override public ItemStack stack() { return stack; }
        @Override public int depth() { return depth; }
        @Override public Source source() { return top.source(); }

        /** Nothing on this ref's chain changed since it was read. */
        boolean unchanged() {
            return holder == null ? top.stillThere() : holder.unchanged();
        }

        /** Writes this item's place back: its holder's list (it's already in it), then up. */
        void write() {
            if (holder == null) top.container().setChanged();
            else holder.write();
        }

        @Override
        public ItemStack take(int count) {
            if (count <= 0 || stack.isEmpty() || !unchanged()) return ItemStack.EMPTY;
            ItemStack taken = stack.split(count);
            write();
            return taken;
        }

        @Override
        public boolean commit() {
            if (!unchanged()) return false;
            write();
            return true;
        }
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
}
