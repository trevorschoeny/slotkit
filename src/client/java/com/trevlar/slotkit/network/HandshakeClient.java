package com.trevlar.slotkit.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.ApiStatus;

/**
 * The client's side of the negotiation. It never refuses anything: SlotKit's client
 * has no disconnect path. When a configuration starts it forgets the last server
 * (singleplayer is this very game, so everything is on there); a server's hello is
 * recorded and answered with this game's protocol, declaring mods and created slot
 * panels. A server without SlotKit never says hello, so the record stays "no SlotKit"
 * and the client builds no created slots and sends no SlotKit play payload there.
 */
@ApiStatus.Internal
public final class HandshakeClient {

    private HandshakeClient() {}

    public static void register() {
        ClientConfigurationConnectionEvents.START.register((handler, client) ->
                Handshake.setServer(Minecraft.getInstance().hasSingleplayerServer()
                        ? Handshake.ServerRecord.SINGLEPLAYER : Handshake.ServerRecord.NONE));
        ClientConfigurationNetworking.registerGlobalReceiver(Handshake.Hello.TYPE, (hello, context) -> {
            if (!Minecraft.getInstance().hasSingleplayerServer()) Handshake.setServer(Handshake.ServerRecord.of(hello));
            context.responseSender().sendPacket(Handshake.local().reply());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Handshake.setServer(null));
    }
}
