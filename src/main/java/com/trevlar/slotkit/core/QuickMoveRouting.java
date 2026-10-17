package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes;
import com.trevlar.slotkit.api.slot.QuickMoveRoutes.Dest;
import com.trevlar.slotkit.mixin.AbstractContainerMenuInvoker;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Shift-click on every menu, at the {@code quickMoveStack} calls in vanilla's
 * {@code doClick} ({@code SKQuickMoveRouteMixin}, both sides): the routes a mod
 * registered ({@link QuickMoveRoutes}), SlotKit's default for parity slots, and the
 * clamp that keeps a menu's own routing out of the parity slots appended to it.
 */
@ApiStatus.Internal
public final class QuickMoveRouting {

    private QuickMoveRouting() {}

    // Set while a menu's own quickMoveStack runs on SlotKit's behalf: its
    // moveItemStackTo ranges stop at the first parity slot (SKQuickMoveRouteMixin).
    // Per thread, since an integrated server and its client click on two threads.
    private static final ThreadLocal<Integer> CLAMP = new ThreadLocal<>();

    /** The end a {@code moveItemStackTo(start, end)} call may reach while clamped. */
    public static int clampEnd(int start, int end) {
        Integer firstParity = CLAMP.get();
        return firstParity != null && start < firstParity ? Math.min(end, firstParity) : end;
    }

    /**
     * One shift-click from {@code index}, in place of the menu's own
     * {@code quickMoveStack}, which {@code vanilla} calls. Returns what
     * {@code quickMoveStack} would: the original stack when something moved (so
     * {@code doClick} calls again for the rest), empty when nothing did.
     */
    public static ItemStack quickMove(AbstractContainerMenu menu, Player player, int index,
                                      BiFunction<Player, Integer, ItemStack> vanilla) {
        if (index < 0 || index >= menu.slots.size()) return vanilla.apply(player, index);
        Slot from = menu.slots.get(index);
        if (!from.hasItem()) return vanilla.apply(player, index);

        List<Dest> route = QuickMoveRoutes.routeFor(menu, player, from);
        if (route == null) route = defaultRoute(menu, from);
        if (route == null) return clamped(menu, player, index, vanilla);

        @Nullable SlotGroupId source = groupOf(menu, from);
        ItemStack live = from.getItem();          // the live stack moveItemStackTo shrinks
        ItemStack original = live.copy();
        for (Dest dest : route) {
            if (live.isEmpty()) break;
            if (dest instanceof Dest.ToVanilla) {
                if (live.getCount() != original.getCount()) break;   // moved: doClick calls again, VANILLA gets the rest then
                ItemStack r = clamped(menu, player, index, vanilla);
                // Into parity groups with no route, after the menu's own routing moved nothing.
                if (r.isEmpty() && from.hasItem() && !(menu instanceof InventoryMenu) && isDefault(route)) {
                    moveInto(menu, from.getItem(), shiftClickTargets(menu, from));
                    ItemStack after = from.getItem();
                    if (after.getCount() != original.getCount()) return finish(from, player, after, original);
                }
                return r;
            }
            moveInto(menu, live, slotsOf(menu, player, dest, from, source));
        }
        if (live.getCount() == original.getCount()) return ItemStack.EMPTY;   // nothing moved, nothing more to do
        return finish(from, player, live, original);
    }

    // Vanilla quickMoveStack's closing bookkeeping.
    private static ItemStack finish(Slot from, Player player, ItemStack live, ItemStack original) {
        if (live.isEmpty()) from.setByPlayer(ItemStack.EMPTY, original);
        else from.setChanged();
        from.onTake(player, live);
        return original;
    }

    // The marker list the default "into parity groups" case uses on menus other than
    // the inventory: vanilla first, then the targets. Compared by identity.
    private static final List<Dest> VANILLA_THEN_TARGETS = List.of(Dest.VANILLA);
    private static final List<Dest> TARGETS_THEN_VANILLA = List.of(new Dest.Group(SlotGroupId.created("slotkit:shift_click_targets", "*")), Dest.VANILLA);

    private static boolean isDefault(List<Dest> route) { return route == VANILLA_THEN_TARGETS; }

    /** SlotKit's route when no mod gave one, or {@code null} for the menu's own routing. */
    private static @Nullable List<Dest> defaultRoute(AbstractContainerMenu menu, Slot from) {
        CreatedSlot created = CreatedSlotAccess.asMKCSlot(from);
        if (created != null) return ParitySlotRegistry.isParity(created.panelId()) ? List.of(Dest.PLAYER) : null;
        if (!hasShiftClickTargets(menu)) return null;
        return menu instanceof InventoryMenu ? TARGETS_THEN_VANILLA : VANILLA_THEN_TARGETS;
    }

    /** The menu's own quickMoveStack, its ranges stopped short of the parity slots. */
    private static ItemStack clamped(AbstractContainerMenu menu, Player player, int index,
                                     BiFunction<Player, Integer, ItemStack> vanilla) {
        int first = firstParitySlot(menu);
        if (first < 0) return vanilla.apply(player, index);
        Integer before = CLAMP.get();
        CLAMP.set(first);
        try {
            return vanilla.apply(player, index);
        } finally {
            if (before == null) CLAMP.remove(); else CLAMP.set(before);
        }
    }

    // Merge into partial stacks, then fill empty slots, one slot at a time, as
    // vanilla's moveItemStackTo does over a range, so each slot judges the move.
    private static void moveInto(AbstractContainerMenu menu, ItemStack live, List<Integer> targets) {
        AbstractContainerMenuInvoker move = (AbstractContainerMenuInvoker) menu;
        for (int pass = 0; pass < 2 && !live.isEmpty(); pass++) {
            for (int i : targets) {
                if (live.isEmpty()) break;
                Slot to = menu.slots.get(i);
                if (to.hasItem() != (pass == 0) || !to.isActive()) continue;
                move.sk$moveItemStackTo(live, i, i + 1, false);
            }
        }
    }

    /** The menu slots a destination names, in the order a stack tries them. */
    private static List<Integer> slotsOf(AbstractContainerMenu menu, Player player, Dest dest, Slot from,
                                         @Nullable SlotGroupId source) {
        List<Integer> out = new ArrayList<>();
        switch (dest) {
            case Dest.ToPlayer p -> {
                // Main inventory and hotbar in vanilla's order for this menu: on the
                // inventory screen main first, as vanilla moves armour out; on any other
                // menu walked backwards, hotbar end first, as it moves a container's stack.
                Inventory inv = player.getInventory();
                for (Slot s : menu.slots) {
                    if (s.container == inv && s.getContainerSlot() < Inventory.INVENTORY_SIZE
                            && CreatedSlotAccess.asMKCSlot(s) == null && s != from) out.add(s.index);
                }
                if (!(menu instanceof InventoryMenu)) java.util.Collections.reverse(out);
                return out;
            }
            case Dest.Group g -> {
                // The default route's stand-in for "every shiftClickIn(true) group".
                if (g == TARGETS_THEN_VANILLA.get(0)) return shiftClickTargets(menu, from);
                if (g.id().equals(source)) return out;
                return slotsOfGroup(menu, g.id(), from);
            }
            case Dest.GroupOf g -> {
                if (g.menuSlot() < 0 || g.menuSlot() >= menu.slots.size()) return out;
                SlotGroupId id = groupOf(menu, menu.slots.get(g.menuSlot()));
                if (id == null || id.equals(source)) return out;
                return slotsOfGroup(menu, id, from);
            }
            case Dest.ToVanilla v -> { return out; }
        }
    }

    private static List<Integer> slotsOfGroup(AbstractContainerMenu menu, SlotGroupId id, Slot from) {
        List<Integer> out = new ArrayList<>();
        for (ResolvedSlotGroup g : SlotGroupCategories.groups(menu)) {
            if (!g.id().equals(id)) continue;
            for (Slot s : g.slots()) if (s != from) out.add(s.index);
        }
        return out;
    }

    /** The group {@code slot} is in on this menu, or {@code null}. */
    private static @Nullable SlotGroupId groupOf(AbstractContainerMenu menu, Slot slot) {
        Slot target = Slots.target(slot);
        for (ResolvedSlotGroup g : SlotGroupCategories.groups(menu)) {
            for (Slot s : g.slots()) if (s == slot || s == target) return g.id();
        }
        return null;
    }

    private static boolean isTarget(Slot s) {
        CreatedSlot c = CreatedSlotAccess.asMKCSlot(s);
        return c != null && ParityGroup.isShiftClickTarget(SlotGroupId.created(c.panelId(), c.groupId()));
    }

    private static boolean hasShiftClickTargets(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) if (isTarget(s)) return true;
        return false;
    }

    // Groups that declared shiftClickIn(true), in menu order.
    private static List<Integer> shiftClickTargets(AbstractContainerMenu menu, Slot from) {
        List<Integer> out = new ArrayList<>();
        for (Slot s : menu.slots) if (s != from && isTarget(s)) out.add(s.index);
        return out;
    }

    private static int firstParitySlot(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            CreatedSlot c = CreatedSlotAccess.asMKCSlot(s);
            if (c != null && ParitySlotRegistry.isParity(c.panelId())) return s.index;
        }
        return -1;
    }
}
