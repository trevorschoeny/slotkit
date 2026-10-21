package com.trevlar.slotkit.core;

import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupId;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * A slot group's box on a container screen, the reference every panel on the group
 * ({@code SlotGroupPanelAdapter}) is placed around. Apart from the screen so the
 * validator can check it on a real menu with no game window.
 */
@ApiStatus.Internal
public final class SlotGroupBox {

    private SlotGroupBox() {}

    /**
     * The rectangle around the group's slots' 18x18 frames, in screen pixels for a
     * screen whose menu sits at {@code (leftPos, topPos)}, or {@code null} when the
     * group is not on the menu or none of its slots is on screen. Re-resolved on every call, since creative tab
     * switches and dynamic menus change {@code menu.slots}. Each group gets its own
     * box: a created group declaring a vanilla category never stretches that
     * category's box.
     *
     * <p>The frame, not the 16x16 item box: vanilla lays slots out on an 18 px pitch,
     * so a created slot (itself an 18x18 frame) stacked flush on this box with
     * {@code gap(0)} lands exactly one pitch away, on the grid. Measuring from the item
     * box, with MenuKit's 2 px gap, put it 1 to 3 px off.
     */
    public static @Nullable Reference of(AbstractContainerMenu menu, int leftPos, int topPos, SlotGroupId id) {
        for (ResolvedSlotGroup group : SlotGroupCategories.groups(menu)) {
            if (!group.id().equals(id) || group.slots().isEmpty()) continue;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Slot slot : group.slots()) {
                // Parked (scrolled out of a region, not presented) and inactive slots (past an
                // active count) aren't on screen, so they don't stretch the box.
                if (slot.x == com.trevlar.slotkit.api.slot.CreatedSlots.OFFSCREEN || !slot.isActive()) continue;
                // A slot's frame is its item box grown by 1 px on every side.
                minX = Math.min(minX, slot.x - 1);
                minY = Math.min(minY, slot.y - 1);
                maxX = Math.max(maxX, slot.x + 17);
                maxY = Math.max(maxY, slot.y + 17);
            }
            if (minX == Integer.MAX_VALUE) return null;   // none of its slots is on screen
            return new Reference(leftPos + minX, topPos + minY, maxX - minX, maxY - minY);
        }
        return null;
    }
}
