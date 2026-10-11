package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.inject.SlotHoverResult;
import com.trevlar.menukit.inject.SlotScreenHook;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Set;

/**
 * Containers' implementation of MenuKit's {@link com.trevlar.menukit.inject.SlotScreenHook}.
 * This is where input for registered slots hosted in panels plugs into MenuKit's
 * screen dispatch. Registered with {@code SlotScreenDispatcher.setHook} at client
 * init.
 *
 * <p>It handles input and the per-frame park. Vanilla draws a registered slot in its
 * own slot pass at the {@code Slot.x/y} its {@code SlotElement} wrote, so this hook
 * renders nothing. {@link #isCreated} marks the layer boundary in vanilla's slot
 * pass, and {@link #endFrame} parks the attached slots no panel showed this frame.
 * The rest is hover and click resolution. Using the panel ids from
 * {@link SlotElementRegistry} (those that currently host a {@code SlotElement}), it
 * asks {@link CreatedSlotInput} which {@code CreatedSlot} a screen point covers, so
 * vanilla's {@code getHoveredSlot} sends hover and clicks to the registered slot
 * rather than the vanilla slot beneath it. It also eats clicks that land in a
 * revealed panel's empty space.
 *
 * <p>Client only: every method runs on the render and input thread, and the server
 * never constructs this.
 */
public final class CreatedSlotScreenHook implements SlotScreenHook {

    @Override
    public boolean isCreated(net.minecraft.world.inventory.Slot slot) {
        return CreatedSlotAccess.asMKCSlot(slot) != null;
    }

    @Override
    public void endFrame(AbstractContainerScreen<?> screen) {
        if (!SlotElementRegistry.hasActive()) return;
        SlotElementRegistry.parkUnpresented(screen.getMenu());
    }

    @Override
    public SlotHoverResult resolveHover(AbstractContainerScreen<?> screen,
                                         double mouseX, double mouseY) {
        Set<String> ids = SlotElementRegistry.activePanelIds();
        if (ids.isEmpty()) return SlotHoverResult.PASS;

        CreatedSlotInput.Resolution r =
                CreatedSlotInput.resolveHoveredSlot(screen, mouseX, mouseY, ids);
        if (!r.handled()) return SlotHoverResult.PASS;
        // Handled with a slot: the slot wins. Handled with null: a gap in the panel, so block.
        return r.slot() == null ? SlotHoverResult.BLOCK : SlotHoverResult.of(r.slot());
    }

    @Override
    public boolean mouseClicked(AbstractContainerScreen<?> screen,
                                double mouseX, double mouseY, int button) {
        // Eat a click that lands in a revealed panel's empty space, so a carried item
        // can't fall through to the inert vanilla slot behind it. A click on the slot
        // itself is not eaten, since getHoveredSlot already routed it. In an opaque
        // panel, opacity already eats gap clicks at the MouseHandler level before this
        // runs; this still covers a non-opaque panel of bare slots.
        Set<String> ids = SlotElementRegistry.activePanelIds();
        if (ids.isEmpty()) return false;
        CreatedSlotInput.Resolution r =
                CreatedSlotInput.resolveHoveredSlot(screen, mouseX, mouseY, ids);
        return r.handled() && r.slot() == null;
    }
}
