package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.SlotGating;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The gate at a dispenser or dropper: a gated slot is skipped when it picks a random slot to fire. */
@Mixin(DispenserBlockEntity.class)
public class MKDispenserGateMixin {

    @Inject(method = "getRandomSlot", at = @At("RETURN"), cancellable = true)
    private void mk$gateDispense(RandomSource random, CallbackInfoReturnable<Integer> cir) {
        int slot = cir.getReturnValueI();
        if (slot >= 0 && !SlotGating.mayExtractFrom((Container) (Object) this, slot)) {
            cir.setReturnValue(-1);
        }
    }
}
