package com.trevlar.menukit.window;

import com.trevlar.menukit.core.SlotGroupCategory;
import com.trevlar.menukit.inject.SlotGroupCategories;
import com.trevlar.menukit.inject.Slots;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The slot an {@linkplain SlotOperations operation} is about to act on, as much
 * of it as the seam can supply. What a {@link SlotOperations.Veto} sees.
 *
 * <p>Every seam has the backing {@link Container} and the container-relative
 * index, which is how a locking mod already keys its locks (the player inventory
 * by index, an ender chest by index, a placed container by identity). A menu seam
 * also has the live {@link Slot} and its menu, and from those the slot's
 * {@link SlotGroupCategory} on that menu. A click seam has the acting
 * {@link Player}; a per-player lock needs to know whose click this is, so a LAN
 * guest is not checked against the host's locks. Anything the seam does not have
 * is {@code null}: world pickup ({@code Inventory.getFreeSlot}) has no menu and
 * no slot, only the inventory, its index and its owner.
 *
 * @param container     the container the slot reads and writes
 * @param containerSlot the slot's index within {@code container}
 * @param slot          the live slot on the menu, or {@code null} off-menu
 * @param menu          the open menu, or {@code null} off-menu
 * @param player        who is acting, or {@code null} for automation
 * @param category      the slot's category on {@code menu}, or {@code null} when
 *                      off-menu or in no category
 */
public record SlotRef(Container container, int containerSlot, @Nullable Slot slot,
                      @Nullable AbstractContainerMenu menu, @Nullable Player player,
                      @Nullable SlotGroupCategory category) {

    public SlotRef {
        Objects.requireNonNull(container, "container");
    }

    /** A slot reached through an open menu: the click and shift-click seams. */
    public static SlotRef of(AbstractContainerMenu menu, Slot slot, @Nullable Player player) {
        Slot target = Slots.target(slot); // identity off the creative wrapper, as everywhere
        return new SlotRef(target.container, target.getContainerSlot(), slot, menu, player,
                categoryOf(menu, slot));
    }

    /** A slot reached with no menu open: world pickup into an inventory. */
    public static SlotRef of(Container container, int containerSlot, @Nullable Player player) {
        return new SlotRef(container, containerSlot, null, null, player, null);
    }

    // A menu's slot list is fixed at construction (Containers appends its created
    // slots there too), so its category map is computed once per menu instance and
    // read for every slot a shift-click walks. Weak keys: a menu lives as long as
    // its screen. ponytail: no invalidation; if a menu ever grows slots after it
    // opens, key this on (menu, slots.size()).
    private static final Map<AbstractContainerMenu, Map<Slot, SlotGroupCategory>> CATEGORIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static @Nullable SlotGroupCategory categoryOf(AbstractContainerMenu menu, Slot slot) {
        return CATEGORIES.computeIfAbsent(menu, SlotGroupCategories::categoriesBySlot).get(slot);
    }
}
