package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Slot groups on creative's item picker, the one vanilla menu that exists only on
 * the client (its class is a client class, so it cannot be named in common code).
 * Every other vanilla menu is resolved in common init by
 * {@link VanillaSlotGroupResolvers}, so a server knows their categories too.
 */
public final class CreativeSlotGroupResolver {

    private CreativeSlotGroupResolver() {}

    /** Called once from {@code MenuKitClient}. */
    public static void registerClient() {
        register();
    }

    /**
     * Creative inventory uses its own {@code ItemPickerMenu} which rebuilds
     * {@code menu.slots} on every tab change. ScreenPanelRegistry re-resolves
     * slot groups every frame, so the resolver goes by the current slot count:
     *
     * <ul>
     *   <li><b>Any tab but INVENTORY: always 54 slots.</b> 45 creative items at
     *       indices 0-44, the player hotbar at 45-53. The HOTBAR, SEARCH and
     *       category tabs all use this layout; they change which items show, not
     *       the slot count. Only {@code PLAYER_HOTBAR} resolves, because the
     *       hotbar is always visible in creative.</li>
     *   <li><b>INVENTORY tab: any other size.</b> Vanilla's {@code selectTab}
     *       clears the slots, re-adds wrappers around every slot in
     *       {@code player.inventoryMenu} and then appends the
     *       {@code destroyItemSlot} bin. Without other mods that is 46 wrappers
     *       plus 1 bin, 47 slots. A mod adding N slots to {@code InventoryMenu}
     *       (such as Inventory Plus's equipment slots) makes it 46 + N + 1.</li>
     * </ul>
     *
     * <p>Matching one exact INVENTORY-tab count doesn't work: 46 forgets the bin,
     * 47 breaks as soon as a mod adds slots (Inventory Plus adds 2, giving 49).
     * Indices 0-45, the player-inventory categories, stay put because vanilla's
     * rebuild keeps InventoryMenu's slot order. The cost of {@code size != 54} is
     * that a modded creative tab with a slot count other than 54 would be read as
     * the INVENTORY tab. None is known in vanilla or common mods; narrow the check
     * if one appears.
     */
    static void register() {
        SlotGroupCategories.register(CreativeModeInventoryScreen.ItemPickerMenu.class, menu -> {
            int size = menu.slots.size();
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            if (size == 54) {
                // 45 creative-item slots and the 9 hotbar slots. No PLAYER_INVENTORY:
                // the main inventory isn't shown on these tabs.
                out.put(SlotGroupCategory.PLAYER_HOTBAR, VanillaSlotGroupResolvers.range(45, 54));
            } else if (size >= 46) {
                // INVENTORY tab. Only indices 0-45 are stable; 46 and up hold
                // mod-added slots and the destroyItemSlot bin, which have no
                // category.
                out.put(SlotGroupCategory.CRAFTING_OUTPUT, VanillaSlotGroupResolvers.range(0, 1));
                out.put(SlotGroupCategory.CRAFTING_INPUT, VanillaSlotGroupResolvers.range(1, 5));
                out.put(SlotGroupCategory.PLAYER_ARMOR, VanillaSlotGroupResolvers.range(5, 9));
                out.put(SlotGroupCategory.PLAYER_INVENTORY, VanillaSlotGroupResolvers.range(9, 36));
                out.put(SlotGroupCategory.PLAYER_HOTBAR, VanillaSlotGroupResolvers.range(36, 45));
                out.put(SlotGroupCategory.PLAYER_OFFHAND, VanillaSlotGroupResolvers.range(45, 46));
            }
            // Fewer than 46 slots is probably a rebuild in progress: return nothing
            // rather than a partial match.
            return Collections.unmodifiableMap(out);
        });
    }
}
