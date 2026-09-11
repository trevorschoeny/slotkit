package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotOperations;
import com.trevlar.menukit.window.SlotRef;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces {@link BehaviorKeys#WORLD_PICKUP}: whether an item picked up from the
 * world may land in a slot. Vanilla's {@code Inventory.add} picks the slot in two
 * steps, a partial stack of the same item first ({@code getSlotWithRemainingSpace})
 * and an empty slot second ({@code getFreeSlot}); both are plain scans, and both
 * are re-run here with one extra test per candidate. A refused slot is passed
 * over and the next candidate is taken, exactly as if it were full.
 *
 * <p>Off-menu seam: the ref carries the inventory, the index and its owner, and
 * no menu, so the slot resolves from its own declaration or the key's default and
 * then the vetoes ({@code docs/limits.md}).
 *
 * <p>ponytail: mirrors vanilla 26.2's two scans (selected, offhand, then the
 * list). If a Minecraft update changes that order, update it here too.
 */
@Mixin(Inventory.class)
public abstract class MKWorldPickupMixin {

    @Shadow @Final private NonNullList<ItemStack> items;
    @Shadow @Final public Player player;
    @Shadow public abstract int getSelectedSlot();
    @Shadow public abstract ItemStack getItem(int index);
    @Shadow private boolean hasRemainingSpaceForItem(ItemStack existing, ItemStack incoming) {
        throw new AssertionError("shadow");
    }

    private boolean mk$allows(int index) {
        Inventory self = (Inventory) (Object) this;
        return SlotOperations.allows(SlotRef.of(self, index, player), BehaviorKeys.WORLD_PICKUP);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void mk$pickupIntoPartial(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int selected = getSelectedSlot();
        if (hasRemainingSpaceForItem(getItem(selected), stack) && mk$allows(selected)) {
            cir.setReturnValue(selected);
            return;
        }
        if (hasRemainingSpaceForItem(getItem(Inventory.SLOT_OFFHAND), stack) && mk$allows(Inventory.SLOT_OFFHAND)) {
            cir.setReturnValue(Inventory.SLOT_OFFHAND);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (hasRemainingSpaceForItem(items.get(i), stack) && mk$allows(i)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void mk$pickupIntoEmpty(CallbackInfoReturnable<Integer> cir) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty() && mk$allows(i)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }
}
