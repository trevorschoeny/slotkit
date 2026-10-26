package com.trevlar.slotkit.api.item;

import com.trevlar.slotkit.api.storage.StorageContainerAdapter;
import com.trevlar.slotkit.core.attachment.AttachedPlayerStorage;
import com.trevlar.slotkit.core.attachment.StorageAttachments;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
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
 * item one container deep in the inventory, then two deep; then each carried attachment
 * (in declaration order), then the ender chest, each in the same order. Within one
 * source a shallower item always comes before a deeper one.
 *
 * <h3>Carried attachments</h3>
 * A player attachment declared {@code playerAttached(...).carried()} holds things the
 * player carries (a pocket, a carried ender chest). Under {@link ItemScope#carried} its
 * slots are walked like the inventory's, with source {@link ItemRef.Source#CARRIED} and
 * paths naming the attachment. A client sees an attachment's contents when they reach it:
 * through a menu holding its slots (the player's inventory menu, for a slot added there
 * on both sides), or always for {@code playerAttachedVisible}.
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
 * <h3>Other containers</h3>
 * The {@code Container} overloads of {@code walk}, {@code find} and {@code at} do the same
 * from a container the caller supplies, such as the chest a player has open; their paths
 * have source {@link ItemRef.Source#CONTAINER}. Ordering across sources is the caller's.
 *
 * <h3>Menus on a nested item</h3>
 * {@link #live} binds a {@link LiveItem} to an item at a path, for a menu that stays open
 * on it across many writes and closes when it goes stale.
 *
 * <p>Server side only, on the server thread, except {@code find}.
 */
public final class ItemSources {

    private ItemSources() {}

    /** Visits each item in scope, nearest first, until the visitor returns false. */
    public static void walk(ServerPlayer player, ItemScope scope, ItemVisitor visitor) {
        walkPlayer(player, scope, visitor);
    }

    // The player's sources in order: inventory, carried attachments, ender chest. False when the visitor stopped.
    private static boolean walkPlayer(Player player, ItemScope scope, ItemVisitor visitor) {
        // The inventory's 36 main slots, hotbar (0 to 8) first: that's also slot order.
        if (!walkContainer(player.getInventory(), 36, ItemRef.Source.INVENTORY, null, scope, visitor)) return false;
        if (scope.carried()) {
            for (var e : StorageAttachments.carried().entrySet()) {
                Container c = new StorageContainerAdapter(e.getValue().bind(player));
                if (!walkContainer(c, c.getContainerSize(), ItemRef.Source.CARRIED, e.getKey(), scope, visitor)) return false;
            }
        }
        if (scope.enderChest()) {
            Container ender = player.getEnderChestInventory();
            return walkContainer(ender, ender.getContainerSize(), ItemRef.Source.ENDER_CHEST, null, scope, visitor);
        }
        return true;
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

    /** One item {@link #find} saw: where it is, and a copy of it ({@code path().depth()} is how deep). */
    public record Found(ItemPath path, ItemStack stack) {}

    /**
     * Every item in scope that {@code test} accepts, nearest first, read only: paths and
     * copies, nothing written. Safe on either side. On the client it reads the contents
     * that ride on the item stacks the client has. Vanilla never sends a client its
     * ender chest's contents outside the chest's own menu, so a client finds nothing
     * there; carried attachments are as the class notes say. A client sends a chosen
     * {@link Found#path()} to the server, which acts on it with {@link #at}.
     */
    public static List<Found> find(Player player, ItemScope scope, Predicate<ItemStack> test) {
        List<Found> out = new ArrayList<>();
        ItemVisitor collect = ref -> {
            if (test.test(ref.stack())) out.add(new Found(ref.path(), ref.stack().copy()));
            return true;
        };
        walkPlayer(player, scope, collect);
        return out;
    }

    /**
     * The live item at {@code path}, or {@code null} when it isn't there: a slot out of
     * range, an empty slot, an index past a container's contents, or a step through an
     * item that isn't a shulker box or bundle. For acting on a path a client chose: the
     * server checks the item is still what the client expects, then takes from it. The
     * reference is as fresh as a walk's, so a take writes back up the chain.
     */
    public static @Nullable ItemRef at(ServerPlayer player, ItemPath path) {
        Container container = containerOf(player, path);
        return container == null ? null : resolve(container, slotsOf(container, path.source()), path);
    }

    /**
     * The path of the item in a menu slot, for acting on a slot the player clicked: an
     * inventory main slot, an ender chest slot, or a slot over one of this player's carried
     * attachments. {@code null} for any other slot (armor, offhand, crafting, a chest).
     *
     * <p>Either side, with the same answer for the same slot: a client drawing a slot names
     * its item as the server will. A creative tab's slot names the slot it shows. On a
     * client an ender chest's menu slots aren't the player's ender chest (vanilla gives
     * the client a stand-in container), so there they are {@code null}: a mod that knows
     * the ender chest is open names them itself. Since 2.2.0.
     */
    public static @Nullable ItemPath pathOf(Player player, Slot shown) {
        Slot slot = com.trevlar.slotkit.core.Slots.target(shown);
        int i = slot.getContainerSlot();
        if (slot.container == player.getInventory()) return i < 36 ? new ItemPath(ItemRef.Source.INVENTORY, i, List.of()) : null;
        if (slot.container == player.getEnderChestInventory()) return new ItemPath(ItemRef.Source.ENDER_CHEST, i, List.of());
        if (slot.container instanceof StorageContainerAdapter a && a.getStorage() instanceof AttachedPlayerStorage s
                && s.owner() == player && StorageAttachments.carried().containsKey(s.attachment())) {
            return new ItemPath(ItemRef.Source.CARRIED, i, List.of(), s.attachment());
        }
        return null;
    }

    /**
     * The player's container a path starts in: the inventory, the ender chest, or a
     * carried attachment's contents. {@code null} for a supplied container's path, or an
     * attachment that isn't carried. A carried attachment's container may be a new object
     * on each call, but its stacks are the same objects as every other binding's (the
     * menu's slots, a shift-click), until something changes them, so a stack can be
     * tracked by identity from tick to tick. Since 2.2.0.
     */
    public static @Nullable Container containerOf(Player player, ItemPath path) {
        return switch (path.source()) {
            case INVENTORY -> player.getInventory();
            case ENDER_CHEST -> player.getEnderChestInventory();
            case CONTAINER -> null;   // the player can't name that container
            case CARRIED -> {
                var attachment = StorageAttachments.carried().get(path.attachment());
                yield attachment == null ? null : new StorageContainerAdapter(attachment.bind(player));
            }
        };
    }

    // A supplied container (an open chest, a barrel)

    /**
     * Visits each item in {@code container} and, with the scope's flags, the containers
     * inside them, nearest first, until the visitor returns false. Every slot of the
     * container is a top-level slot, in index order; the refs' source is
     * {@link ItemRef.Source#CONTAINER}. The scope's {@code enderChest} flag doesn't apply.
     * Server side only, on the server thread: pass the server's container (a chest's
     * block entity, or the container behind its menu).
     */
    public static void walk(Container container, ItemScope scope, ItemVisitor visitor) {
        walkContainer(container, container.getContainerSize(), ItemRef.Source.CONTAINER, null, scope, visitor);
    }

    /**
     * Every item in {@code container} that {@code test} accepts, as {@link #walk(Container,
     * ItemScope, ItemVisitor)} orders them, read only. Safe on either side: on the client,
     * pass the container behind the open screen's menu, which the server keeps in step.
     * Paths have source {@link ItemRef.Source#CONTAINER} and name the container's own slot
     * index; the server resolves one with {@link #at(Container, ItemPath)}.
     */
    public static List<Found> find(Container container, ItemScope scope, Predicate<ItemStack> test) {
        List<Found> out = new ArrayList<>();
        walk(container, scope, ref -> {
            if (test.test(ref.stack())) out.add(new Found(ref.path(), ref.stack().copy()));
            return true;
        });
        return out;
    }

    /**
     * The live item at a {@link ItemRef.Source#CONTAINER} path in {@code container}, or
     * {@code null} when it isn't there (as {@link #at(ServerPlayer, ItemPath)}) or the path's
     * source is another one. The caller supplies the container the path was found in,
     * typically the one behind the menu the player has open, so a client can't point the
     * server at a container of its choosing.
     */
    public static @Nullable ItemRef at(Container container, ItemPath path) {
        if (path.source() != ItemRef.Source.CONTAINER) return null;
        return resolve(container, container.getContainerSize(), path);
    }

    // Live items

    /**
     * A {@link LiveItem} bound to the item at {@code path} in the player's inventory, ender
     * chest or a carried attachment, for a menu that stays open on it across many writes. {@code null} when
     * the item isn't there, as {@link #at(ServerPlayer, ItemPath)}.
     */
    public static @Nullable LiveItem live(ServerPlayer player, ItemPath path) {
        Container container = containerOf(player, path);
        return container == null ? null : LiveItem.bind(container, slotsOf(container, path.source()), path);
    }

    /**
     * A {@link LiveItem} bound to the item at a {@link ItemRef.Source#CONTAINER} path in
     * {@code container}. {@code null} when the item isn't there, as {@link #at(Container, ItemPath)}.
     */
    public static @Nullable LiveItem live(Container container, ItemPath path) {
        if (path.source() != ItemRef.Source.CONTAINER) return null;
        return LiveItem.bind(container, container.getContainerSize(), path);
    }

    /** How many top-level slots a source has: the inventory's 36 main slots, or all of the container. */
    private static int slotsOf(Container container, ItemRef.Source source) {
        return source == ItemRef.Source.INVENTORY ? 36 : container.getContainerSize();
    }

    /** Follows {@code path} down from {@code container}'s first {@code slots} slots: a fresh ref, or null. */
    static @Nullable Ref resolve(Container container, int slots, ItemPath path) {
        if (path.depth() > ANYWHERE.maxDepth()) return null;
        if (path.slot() < 0 || path.slot() >= slots) return null;
        ItemStack top = container.getItem(path.slot());
        if (top.isEmpty()) return null;
        Ref ref = new Ref(new Top(container, path.slot(), top, path.source(), path.attachment()), null, -1, top, 0);
        for (int index : path.inside()) {
            Holder holder = ref.child();
            if (holder == null || index < 0 || index >= holder.items.size()) return null;
            ItemStack inner = holder.items.get(index);
            if (inner.isEmpty()) return null;
            ref = new Ref(ref.top, holder, index, inner, ref.depth + 1);
        }
        return ref;
    }

    // Every container kind, for following a path whatever scope found it.
    private static final ItemScope ANYWHERE = ItemScope.inventory().enderChest(true).shulkers(true).bundles(true);

    // The walk

    /** One top-level container, level by level. Returns false when the visitor stopped. */
    private static boolean walkContainer(Container container, int slots, ItemRef.Source source,
                                         @Nullable Identifier attachment, ItemScope scope, ItemVisitor visitor) {
        List<Ref> level = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) level.add(new Ref(new Top(container, i, stack, source, attachment), null, -1, stack, 0));
        }
        while (!level.isEmpty()) {
            for (Ref ref : level) {
                if (ref.stack().isEmpty()) continue;   // emptied by an earlier take this walk
                if (!visitor.visit(ref)) return false;
            }
            if (level.get(0).depth >= scope.maxDepth()) break;
            List<Ref> next = new ArrayList<>();
            for (Ref ref : level) {
                Holder holder = ref.child();
                if (holder == null || !(holder.bundle ? scope.bundles() : scope.shulkers())) continue;
                for (int i = 0; i < holder.items.size(); i++) {
                    ItemStack inner = holder.items.get(i);
                    if (!inner.isEmpty()) next.add(new Ref(ref.top, holder, i, inner, ref.depth + 1));
                }
            }
            level = next;
        }
        return true;
    }

    // The top-level slot a chain starts from, the stack it held when read, and its carried attachment's id if any.
    private record Top(Container container, int slot, ItemStack stack, ItemRef.Source source, @Nullable Identifier attachment) {
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
        @Nullable Object readFrom;       // the component object the list was read from (null: none yet)
        // Bumped by a put, which may reorder the list (a bundle inserts at the front): refs
        // read before it are stale, so none of them can write a list it no longer matches.
        int generation;

        private Holder(Ref owner, boolean bundle, List<ItemStack> items, @Nullable Object readFrom) {
            this.owner = owner;
            this.bundle = bundle;
            this.items = items;
            this.readFrom = readFrom;
        }

        /**
         * The holder for {@code ref}'s item if it's a single shulker box or bundle, or null.
         * A stack of several (stackable shulkers from a mod or a data pack) shares one
         * component, so writing into it would fill or empty every box at once.
         */
        static @Nullable Holder open(Ref ref) {
            ItemStack stack = ref.stack();
            if (stack.getCount() != 1) return null;
            if (isShulkerBox(stack)) {
                // An empty box may carry no component at all; it still has 27 slots to put into.
                ItemContainerContents c = stack.get(DataComponents.CONTAINER);
                List<ItemStack> items = new ArrayList<>(c == null ? List.of() : c.itemCopies().toList());
                while (items.size() < SHULKER_SLOTS) items.add(ItemStack.EMPTY);
                return new Holder(ref, false, items, c);
            }
            BundleContents c = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (c == null) return null;
            return new Holder(ref, true, new ArrayList<>(c.itemCopies().toList()), c);
        }

        /**
         * Puts what fits of {@code stack} in, by vanilla's rules, and writes back. Returns
         * what didn't fit. A shulker box takes anything that may go inside a container
         * item, merging into matching stacks first, then empty slots. A bundle takes what
         * its weight allows, the way vanilla's bundle insert does (newest first).
         */
        ItemStack put(ItemStack stack) {
            ItemStack rest = stack.copy();
            if (bundle) {
                BundleContents.Mutable m = new BundleContents(items.stream()
                        .filter(s -> !s.isEmpty()).map(ItemStackTemplate::fromNonEmptyStack).toList()).asMutable();
                int moved = m.tryInsert(stack.copy());
                if (moved <= 0) return rest;
                rest.shrink(moved);
                items.clear();
                items.addAll(m.toImmutable().itemCopies().toList());
            } else {
                if (!stack.getItem().canFitInsideContainerItems()) return rest;
                int max = stack.getMaxStackSize();
                // Only a box's 27 slots: a placed box loads no more, so anything past them is lost.
                int slots = Math.min(items.size(), SHULKER_SLOTS);
                for (int i = 0; i < slots && !rest.isEmpty(); i++) {   // merge into matching stacks first
                    ItemStack in = items.get(i);
                    if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest) && in.getCount() < max) {
                        int n = Math.min(max - in.getCount(), rest.getCount());
                        in.grow(n);
                        rest.shrink(n);
                    }
                }
                for (int i = 0; i < slots && !rest.isEmpty(); i++) {   // then empty slots
                    if (items.get(i).isEmpty()) items.set(i, rest.split(Math.min(max, rest.getCount())));
                }
                if (rest.getCount() == stack.getCount()) return rest;
            }
            generation++;
            write();
            return rest;
        }

        /** True while this holder's component is still the one its list was read from, all the way up. */
        boolean unchanged() {
            Object now = owner.stack().get(bundle ? DataComponents.BUNDLE_CONTENTS : DataComponents.CONTAINER);
            // A box with no component yet reads as null; one that gained contents since is changed.
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
    static final class Ref implements ItemRef {
        final Top top;
        final @Nullable Holder holder;
        final int index;
        final ItemStack stack;
        final int depth;
        final int generation;                  // the holder's generation when this ref was read
        private @Nullable Holder child;        // this item's own contents, opened once and shared
        private boolean childOpened;

        Ref(Top top, @Nullable Holder holder, int index, ItemStack stack, int depth) {
            this.top = top;
            this.holder = holder;
            this.index = index;
            this.stack = stack;
            this.depth = depth;
            this.generation = holder == null ? 0 : holder.generation;
        }

        /** This item's contents if it's a shulker box or bundle, or null. Every ref into it shares one. */
        @Nullable Holder child() {
            if (!childOpened) { child = Holder.open(this); childOpened = true; }
            return child;
        }

        @Override public ItemStack stack() { return stack; }
        @Override public int depth() { return depth; }
        @Override public Source source() { return top.source(); }

        @Override
        public ItemPath path() {
            if (holder == null) return new ItemPath(top.source(), top.slot(), List.of(), top.attachment());
            ItemPath up = holder.owner.path();
            List<Integer> inside = new ArrayList<>(up.inside());
            // A bundle's component drops emptied stacks while this walk's list keeps them in
            // place, so its index counts only the stacks before this one that are still there.
            int at = index;
            if (holder.bundle) for (int i = 0; i < index; i++) if (holder.items.get(i).isEmpty()) at--;
            inside.add(at);
            return new ItemPath(up.source(), up.slot(), inside, up.attachment());
        }

        /** Nothing on this ref's chain changed since it was read. */
        boolean unchanged() {
            return holder == null ? top.stillThere() : holder.generation == generation && holder.unchanged();
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
        public ItemStack put(ItemStack stack) {
            Holder into = child();
            if (stack.isEmpty() || into == null || !unchanged() || !into.unchanged()) return stack.copy();
            return into.put(stack);
        }

        @Override
        public boolean commit() {
            if (!unchanged()) return false;
            write();
            return true;
        }

        /**
         * Puts a copy of {@code with} in this nested item's place and writes up the chain.
         * For {@link LiveItem}, whose working stack is its own object, not the walk's.
         */
        boolean replace(ItemStack with) {
            if (holder == null || !unchanged()) return false;
            holder.items.set(index, with.copy());
            holder.write();
            return true;
        }
    }

    private static final int SHULKER_SLOTS = 27;

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
}
