package com.trevorschoeny.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

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

    /** Update the hovered slot (MKClient, each client tick). Null clears it. */
    public static void tickHover(@Nullable AbstractContainerMenu menu, @Nullable Slot hoveredSlot) {
        hovered = (menu != null && hoveredSlot != null)
                ? ClientSlotAddressing.addressOf(menu, hoveredSlot) : null;
    }

    /** Record the last-clicked slot (MKClient, on a screen click). */
    public static void recordClick(@Nullable AbstractContainerMenu menu, @Nullable Slot clickedSlot) {
        if (menu != null && clickedSlot != null) {
            selected = ClientSlotAddressing.addressOf(menu, clickedSlot);
        }
    }

    /** Clear the selection (MKClient, when a container screen closes). */
    public static void clearSelection() {
        selected = null;
    }

    /** The hovered slot's {@link Address}, or {@code null} if nothing is hovered. */
    public static @Nullable Address hovered() { return hovered; }

    /** The last-clicked slot's {@link Address}, or {@code null}. */
    public static @Nullable Address selected() { return selected; }

    /** Whether {@code address} is the currently-hovered slot. */
    public static boolean isHovered(@Nullable Address address) {
        return address != null && address.equals(hovered);
    }

    /** Whether {@code address} is the last-selected slot. */
    public static boolean isSelected(@Nullable Address address) {
        return address != null && address.equals(selected);
    }
}
