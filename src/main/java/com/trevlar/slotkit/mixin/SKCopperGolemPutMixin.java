package com.trevlar.slotkit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.slotkit.api.window.Automation;

import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A copper golem honors the automation rules ({@link Automation}): reservations, a mod's lock.
 *
 * <p>Putting: the whole put is wrapped, so whatever chose the slots runs as it is (vanilla's
 * walk, or Savvy Copper Golems' walk into the shulker boxes inside a chest), and then
 * {@link Automation#settle} undoes what landed where it may not and finds it an allowed slot
 * in the same chest or box, or hands it back to the golem. Wrapped, not replaced, so no mod
 * that changes the walk has to know about SlotKit.
 *
 * <p>Taking: a slot that refuses {@code AUTOMATION_TAKE} reads as empty to the golem's pick,
 * so it takes from the next slot.
 */
@Mixin(TransportItemsBetweenContainers.class)
public class SKCopperGolemPutMixin {

    @WrapMethod(method = "addItemsToContainer")
    private static ItemStack sk$putWhereAllowed(PathfinderMob body, Container container, Operation<ItemStack> original) {
        ItemStack held = body.getMainHandItem().copy();
        ItemStack[] before = new ItemStack[container.getContainerSize()];
        for (int slot = 0; slot < before.length; slot++) before[slot] = container.getItem(slot).copy();
        // Vanilla moves the held stack itself into the chest, so held is a copy taken first.
        return Automation.settle(container, before, held, original.call(body, container));
    }

    @WrapOperation(method = "pickupItemFromContainer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private static boolean sk$takeWhereAllowed(ItemStack stack, Operation<Boolean> original,
                                               @Local(argsOnly = true) Container container, @Local(ordinal = 0) int slot) {
        return original.call(stack) || !Automation.mayTake(container, slot);
    }
}
