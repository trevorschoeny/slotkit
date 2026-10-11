package com.trevlar.menukit.api.window;

import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.SlotGroupCategories;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Display names for slots, on the client: labels like "Hotbar 3", "Chestplate" or
 * "Offhand". Names are for display only and separate from identity (see
 * {@link Address}).
 *
 * <h2>Two sources, in priority order</h2>
 * <ol>
 *   <li><b>Per-address overrides</b> ({@link #override}): a name registered against
 *       a specific {@link Address}. MenuKit: Containers names its created slots
 *       this way when it creates them (group label and ordinal), and any consumer
 *       can name a custom slot. MenuKit never sees the created slot's type; the
 *       name is keyed by the address the consumer mints.</li>
 *   <li><b>Vanilla names</b>, computed from the live slot's role. MenuKit already
 *       categorizes every vanilla slot through {@link SlotGroupCategories} (the 22
 *       vanilla menus); naming is a table from each {@link SlotGroupCategory} to a
 *       {@link Spec} (a numbered label, or a list of single names), formatted with
 *       the slot's 1-based ordinal in its category. Nothing is stored for vanilla
 *       slots; the name is computed on each lookup.</li>
 * </ol>
 *
 * <h2>Reads only</h2>
 * It reads {@link SlotGroupCategories} and the override map, and never touches
 * {@code Address}, the window engine, the addressing or {@code ContainerIdentity}.
 * Categories stay coarse: armor is still one {@code PLAYER_ARMOR} group for
 * targeting, and the four names come from its {@link Spec}, not from splitting the
 * category, which would change the category every panel-targeting consumer relies
 * on.
 *
 * <p>Works with MenuKit alone: without Containers the override map is empty and
 * vanilla names come from MenuKit's own resolvers. Lookups run on the client
 * thread; the override map is concurrent because a created slot's name may be
 * registered from another thread while the menu is built.
 */
public final class SlotNames {

    private SlotNames() {}

    /**
     * A naming rule for one category: a numbered label ("Hotbar" gives "Hotbar 3"),
     * or a list of single names by ordinal ("Helmet", "Chestplate" and so on). A
     * one-slot category with a label drops the number (just "Offhand").
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

    /** Naming rules per {@link SlotGroupCategory}, filled once for every vanilla category. */
    private static final Map<SlotGroupCategory, Spec> SPECS = new ConcurrentHashMap<>();

    /** Name overrides per {@link Address} (created slots, consumers' custom slots). */
    private static final Map<Address, String> OVERRIDES = new ConcurrentHashMap<>();

    static {
        // Player-scoped
        SPECS.put(SlotGroupCategory.PLAYER_HOTBAR, ordinal("Hotbar"));
        SPECS.put(SlotGroupCategory.PLAYER_INVENTORY, ordinal("Inventory"));
        SPECS.put(SlotGroupCategory.PLAYER_ARMOR,
                singletons("Helmet", "Chestplate", "Leggings", "Boots"));
        SPECS.put(SlotGroupCategory.PLAYER_OFFHAND, ordinal("Offhand"));
        // Storage
        SPECS.put(SlotGroupCategory.CHEST_STORAGE, ordinal("Chest"));
        SPECS.put(SlotGroupCategory.SHULKER_STORAGE, ordinal("Shulker"));
        SPECS.put(SlotGroupCategory.DISPENSER_STORAGE, ordinal("Dispenser"));
        SPECS.put(SlotGroupCategory.HOPPER_STORAGE, ordinal("Hopper"));
        // Crafting family
        SPECS.put(SlotGroupCategory.CRAFTING_INPUT, ordinal("Crafting input"));
        SPECS.put(SlotGroupCategory.CRAFTING_OUTPUT, ordinal("Crafting result"));
        SPECS.put(SlotGroupCategory.CRAFTER_GRID, ordinal("Crafter slot"));
        SPECS.put(SlotGroupCategory.CRAFTER_RESULT, ordinal("Crafter result"));
        // Furnace family
        SPECS.put(SlotGroupCategory.FURNACE_INPUT, ordinal("Furnace input"));
        SPECS.put(SlotGroupCategory.FURNACE_FUEL, ordinal("Fuel"));
        SPECS.put(SlotGroupCategory.FURNACE_OUTPUT, ordinal("Furnace output"));
        // Utility blocks
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
        // Brewing
        SPECS.put(SlotGroupCategory.BREWING_POTIONS, ordinal("Bottle"));
        SPECS.put(SlotGroupCategory.BREWING_INGREDIENT, ordinal("Brewing ingredient"));
        SPECS.put(SlotGroupCategory.BREWING_FUEL, ordinal("Blaze powder"));
        // Trading
        SPECS.put(SlotGroupCategory.MERCHANT_PAYMENT, ordinal("Trade input"));
        SPECS.put(SlotGroupCategory.MERCHANT_RESULT, ordinal("Trade result"));
        // Beacon
        SPECS.put(SlotGroupCategory.BEACON_PAYMENT, ordinal("Beacon payment"));
        // Mounts
        SPECS.put(SlotGroupCategory.MOUNT_SADDLE, ordinal("Saddle"));
        SPECS.put(SlotGroupCategory.MOUNT_BODY_ARMOR, ordinal("Mount armor"));
        SPECS.put(SlotGroupCategory.MOUNT_STORAGE, ordinal("Mount storage"));
    }

    /** Names the slot at {@code address} (a created or custom slot). Takes priority over the vanilla name. */
    public static void override(Address address, String name) {
        if (address != null && name != null) OVERRIDES.put(address, name);
    }

    /** Registers the naming rule for a category, typically one a mod defined. Throws if it already has one. */
    public static void register(SlotGroupCategory category, String label, String... singletons) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("SlotNames.register(" + category + ")");
        Object was = SPECS.putIfAbsent(category, singletons.length == 0 ? ordinal(label) : singletons(singletons));
        if (was != null) {
            throw new IllegalStateException("SlotNames: " + category + " already has names; two mods naming one "
                    + "category's slots would be settled by load order (§0063)");
        }
    }

    /**
     * The display name of the slot, or {@code null} if it has none (an unnamed or
     * uncategorized vanilla slot, or a modded menu with no resolver). Checks the
     * per-address override first, then the vanilla name from the live slot.
     */
    public static @Nullable String nameOf(@Nullable Address address,
                                          @Nullable AbstractContainerMenu menu, @Nullable Slot slot) {
        if (address != null) {
            String custom = OVERRIDES.get(address);
            if (custom != null) return custom;
        }
        return (menu != null && slot != null) ? vanillaName(menu, slot) : null;
    }

    private static @Nullable String vanillaName(AbstractContainerMenu menu, Slot slot) {
        Map<SlotGroupCategory, List<Slot>> categories = SlotGroupCategories.of(menu);
        for (Map.Entry<SlotGroupCategory, List<Slot>> entry : categories.entrySet()) {
            List<Slot> slots = entry.getValue();
            int ordinal = slots.indexOf(slot);   // an identity match: resolvers return subList views of menu.slots
            if (ordinal >= 0) {
                Spec spec = SPECS.get(entry.getKey());
                return spec != null ? spec.format(ordinal, slots.size()) : null;
            }
        }
        return null;
    }
}
