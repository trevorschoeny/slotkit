package com.trevlar.menukit.api.slot;

import com.trevlar.menukit.inject.VanillaSlotGroupResolvers;
import com.trevlar.menukit.api.slot.SlotGroupCategory;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Map;

/**
 * Maps one menu class's instances to slot groups by {@link SlotGroupCategory},
 * as slot indices (positions in {@code menu.slots}). The library turns indices
 * into {@link net.minecraft.world.inventory.Slot}s itself
 * ({@link SlotGroupCategories#of}), so a consumer never holds a raw vanilla
 * {@code Slot}.
 *
 * <p>The vanilla resolvers ship in {@link com.trevlar.menukit.MenuKitClient}. A mod
 * registers resolvers for its own menu classes with
 * {@link SlotGroupCategories#register}.
 *
 * <p>MenuKit's screen registry turns the returned index arrays into slot-group
 * bounding boxes each frame. Each slot should appear in exactly one category's
 * array: one category has many slots, and a slot has one category.
 *
 * <p>Resolution runs once per screen open, and the result is kept for the
 * screen's lifetime. Menus whose slot set changes while open are not supported.
 *
 * <h3>Indices, not raw slots</h3>
 *
 * This interface used to return {@code List<Slot>}, the one place MenuKit handed a
 * consumer raw vanilla slots. Now a consumer describes a group by index and never
 * reads {@code menu.slots}. Two cases cover every need:
 * <ul>
 *   <li><b>Vanilla slot groups</b> (the hotbar, a furnace's input, the crafting
 *       grid) already ship as built-in {@link SlotGroupCategory} constants
 *       ({@link VanillaSlotGroupResolvers}). A consumer anchors to
 *       {@code SlotGroupCategory.PLAYER_HOTBAR} directly, with no resolver.</li>
 *   <li><b>A consumer's own menu</b>: the consumer built it, so its slot order is
 *       the consumer's own. {@code int[]} indices into that order refer to the
 *       consumer's layout, not vanilla's.</li>
 * </ul>
 * So a consumer writes resolvers only for its own menu classes, in its own index
 * space. See also {@link SlotGroupCategories}.
 */
@FunctionalInterface
public interface SlotGroupResolver {

    /**
     * Maps the given menu instance's slots to category-keyed index arrays.
     * Returns an empty map for categories the menu doesn't contain (rather than
     * populating with empty arrays). {@link SlotGroupCategories#of} skips
     * out-of-range indices when it reads the slots.
     */
    Map<SlotGroupCategory, int[]> resolve(AbstractContainerMenu menu);
}
