package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The category of a player inventory slot, from its slot key alone. A player's
 * inventory index means the same thing on every menu: 0 to 8 the hotbar, 9 to 35 the
 * main grid, 36 to 39 armor, 40 the offhand. So the category is never read from a
 * menu's layout, which is what used to go wrong on creative's tabs, where the slot
 * list changes with every tab switch.
 */
@ApiStatus.Internal
public final class PlayerSlots {

    private PlayerSlots() {}

    /** The category of the player inventory slot at {@code index}, or {@code null} for an index past the offhand. */
    public static @Nullable SlotGroupCategory categoryOf(int index) {
        if (index >= 0 && index < 9) return SlotGroupCategory.PLAYER_HOTBAR;
        if (index < Inventory.INVENTORY_SIZE) return index < 0 ? null : SlotGroupCategory.PLAYER_INVENTORY;
        if (index < Inventory.SLOT_OFFHAND) return SlotGroupCategory.PLAYER_ARMOR;
        return index == Inventory.SLOT_OFFHAND ? SlotGroupCategory.PLAYER_OFFHAND : null;
    }

    /** The category of {@code slot} if it is one of the player's inventory slots (creative's wrapper seen through), else {@code null}. */
    public static @Nullable SlotGroupCategory categoryOf(Slot slot) {
        Slot target = Slots.target(slot);
        return target.container instanceof Inventory ? categoryOf(target.getContainerSlot()) : null;
    }

    /**
     * The menu indices of the player's inventory slots on {@code menu}, by category, in
     * menu order. A category the menu doesn't show is absent.
     */
    public static Map<SlotGroupCategory, int[]> groupsOn(AbstractContainerMenu menu) {
        Map<SlotGroupCategory, List<Integer>> acc = new LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            SlotGroupCategory c = categoryOf(menu.slots.get(i));
            if (c != null) acc.computeIfAbsent(c, k -> new ArrayList<>()).add(i);
        }
        Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
        acc.forEach((c, list) -> out.put(c, list.stream().mapToInt(Integer::intValue).toArray()));
        return out;
    }
}
