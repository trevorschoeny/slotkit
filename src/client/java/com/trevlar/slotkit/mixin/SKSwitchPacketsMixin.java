package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.MenuSwitchClient;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The packets {@link MenuSwitchClient} waits between: a server-opened screen is built
 * with capture on, and a menu's first contents show its held screen just before they apply.
 * Each inject sits after the thread check, which reschedules a packet read off the game thread.
 */
@ApiStatus.Internal
@Mixin(ClientPacketListener.class)
public abstract class SKSwitchPacketsMixin {

    @Inject(method = "handleOpenScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
    private void sk$captureOn(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        MenuSwitchClient.capturing(true);
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void sk$captureOff(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        MenuSwitchClient.capturing(false);
    }

    @Inject(method = "handleContainerContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER))
    private void sk$showHeld(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MenuSwitchClient.contentArriving(packet.containerId());
    }
}
