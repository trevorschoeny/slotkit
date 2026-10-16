package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.group.SlotGroupResolver;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.NautilusInventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link SlotGroupResolver}s SlotKit ships for every vanilla 26.2
 * {@link AbstractContainerMenu} whose slots map to named categories.
 *
 * <p>Slot-index ranges were checked against vanilla's
 * {@code net.minecraft.world.inventory.*Menu} classes in the Loom decompile.
 * Most menus follow a "N specific slots + 27 player inventory + 9 player
 * hotbar" pattern; variable-size menus (ChestMenu,
 * HorseInventoryMenu) derive their specific-slot count from the menu's own
 * slot count ({@link #ownSlotCount}) minus the 36 player slots.
 *
 * <p>Each resolver returns an immutable map keyed by {@link SlotGroupCategory},
 * whose values are slot-index arrays into {@code menu.slots}
 * ({@link SlotGroupCategories#of} dereferences them to {@code Slot}s). Empty
 * arrays are omitted, as {@link SlotGroupResolver} requires. The index ranges are
 * written out directly, with each menu's layout in the comment above it.
 */
@ApiStatus.Internal
public final class VanillaSlotGroupResolvers {

    private VanillaSlotGroupResolvers() {}

    /**
     * Called once from {@code SlotKit.init}, on both sides. Registers a resolver for
     * each vanilla menu class that exists on a server, so a vanilla slot has its
     * category there too: the operations cascade, {@code AppliesTo} and
     * {@code SlotGroups.of} resolve on the server that enforces them. (Until 6.0.0
     * this ran from client init only, and a dedicated server saw no vanilla
     * categories at all.)
     */
    public static void registerAll() {
        registerPlayerAndStorage();
        registerCraftingFamily();
        registerFurnaceFamily();
        registerUtilityBlocks();
        registerBrewingTradingBeacon();
        registerMounts();
    }

    // Shared helpers

    /**
     * How many slots the menu built itself: every slot before the first created one.
     * SlotKit appends created slots after the menu's constructor, on every container
     * menu (a slot mod's panel is built everywhere), so {@code menu.slots.size()}
     * counts them too. A resolver that sizes a layout from the slot count reads this
     * instead: counting a chest's created slots as chest storage shifted the
     * player-inventory range onto the hotbar and the parked created slots
     * (y = -10000), and every panel anchored to the inventory flew off the frame.
     */
    static int ownSlotCount(AbstractContainerMenu menu) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i) instanceof CreatedSlot) return i;
        }
        return menu.slots.size();
    }

    /** A contiguous index range {@code [fromInclusive, toExclusive)} as an {@code int[]}. */
    static int[] range(int fromInclusive, int toExclusive) {
        int[] r = new int[Math.max(0, toExclusive - fromInclusive)];
        for (int i = 0; i < r.length; i++) r[i] = fromInclusive + i;
        return r;
    }

    private static int[] ints(List<Integer> indices) {
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Adds the standard player-inventory-tail categories (27 inventory + 9
     * hotbar) starting at {@code startIndex}. Used by every menu that calls
     * vanilla's {@code addStandardInventorySlots} after its specific slots.
     */
    private static void addPlayerInvTail(Map<SlotGroupCategory, int[]> out, int startIndex) {
        out.put(SlotGroupCategory.PLAYER_INVENTORY, range(startIndex, startIndex + 27));
        out.put(SlotGroupCategory.PLAYER_HOTBAR, range(startIndex + 27, startIndex + 36));
    }

    // Player inventory (survival, and creative's inventory tab), and storage

    private static void registerPlayerAndStorage() {
        // InventoryMenu: 1 result + 4 crafting (2×2) + 4 armor + 27 inv + 9 hotbar + 1 offhand
        SlotGroupCategories.register(InventoryMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.CRAFTING_OUTPUT, range(0, 1));
            out.put(SlotGroupCategory.CRAFTING_INPUT, range(1, 5));
            out.put(SlotGroupCategory.PLAYER_ARMOR, range(5, 9));
            out.put(SlotGroupCategory.PLAYER_INVENTORY, range(9, 36));
            out.put(SlotGroupCategory.PLAYER_HOTBAR, range(36, 45));
            out.put(SlotGroupCategory.PLAYER_OFFHAND, range(45, 46));
            return Collections.unmodifiableMap(out);
        });

        // ChestMenu: N storage + 27 inv + 9 hotbar (N = 9, 18, 27, 36, 45, 54)
        // Includes chests, barrels (Barrel uses ChestMenu with size 27).
        SlotGroupCategories.register(ChestMenu.class, menu -> {
            int storage = ownSlotCount(menu) - 36;
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.CHEST_STORAGE, range(0, storage));
            addPlayerInvTail(out, storage);
            return Collections.unmodifiableMap(out);
        });

        // ShulkerBoxMenu: 27 shulker + 27 inv + 9 hotbar = 63 slots in vanilla. Mapped by
        // container, not by index range, because a mod may enlarge the box: Shulker
        // Palette appends more slots over the same shulker container after the player
        // inventory (slots can only be added at construction). Every slot over the
        // box's container is shulker storage, so a 16-row box still reads as one
        // "Shulker" group; the player's slots are sorted by their inventory index.
        // For a vanilla box this gives exactly 0..26, 27..53 and 54..62.
        SlotGroupCategories.register(ShulkerBoxMenu.class, menu -> {
            Container box = menu.slots.get(0).container; // vanilla adds the box's slots first
            List<Integer> storage = new ArrayList<>(), inventory = new ArrayList<>(), hotbar = new ArrayList<>();
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot instanceof CreatedSlot) continue; // created slots resolve as their own groups
                if (slot.container == box) storage.add(i);
                else if (slot.container instanceof Inventory) {
                    (Inventory.isHotbarSlot(slot.getContainerSlot()) ? hotbar : inventory).add(i);
                }
            }
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.SHULKER_STORAGE, ints(storage));
            if (!inventory.isEmpty()) out.put(SlotGroupCategory.PLAYER_INVENTORY, ints(inventory));
            if (!hotbar.isEmpty()) out.put(SlotGroupCategory.PLAYER_HOTBAR, ints(hotbar));
            return Collections.unmodifiableMap(out);
        });

        // DispenserMenu: 9 dispenser (3×3) + 27 inv + 9 hotbar = 45 slots
        // Used by dispenser + dropper (both MenuType.GENERIC_3x3).
        SlotGroupCategories.register(DispenserMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.DISPENSER_STORAGE, range(0, 9));
            addPlayerInvTail(out, 9);
            return Collections.unmodifiableMap(out);
        });

        // HopperMenu: 5 hopper + 27 inv + 9 hotbar = 41 slots
        SlotGroupCategories.register(HopperMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.HOPPER_STORAGE, range(0, 5));
            addPlayerInvTail(out, 5);
            return Collections.unmodifiableMap(out);
        });
    }

    // Crafting family

    private static void registerCraftingFamily() {
        // CraftingMenu: 1 result + 9 crafting (3×3) + 27 inv + 9 hotbar = 46 slots
        SlotGroupCategories.register(CraftingMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.CRAFTING_OUTPUT, range(0, 1));
            out.put(SlotGroupCategory.CRAFTING_INPUT, range(1, 10));
            addPlayerInvTail(out, 10);
            return Collections.unmodifiableMap(out);
        });

        // CrafterMenu: 9 crafter grid + 27 inv + 9 hotbar + 1 non-interactive result = 46
        // Note: the result slot is at slot 45 (after player inventory), not at slot 0.
        SlotGroupCategories.register(CrafterMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.CRAFTER_GRID, range(0, 9));
            addPlayerInvTail(out, 9);
            out.put(SlotGroupCategory.CRAFTER_RESULT, range(45, 46));
            return Collections.unmodifiableMap(out);
        });
    }

    // Furnace family

    private static void registerFurnaceFamily() {
        // AbstractFurnaceMenu: slot 0 input, 1 fuel, 2 output. Then 27 inv + 9 hotbar.
        // FurnaceMenu, SmokerMenu and BlastFurnaceMenu share the layout, so one
        // resolver registers for all three.
        SlotGroupResolver furnaceResolver = menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.FURNACE_INPUT, range(0, 1));
            out.put(SlotGroupCategory.FURNACE_FUEL, range(1, 2));
            out.put(SlotGroupCategory.FURNACE_OUTPUT, range(2, 3));
            addPlayerInvTail(out, 3);
            return Collections.unmodifiableMap(out);
        };
        SlotGroupCategories.register(FurnaceMenu.class, furnaceResolver);
        SlotGroupCategories.register(SmokerMenu.class, furnaceResolver);
        SlotGroupCategories.register(BlastFurnaceMenu.class, furnaceResolver);
    }

    // Utility blocks with slots

    private static void registerUtilityBlocks() {
        // EnchantmentMenu: slot 0 input, slot 1 lapis. Then 27 inv + 9 hotbar.
        SlotGroupCategories.register(EnchantmentMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.ENCHANTING_INPUT, range(0, 1));
            out.put(SlotGroupCategory.ENCHANTING_LAPIS, range(1, 2));
            addPlayerInvTail(out, 2);
            return Collections.unmodifiableMap(out);
        });

        // AnvilMenu (via ItemCombinerMenu): slots 0-1 inputs, slot 2 output. Then inv.
        SlotGroupCategories.register(AnvilMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.ANVIL_INPUT, range(0, 2));
            out.put(SlotGroupCategory.ANVIL_OUTPUT, range(2, 3));
            addPlayerInvTail(out, 3);
            return Collections.unmodifiableMap(out);
        });

        // GrindstoneMenu: slots 0-1 inputs, slot 2 output. Then inv.
        SlotGroupCategories.register(GrindstoneMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.GRINDSTONE_INPUT, range(0, 2));
            out.put(SlotGroupCategory.GRINDSTONE_OUTPUT, range(2, 3));
            addPlayerInvTail(out, 3);
            return Collections.unmodifiableMap(out);
        });

        // SmithingMenu: slot 0 template, 1 base, 2 addition, 3 output. Then inv.
        SlotGroupCategories.register(SmithingMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.SMITHING_TEMPLATE, range(0, 1));
            out.put(SlotGroupCategory.SMITHING_BASE, range(1, 2));
            out.put(SlotGroupCategory.SMITHING_ADDITION, range(2, 3));
            out.put(SlotGroupCategory.SMITHING_OUTPUT, range(3, 4));
            addPlayerInvTail(out, 4);
            return Collections.unmodifiableMap(out);
        });

        // LoomMenu: slot 0 banner, 1 dye, 2 pattern, 3 output. Then inv (INV_SLOT_START=4).
        SlotGroupCategories.register(LoomMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.LOOM_BANNER, range(0, 1));
            out.put(SlotGroupCategory.LOOM_DYE, range(1, 2));
            out.put(SlotGroupCategory.LOOM_PATTERN, range(2, 3));
            out.put(SlotGroupCategory.LOOM_OUTPUT, range(3, 4));
            addPlayerInvTail(out, 4);
            return Collections.unmodifiableMap(out);
        });

        // StonecutterMenu: slot 0 input, slot 1 output. Then inv (INV_SLOT_START=2).
        SlotGroupCategories.register(StonecutterMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.STONECUTTER_INPUT, range(0, 1));
            out.put(SlotGroupCategory.STONECUTTER_OUTPUT, range(1, 2));
            addPlayerInvTail(out, 2);
            return Collections.unmodifiableMap(out);
        });

        // CartographyTableMenu: slot 0 map, 1 additional, 2 result. Then inv.
        SlotGroupCategories.register(CartographyTableMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.CARTOGRAPHY_MAP, range(0, 1));
            out.put(SlotGroupCategory.CARTOGRAPHY_ADDITIONAL, range(1, 2));
            out.put(SlotGroupCategory.CARTOGRAPHY_OUTPUT, range(2, 3));
            addPlayerInvTail(out, 3);
            return Collections.unmodifiableMap(out);
        });
    }

    // Brewing, trading and beacon

    private static void registerBrewingTradingBeacon() {
        // BrewingStandMenu: slots 0-2 potions, 3 ingredient, 4 fuel. Then inv.
        SlotGroupCategories.register(BrewingStandMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.BREWING_POTIONS, range(0, 3));
            out.put(SlotGroupCategory.BREWING_INGREDIENT, range(3, 4));
            out.put(SlotGroupCategory.BREWING_FUEL, range(4, 5));
            addPlayerInvTail(out, 5);
            return Collections.unmodifiableMap(out);
        });

        // MerchantMenu: slots 0-1 payment, slot 2 result. Then inv.
        SlotGroupCategories.register(MerchantMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.MERCHANT_PAYMENT, range(0, 2));
            out.put(SlotGroupCategory.MERCHANT_RESULT, range(2, 3));
            addPlayerInvTail(out, 3);
            return Collections.unmodifiableMap(out);
        });

        // BeaconMenu: slot 0 payment. Then inv.
        SlotGroupCategories.register(BeaconMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.BEACON_PAYMENT, range(0, 1));
            addPlayerInvTail(out, 1);
            return Collections.unmodifiableMap(out);
        });
    }

    // Mounts (horse and nautilus, through AbstractMountInventoryMenu)

    private static void registerMounts() {
        // HorseInventoryMenu: slot 0 saddle, 1 body armor, then optional storage
        // (3 * j slots, j = 0 for horse/mule, 3 for donkey/mule-with-chest, 5 for
        // llama). Then 27 inv + 9 hotbar. Total = 2 + 3j + 36.
        SlotGroupCategories.register(HorseInventoryMenu.class, menu -> {
            int storage = ownSlotCount(menu) - 38; // 38 = 2 saddle/armor + 36 player
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.MOUNT_SADDLE, range(0, 1));
            out.put(SlotGroupCategory.MOUNT_BODY_ARMOR, range(1, 2));
            if (storage > 0) {
                out.put(SlotGroupCategory.MOUNT_STORAGE, range(2, 2 + storage));
            }
            addPlayerInvTail(out, 2 + storage);
            return Collections.unmodifiableMap(out);
        });

        // NautilusInventoryMenu: slot 0 saddle, 1 body armor. Then 27 inv + 9 hotbar.
        // No storage grid for nautilus.
        SlotGroupCategories.register(NautilusInventoryMenu.class, menu -> {
            Map<SlotGroupCategory, int[]> out = new LinkedHashMap<>();
            out.put(SlotGroupCategory.MOUNT_SADDLE, range(0, 1));
            out.put(SlotGroupCategory.MOUNT_BODY_ARMOR, range(1, 2));
            addPlayerInvTail(out, 2);
            return Collections.unmodifiableMap(out);
        });
    }

}
