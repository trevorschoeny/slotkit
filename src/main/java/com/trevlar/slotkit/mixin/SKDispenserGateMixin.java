package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.window.Automation;
import com.trevlar.slotkit.window.SlotGating;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gate at a dispenser or dropper: a gated slot is skipped when it picks a random slot
 * to fire. Vanilla's pick, with gated slots left out of the draw, so a dispenser still
 * fires from its other slots when the one it would have picked is gated. A slot that
 * refuses {@code AUTOMATION_TAKE} is left out the same way.
 */
@Mixin(DispenserBlockEntity.class)
public class SKDispenserGateMixin {

    @Inject(method = "getRandomSlot", at = @At("HEAD"), cancellable = true)
    private void sk$gateDispense(RandomSource random, CallbackInfoReturnable<Integer> cir) {
        DispenserBlockEntity self = (DispenserBlockEntity) (Object) this;
        self.unpackLootTable(null);
        // Reservoir pick over the non-empty, ungated slots, as vanilla draws over the non-empty ones.
        int picked = -1;
        int seen = 1;
        for (int k = 0; k < self.getContainerSize(); k++) {
            if (!self.getItem(k).isEmpty() && SlotGating.mayExtractFrom(self, k)
                    && Automation.mayTake(self, k) && random.nextInt(seen++) == 0) {
                picked = k;
            }
        }
        cir.setReturnValue(picked);
    }
}
