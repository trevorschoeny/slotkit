package com.trevlar.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The entry point to THE ONE WINDOW — how a consumer obtains a typed handle to set
 * or read behavior on anything addressable.
 *
 * <h2>Menu-independent vs menu-bound</h2>
 *
 * A panel, a panel element, and a created slot have ONE address everywhere they
 * appear (rooted at a constant family by id), so handles to them need no menu and
 * are reached statically — {@link #panel}, {@link #element}. This matters for a
 * standalone screen that has panels but no {@code AbstractContainerMenu} at all:
 * you still address its panels the same way.
 *
 * <p>Only a <em>vanilla</em> slot is identified by its position in a live menu, so
 * addressing one by index needs that menu — {@link #view(AbstractContainerMenu)}
 * returns the menu-bound {@link WindowView} for {@code slot(int)} and friends.
 */
public final class Window {

    private Window() {}

    /** A handle on the panel with id {@code panelId} (its own visibility/opacity/inertness). */
    public static PanelHandle panel(String panelId) {
        return new PanelHandle(PanelAddressing.ofPanel(panelId));
    }

    /** A handle on element {@code elementDeclId} within panel {@code panelId}. */
    public static ElementHandle element(String panelId, String elementDeclId) {
        return new ElementHandle(PanelAddressing.ofElement(panelId, elementDeclId));
    }

    /** A handle on anything already named by an {@link Address} (slot/element/panel). */
    public static SlotHandle slot(Address address) {
        return new SlotHandle(address);
    }

    /** The menu-bound view, for addressing a vanilla slot by its live menu index. */
    public static WindowView view(AbstractContainerMenu menu) {
        return WindowView.of(menu);
    }
}
