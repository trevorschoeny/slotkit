package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.SlotGroupCategory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Map;

/**
 * Per-menu-class strategy that maps an {@link AbstractContainerMenu}
 * instance to its {@link SlotGroupCategory}-keyed slot groupings. Vanilla
 * menu resolvers ship in
 * {@link com.trevorschoeny.menukit.MKClient}; modded consumers
 * register resolvers for their own menu classes via
 * {@link SlotGroupCategories#register}.
 *
 * <p>The returned map entries' {@link Slot} lists are what
 * {@link ScreenPanelRegistry} uses to compute slot-group bounding boxes
 * per frame. Each slot should appear in exactly one category's list —
 * categorization is 1:N (one category → many slots), not N:N.
 *
 * <p>Resolution runs once per screen open; the result is implicitly
 * cached for the screen's lifetime. Dynamic menus whose slot set changes
 * mid-session aren't supported in v1 (see M8 §5.4 caching constraint).
 *
 * <h3>The documented exception to the address-only rule</h3>
 *
 * This interface (with {@link SlotGroupCategories#register}/{@code extend}/{@code of})
 * is the ONE legitimate consumer-facing raw-vanilla-{@link Slot} surface in MenuKit
 * — the <b>vanilla-observation seam</b>, where a consumer hands MenuKit the raw
 * vanilla {@code Slot}s it has <em>observed</em> on a vanilla menu, grouping them by
 * {@link SlotGroupCategory}. That polarity is the opposite of the created-slot
 * Address world: there the consumer <em>declares</em> slots and addresses them by
 * identity; here the consumer is <em>reading back</em> slots vanilla already owns,
 * which have no MenuKit Address to name them by. Everything else in the library is
 * addressed — this seam is the deliberate carve-out. See also
 * {@link SlotGroupCategories}.
 */
@FunctionalInterface
public interface SlotGroupResolver {

    /**
     * Maps the given menu instance's slots to category-keyed sub-lists.
     * Returns an empty map for categories the menu doesn't contain
     * (rather than populating with empty lists).
     */
    Map<SlotGroupCategory, List<Slot>> resolve(AbstractContainerMenu menu);
}
