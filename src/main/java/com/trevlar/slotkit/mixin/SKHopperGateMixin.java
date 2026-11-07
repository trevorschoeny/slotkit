package com.trevlar.slotkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.slotkit.api.window.Automation;
import com.trevlar.slotkit.window.SlotGating;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gate at hopper automation: a gate that denies extract or place stops a hopper
 * taking from or moving into a gated slot. Nobody is acting, so a gate that opens
 * for some players still enforces here. Then the automation operations
 * ({@link Automation}): a slot that refuses automation is skipped, and a slot's
 * {@link com.trevlar.slotkit.api.window.SlotOperations.Limit} holds. {@code tryMoveInItem} is also how a dropper fills a
 * container and how a hopper takes an item off the ground.
 */
@Mixin(HopperBlockEntity.class)
public class SKHopperGateMixin {

    @Inject(method = "tryTakeInItemFromSlot", at = @At("HEAD"), cancellable = true)
    private static void sk$gateExtract(Hopper hopper, Container container, int slot, Direction direction,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!SlotGating.mayExtractFrom(container, slot) || !Automation.mayTake(container, slot)) cir.setReturnValue(false);
    }

    /** Wrapped, not canceled at HEAD, so other mods' injections in it still run. */
    @WrapMethod(method = "tryMoveInItem")
    private static ItemStack sk$gateInsert(@Nullable Container source, Container destination, ItemStack stack, int slot,
                                           @Nullable Direction direction, Operation<ItemStack> original) {
        if (!SlotGating.mayPlaceInto(destination, slot, stack)) return stack;
        int room = Automation.room(destination, slot, stack);
        if (room <= 0) return stack;
        if (room >= stack.getCount()) return original.call(source, destination, stack, slot, direction);
        // Only part fits under a cap: move that part, hand back the rest and whatever of it didn't go.
        ItemStack left = original.call(source, destination, stack.copyWithCount(room), slot, direction);
        return stack.copyWithCount(stack.getCount() - room + left.getCount());
    }

    /** A hopper ejecting its own gated slot: that slot reads as empty, so it is skipped. */
    @WrapOperation(
            method = "ejectItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack sk$gateHopperEject(HopperBlockEntity hopper, int slot, Operation<ItemStack> original) {
        if (!SlotGating.mayExtractFrom(hopper, slot) || !Automation.mayTake(hopper, slot)) return ItemStack.EMPTY;
        return original.call(hopper, slot);
    }
}
