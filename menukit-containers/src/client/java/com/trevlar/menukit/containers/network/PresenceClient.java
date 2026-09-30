package com.trevlar.menukit.containers.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

/**
 * The client's side of the presence check (§0069): forget what the last server said
 * when a configuration starts, answer the server's hello with this game's protocol,
 * and record that the server runs Containers. A server without it never says hello,
 * so the flag stays off and the client builds no created slots on it.
 */
@ApiStatus.Internal
public final class PresenceClient {

    private PresenceClient() {}

    public static void register() {
        ClientConfigurationConnectionEvents.START.register((handler, client) ->
                // Singleplayer is this very game: its server runs Containers.
                Presence.setServerHasContainers(Minecraft.getInstance().hasSingleplayerServer()));
        ClientConfigurationNetworking.registerGlobalReceiver(Presence.Hello.TYPE, (hello, context) -> {
            Presence.setServerHasContainers(true);
            context.responseSender().sendPacket(new Presence.Reply(Presence.PROTOCOL));
        });
    }
}
