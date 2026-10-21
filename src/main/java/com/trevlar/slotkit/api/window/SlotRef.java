package com.trevlar.slotkit.api.window;

import com.trevlar.slotkit.window.ClientSlotAddressing;
import com.trevlar.slotkit.window.VanillaAddressing;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.core.Slots;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * guest is not checked against the host's locks. The slot's {@link Address} is
 * here too, minted once by the installed rule, so a veto that keys on it never
 * derives it itself. Anything the seam does not have is {@code null}: an inventory
 * insert ({@code Inventory.getFreeSlot}) has no menu and no slot, only the
 * inventory, its index and its owner, and an address only where the container has
 * an identity.
 *
 * @param container     the container the slot reads and writes
 * @param containerSlot the slot's index within {@code container}
 * @param slot          the live slot on the menu, or {@code null} off-menu
 * @param menu          the open menu, or {@code null} off-menu
 * @param player        who is acting, or {@code null} for automation
 * @param category      the slot's category on {@code menu}, or {@code null} when
 *                      off-menu or in no category
 * @param address       the slot's window address, or {@code null} when the seam
 *                      cannot identify the container
 */
public record SlotRef(Container container, int containerSlot, @Nullable Slot slot,
                      @Nullable AbstractContainerMenu menu, @Nullable Player player,
                      @Nullable SlotGroupCategory category, @Nullable Address address) {

    public SlotRef {
        Objects.requireNonNull(container, "container");
    }

    /** A slot reached through an open menu: the click and shift-click seams. */
    public static SlotRef of(AbstractContainerMenu menu, Slot slot, @Nullable Player player) {
        Slot target = Slots.target(slot); // identity comes from under the creative wrapper
        return new SlotRef(target.container, target.getContainerSlot(), slot, menu, player,
                categoryOf(menu, slot), addressOf(menu, slot));
    }

    /**
     * The player-inventory slot at {@code index} as it sits on {@code menu}, so it
     * resolves with its menu category; the bare container form when the menu does
     * not show it (vanilla swaps against the inventory directly, so the swap works
     * on such a menu too).
     */
    public static SlotRef inventory(AbstractContainerMenu menu, Player player, int index) {
        Inventory inventory = player.getInventory();
        for (Slot s : menu.slots) {
            Slot target = Slots.target(s);
            if (target.container == inventory && target.getContainerSlot() == index) {
                return of(menu, s, player);
            }
        }
        return of(inventory, index, player);
    }

    /** A slot reached with no menu open: an inventory insert. */
    public static SlotRef of(Container container, int containerSlot, @Nullable Player player) {
        return new SlotRef(container, containerSlot, null, null, player, null,
                VanillaAddressing.addressOf(container, containerSlot).orElse(null));
    }

    // A slot's category and address are computed once per Slot object, which is what
    // every operation question and shift-click walk asks about. Keyed by the slot, not
    // the menu: creative rebuilds its slot list on every tab switch, and a rebuilt
    // wrapper is a new key. Weak keys: a slot lives as long as its menu.
    private static final Map<Slot, Optional<SlotGroupCategory>> CATEGORIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Slot, Address> ADDRESSES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static @Nullable SlotGroupCategory categoryOf(AbstractContainerMenu menu, Slot slot) {
        Optional<SlotGroupCategory> known = CATEGORIES.get(slot);
        if (known == null) {
            // One resolve answers every slot on the menu, so fill them all at once.
            Map<Slot, SlotGroupCategory> all = SlotGroupCategories.categoriesBySlot(menu);
            all.forEach((s, c) -> CATEGORIES.putIfAbsent(s, Optional.of(c)));
            known = Optional.ofNullable(all.get(slot));
            CATEGORIES.putIfAbsent(slot, known);
        }
        return known.orElse(null);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public static Address addressOf(AbstractContainerMenu menu, Slot slot) {
        Address known = ADDRESSES.get(slot);
        if (known != null) return known;
        Address minted = ClientSlotAddressing.addressOf(menu, slot);
        ADDRESSES.putIfAbsent(slot, minted);
        return minted;
    }
}
