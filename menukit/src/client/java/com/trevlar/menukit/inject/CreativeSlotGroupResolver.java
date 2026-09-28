package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.SlotGroupCategory;
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

    /** Called once from {@code MKClient}. */
    public static void registerClient() {
        register();
    }

    /**
     * Creative inventory uses its own {@code ItemPickerMenu} which rebuilds
     * {@code menu.slots} on every tab change. ScreenPanelRegistry re-resolves
     * slot groups per frame (M8 §5.4), so the resolver discriminates by the
     * current slot count:
     *
     * <ul>
     *   <li><b>Non-INVENTORY tab — always 54 slots.</b> 45 creative items at
     *       indices 0-44, player hotbar at 45-53. The HOTBAR, SEARCH, and
     *       category tabs all re-use this layout (they change which items
     *       are displayed in the creative-item slots, not the slot count).
     *       Only {@code PLAYER_HOTBAR} resolves — the hotbar is always
     *       visually present in creative.</li>
     *   <li><b>INVENTORY tab — size ≠ 54.</b> Vanilla's {@code selectTab}
     *       clears slots and re-adds wrappers around every slot in
     *       {@code player.inventoryMenu}, then appends a
     *       {@code destroyItemSlot} trash bin. Without consumer mods, this
     *       is 46 wrappers + 1 destroy = 47 slots. With a mod registering N
     *       extra slots onto {@code InventoryMenu} (e.g., inventory-plus's
     *       equipment slots), it's (46 + N) wrappers + 1 destroy.</li>
     * </ul>
     *
     * <p><b>Why not match on a specific INVENTORY-tab count?</b> Early
     * drafts tried size == 46 (vanilla InventoryMenu slot count, forgetting
     * the destroy slot) and size == 47 (vanilla including destroy). Both
     * failed in dev because inventory-plus slots 2 slots into
     * {@code InventoryMenu}, producing size == 49. Any mod doing similar
     * registering changes the count. The slot layout at indices 0-45 — the
     * player-inventory categories — is invariant under such registering
     * because vanilla's rebuild loop preserves InventoryMenu's slot order.
     * Using {@code size != 54} captures INVENTORY-tab state correctly across
     * mod-extended inventories, at the cost of ambiguity if a future modded
     * creative tab ever produces a non-54 slot count outside the INVENTORY
     * tab. No such case observed in vanilla or common mods; re-narrow the
     * check if one surfaces.
     */
    static void register() {
        SlotGroupCategories.register(CreativeModeInventoryScreen.ItemPickerMenu.class, menu -> {
            int size = menu.slots.size();
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            if (size == 54) {
                // Non-INVENTORY tab: 45 creative-item slots + 9 hotbar.
                // PLAYER_INVENTORY absent (main inventory isn't visible here);
                // PLAYER_HOTBAR resolves because the hotbar IS always visible.
                out.put(SlotGroupCategory.PLAYER_HOTBAR, VanillaSlotGroupResolvers.range(45, 54));
            } else if (size >= 46) {
                // INVENTORY tab — size varies with mod-registered InventoryMenu
                // slots. Indices 0-45 are stable per vanilla's rebuild loop
                // order; claim only those for the player-inventory layout.
                // Index 46 and beyond may include mod-registered slots and the
                // destroyItemSlot trash bin — not named categories; skipped.
                out.put(SlotGroupCategory.CRAFTING_OUTPUT, VanillaSlotGroupResolvers.range(0, 1));
                out.put(SlotGroupCategory.CRAFTING_INPUT, VanillaSlotGroupResolvers.range(1, 5));
                out.put(SlotGroupCategory.PLAYER_ARMOR, VanillaSlotGroupResolvers.range(5, 9));
                out.put(SlotGroupCategory.PLAYER_INVENTORY, VanillaSlotGroupResolvers.range(9, 36));
                out.put(SlotGroupCategory.PLAYER_HOTBAR, VanillaSlotGroupResolvers.range(36, 45));
                out.put(SlotGroupCategory.PLAYER_OFFHAND, VanillaSlotGroupResolvers.range(45, 46));
            }
            // Any other slot count (< 46, likely transient rebuild state)
            // returns empty — silent skip rather than partial match.
            return Collections.unmodifiableMap(out);
        });
    }
}
