package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.MenuSwitchClient;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds a container screen the server opened over another until its contents arrive ({@link MenuSwitchClient}). */
@ApiStatus.Internal
@Mixin(Gui.class)
public abstract class SKScreenHoldMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sk$holdForContents(@Nullable Screen screen, CallbackInfo ci) {
        if (MenuSwitchClient.onSetScreen(screen)) ci.cancel();
    }
}
