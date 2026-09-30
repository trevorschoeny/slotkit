package com.trevlar.menukit.containers.api.menu;

import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.containers.api.menu.MenuPanel;
import com.trevlar.menukit.containers.api.storage.PlayerStorage;
import com.trevlar.menukit.containers.api.slot.SlotGroup;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.SlotOperations;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shift-click routing for a {@link CustomContainerMenu}, split out of the handler
 * (§0067). Declarative, in three layers:
 *
 * <ol>
 *   <li>The source slot must allow shift-click out (the {@code SHIFT_CLICK_OUT}
 *       operation), or nothing moves.</li>
 *   <li>Candidates: every other group on a shown panel whose slot allows
 *       shift-click in and can accept the stack (the gate and inertness).</li>
 *   <li>Order: a group the source is paired with first, then the other side of the
 *       player / container divide, then declared priority, highest first. Each
 *       candidate merges into partial stacks, then fills empties, until the stack is
 *       gone.</li>
 * </ol>
 */
final class QuickMoveRouter {

    private final CustomContainerMenu menu;

    QuickMoveRouter(CustomContainerMenu menu) {
        this.menu = menu;
    }

    /** Vanilla's {@code quickMoveStack} contract: the original stack if anything moved, else empty. */
    ItemStack route(Player player, int index) {
        Slot rawSlot = menu.slots.get(index);
        if (!(rawSlot instanceof CreatedSlot sourceSlot) || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        if (!SlotOperations.allowsGesture(menu, sourceSlot, player, BehaviorKeys.SHIFT_CLICK_OUT)) {
            return ItemStack.EMPTY;
        }
        SlotGroup sourceGroup = sourceSlot.getGroup();
        ItemStack originalStack = sourceSlot.getItem().copy();
        ItemStack workingStack = sourceSlot.getItem();

        // One live slot per group, to ask the shift-click-in operation and the gate
        // by its address.
        Map<SlotGroup, CreatedSlot> reps = new HashMap<>();
        for (Slot s : menu.slots) {
            if (s instanceof CreatedSlot mk) reps.putIfAbsent(mk.getGroup(), mk);
        }

        List<SlotGroup> candidates = new ArrayList<>();
        for (MenuPanel panel : menu.getPanels()) {
            if (!panel.isShown()) continue; // a hidden panel's slots are inert
            for (SlotGroup group : menu.getGroupsFor(panel.id())) {
                if (group == sourceGroup) continue;
                CreatedSlot rep = reps.get(group);
                if (rep == null) continue;
                if (!SlotOperations.allowsGesture(menu, rep, player, BehaviorKeys.SHIFT_CLICK_IN)) continue;
                if (!rep.mayPlace(workingStack)) continue;
                candidates.add(group);
            }
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;

        boolean sourceIsPlayer = sourceGroup.getStorage() instanceof PlayerStorage;
        candidates.sort((a, b) -> {
            // 1. Directional pairing first.
            boolean aPaired = sourceGroup.getPairedWith().contains(a);
            boolean bPaired = sourceGroup.getPairedWith().contains(b);
            if (aPaired != bPaired) return aPaired ? -1 : 1;
            // 2. The other side: from the player, containers first; from a container, the player.
            boolean aIsPlayer = a.getStorage() instanceof PlayerStorage;
            boolean bIsPlayer = b.getStorage() instanceof PlayerStorage;
            if (aIsPlayer != bIsPlayer) {
                return (aIsPlayer == sourceIsPlayer) ? 1 : -1;
            }
            // 3. Declared priority, highest first.
            return Integer.compare(b.getShiftClickPriority(), a.getShiftClickPriority());
        });

        for (SlotGroup candidate : candidates) {
            if (workingStack.isEmpty()) break;
            menu.moveInto(workingStack, candidate.getFlatIndexStart(), candidate.getFlatIndexEnd());
        }

        if (workingStack.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return workingStack.getCount() < originalStack.getCount() ? originalStack : ItemStack.EMPTY;
    }
}
