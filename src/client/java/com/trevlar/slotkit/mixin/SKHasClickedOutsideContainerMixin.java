package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.ClickOutsideHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes vanilla's click classification for slots outside the container frame
 * on {@link AbstractContainerScreen}, meaning any screen that inherits the base
 * {@code hasClickedOutside} without overriding it (chests, hoppers, dispensers,
 * shulker boxes and so on).
 *
 * <p>Without it, clicks on registered or decoration slots outside the container
 * frame count as "outside" clicks (k=-999), which turns PICKUP into THROW and
 * drops the item as an entity instead of putting it on the cursor.
 *
 * <p>Screens that override {@code hasClickedOutside} need their own mixins:
 * see {@link SKHasClickedOutsideMixin} (recipe-book screens) and
 * {@link SKHasClickedOutsideCreativeMixin} (creative).
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class SKHasClickedOutsideContainerMixin {

    @Inject(method = "hasClickedOutside(DDII)Z", at = @At("HEAD"), cancellable = true)
    private void sk$exemptSlotPositions(double mouseX, double mouseY,
                                              int leftPos, int topPos,
                                              CallbackInfoReturnable<Boolean> cir) {
        var self = (AbstractContainerScreen<?>) (Object) this;
        if (ClickOutsideHelper.clickLandsOnActiveSlot(self, mouseX, mouseY, leftPos, topPos)) {
            cir.setReturnValue(false);
        }
    }
}
