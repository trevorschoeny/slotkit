package com.trevorschoeny.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * The client-observed slot INTERACTION signals — which slot the cursor is over
 * ({@link #hovered}) and which slot was last clicked ({@link #selected}). The
 * interaction sibling of {@link ObservedReactions} (which observes content): both
 * are pure client observers keyed by {@link Address} through {@link ClientSlotAddressing}.
 *
 * <h2>Why this exists (the window owns slot reactivity)</h2>
 *
 * THE ONE WINDOW already lets a panel/element react to slot <em>content</em>
 * (observed reactions) and to any client predicate (a {@link VisibilityRule}). But a
 * rule that wants to react to <em>hover</em> or <em>click</em> had nowhere to read
 * that from — it would have to reach into the vanilla screen. This is the one place
 * for it: a {@code VisibilityRule} reads {@code WindowSignals.isHovered(addr)} /
 * {@code isSelected(addr)}, so "reveal this panel while that slot is hovered" or
 * "show info for the last-clicked slot" is expressed through the window, by address.
 *
 * <h2>Two ways to identify the slot</h2>
 *
 * {@link #hovered}/{@link #selected} give the kind-aware {@link Address} (use for a
 * created slot, mintable by id via the MKC adapter). {@link #hoveredIndex}/{@link
 * #selectedIndex} give the slot's index within the currently-open menu (handy for a
 * vanilla slot a consumer knows by position). Both are updated by {@code MKClient}
 * (hover each client tick; selection on a screen click); pure state otherwise.
 *
 * <p>Client-thread only.
 */
public final class WindowSignals {

    private WindowSignals() {}

    private static volatile @Nullable Address hoveredAddr;
    private static volatile int hoveredIdx = -1;
    private static volatile @Nullable Address selectedAddr;
    private static volatile int selectedIdx = -1;

    /** Update the hovered slot (MKClient, each client tick). Null clears it. */
    public static void tickHover(@Nullable AbstractContainerMenu menu, @Nullable Slot hoveredSlot) {
        if (menu != null && hoveredSlot != null) {
            hoveredAddr = ClientSlotAddressing.addressOf(menu, hoveredSlot);
            hoveredIdx = hoveredSlot.index;
        } else {
            hoveredAddr = null;
            hoveredIdx = -1;
        }
    }

    /** Record the last-clicked slot (MKClient, on a screen click). */
    public static void recordClick(@Nullable AbstractContainerMenu menu, @Nullable Slot clickedSlot) {
        if (menu != null && clickedSlot != null) {
            selectedAddr = ClientSlotAddressing.addressOf(menu, clickedSlot);
            selectedIdx = clickedSlot.index;
        }
    }

    /** Clear the selection (MKClient, when a container screen closes). */
    public static void clearSelection() {
        selectedAddr = null;
        selectedIdx = -1;
    }

    /** The hovered slot's {@link Address}, or {@code null} if nothing is hovered. */
    public static @Nullable Address hovered() { return hoveredAddr; }

    /** The hovered slot's index within the open menu, or {@code -1}. */
    public static int hoveredIndex() { return hoveredIdx; }

    /** The last-clicked slot's {@link Address}, or {@code null}. */
    public static @Nullable Address selected() { return selectedAddr; }

    /** The last-clicked slot's index within the open menu, or {@code -1}. */
    public static int selectedIndex() { return selectedIdx; }

    /** Whether {@code address} is the currently-hovered slot. */
    public static boolean isHovered(@Nullable Address address) {
        return address != null && address.equals(hoveredAddr);
    }

    /** Whether {@code address} is the last-selected slot. */
    public static boolean isSelected(@Nullable Address address) {
        return address != null && address.equals(selectedAddr);
    }
}
