package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.window.ActingPlayer;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.ClickOperations;
import com.trevlar.menukit.window.SlotGating;
import com.trevlar.menukit.window.SlotOperations;
import com.trevlar.menukit.window.SlotRef;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MenuKit's one seam at each menu-level vanilla method (§0064). Each asks the
 * cascade, the gate and the vetoes once per slot; a click a mod simulates under
 * {@code SlotOperations.as(...)} is judged by the operation it serves:
 *
 * <ul>
 *   <li>{@code doClick} HEAD, server side: the operations the click names on its
 *       own slot ({@link ClickOperations}). The client judged the same click once
 *       already, at its send boundary ({@code MKClickSendMixin}), and a refused
 *       click never left it, so the client's prediction does not ask again.</li>
 *   <li>{@code moveItemStackTo}, the destination side of a shift-click
 *       ({@link BehaviorKeys#SHIFT_CLICK_IN}). Two passes, two hooks on one
 *       decision: the merge pass reads {@code getItem} and never asks
 *       {@code mayPlace}, so a refused slot reads as empty there and the gate is
 *       asked there too; the empty pass asks {@code mayPlace}, where the slot-level
 *       seam ({@code MKSlotGateMixin}) already applies the gate, so only the
 *       operation is asked here.</li>
 *   <li>{@code canTakeItemForPickAll}: double-click collect
 *       ({@link BehaviorKeys#COLLECT}). Vanilla then asks the slot's own
 *       {@code mayPickup}, which is where the gate answers.</li>
 *   <li>{@code canDragTo}: drag-fill ({@link BehaviorKeys#DRAG_FILL}).</li>
 * </ul>
 *
 * A slot nobody declared on resolves {@code TRUE} and {@code OPEN}, so untouched
 * menus are exactly vanilla.
 *
 * <p>Known limit: a menu subclass that overrides one of these methods without
 * calling {@code super} (creative's item picker overrides
 * {@code canTakeItemForPickAll}) bypasses that seam on that menu.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MKOperationsMixin {

    /** The operations the click names on its own slot, judged on the server; the client judged at send. */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mk$clickOperations(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (ClickOperations.refuses(self, slotId, button, input, player)) ci.cancel();
    }

    private boolean mk$allowsShiftClickIn(Slot slot) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        return SlotOperations.allowsGesture(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.SHIFT_CLICK_IN);
    }

    /** Empty pass: a refused destination is unplaceable. The gate answers inside {@code mayPlace} itself. */
    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mk$shiftClickInPlace(Slot slot, ItemStack stack, Operation<Boolean> original) {
        if (!mk$allowsShiftClickIn(slot)) return false;
        return original.call(slot, stack);
    }

    /**
     * Merge pass: a refused or gated destination reads as empty, so nothing merges
     * into it. Only the merge pass's {@code getItem} (the first in the method,
     * {@code ordinal = 0}): the empty pass reads {@code getItem} too, to test for
     * emptiness, and asks {@code mayPlace} right after, which is where its one
     * question is put.
     */
    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mk$shiftClickInMerge(Slot slot, Operation<ItemStack> original,
                                           @Local(argsOnly = true) ItemStack movingStack) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!mk$allowsShiftClickIn(slot)) return ItemStack.EMPTY;
        if (!SlotGating.mayPlaceOnMove(self, slot, movingStack)) return ItemStack.EMPTY;
        return original.call(slot);
    }

    @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
    private void mk$collect(ItemStack carried, Slot slot, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!SlotOperations.allowsGesture(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.COLLECT)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void mk$dragFill(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!SlotOperations.allowsGesture(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.DRAG_FILL)) {
            cir.setReturnValue(false);
        }
    }
}
