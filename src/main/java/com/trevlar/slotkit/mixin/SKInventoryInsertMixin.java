package com.trevlar.slotkit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.window.SlotGating;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enforces {@link BehaviorKeys#INVENTORY_INSERT}: whether an item given to the
 * inventory may land in a slot. Every insertion goes through {@code Inventory.add},
 * which picks the slot in two steps, a partial stack of the same item first
 * ({@code getSlotWithRemainingSpace}) and an empty slot second ({@code getFreeSlot}):
 * a pickup from the ground, {@code /give}, creative pick-block, a crafting grid's
 * returns, recipe placement.
 *
 * <h3>Correct the answer, don't pre-empt it</h3>
 *
 * Both methods are wrapped, not canceled at HEAD. The original runs first, with
 * every other mod's injections in it, and SlotKit looks only at the slot it chose.
 * If that slot is allowed, the answer stands untouched; only a refused slot makes
 * SlotKit re-scan in vanilla's order for the next one it allows. A HEAD cancel here
 * would race other mods' HEAD cancels, and whichever mixin applied first would
 * silently switch the other off. That shipped as a pre-release bug once.
 *
 * <p>There is no menu here: the ref carries the inventory, the index and its owner,
 * so the slot resolves from its own declaration or the key's default, then the
 * vetoes.
 *
 * <p>The re-scan copies vanilla 26.2's order (selected, offhand, then the list). If
 * a Minecraft update changes that order, change it here too.
 */
@Mixin(Inventory.class)
public abstract class SKInventoryInsertMixin {

    @Shadow @Final private NonNullList<ItemStack> items;
    @Shadow @Final public Player player;
    @Shadow public abstract int getSelectedSlot();
    @Shadow public abstract ItemStack getItem(int index);
    @Shadow private boolean hasRemainingSpaceForItem(ItemStack existing, ItemStack incoming) {
        throw new AssertionError("shadow");
    }

    // The stack being added, for the slot gates: getFreeSlot doesn't take it. Server thread.
    @Unique private @Nullable ItemStack sk$incoming;

    private boolean sk$allows(int index) {
        return sk$allows(index, sk$incoming);
    }

    // The insert operation, then the slot's own gate, which vanilla's add never asks.
    private boolean sk$allows(int index, @Nullable ItemStack stack) {
        Inventory self = (Inventory) (Object) this;
        ItemStack incoming = stack == null ? ItemStack.EMPTY : stack;
        if (!SlotOperations.allowsPut(SlotRef.of(self, index, player), BehaviorKeys.INVENTORY_INSERT, incoming)) {
            return false;
        }
        if (incoming.isEmpty()) return true;
        Slot slot = SlotGating.inventorySlot(player, index);
        return slot == null || SlotGating.mayPlaceAt(slot, stack);
    }

    @WrapMethod(method = "add(ILnet/minecraft/world/item/ItemStack;)Z")
    private boolean sk$noteAdded(int slot, ItemStack stack, Operation<Boolean> original) {
        ItemStack outer = sk$incoming;
        sk$incoming = stack;
        try {
            return original.call(slot, stack);
        } finally {
            sk$incoming = outer;
        }
    }

    @WrapMethod(method = "getSlotWithRemainingSpace")
    private int sk$pickupIntoPartial(ItemStack stack, Operation<Integer> original) {
        int chosen = original.call(stack);
        if (chosen < 0 || sk$allows(chosen, stack)) return chosen;   // no answer, or an allowed one: stands
        int selected = getSelectedSlot();
        if (hasRemainingSpaceForItem(getItem(selected), stack) && sk$allows(selected, stack)) return selected;
        if (hasRemainingSpaceForItem(getItem(Inventory.SLOT_OFFHAND), stack) && sk$allows(Inventory.SLOT_OFFHAND, stack)) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int i = 0; i < items.size(); i++) {
            if (hasRemainingSpaceForItem(items.get(i), stack) && sk$allows(i, stack)) return i;
        }
        return -1;
    }

    /** A reserved slot's cap holds for a pickup too: vanilla fills a slot to the stack size. */
    @WrapOperation(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I"))
    private int sk$reservedCap(Inventory self, ItemStack stack, Operation<Integer> original, @Local(argsOnly = true) int slot) {
        return Math.min(original.call(self, stack), Reservations.limit(SlotRef.of(self, slot, player), stack));
    }

    @WrapMethod(method = "getFreeSlot")
    private int sk$pickupIntoEmpty(Operation<Integer> original) {
        int chosen = original.call();
        if (chosen < 0 || sk$allows(chosen)) return chosen;   // no answer, or an allowed one: stands
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty() && sk$allows(i)) return i;
        }
        return -1;
    }
}
