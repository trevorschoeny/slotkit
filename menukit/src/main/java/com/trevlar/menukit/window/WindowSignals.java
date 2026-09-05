package com.trevlar.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The client-observed slot INTERACTION signals — which slot the cursor is over
 * ({@link #hovered}) and which slot was last clicked ({@link #selected}), as
 * {@link Address}es. The interaction sibling of {@link ObservedReactions} (which
 * observes content): both are pure client observers keyed by address through
 * {@link ClientSlotAddressing}.
 *
 * <h2>Address-keyed, by design</h2>
 *
 * A consumer reacts to its OWN addressed things — {@code WindowSignals.isHovered(addr)}
 * / {@code isSelected(addr)} where {@code addr} is a panel/created-slot address it
 * minted by id. It does NOT ask "what vanilla index is hovered" — addressing by
 * identity is what makes a rule work the same in creative and survival (a created
 * slot has one address in both). The hover/click are captured by {@code MKClient}
 * (hover each client tick; selection on a screen click; cleared on close) and mapped
 * through the shared addressing, so a hovered created slot reports its created address.
 *
 * <p>Client-thread only.
 */
public final class WindowSignals {

    private WindowSignals() {}

    private static volatile @Nullable Address hovered;
    private static volatile @Nullable Address selected;

    // Display names (SlotNames) for the hovered/selected slot — computed here, where
    // the live slot is in hand, so a consumer holding only the address can still read
    // a human label. Pure display; mirrors the address fields above.
    private static volatile @Nullable String hoveredName;
    private static volatile @Nullable String selectedName;

    /**
     * Update the hovered slot (MKClient, each client tick). Null clears it.
     *
     * <p><b>Internal write feeder.</b> Only MK's client tick feeds the signals;
     * it takes raw vanilla {@code Slot}s straight off the live screen. Consumers
     * READ the signals through the public address-keyed/display API below
     * ({@link #isHovered}/{@link #hoveredName}/…); they never feed them.
     */
    @ApiStatus.Internal
    public static void tickHover(@Nullable AbstractContainerMenu menu, @Nullable Slot hoveredSlot) {
        hovered = (menu != null && hoveredSlot != null)
                ? ClientSlotAddressing.addressOf(menu, hoveredSlot) : null;
        hoveredName = SlotNames.nameOf(hovered, menu, hoveredSlot);
    }

    /**
     * Record the last-clicked slot (MKClient, on a screen click).
     *
     * <p><b>Internal write feeder</b> (see {@link #tickHover}). Consumers read the
     * selection through the public API ({@link #isSelected}/{@link #selectedName}).
     */
    @ApiStatus.Internal
    public static void recordClick(@Nullable AbstractContainerMenu menu, @Nullable Slot clickedSlot) {
        if (menu != null && clickedSlot != null) {
            selected = ClientSlotAddressing.addressOf(menu, clickedSlot);
            selectedName = SlotNames.nameOf(selected, menu, clickedSlot);
        }
    }

    /**
     * Clear the selection (MKClient, when a container screen closes).
     *
     * <p><b>Internal write feeder</b> (see {@link #tickHover}/{@link #recordClick}) —
     * the third member of the write trio. Only MK's client lifecycle calls it;
     * consumers read the selection through the public API
     * ({@link #isSelected}/{@link #selectedName}) and never reset it themselves.
     */
    @ApiStatus.Internal
    public static void clearSelection() {
        selected = null;
        selectedName = null;
    }

    /** The hovered slot's {@link Address}, or {@code null} if nothing is hovered. */
    public static @Nullable Address hovered() { return hovered; }

    /** The last-clicked slot's {@link Address}, or {@code null}. */
    public static @Nullable Address selected() { return selected; }

    /** The hovered slot's display name (e.g. "Hotbar 3", "Chestplate"), or {@code null}. */
    public static @Nullable String hoveredName() { return hoveredName; }

    /** The last-clicked slot's display name, or {@code null}. */
    public static @Nullable String selectedName() { return selectedName; }

    /** Whether {@code address} is the currently-hovered slot. */
    public static boolean isHovered(@Nullable Address address) {
        return address != null && address.equals(hovered);
    }

    /** Whether {@code address} is the last-selected slot. */
    public static boolean isSelected(@Nullable Address address) {
        return address != null && address.equals(selected);
    }
}
