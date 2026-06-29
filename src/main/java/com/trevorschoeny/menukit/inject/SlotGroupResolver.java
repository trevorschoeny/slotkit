package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.SlotGroupCategory;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Map;

/**
 * Per-menu-class strategy that maps an {@link AbstractContainerMenu} instance
 * to its {@link SlotGroupCategory}-keyed slot groupings, expressed as
 * <b>slot indices</b> (positions in {@code menu.slots}). The library owns the
 * index→{@link net.minecraft.world.inventory.Slot} dereference internally
 * ({@link SlotGroupCategories#of}); a consumer never holds a raw vanilla
 * {@code Slot}.
 *
 * <p>Library vanilla resolvers ship in
 * {@link com.trevorschoeny.menukit.MKClient}; modded consumers register
 * resolvers for their own menu classes via
 * {@link SlotGroupCategories#register}.
 *
 * <p>The returned map's index arrays are what {@link ScreenPanelRegistry} turns
 * into slot-group bounding boxes per frame. Each slot should appear in exactly
 * one category's array — categorization is 1:N (one category → many slots), not
 * N:N.
 *
 * <p>Resolution runs once per screen open; the result is implicitly cached for
 * the screen's lifetime. Dynamic menus whose slot set changes mid-session aren't
 * supported in v1 (see M8 §5.4 caching constraint).
 *
 * <h3>Indices, not raw slots — the address-only line holds here too</h3>
 *
 * Earlier this interface returned {@code List<Slot>}, the one consumer-facing
 * raw-vanilla-{@code Slot} surface in MenuKit. That leak is gone: a consumer now
 * describes a slot group by index, never by dereferencing {@code menu.slots}.
 * Two clean patterns cover every need:
 * <ul>
 *   <li><b>Vanilla slot groups</b> (the hotbar, a furnace's input, the crafting
 *       grid) are already shipped as built-in {@link SlotGroupCategory}
 *       constants ({@link VanillaSlotGroupResolvers}). A consumer anchors to
 *       {@code SlotGroupCategory.PLAYER_HOTBAR} directly — no resolver, no slots.</li>
 *   <li><b>A consumer's own menu</b> — the consumer built it, so its slot order
 *       is the consumer's own coordinate system. Returning {@code int[]} indices
 *       into that order references the consumer's own layout, not vanilla's.</li>
 * </ul>
 * So a consumer only ever writes a resolver for their own menu classes, in their
 * own index space — the library does the rest. See also {@link SlotGroupCategories}.
 */
@FunctionalInterface
public interface SlotGroupResolver {

    /**
     * Maps the given menu instance's slots to category-keyed index arrays.
     * Returns an empty map for categories the menu doesn't contain (rather than
     * populating with empty arrays). Out-of-range indices are skipped
     * defensively by {@link SlotGroupCategories#of} at dereference time.
     */
    Map<SlotGroupCategory, int[]> resolve(AbstractContainerMenu menu);
}
