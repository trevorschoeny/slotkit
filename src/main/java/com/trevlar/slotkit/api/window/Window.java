package com.trevlar.slotkit.api.window;

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

    /** A handle on a slot already named by an {@link Address} (vanilla or created). */
    public static SlotHandle slot(Address address) {
        return new SlotHandle(address);
    }

    /** The menu-bound view, for addressing a vanilla slot by its live menu index. */
    public static WindowView view(AbstractContainerMenu menu) {
        return WindowView.of(menu);
    }
}
