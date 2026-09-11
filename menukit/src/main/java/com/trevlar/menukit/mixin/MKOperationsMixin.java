package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.inject.Slots;
import com.trevlar.menukit.window.ActingPlayer;
import com.trevlar.menukit.window.BehaviorKey;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotOperations;
import com.trevlar.menukit.window.SlotRef;
import com.trevlar.menukit.window.TriBool;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces vanilla's menu-level {@linkplain SlotOperations operations} at
 * vanilla's own seams, for every slot kind, by asking
 * {@link SlotOperations#allows} once per slot touched:
 *
 * <ul>
 *   <li>{@code doClick} HEAD: shift-click out of the clicked slot
 *       ({@link BehaviorKeys#SHIFT_CLICK_OUT}), number-key and offhand swaps
 *       ({@link BehaviorKeys#HOTBAR_SWAP}, {@link BehaviorKeys#OFFHAND_SWAP}; both
 *       slots of the swap must allow it), and Q / Ctrl-Q
 *       ({@link BehaviorKeys#DROP}, {@link BehaviorKeys#DROP_STACK}). A refusal
 *       cancels the click before vanilla moves anything.</li>
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

    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void mk$clickOperations(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (slotId < 0 || slotId >= slots.size()) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot slot = slots.get(slotId);
        BehaviorKey<TriBool> op = switch (input) {
            case QUICK_MOVE -> BehaviorKeys.SHIFT_CLICK_OUT;
            case THROW -> button == 0 ? BehaviorKeys.DROP : BehaviorKeys.DROP_STACK;
            case SWAP -> button == Inventory.SLOT_OFFHAND ? BehaviorKeys.OFFHAND_SWAP
                    : Inventory.isHotbarSlot(button) ? BehaviorKeys.HOTBAR_SWAP
                    : null; // vanilla ignores any other button; so do we
            default -> null;
        };
        if (op == null) return;
        if (!SlotOperations.allows(SlotRef.of(self, slot, player), op)) {
            ci.cancel();
            return;
        }
        // A swap has two participants; the hotbar or offhand slot must allow it too.
        if (input == ContainerInput.SWAP && !SlotOperations.allows(inventorySlot(self, player, button), op)) {
            ci.cancel();
        }
    }

    /**
     * The player-inventory slot at {@code index} as it sits on this menu, so it
     * resolves with its menu category; the bare container form when the menu does
     * not show it (vanilla swaps against the inventory directly, so the swap works
     * on such a menu too).
     */
    private static SlotRef inventorySlot(AbstractContainerMenu menu, Player player, int index) {
        Inventory inventory = player.getInventory();
        for (Slot s : menu.slots) {
            Slot target = Slots.target(s);
            if (target.container == inventory && target.getContainerSlot() == index) {
                return SlotRef.of(menu, s, player);
            }
        }
        return SlotRef.of(inventory, index, player);
    }

    private boolean mk$allowsShiftClickIn(Slot slot) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        return SlotOperations.allows(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.SHIFT_CLICK_IN);
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
        if (!SlotOperations.allows(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.COLLECT)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void mk$dragFill(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (!SlotOperations.allows(SlotRef.of(self, slot, ActingPlayer.current()), BehaviorKeys.DRAG_FILL)) {
            cir.setReturnValue(false);
        }
    }
}
