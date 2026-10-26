package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.slot.SlotDoubleClick;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The stack lifted by what may be the first half of a {@link SlotDoubleClick} is drawn where
 * it sat, not at the cursor, until the pair ends, so a double-click shows no movement.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
abstract class SKHeldInPlaceMixin {

    @ModifyArgs(method = "extractCarriedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractFloatingItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void sk$heldInPlace(Args args, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] at = SlotDoubleClick.heldAt(mouseX, mouseY);
        if (at == null) return;
        args.set(2, at[0]);
        args.set(3, at[1]);
    }
}
