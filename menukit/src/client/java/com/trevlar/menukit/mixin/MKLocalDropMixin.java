package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.PlayerActions;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Q and Ctrl-Q with no screen open: the client refuses before it drops. Since
 * 26.3 the drop key goes through {@code MultiPlayerGameMode.dropItem}, which
 * removes the item from the selected slot as a prediction and sends the action,
 * so the refusal has to come first: refused here, nothing is removed and
 * nothing is sent, and it holds on a server that does not run MenuKit.
 * Cancelling also skips the arm swing, as for an empty hand.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MKLocalDropMixin {

    @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), cancellable = true)
    private void mk$refuseDrop(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = fullStack
                ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                : ServerboundPlayerActionPacket.Action.DROP_ITEM;
        if (PlayerActions.refuses(player, action)) ci.cancel();
    }
}
