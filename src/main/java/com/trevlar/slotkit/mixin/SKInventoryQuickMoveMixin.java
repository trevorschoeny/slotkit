package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.core.CreatedSlotAccess;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-click into created slots on the player's inventory menu, for groups that
 * declared {@code SlotSpec.shiftClickIn(true)}. Vanilla's {@code quickMoveStack}
 * there only ever routes to its own 46 slots, so without this every slot mod
 * carried its own copy of this mixin.
 *
 * <p>Before vanilla's routing: a stack shift-clicked from any slot that is not
 * itself created first merges into partial stacks in the target groups, then fills
 * their empty slots. Each try goes through vanilla's {@code moveItemStackTo} on one
 * slot, so the group's gate and the {@code SHIFT_CLICK_IN} cascade judge it exactly
 * as they judge any shift-click destination. If anything moved, vanilla's closing
 * bookkeeping runs and the original is returned; {@code doClick} then calls again,
 * and whatever is left (nothing fits any more) goes to vanilla untouched.
 *
 * <p>Out of a created slot needs nothing: vanilla sends an index past the offhand
 * to the main inventory, then the hotbar. Only the inventory menu: in a chest the
 * container comes first, as it does for armour. Both sides, for prediction.
 */
@Mixin(InventoryMenu.class)
public abstract class SKInventoryQuickMoveMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void sk$shiftClickIntoCreated(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (index < 0 || index >= menu.slots.size()) return;
        Slot from = menu.slots.get(index);
        if (!from.hasItem() || CreatedSlotAccess.asMKCSlot(from) != null) return;

        ItemStack live = from.getItem();          // the live stack moveItemStackTo shrinks
        ItemStack original = live.copy();
        AbstractContainerMenuInvoker move = (AbstractContainerMenuInvoker) menu;
        // Two passes over the targets, as vanilla's merge does over a range:
        // partial stacks first, then empty slots.
        for (int pass = 0; pass < 2 && !live.isEmpty(); pass++) {
            for (int i = 0; i < menu.slots.size() && !live.isEmpty(); i++) {
                Slot to = menu.slots.get(i);
                if (to.hasItem() != (pass == 0) || !to.isActive()) continue;
                CreatedSlot created = CreatedSlotAccess.asMKCSlot(to);
                if (created == null
                        || !ParityGroup.isShiftClickTarget(SlotGroupId.created(created.panelId(), created.groupId()))) continue;
                move.sk$moveItemStackTo(live, i, i + 1, false);
            }
        }
        if (live.getCount() == original.getCount()) return;   // nothing fit: vanilla routes it

        // Vanilla quickMoveStack's closing bookkeeping.
        if (live.isEmpty()) from.setByPlayer(ItemStack.EMPTY, original);
        else from.setChanged();
        from.onTake(player, live);
        cir.setReturnValue(original);
    }
}
