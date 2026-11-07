package com.trevlar.slotkit.api.window;

import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.item.ItemPath;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.Container;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * What automation asks before it moves items: a hopper, a dropper, a copper golem, or a
 * mod's own (a golem that sorts into the shulker boxes inside a chest). Each question is
 * the slot's {@link BehaviorKeys#AUTOMATION_PUT} or {@link BehaviorKeys#AUTOMATION_TAKE}
 * with no player, so the cascade and every veto for both modes speak, the put vetoes with
 * the stack: a reservation, a mod's lock. SlotKit asks these at vanilla's hoppers,
 * droppers, dispensers and copper golems; a mod that moves items itself asks them per
 * slot, and gets the same answers. Server side. Since 3.0.0.
 *
 * <pre>{@code
 * int n = Automation.room(chest, slot, held);           // 0: skip this slot
 * int m = Automation.room(boxInChest, i, held);         // a slot inside a shulker box item
 * }</pre>
 */
public final class Automation {

    private Automation() {}

    /**
     * How many of {@code incoming} automation may put into {@code container}'s slot now:
     * 0 when the slot refuses it or holds a different stack, else what fits under the
     * slot's limit (the stack size, or a {@link SlotOperations.Limit}'s cap) beside what is there. Doesn't
     * ask the container's own {@code canPlaceItem}; vanilla's movers already do.
     */
    public static int room(Container container, int slot, ItemStack incoming) {
        if (incoming.isEmpty()) return 0;
        SlotRef ref = SlotRef.of(container, slot, null);
        if (!SlotOperations.allowsPut(ref, BehaviorKeys.AUTOMATION_PUT, incoming)) return 0;
        ItemStack there = container.getItem(slot);
        int limit = Math.min(incoming.getMaxStackSize(), SlotOperations.limitOf(ref, incoming));
        if (there.isEmpty()) return Math.min(incoming.getCount(), limit);
        if (!ItemStack.isSameItemSameComponents(there, incoming)) return 0;
        return Math.max(0, Math.min(incoming.getCount(), limit - there.getCount()));
    }

    /**
     * {@link #room(Container, int, ItemStack)} for slot {@code index} inside {@code box}, an
     * item holding a container (a shulker box in a chest), judged by what the box carries:
     * its reservations, a mod's slot state on it. Reads only; the caller writes the box.
     */
    public static int room(ItemStack box, int index, ItemStack incoming) {
        return room(new HeldContents(box), index, incoming);
    }

    /** Whether automation may take items out of {@code container}'s slot. */
    public static boolean mayTake(Container container, int slot) {
        return SlotOperations.allows(SlotRef.of(container, slot, null), BehaviorKeys.AUTOMATION_TAKE);
    }

    /**
     * Undoes what a mover put where it may not, after the fact: {@code before} is each slot
     * of {@code container} as it was, {@code held} what the mover carried, {@code left} what
     * it kept. A slot (or a slot inside a box item in it) that grew by more than
     * {@link #room} allows goes back to what it held plus what was allowed, and the rest goes
     * to the first slots of that same container (or box) that take it, then back to the
     * mover. Returns what the mover keeps. How a copper golem is held to the rules whichever
     * mod chose its slots (Savvy Copper Golems sorts into the boxes inside a chest).
     */
    @ApiStatus.Internal
    public static ItemStack settle(Container container, ItemStack[] before, ItemStack held, ItemStack left) {
        int back = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack was = before[slot];
            ItemStack now = container.getItem(slot);
            if (isBox(was) && isBox(now) && ItemStack.isSameItem(was, now)) {
                // The mover may have filled the box: settle its slots the same way, then write it back.
                HeldContents wasBox = new HeldContents(was);
                HeldContents nowBox = new HeldContents(now);
                back += settleSlots(nowBox, wasBox.getItems(), held);
                ItemContainerContents settled = ItemContainerContents.fromItems(nowBox.getItems());
                if (!settled.equals(now.get(DataComponents.CONTAINER))) {
                    now.set(DataComponents.CONTAINER, settled);
                    container.setItem(slot, now);
                }
            }
        }
        back += settleSlots(container, before, held);
        if (back <= 0) return left;
        if (left.isEmpty()) return held.copyWithCount(back);
        left.grow(back);
        return left;
    }

    /** {@link #settle} for one container's own slots: the count that found no slot in it. */
    private static int settleSlots(Container container, java.util.List<ItemStack> before, ItemStack held) {
        return settleSlots(container, before.toArray(ItemStack[]::new), held);
    }

    private static int settleSlots(Container container, ItemStack[] before, ItemStack held) {
        int pool = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack was = before[slot];
            ItemStack now = container.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(now, held)) continue;
            int added = was.isEmpty() ? now.getCount()
                    : ItemStack.isSameItemSameComponents(was, now) ? now.getCount() - was.getCount() : 0;
            if (added <= 0) continue;
            container.setItem(slot, was.copy());
            int allowed = room(container, slot, held.copyWithCount(added));
            put(container, slot, held, allowed);
            pool += added - allowed;
        }
        // What was refused goes to the first slots of this container that take it.
        for (int slot = 0; slot < container.getContainerSize() && pool > 0; slot++) {
            int n = Math.min(pool, room(container, slot, held.copyWithCount(pool)));
            put(container, slot, held, n);
            pool -= n;
        }
        return pool;
    }

    private static void put(Container container, int slot, ItemStack held, int count) {
        if (count <= 0) return;
        ItemStack there = container.getItem(slot);
        container.setItem(slot, there.isEmpty() ? held.copyWithCount(count) : there.copyWithCount(there.getCount() + count));
    }

    // One box with a container on it; a stack of several shares one contents and isn't filled.
    private static boolean isBox(ItemStack stack) {
        return stack.getCount() == 1 && stack.has(DataComponents.CONTAINER);
    }

    /**
     * A box item's contents as a container that names its item, so a veto reads the box's
     * own slot state ({@code SlotStateChannel.get(SlotRef)}). A copy: writes go nowhere.
     */
    private static final class HeldContents extends SimpleContainer implements ItemContents {
        private final ItemStack box;

        HeldContents(ItemStack box) {
            super(Math.max(27, box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).size()));
            this.box = box;
            box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public @Nullable ItemPath itemPath(Player player) {
            return null;
        }

        @Override
        public ItemStack item(@Nullable Player player) {
            return box;
        }
    }
}
