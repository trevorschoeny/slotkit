package com.trevorschoeny.menukit.window;

import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.inject.SlotGroupCategories;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The display-NAME layer for windowed slots — MK-owned, client-side, purely a
 * display concern (separate from identity; see {@link Address}). Turns a slot into
 * a human label like "Hotbar 3", "Chestplate", or "Offhand".
 *
 * <h2>Two sources, in priority order</h2>
 * <ol>
 *   <li><b>Per-address overrides</b> ({@link #override}) — a name registered against
 *       a specific {@link Address}. This is how MKC names its created slots at
 *       creation (group label + ordinal), and how any consumer names a custom slot.
 *       MK never sees the created slot's type (§0042): the name is keyed by the
 *       Address the consumer mints.</li>
 *   <li><b>Vanilla taxonomy</b> — computed on the fly from the live slot's role.
 *       MK already categorizes every vanilla slot via {@link SlotGroupCategories}
 *       (the 22 vanilla menus, M8/§0043); naming is a thin table mapping each
 *       {@link SlotGroupCategory} to a {@link Spec} (a numbered label, or a list of
 *       named singletons), formatted with the slot's 1-based ordinal within its
 *       category. Nothing is stored for vanilla slots; it's recomputed per lookup.</li>
 * </ol>
 *
 * <h2>Why this is purely additive</h2>
 * It reads {@link SlotGroupCategories} (already client-side, already per-screen) and
 * the override map; it never touches {@code Address}, the window engine, the
 * addressing, or the {@code VanillaSlotIdentity} port. Coarse categories stay coarse
 * — armor is still ONE {@code PLAYER_ARMOR} group for targeting; the four distinct
 * names come from the naming {@link Spec}, NOT from splitting the category (which
 * would change resolved-category behavior every panel-targeting consumer relies on).
 *
 * <p>Works MK-alone: with no MKC, the override map is simply empty and vanilla names
 * compute from MK's own resolvers. Client-thread for lookup; the override map is
 * concurrent because a created-slot name may be registered off the menu-build thread.
 */
public final class SlotNames {

    private SlotNames() {}

    /**
     * A naming rule for one category: either a numbered label ("Hotbar" → "Hotbar 3"),
     * or a list of named singletons ("Helmet"/"Chestplate"/... by ordinal). A
     * single-slot category formatted as a label drops the ordinal (just "Offhand").
     */
    private record Spec(String label, String @Nullable [] singletons) {
        String format(int ordinal, int size) {
            if (singletons != null && ordinal >= 0 && ordinal < singletons.length) {
                return singletons[ordinal];
            }
            return size <= 1 ? label : label + " " + (ordinal + 1);
        }
    }

    private static Spec ordinal(String label) { return new Spec(label, null); }
    private static Spec singletons(String... names) { return new Spec(names[0], names); }

    /** Per-{@link SlotGroupCategory} naming rules. Populated once for all vanilla categories. */
    private static final Map<SlotGroupCategory, Spec> SPECS = new ConcurrentHashMap<>();

    /** Per-{@link Address} name overrides (created slots, consumer customs). */
    private static final Map<Address, String> OVERRIDES = new ConcurrentHashMap<>();

    static {
        // ── Player-scoped ──────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.PLAYER_HOTBAR, ordinal("Hotbar"));
        SPECS.put(SlotGroupCategory.PLAYER_INVENTORY, ordinal("Inventory"));
        SPECS.put(SlotGroupCategory.PLAYER_ARMOR,
                singletons("Helmet", "Chestplate", "Leggings", "Boots"));
        SPECS.put(SlotGroupCategory.PLAYER_OFFHAND, ordinal("Offhand"));
        // ── Storage ─────────────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.CHEST_STORAGE, ordinal("Chest"));
        SPECS.put(SlotGroupCategory.SHULKER_STORAGE, ordinal("Shulker"));
        SPECS.put(SlotGroupCategory.DISPENSER_STORAGE, ordinal("Dispenser"));
        SPECS.put(SlotGroupCategory.HOPPER_STORAGE, ordinal("Hopper"));
        // ── Crafting family ──────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.CRAFTING_INPUT, ordinal("Crafting input"));
        SPECS.put(SlotGroupCategory.CRAFTING_OUTPUT, ordinal("Crafting result"));
        SPECS.put(SlotGroupCategory.CRAFTER_GRID, ordinal("Crafter slot"));
        SPECS.put(SlotGroupCategory.CRAFTER_RESULT, ordinal("Crafter result"));
        // ── Furnace family ───────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.FURNACE_INPUT, ordinal("Furnace input"));
        SPECS.put(SlotGroupCategory.FURNACE_FUEL, ordinal("Fuel"));
        SPECS.put(SlotGroupCategory.FURNACE_OUTPUT, ordinal("Furnace output"));
        // ── Utility blocks ───────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.ENCHANTING_INPUT, ordinal("Enchant item"));
        SPECS.put(SlotGroupCategory.ENCHANTING_LAPIS, ordinal("Lapis"));
        SPECS.put(SlotGroupCategory.ANVIL_INPUT, singletons("Anvil item", "Anvil material"));
        SPECS.put(SlotGroupCategory.ANVIL_OUTPUT, ordinal("Anvil result"));
        SPECS.put(SlotGroupCategory.GRINDSTONE_INPUT, ordinal("Grindstone input"));
        SPECS.put(SlotGroupCategory.GRINDSTONE_OUTPUT, ordinal("Grindstone result"));
        SPECS.put(SlotGroupCategory.SMITHING_TEMPLATE, ordinal("Smithing template"));
        SPECS.put(SlotGroupCategory.SMITHING_BASE, ordinal("Smithing base"));
        SPECS.put(SlotGroupCategory.SMITHING_ADDITION, ordinal("Smithing addition"));
        SPECS.put(SlotGroupCategory.SMITHING_OUTPUT, ordinal("Smithing result"));
        SPECS.put(SlotGroupCategory.LOOM_BANNER, ordinal("Banner"));
        SPECS.put(SlotGroupCategory.LOOM_DYE, ordinal("Dye"));
        SPECS.put(SlotGroupCategory.LOOM_PATTERN, ordinal("Pattern"));
        SPECS.put(SlotGroupCategory.LOOM_OUTPUT, ordinal("Loom result"));
        SPECS.put(SlotGroupCategory.STONECUTTER_INPUT, ordinal("Stonecutter input"));
        SPECS.put(SlotGroupCategory.STONECUTTER_OUTPUT, ordinal("Stonecutter result"));
        SPECS.put(SlotGroupCategory.CARTOGRAPHY_MAP, ordinal("Map"));
        SPECS.put(SlotGroupCategory.CARTOGRAPHY_ADDITIONAL, ordinal("Cartography paper"));
        SPECS.put(SlotGroupCategory.CARTOGRAPHY_OUTPUT, ordinal("Cartography result"));
        // ── Brewing ──────────────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.BREWING_POTIONS, ordinal("Bottle"));
        SPECS.put(SlotGroupCategory.BREWING_INGREDIENT, ordinal("Brewing ingredient"));
        SPECS.put(SlotGroupCategory.BREWING_FUEL, ordinal("Blaze powder"));
        // ── Trading ──────────────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.MERCHANT_PAYMENT, ordinal("Trade input"));
        SPECS.put(SlotGroupCategory.MERCHANT_RESULT, ordinal("Trade result"));
        // ── Beacon ───────────────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.BEACON_PAYMENT, ordinal("Beacon payment"));
        // ── Mounts ───────────────────────────────────────────────────────────
        SPECS.put(SlotGroupCategory.MOUNT_SADDLE, ordinal("Saddle"));
        SPECS.put(SlotGroupCategory.MOUNT_BODY_ARMOR, ordinal("Mount armor"));
        SPECS.put(SlotGroupCategory.MOUNT_STORAGE, ordinal("Mount storage"));
    }

    // ── Registration (MKC created slots, consumer customs) ──────────────────

    /** Names the slot at {@code address} (created slot / custom). Overrides the vanilla taxonomy. */
    public static void override(Address address, String name) {
        if (address != null && name != null) OVERRIDES.put(address, name);
    }

    /** Registers/replaces the naming rule for a (typically consumer-defined) category. */
    public static void register(SlotGroupCategory category, String label, String... singletons) {
        SPECS.put(category, singletons.length == 0 ? ordinal(label) : singletons(singletons));
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    /**
     * The display name of the slot, or {@code null} if it has none (an unnamed /
     * uncategorized vanilla slot, or a modded menu with no resolver). Checks the
     * per-address override first, then the vanilla taxonomy from the live slot.
     */
    public static @Nullable String nameOf(@Nullable Address address,
                                          @Nullable AbstractContainerMenu menu, @Nullable Slot slot) {
        if (address != null) {
            String custom = OVERRIDES.get(address);
            if (custom != null) return custom;
        }
        return (menu != null && slot != null) ? vanillaName(menu, slot) : null;
    }

    /** Compute a vanilla slot's name from its resolved category + 1-based ordinal within it. */
    private static @Nullable String vanillaName(AbstractContainerMenu menu, Slot slot) {
        Map<SlotGroupCategory, List<Slot>> categories = SlotGroupCategories.of(menu);
        for (Map.Entry<SlotGroupCategory, List<Slot>> entry : categories.entrySet()) {
            List<Slot> slots = entry.getValue();
            int ordinal = slots.indexOf(slot);   // identity match — resolvers return subList views of menu.slots
            if (ordinal >= 0) {
                Spec spec = SPECS.get(entry.getKey());
                return spec != null ? spec.format(ordinal, slots.size()) : null;
            }
        }
        return null;
    }
}
