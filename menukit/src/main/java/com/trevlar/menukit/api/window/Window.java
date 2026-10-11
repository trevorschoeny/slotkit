package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.PanelAddressing;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The entry point to the window: where a consumer gets a typed handle to set or read
 * behavior on anything addressable.
 *
 * <h2>Menu-independent and menu-bound</h2>
 *
 * A panel, a panel element and a created slot have one address everywhere they
 * appear (rooted at a constant family by id), so their handles need no menu and
 * are reached statically through {@link #panel} and {@link #element}. A standalone
 * screen with panels but no {@code AbstractContainerMenu} addresses its panels the
 * same way.
 *
 * <p>Only a vanilla slot is identified by its position in a live menu, so
 * addressing one by index needs that menu: {@link #view(AbstractContainerMenu)}
 * returns the menu-bound {@link WindowView} for {@code slot(int)} and the rest.
 */
public final class Window {

    private Window() {}

    /** A handle on the panel with id {@code panelId} (its own visibility and opacity). */
    public static PanelHandle panel(String panelId) {
        return new PanelHandle(PanelAddressing.ofPanel(panelId));
    }

    /** A handle on element {@code elementDeclId} within panel {@code panelId}. */
    public static ElementHandle element(String panelId, String elementDeclId) {
        return new ElementHandle(PanelAddressing.ofElement(panelId, elementDeclId));
    }

    /** A handle on anything already named by an {@link Address} (slot, element or panel). */
    public static SlotHandle slot(Address address) {
        return new SlotHandle(address);
    }

    /** The menu-bound view, for addressing a vanilla slot by its live menu index. */
    public static WindowView view(AbstractContainerMenu menu) {
        return WindowView.of(menu);
    }
}
