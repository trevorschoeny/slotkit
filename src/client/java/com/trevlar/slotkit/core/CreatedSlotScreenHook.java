package com.trevlar.slotkit.core;

import com.trevlar.menukit.api.host.SlotHoverResult;
import com.trevlar.menukit.api.host.SlotScreenHook;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Set;

/**
 * SlotKit's implementation of MenuKit's {@link SlotScreenHook}: where input for
 * registered slots hosted in panels plugs into MenuKit's screen dispatch. Installed
 * once ({@link #INSTANCE}) with {@code SlotScreenHook.install} at client init.
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

    /** The one hook, installed into MenuKit at client init and asked directly by SlotKit's own mixins. */
    public static final CreatedSlotScreenHook INSTANCE = new CreatedSlotScreenHook();

    private CreatedSlotScreenHook() {}

    @Override
    public boolean isCreated(net.minecraft.world.inventory.Slot slot) {
        return CreatedSlotAccess.asMKCSlot(slot) != null;
    }

    @Override
    public void endFrame(AbstractContainerScreen<?> screen) {
        SlotElementRegistry.endOfFrame();
        if (!SlotElementRegistry.hasActive()) return;
        SlotElementRegistry.parkUnpresented(screen.getMenu());
    }

    @Override
    public SlotHoverResult resolveHover(AbstractContainerScreen<?> screen,
                                         double mouseX, double mouseY) {
        if (!SlotElementRegistry.hasActive()) return SlotHoverResult.PASS;
        Set<String> ids = SlotElementRegistry.activePanelIds(); // empty when only vanilla slots are hosted

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
        if (!SlotElementRegistry.hasActive()) return false;
        Set<String> ids = SlotElementRegistry.activePanelIds();
        CreatedSlotInput.Resolution r =
                CreatedSlotInput.resolveHoveredSlot(screen, mouseX, mouseY, ids);
        return r.handled() && r.slot() == null;
    }
}
