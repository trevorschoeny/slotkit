package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.ClientSlotAddressing;
import com.trevlar.menukit.window.ObservedReactions;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Which slot the cursor is over ({@link #hovered}) and which slot was last clicked
 * ({@link #selected}), as {@link Address}es, observed on the client. The
 * {@link ObservedReactions} counterpart for interaction rather than content: both
 * are client observers keyed by address through {@link ClientSlotAddressing}.
 *
 * <h2>Keyed by address</h2>
 *
 * A consumer asks about its own things, {@code WindowSignals.isHovered(addr)} or
 * {@code isSelected(addr)}, where {@code addr} is a panel or created-slot address it
 * minted by id. It does not ask which vanilla index is hovered: addressing by
 * identity makes a rule work the same in creative and survival, since a created
 * slot has one address in both. {@code MenuKitClient} captures hover each client
 * tick and selection on a screen click, clears the selection on close, and maps
 * both through the shared addressing, so a hovered created slot reports its
 * created address.
 *
 * <p>Client thread only.
 */
public final class WindowSignals {

    private WindowSignals() {}

    private static volatile @Nullable Address hovered;
    private static volatile @Nullable Address selected;

    // Display names (SlotNames) for the hovered and selected slot, computed here
    // where the live slot is in hand, so a consumer holding only the address can
    // still read a label.
    private static volatile @Nullable String hoveredName;
    private static volatile @Nullable String selectedName;

    /**
     * Updates the hovered slot (MenuKitClient, each client tick). Null clears it.
     * Only MenuKit's client tick writes the signals, from raw slots on the live
     * screen. Consumers read them through {@link #isHovered}, {@link #hoveredName}
     * and the rest.
     */
    @ApiStatus.Internal
    public static void tickHover(@Nullable AbstractContainerMenu menu, @Nullable Slot hoveredSlot) {
        hovered = (menu != null && hoveredSlot != null)
                ? ClientSlotAddressing.addressOf(menu, hoveredSlot) : null;
        hoveredName = SlotNames.nameOf(hovered, menu, hoveredSlot);
    }

    /**
     * Records the last-clicked slot (MenuKitClient, on a screen click). Consumers
     * read the selection through {@link #isSelected} and {@link #selectedName}.
     */
    @ApiStatus.Internal
    public static void recordClick(@Nullable AbstractContainerMenu menu, @Nullable Slot clickedSlot) {
        if (menu != null && clickedSlot != null) {
            selected = ClientSlotAddressing.addressOf(menu, clickedSlot);
            selectedName = SlotNames.nameOf(selected, menu, clickedSlot);
        }
    }

    /**
     * Clears the selection (MenuKitClient, when a container screen closes). Only
     * MenuKit calls it; consumers never reset the selection themselves.
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

    /** Whether {@code address} is the hovered slot. */
    public static boolean isHovered(@Nullable Address address) {
        return address != null && address.equals(hovered);
    }

    /** Whether {@code address} is the last-selected slot. */
    public static boolean isSelected(@Nullable Address address) {
        return address != null && address.equals(selected);
    }
}
