package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.window.SlotGating;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The slot-level seam: the gate ({@code GATING}) and Curse of Binding
 * ({@code BINDING}) on every slot, vanilla or created, applied where vanilla itself
 * asks. A plain click's mutation runs through {@code Slot.safeInsert},
 * {@code safeTake} and {@code tryRemove}, which consult {@code mayPlace} and
 * {@code mayPickup} internally, and the stack cap is {@code getMaxStackSize}, so
 * gating the three predicates here covers every path (direct click, the empty pass
 * of a shift-click, pick-all, the creative bridge) once. A created slot resolves
 * through the same seam by its created address, so it no longer gates itself a
 * second time.
 *
 * <p>Zero cost when nothing is gated: {@link SlotGating} returns before minting an
 * address unless some server behaviour has been declared.
 */
@Mixin(Slot.class)
public class MKSlotGateMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mk$gateMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!SlotGating.mayPlaceAt((Slot) (Object) this, stack)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mk$gateMayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!SlotGating.mayPickupAt((Slot) (Object) this, player)) cir.setReturnValue(false);
    }

    @ModifyReturnValue(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private int mk$gateMaxStack(int vanillaMax, @Local(argsOnly = true) ItemStack stack) {
        return SlotGating.maxStackAt((Slot) (Object) this, stack, vanillaMax);
    }
}
