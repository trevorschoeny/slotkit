package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.CreatedSlots;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-side input resolution for registered slots hosted in panels: the hover and
 * click side of {@code SlotElement}, and where a panel makes what it covers inert for
 * slots. Called from {@link CreatedSlotScreenHook}, which passes the active panel ids
 * from {@link SlotElementRegistry}. Not for consumers.
 *
 * <h3>Why it is needed</h3>
 *
 * Registered {@link CreatedSlot}s are appended last to {@code menu.slots}, so where a
 * registered slot sits over a vanilla slot, vanilla's {@code getHoveredSlot} (which
 * returns the first slot under the cursor) would pick the vanilla slot beneath it.
 * The registered slot is visibly on top (its panel drew the frame and vanilla drew
 * the item at the same {@code x/y}), but clicks would go through it. The rule that
 * everything behind a panel is inert never reached it, because that rule works from
 * MenuKit's panel registry, while a registered slot's hover is resolved from the
 * slot, not the panel box. This class resolves it from the slot. Where a registered
 * slot covers no vanilla slot, vanilla would find it by {@code x/y} on its own, and
 * the result here is the same.
 *
 * <h3>What it does</h3>
 *
 * {@link #resolveHoveredSlot} answers, for a screen point:
 * <ul>
 *   <li>over a revealed registered slot: that slot wins, and hover and click go to it
 *       rather than the covered vanilla slot;</li>
 *   <li>inside a revealed registered panel but between slots: blocked, so the covered
 *       vanilla slot is inert across the panel's bounding box;</li>
 *   <li>anywhere else: pass, and vanilla resolves as usual.</li>
 * </ul>
 *
 * Hidden (inert) registered panels neither win nor block.
 *
 * <h3>How it is wired</h3>
 *
 * MenuKit's {@code AbstractContainerScreen} mixins ({@code getHoveredSlot},
 * {@code mouseClicked}) call the {@link com.trevlar.menukit.api.host.SlotScreenHook}
 * dispatch, which SlotKit fills with {@link CreatedSlotScreenHook}. That hook calls
 * {@link #resolveHoveredSlot} with the panel ids that currently host a
 * {@code SlotElement}. The {@code getHoveredSlot} hook makes the registered slot win
 * hover, tooltip and the click target (vanilla sends the click to whatever
 * {@code getHoveredSlot} returns). The {@code mouseClicked} hook makes the panel's
 * empty space inert, so a click in a gap can't fall through and drop a carried item.
 * No consumer mixin is involved.
 *
 * <h3>Client only</h3>
 *
 * References client screen types and only loads on the client. The slot itself
 * ({@link CreatedSlots}) has no client types and runs on both sides.
 */
@ApiStatus.Internal
public final class CreatedSlotInput {

    private CreatedSlotInput() {}

    /**
     * Resolution of a screen point against the revealed registered panels on a
     * screen.
     *
     * @param handled whether a revealed registered panel claims this point. When
     *                true, the caller replaces vanilla's slot resolution with
     *                {@link #slot()}.
     * @param slot    the registered slot under the point, or {@code null} when the
     *                point is inside a revealed panel but not on a slot (the
     *                covered vanilla slot is inert).
     */
    public record Resolution(boolean handled, Slot slot) {
        private static final Resolution PASS = new Resolution(false, null);
        private static final Resolution BLOCKED = new Resolution(true, null);
        private static Resolution hit(Slot s) { return new Resolution(true, s); }
    }

    /**
     * Resolves the slot under {@code (mouseX, mouseY)} considering every revealed
     * registered panel on {@code screen}. Equivalent to
     * {@link #resolveHoveredSlot(AbstractContainerScreen, double, double, Set)}
     * with a {@code null} filter.
     */
    public static Resolution resolveHoveredSlot(AbstractContainerScreen<?> screen,
                                                double mouseX, double mouseY) {
        return resolveHoveredSlot(screen, mouseX, mouseY, null);
    }

    /**
     * Resolves the slot under {@code (mouseX, mouseY)} considering only the
     * revealed slots whose panel id is in {@code panelFilter} (or all slots when
     * {@code panelFilter} is null). The screen dispatcher passes the active
     * panels for the current screen so an opted-out slot neither wins hover nor
     * blocks the slot behind it.
     *
     * <p>Recognises slots directly and through the creative {@code SlotWrapper}
     * ({@code CreatedSlotAccess.asMKCSlot}). On a hit it returns the slot actually in
     * {@code menu.slots}: the raw {@code CreatedSlot} on the survival inventory, the
     * {@code SlotWrapper} on the creative screen. The caller (vanilla's
     * {@code getHoveredSlot}) sends the click to whatever this returns, and creative's
     * click path needs the wrapper.
     *
     * <p>Safe on any container screen; one with no matching revealed slots returns
     * {@link Resolution#PASS}.
     */
    public static Resolution resolveHoveredSlot(AbstractContainerScreen<?> screen,
                                                double mouseX, double mouseY,
                                                @Nullable Set<String> panelFilter) {
        AbstractContainerMenu menu = screen.getMenu();
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int leftPos = acc.sk$getLeftPos();
        int topPos = acc.sk$getTopPos();

        // Cursor in container-relative space (vanilla's isHovering frame).
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;

        // A bounding box per panel over its revealed registered slots, so the
        // "inside a revealed panel" gap test is decided per panel, never by one box
        // spanning two separate panels.
        Map<String, int[]> bounds = null; // panelId -> {minX, minY, maxX, maxY}

        // Vanilla slots a panel presents (SlotElement.slot(Address)) win where they
        // are, like created ones, so a vanilla slot the panel covers can't take the
        // click first. They add no bounds: they are the menu's own slots, and an
        // opaque panel already makes its gaps inert.
        Set<Slot> hosted = SlotElementRegistry.hostedVanillaSlots(menu);

        for (Slot slot : menu.slots) {
            CreatedSlot mk = CreatedSlotAccess.asMKCSlot(slot);
            if (mk == null) {
                if (hosted.contains(slot) && relX >= slot.x - 1 && relX < slot.x + 17
                        && relY >= slot.y - 1 && relY < slot.y + 17) {
                    return Resolution.hit(slot);
                }
                continue;
            }
            if (mk.isInert()) continue;
            if (panelFilter != null && !panelFilter.contains(mk.panelId())) continue;

            // Vanilla's hover area for an 18px slot cell, x-1 to x+17, around the
            // in-menu slot's own x/y (the creative wrapper's on creative). That is the
            // position its panel wrote this frame, where vanilla draws it.
            int x0 = slot.x - 1, y0 = slot.y - 1;
            int x1 = slot.x + 17, y1 = slot.y + 17;

            if (relX >= x0 && relX < x1 && relY >= y0 && relY < y1) {
                // Return the in-menu slot (raw slot, or the creative wrapper).
                return Resolution.hit(slot); // registered slot wins outright
            }

            if (bounds == null) bounds = new HashMap<>();
            int[] b = bounds.computeIfAbsent(mk.panelId(),
                    k -> new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                                   Integer.MIN_VALUE, Integer.MIN_VALUE});
            if (x0 < b[0]) b[0] = x0;
            if (y0 < b[1]) b[1] = y0;
            if (x1 > b[2]) b[2] = x1;
            if (y1 > b[3]) b[3] = y1;
        }

        // No slot hit. Inside a revealed panel's bounds, the covered vanilla slot is
        // inert, so block hover and click behind the panel's empty space.
        if (bounds != null) {
            for (int[] b : bounds.values()) {
                if (relX >= b[0] && relX < b[2] && relY >= b[1] && relY < b[3]) {
                    return Resolution.BLOCKED;
                }
            }
        }

        return Resolution.PASS;
    }
}
