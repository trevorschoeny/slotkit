package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;

/**
 * Slot groups on creative's item picker, the one vanilla menu that exists only on
 * the client (its class is a client class, so it cannot be named in common code).
 * Every other vanilla menu is resolved in common init by
 * {@link VanillaSlotGroupResolvers}, so a server knows their categories too.
 */
public final class CreativeSlotGroupResolver {

    private CreativeSlotGroupResolver() {}

    /** Called once from {@code SlotKitClient}. */
    public static void registerClient() {
        register();
    }

    /**
     * The creative screen's own crafting slots, on the inventory tab. The player's
     * inventory slots need nothing here: their categories come from their inventory
     * index on every menu ({@code PlayerSlots}), so a tab switch can't confuse them.
     * The crafting slots are wrappers of the inventory menu's slots 0 (result) and 1
     * to 4 (grid), read through the wrapper rather than from the tab's slot count.
     */
    static void register() {
        SlotGroupCategories.register(CreativeModeInventoryScreen.ItemPickerMenu.class, menu -> {
            List<Integer> output = new ArrayList<>(), input = new ArrayList<>();
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                Slot target = Slots.target(slot);
                if (target == slot || !(target.container instanceof CraftingContainer
                        || target.container instanceof ResultContainer)) continue;
                (target.index == 0 ? output : input).add(i);
            }
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            if (!output.isEmpty()) out.put(SlotGroupCategory.CRAFTING_OUTPUT, output.stream().mapToInt(Integer::intValue).toArray());
            if (!input.isEmpty()) out.put(SlotGroupCategory.CRAFTING_INPUT, input.stream().mapToInt(Integer::intValue).toArray());
            return Collections.unmodifiableMap(out);
        });
    }
}
