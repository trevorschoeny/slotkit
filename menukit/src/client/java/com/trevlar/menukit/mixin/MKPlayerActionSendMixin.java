package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.ClickTags;
import com.trevlar.menukit.window.PlayerActions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's send boundary for the three player actions that are slot
 * operations (Q, Ctrl-Q, F with no screen open). Every such action leaves through
 * here, vanilla's key handling and a mod sending one itself alike.
 *
 * <p><b>Why the superclass.</b> {@code send} is declared by
 * {@code ClientCommonPacketListenerImpl}, and {@code ClientPacketListener} only
 * inherits it. Mixin resolves an injector's target on the class {@code @Mixin}
 * names, so targeting the subclass killed the client during mixin apply (5.1.0,
 * 2026-09-12). The config-phase listener shares this method and never carries a
 * play packet, so the type test below is also what keeps this to the play phase.
 *
 * <ul>
 *   <li><b>F is refused here.</b> Vanilla predicts nothing for an offhand swap,
 *       so not sending it is the whole refusal.</li>
 *   <li><b>Drops are not.</b> They are refused in {@code LocalPlayer.drop}
 *       ({@link MKLocalDropMixin}), before vanilla's local removal; by the time
 *       the packet is sent that prediction has happened, and refusing only the
 *       packet would leave the client showing an item gone that the server kept.</li>
 *   <li><b>A tag is recorded</b> for any of the three sent inside
 *       {@code SlotOperations.as(...)}, so the integrated server judges it as the
 *       operation it serves ({@link ClickTags}).</li>
 * </ul>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MKPlayerActionSendMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void mk$playerActionSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundPlayerActionPacket actionPacket)) return;
        ServerboundPlayerActionPacket.Action action = actionPacket.getAction();
        if (!PlayerActions.isOperation(action)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && PlayerActions.refuses(player, action)) {
            ci.cancel();
            return;
        }
        ClickTags.Tag tag = ClickTags.current();
        if (tag != null && Minecraft.getInstance().hasSingleplayerServer()) {
            ClickTags.recordAction(player.getUUID(), action, tag);
        }
    }
}
