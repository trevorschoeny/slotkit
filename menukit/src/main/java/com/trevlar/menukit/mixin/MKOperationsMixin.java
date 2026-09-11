package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.window.ActingPlayer;
import com.trevlar.menukit.window.ClickOperations;
import com.trevlar.menukit.window.BehaviorKeys;
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
 * Enforces vanilla's menu-level {@linkplain SlotOperations operations} at
 * vanilla's own seams, for every slot kind, once per slot touched. Each seam asks
 * {@link SlotOperations#allowsGesture}, so a click a mod simulates under
 * {@code SlotOperations.as(...)} is judged by the operation it serves:
 *
 * <ul>
 *   <li>{@code doClick} HEAD: the operations the click names on its own slot
 *       ({@link ClickOperations}): plain click take and put, shift-click out,
 *       hotbar and offhand swaps (both slots), drop one and drop stack, and a slot
 *       joining a drag. A refusal cancels the click before vanilla moves anything.
 *       The client runs the same test before it sends the click at all.</li>
 *   <li>{@code moveItemStackTo}: the destination side of a shift-click
 *       ({@link BehaviorKeys#SHIFT_CLICK_IN}). Two passes, two wraps, the same
 *       shape as Containers' gating seam: the merge pass reads {@code getItem}
 *       and never asks {@code mayPlace}, so a refused slot reads as empty there
 *       and as unplaceable in the empty pass.</li>
 *   <li>{@code canTakeItemForPickAll}: double-click collect
 *       ({@link BehaviorKeys#COLLECT}).</li>
 *   <li>{@code canDragTo}: drag-fill ({@link BehaviorKeys#DRAG_FILL}).</li>
 * </ul>
 *
 * Every seam runs inside {@code doClick} on both sides, so a declaration or veto
 * holds identically on the client's prediction and the integrated server's
 * authoritative run. A slot nobody declared on resolves {@code TRUE}, so untouched
 * menus are exactly vanilla.
 *
 * <p>Known limit: a menu subclass that overrides one of these methods without
 * calling {@code super} (creative's item picker overrides
 * {@code canTakeItemForPickAll}) bypasses that seam on that menu.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MKOperationsMixin {

    /** The operations the click names on its own slot: plain click, shift-click out, drop, swap, drag. */
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mk$clickOperations(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (ClickOperations.refuses(self, slotId, button, input, player)) ci.cancel();
    }

    private boolean mk$allowsShiftClickIn(Slot slot) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        return SlotOperations.allowsGesture(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.SHIFT_CLICK_IN);
    }

    /** Empty pass: a refused destination is unplaceable. */
    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mk$shiftClickInPlace(Slot slot, ItemStack stack, Operation<Boolean> original) {
        if (!mk$allowsShiftClickIn(slot)) return false;
        return original.call(slot, stack);
    }

    /** Merge pass: a refused destination reads as empty, so nothing merges into it. */
    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mk$shiftClickInMerge(Slot slot, Operation<ItemStack> original) {
        if (!mk$allowsShiftClickIn(slot)) return ItemStack.EMPTY;
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
