package com.trevlar.slotkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.state.PerPlayerSlotStateBag;
import com.trevlar.slotkit.state.PortableSlotState;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A portable container broken in survival drops through its loot table, whose
 * {@code copy_components} names only some components (a shulker box's: name, contents,
 * lock, loot table), so custom_data, and the slot state riding in it, would stay behind.
 * The bag {@link SKPortableSlotStateMixin} emits is carried over to the dropped item too.
 */
@ApiStatus.Internal
@Mixin(CopyComponentsFunction.class)
public class SKPortableLootMixin {

    @ModifyReturnValue(method = "run", at = @At("RETURN"))
    private ItemStack sk$keepSlotState(ItemStack stack, @Local(argsOnly = true) LootContext context) {
        BlockEntity be = context.getOptional(LootContextParams.BLOCK_ENTITY);
        if (be == null || stack.isEmpty() || !SlotState.isPortable(be)) return stack;
        PerPlayerSlotStateBag bag = PortableSlotState.read(be.collectComponents().get(DataComponents.CUSTOM_DATA));
        if (bag == null) return stack;
        stack.set(DataComponents.CUSTOM_DATA, PortableSlotState.with(stack.get(DataComponents.CUSTOM_DATA), bag));
        return stack;
    }
}
