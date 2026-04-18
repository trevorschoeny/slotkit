package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes vanilla's click classification for slots positioned outside the
 * container frame. Targets {@link AbstractRecipeBookScreen} because it
 * overrides {@code hasClickedOutside} from {@code AbstractContainerScreen},
 * and {@code InventoryScreen} inherits from it.
 *
 * <p>Without this fix, clicks on slots outside the container frame get
 * classified as "outside" clicks (k=-999), which changes PICKUP to THROW
 * and causes items to physically drop as entities instead of going to the
 * cursor.
 *
 * <p>Returns {@code false} when the click lands on any active slot's bounds
 * (same 1px tolerance as vanilla's {@code isHovering}). Vanilla slots inside
 * the frame are unaffected — vanilla's own hasClickedOutside already returns
 * false for them.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MKHasClickedOutsideMixin {

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void menuKit$exemptSlotPositions(double mouseX, double mouseY,
                                              int leftPos, int topPos,
                                              CallbackInfoReturnable<Boolean> cir) {
        // Access menu via getMenu() on the superclass — no @Shadow needed
        // since getMenu() is public on AbstractContainerScreen.
        var self = (AbstractContainerScreen<?>) (Object) this;
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;
        for (Slot slot : self.getMenu().slots) {
            if (!slot.isActive()) continue;
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
