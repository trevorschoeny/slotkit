package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.inject.SlotScreenDispatcher;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;

/**
 * Shared helper used by the {@code MKHasClickedOutside*Mixin} family to detect
 * whether a click lands on any active slot's bounds. Consolidates the
 * hit-testing logic across {@link AbstractContainerScreen},
 * {@link net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen},
 * and {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}
 * so all three overrides behave identically.
 *
 * <p>Vanilla's {@code hasClickedOutside} misclassifies clicks on slots
 * positioned outside the container frame (typical for registered/decoration
 * slots) as "outside" clicks — changing PICKUP to THROW and dropping items
 * as entities. The mixins use this helper to exempt click positions that
 * land on any active slot, regardless of where the container frame is.
 *
 * <p>1px tolerance matches vanilla's {@code AbstractContainerScreen.isHovering}.
 */
@ApiStatus.Internal
public final class MKClickOutsideHelper {

    private MKClickOutsideHelper() {}

    /**
     * Returns true when the click lands on any active slot's bounds.
     *
     * @param self     the screen whose menu slots to test
     * @param mouseX   mouse X in screen coordinates
     * @param mouseY   mouse Y in screen coordinates
     * @param leftPos  screen's container frame leftPos (as passed to hasClickedOutside)
     * @param topPos   screen's container frame topPos
     * @return true if the click lands on an active slot — meaning the caller
     *         should return false (not outside) from its hasClickedOutside
     */
    public static boolean clickLandsOnActiveSlot(
            AbstractContainerScreen<?> self,
            double mouseX, double mouseY,
            int leftPos, int topPos) {
        // ── MKC slots: ask the same resolution getHoveredSlot uses ──────────
        // A library-registered slot keeps its vanilla Slot.x/y parked off-screen
        // and presents at a separate runtime position, so the Slot.x/y scan below
        // can NEVER see it — that's the latent gap this closes. "Is this click
        // outside the container?" is the same question as "which slot is under
        // the cursor?"; route both through the one resolution that knows the
        // runtime position (and unwraps the creative wrapper). If it claims a slot
        // OR an in-panel point, the click is on the menu, not outside it.
        if (SlotScreenDispatcher.fireResolveHover(self, mouseX, mouseY).handled()) {
            return true;
        }

        // ── Vanilla-positioned slots: hit-test their real Slot.x/y ──────────
        // Slot.x / Slot.y are relative to the container frame's origin.
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;
        for (Slot slot : self.getMenu().slots) {
            if (!slot.isActive()) continue;
            // 1px tolerance on each side (17 = 16 item area + 1 tolerance).
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                return true;
            }
        }
        return false;
    }
}
