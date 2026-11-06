package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.window.ActingPlayer;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SlotOperations.Preference} on a shift-click: a {@code moveItemStackTo} range
 * with a preferred slot in it runs as vanilla's two passes one slot at a time, with
 * the preferred slots first in each pass. Partial stacks, preferred then the rest;
 * then one empty slot, preferred then the rest, as vanilla fills one empty slot per
 * call. Each single-slot call is vanilla's own method, so every veto, gate and limit
 * on it still judges the slot. A range with no preferred slot is left to vanilla.
 */
@ApiStatus.Internal
public final class PreferredMoves {

    private PreferredMoves() {}

    /** Vanilla's {@code moveItemStackTo} over one slot range, {@code [start, end)}, as the menu called it. */
    @FunctionalInterface
    public interface Move {
        boolean call(ItemStack stack, int start, int end, boolean backwards);
    }

    public static boolean moveItemStackTo(AbstractContainerMenu menu, ItemStack stack, int start, int end,
                                          boolean backwards, Move vanilla) {
        if (!SlotOperations.anyPreference() || stack.isEmpty()) return vanilla.call(stack, start, end, backwards);
        end = Math.min(QuickMoveRouting.clampEnd(start, end), menu.slots.size());   // the clamp the original would apply

        // The range in vanilla's order, split into preferred slots and the rest.
        List<Slot> preferred = new ArrayList<>();
        List<Slot> rest = new ArrayList<>();
        for (int k = 0; k < end - start; k++) {
            Slot slot = menu.slots.get(backwards ? end - 1 - k : start + k);
            boolean prefers = SlotOperations.prefers(SlotRef.of(menu, slot, ActingPlayer.current()), stack);
            (prefers ? preferred : rest).add(slot);
        }
        if (preferred.isEmpty()) return vanilla.call(stack, start, end, backwards);

        boolean moved = false;
        for (List<Slot> pass : List.of(preferred, rest)) {
            for (Slot slot : pass) {
                if (stack.isEmpty()) return moved;
                if (slot.hasItem()) moved |= vanilla.call(stack, slot.index, slot.index + 1, false);
            }
        }
        for (List<Slot> pass : List.of(preferred, rest)) {
            for (Slot slot : pass) {
                if (stack.isEmpty()) return moved;
                if (!slot.hasItem() && vanilla.call(stack, slot.index, slot.index + 1, false)) return true;
            }
        }
        return moved;
    }
}
