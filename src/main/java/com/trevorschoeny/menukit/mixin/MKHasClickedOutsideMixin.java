package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKClickOutsideHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes vanilla's click classification for slots outside the container frame
 * on {@link AbstractRecipeBookScreen}. This override sits between
 * {@code AbstractContainerScreen} (the base) and {@code InventoryScreen}
 * (a leaf), so any screen extending AbstractRecipeBookScreen — including
 * InventoryScreen — inherits this fix.
 *
 * <p>See {@link MKHasClickedOutsideContainerMixin} (base) and
 * {@link MKHasClickedOutsideCreativeMixin} (creative) for the sibling targets.
 */
@ApiStatus.Internal
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MKHasClickedOutsideMixin {

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
