package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotRef;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Enforces {@link BehaviorKeys#INVENTORY_INSERT}: whether an item given to the
 * inventory may land in a slot. Every insertion goes through {@code Inventory.add},
 * which picks the slot in two steps, a partial stack of the same item first
 * ({@code getSlotWithRemainingSpace}) and an empty slot second ({@code getFreeSlot}):
 * a pickup from the ground, {@code /give}, creative pick-block, a crafting grid's
 * returns, recipe placement.
 *
 * <h3>Correct the answer, never pre-empt it</h3>
 *
 * Both methods are wrapped, not cancelled at HEAD. The original runs first, with
 * every other mod's injections in it, and MenuKit looks only at the slot it chose.
 * If that slot is allowed, the answer stands untouched; only a refused slot makes
 * MenuKit re-scan in vanilla's order for the next one it allows. A HEAD cancel here
 * would race other mods' HEAD cancels, and whichever mixin applied first would
 * silently switch the other off; that shipped as a pre-release bug once.
 *
 * <p>Off-menu seam: the ref carries the inventory, the index and its owner, and no
 * menu, so the slot resolves from its own declaration or the key's default and then
 * the vetoes ({@code docs/limits.md}).
 *
 * <p>ponytail: the re-scan mirrors vanilla 26.2's order (selected, offhand, then the
 * list). If a Minecraft update changes that order, update it here too.
 */
@Mixin(Inventory.class)
public abstract class MKInventoryInsertMixin {

    @Shadow @Final private NonNullList<ItemStack> items;
    @Shadow @Final public Player player;
    @Shadow public abstract int getSelectedSlot();
    @Shadow public abstract ItemStack getItem(int index);
    @Shadow private boolean hasRemainingSpaceForItem(ItemStack existing, ItemStack incoming) {
        throw new AssertionError("shadow");
    }

    private boolean mk$allows(int index) {
        Inventory self = (Inventory) (Object) this;
        return SlotOperations.allows(SlotRef.of(self, index, player), BehaviorKeys.INVENTORY_INSERT);
    }

    @WrapMethod(method = "getSlotWithRemainingSpace")
    private int mk$pickupIntoPartial(ItemStack stack, Operation<Integer> original) {
        int chosen = original.call(stack);
        if (chosen < 0 || mk$allows(chosen)) return chosen;   // no answer, or an allowed one: stands
        int selected = getSelectedSlot();
        if (hasRemainingSpaceForItem(getItem(selected), stack) && mk$allows(selected)) return selected;
        if (hasRemainingSpaceForItem(getItem(Inventory.SLOT_OFFHAND), stack) && mk$allows(Inventory.SLOT_OFFHAND)) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int i = 0; i < items.size(); i++) {
            if (hasRemainingSpaceForItem(items.get(i), stack) && mk$allows(i)) return i;
        }
        return -1;
    }

    @WrapMethod(method = "getFreeSlot")
    private int mk$pickupIntoEmpty(Operation<Integer> original) {
        int chosen = original.call();
        if (chosen < 0 || mk$allows(chosen)) return chosen;   // no answer, or an allowed one: stands
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty() && mk$allows(i)) return i;
        }
        return -1;
    }
}
