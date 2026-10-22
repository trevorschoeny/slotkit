package com.trevlar.slotkit.mixin;

import com.mojang.serialization.Codec;
import com.trevlar.slotkit.state.PortableSlotState;

import net.minecraft.core.component.DataComponentPatch;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads Containers 6.x's {@code menukit:portable_slot_state} component as SlotKit's
 * custom_data key, the one job a data fixer would do (see {@link PortableSlotState}).
 * The component type is no longer registered, so without this an item saved with it
 * fails to decode and vanishes with its contents. Wraps the patch codec once, as the
 * class initializes, before anything reads the field. Permanent: old saves keep the
 * old form for as long as worlds exist.
 */
@ApiStatus.Internal
@Mixin(DataComponentPatch.class)
public abstract class SKComponentPatchLegacyMixin {

    @Shadow @Final @Mutable
    public static Codec<DataComponentPatch> CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void sk$renameLegacyPortableState(CallbackInfo ci) {
        CODEC = PortableSlotState.renamingLegacy(CODEC);
    }
}
