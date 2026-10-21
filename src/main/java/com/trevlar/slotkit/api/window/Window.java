package com.trevlar.slotkit.api.window;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.window.PanelAddressing;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The entry point to the window: where a consumer gets a typed handle to set or read
 * behavior on anything addressable.
 *
 * <h2>Menu-independent and menu-bound</h2>
 *
 * A created slot has one address everywhere it appears (nested under its panel's
 * id), so its handle needs no menu and is reached statically through
 * {@link #slot(Address)}. A panel's own visibility and opacity are the panel's
 * ({@code Panel.visibleWhen}, {@code opaque}), not the window's.
 *
 * <p>Only a vanilla slot is identified by its position in a live menu, so
 * addressing one by index needs that menu: {@link #view(AbstractContainerMenu)}
 * returns the menu-bound {@link WindowView} for {@code slot(int)} and the rest.
 */
public final class Window {

    private Window() {}

    /**
     * A handle on the slot {@code address} names, declaring for {@code scope}: its gate,
     * binding and operations apply while the acting player's mode is in that scope.
     * {@code Window.slot(ModeScope.SURVIVAL, a).gate(g)} plus
     * {@code Window.slot(ModeScope.BOTH, a).gate(h)} is "g in survival, h otherwise".
     */
    public static SlotHandle slot(ModeScope scope, Address address) {
        return new SlotHandle(scope, address);
    }

    /**
     * {@link #slot(ModeScope, Address)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code Window.slot(ModeScope.BOTH, address)}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static SlotHandle slot(Address address) {
        return new SlotHandle(ModeScope.BOTH, address);
    }

    /** The menu-bound view, for addressing a vanilla slot by its live menu index. */
    public static WindowView view(AbstractContainerMenu menu) {
        return WindowView.of(menu);
    }
}
