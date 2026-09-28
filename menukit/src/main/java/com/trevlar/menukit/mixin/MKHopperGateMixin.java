package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.window.SlotGating;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gate at hopper automation: a gate that denies extract or place stops a hopper
 * taking from or moving into a gated slot. Nobody is acting, so a gate that opens
 * for some players still enforces here.
 */
@Mixin(HopperBlockEntity.class)
public class MKHopperGateMixin {

    @Inject(method = "tryTakeInItemFromSlot", at = @At("HEAD"), cancellable = true)
    private static void mk$gateExtract(Hopper hopper, Container container, int slot, Direction direction,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!SlotGating.mayExtractFrom(container, slot)) cir.setReturnValue(false);
    }

    @Inject(method = "tryMoveInItem", at = @At("HEAD"), cancellable = true)
    private static void mk$gateInsert(Container source, Container destination, ItemStack stack, int slot,
                                      Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotGating.mayPlaceInto(destination, slot, stack)) cir.setReturnValue(stack);
    }

    /** A hopper ejecting its own gated slot: that slot reads as empty, so it is skipped. */
    @WrapOperation(
            method = "ejectItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack mk$gateHopperEject(HopperBlockEntity hopper, int slot, Operation<ItemStack> original) {
        if (!SlotGating.mayExtractFrom(hopper, slot)) return ItemStack.EMPTY;
        return original.call(hopper, slot);
    }
}
