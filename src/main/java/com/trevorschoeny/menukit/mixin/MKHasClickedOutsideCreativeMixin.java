package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKClickOutsideHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes vanilla's click classification for slots outside the container frame
 * on {@link CreativeModeInventoryScreen}. Creative has its own
 * {@code hasClickedOutside} override that also considers tab clicks and
 * stores the result in a private field read later during
 * {@code slotClicked}'s carry-drop logic. The mixin writes {@code false}
 * back to the field as well as returning {@code false} so subsequent reads
 * stay coherent.
 *
 * <p>See {@link MKHasClickedOutsideMixin} (recipe-book) and
 * {@link MKHasClickedOutsideContainerMixin} (base) for the sibling targets.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MKHasClickedOutsideCreativeMixin {

    // The private field that creative's hasClickedOutside override caches.
    // slotClicked reads it at the "slot == null" branch to decide whether
    // to drop the carried stack. Forcing false here prevents stale-state
    // reads if a prior outside click left the field at true.
    @Shadow private boolean hasClickedOutside;

    @Inject(method = "hasClickedOutside(DDII)Z", at = @At("HEAD"), cancellable = true)
    private void menuKit$exemptSlotPositions(double mouseX, double mouseY,
                                              int leftPos, int topPos,
                                              CallbackInfoReturnable<Boolean> cir) {
        var self = (AbstractContainerScreen<?>) (Object) this;
        if (MKClickOutsideHelper.clickLandsOnActiveSlot(self, mouseX, mouseY, leftPos, topPos)) {
            this.hasClickedOutside = false;
            cir.setReturnValue(false);
        }
    }
}
