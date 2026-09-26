package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

/**
 * Enforces {@link BehaviorKeys#WORLD_PICKUP}: whether an item picked up from the
 * world may land in a slot. Vanilla's {@code Inventory.add} picks the slot in two
 * steps, a partial stack of the same item first ({@code getSlotWithRemainingSpace})
 * and an empty slot second ({@code getFreeSlot}).
 *
 * <h3>Correct the answer, never pre-empt it</h3>
 *
 * Both methods are wrapped, not cancelled at HEAD. The original runs first, with
 * every other mod's injections in it (Inventory Plus has its own HEAD inject on
 * {@code getFreeSlot} for its locks), and MenuKit looks only at the slot it chose.
 * If that slot is allowed, the answer stands untouched; only a refused slot makes
 * MenuKit re-scan in vanilla's order for the next one it allows. A HEAD cancel
 * here would race other mods' HEAD cancels, and whichever mixin applied first
 * would silently switch the other off: that shipped as a pre-release bug (a
 * locking mod without a veto lost its pickup protection under 5.1.0's first build).
 * With nothing declared and no vetoes, the method behaves exactly as vanilla and
 * as every other mod made it.
 *
 * <p>Off-menu seam: the ref carries the inventory, the index and its owner, and
 * no menu, so the slot resolves from its own declaration or the key's default and
 * then the vetoes ({@code docs/limits.md}).
 *
 * <p>ponytail: the re-scan mirrors vanilla 26.2's order (selected, offhand, then
 * the list). If a Minecraft update changes that order, update it here too. The
 * re-scan applies MenuKit's rules only; a slot another mod's inject would also
 * have skipped is skipped only if that mod also vetoes it, which is the 5.1.0 way.
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
