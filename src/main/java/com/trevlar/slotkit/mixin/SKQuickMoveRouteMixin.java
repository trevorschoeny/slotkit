package com.trevlar.slotkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.slotkit.core.QuickMoveRouting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shift-click on every menu goes through SlotKit's routing
 * ({@link QuickMoveRouting}): both {@code quickMoveStack} calls in {@code doClick}
 * (the first move and vanilla's repeat-while-same-item loop), on both sides, so the
 * client predicts what the server does. One seam for vanilla and modded menus alike,
 * with no mixin per menu class.
 *
 * <p>While SlotKit runs a menu's own {@code quickMoveStack}, a
 * {@code moveItemStackTo} range that starts below the first slot SlotKit appended ends
 * at it, so a menu's routing never reaches a slot group appended to it.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class SKQuickMoveRouteMixin {

    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"),
            require = 2, allow = 2)
    private ItemStack sk$route(AbstractContainerMenu menu, Player player, int index, Operation<ItemStack> original) {
        return QuickMoveRouting.quickMove(menu, player, index, (p, i) -> original.call(menu, p, i));
    }

    @ModifyVariable(method = "moveItemStackTo", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int sk$clamp(int endIndex, ItemStack stack, int startIndex) {
        return QuickMoveRouting.clampEnd(startIndex, endIndex);
    }
}
