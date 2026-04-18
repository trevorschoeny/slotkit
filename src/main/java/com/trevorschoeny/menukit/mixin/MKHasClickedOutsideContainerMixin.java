package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKClickOutsideHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes vanilla's click classification for slots outside the container frame
 * on {@link AbstractContainerScreen} — i.e., any screen that inherits the base
 * {@code hasClickedOutside} without overriding it (chests, hoppers, dispensers,
 * shulker boxes, etc.).
 *
 * <p>Without this fix, clicks on grafted/decoration slots positioned outside
 * the container frame get classified as "outside" clicks (k=-999), which
 * changes PICKUP to THROW and causes items to physically drop as entities
 * instead of going to the cursor.
 *
 * <p>Screens that override {@code hasClickedOutside} need their own mixins —
 * see {@link MKHasClickedOutsideMixin} (recipe-book screens) and
 * {@link MKHasClickedOutsideCreativeMixin} (creative).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKHasClickedOutsideContainerMixin {

    @Inject(method = "hasClickedOutside(DDII)Z", at = @At("HEAD"), cancellable = true)
    private void menuKit$exemptSlotPositions(double mouseX, double mouseY,
                                              int leftPos, int topPos,
                                              CallbackInfoReturnable<Boolean> cir) {
        var self = (AbstractContainerScreen<?>) (Object) this;
        if (MKClickOutsideHelper.clickLandsOnActiveSlot(self, mouseX, mouseY, leftPos, topPos)) {
            cir.setReturnValue(false);
        }
    }
}
