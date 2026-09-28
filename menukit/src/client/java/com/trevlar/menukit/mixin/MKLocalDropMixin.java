package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.PlayerActions;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Q and Ctrl-Q with no screen open: the client refuses before it drops. Vanilla's
 * {@code LocalPlayer.drop} removes the item from the selected slot as a
 * prediction <em>before</em> sending the action, so the refusal has to come
 * first: refused here, nothing is removed and nothing is sent, and it holds on a
 * server that does not run MenuKit. Returning {@code false} also skips the arm
 * swing, as for an empty hand.
 */
@Mixin(LocalPlayer.class)
public abstract class MKLocalDropMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void mk$refuseDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        ServerboundPlayerActionPacket.Action action = fullStack
                ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                : ServerboundPlayerActionPacket.Action.DROP_ITEM;
        if (PlayerActions.refuses(self, action)) cir.setReturnValue(false);
    }
}
