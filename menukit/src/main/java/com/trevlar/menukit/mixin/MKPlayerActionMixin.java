package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.ClickTags;
import com.trevlar.menukit.window.PlayerActions;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server side of Q, Ctrl-Q and F with no screen open: the action is refused
 * when the selected hotbar slot (and, for F, the offhand) refuses the operation.
 *
 * <p>Injected just after {@code PacketUtils.ensureRunningOnSameThread}: vanilla
 * first receives the packet on the network thread and bounces it to the server
 * thread there, so everything after that call runs on the server thread only,
 * where the inventory may be read. A tag the client recorded for the action is
 * claimed for the length of the decision.
 *
 * <p>On a refusal the client's view of its menu is resent. A client running
 * MenuKit never predicted the drop, but one without it did, and would otherwise
 * show the item gone until something else resynced it.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MKPlayerActionMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void mk$playerActionOperations(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (!PlayerActions.isOperation(action)) return;
        ClickTags.Tag outer = ClickTags.enter(ClickTags.claimAction(player.getUUID(), action));
        try {
            if (PlayerActions.refuses(player, action)) {
                player.containerMenu.sendAllDataToRemote();
                ci.cancel();
            }
        } finally {
            ClickTags.exit(outer);
        }
    }
}
