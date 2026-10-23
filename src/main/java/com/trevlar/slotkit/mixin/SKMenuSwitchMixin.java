package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.menu.MenuSwitch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * {@link MenuSwitch}'s seams on the player: where a menu opens (its cursor stack, its
 * title), where the menu in front closes (Escape included, through
 * {@code handleContainerClose}), the tick that checks the set-aside menu, and the
 * container counter, which skips the set-aside menu's id so the client never mistakes
 * one menu for the other.
 */
@ApiStatus.Internal
@Mixin(ServerPlayer.class)
public abstract class SKMenuSwitchMixin {

    @Shadow private int containerCounter;

    @Inject(method = "initMenu", at = @At("HEAD"))
    private void sk$switchCarried(AbstractContainerMenu menu, CallbackInfo ci) {
        MenuSwitch.menuOpening(menu);
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void sk$recordTitle(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        OptionalInt id = cir.getReturnValue();
        if (id != null && id.isPresent()) MenuSwitch.menuOpened((ServerPlayer) (Object) this, provider.getDisplayName());
    }

    @Inject(method = "doCloseContainer", at = @At("TAIL"))
    private void sk$frontClosed(CallbackInfo ci) {
        MenuSwitch.frontClosed((ServerPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sk$checkSetAside(CallbackInfo ci) {
        MenuSwitch.tick((ServerPlayer) (Object) this);
    }

    @Inject(method = "nextContainerCounter", at = @At("TAIL"))
    private void sk$skipHeldId(CallbackInfo ci) {
        if (containerCounter == MenuSwitch.heldId((ServerPlayer) (Object) this)) {
            containerCounter = containerCounter % 100 + 1;
        }
    }
}
