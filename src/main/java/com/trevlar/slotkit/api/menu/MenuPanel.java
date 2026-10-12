package com.trevlar.slotkit.api.menu;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.CreatedSlots;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A panel as a menu knows it on both sides: its id and whether its slots are shown.
 * The server's half of a panel. What a panel looks like (its elements, style
 * and placement) is MenuKit's client {@code Panel}, built by the screen; a created slot
 * reads only this, so a menu builds the same slots on a dedicated server, where no
 * {@code Panel} exists.
 *
 * <p>A hidden panel's slots are inert: {@link CreatedSlot#getItem()} returns
 * empty, and they refuse placement and pickup. On a custom menu the server decides
 * whether a panel is shown and the client follows ({@code CustomContainerMenu});
 * on a vanilla menu ({@link CreatedSlots#onto}) the server always shows it and the client
 * shows it according to its reveal predicate.
 *
 * @param id    the panel id the menu's created slots carry
 * @param shown whether the panel is shown now; read every time a slot asks
 */
public record MenuPanel(String id, BooleanSupplier shown) {

    public MenuPanel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shown, "shown");
    }

    /** Whether the panel is shown now. */
    public boolean isShown() {
        return shown.getAsBoolean();
    }
}
