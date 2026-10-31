package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.menu.MenuSwitch;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A click the client sent before it heard of a {@link MenuSwitch} switch is addressed to
 * the menu it left, and vanilla drops it on the container id. Once on the server thread,
 * {@link MenuSwitch#clickArrived} readdresses it to the menu in front when it means the
 * same there.
 */
@ApiStatus.Internal
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SKStaleClickMixin {

    @Shadow public ServerPlayer player;

    @ModifyVariable(method = "handleContainerClick", argsOnly = true,
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private ServerboundContainerClickPacket sk$readdressStaleClick(ServerboundContainerClickPacket packet) {
        return MenuSwitch.clickArrived(player, packet);
    }
}
