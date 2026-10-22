package com.trevlar.slotkit.api;

import com.trevlar.slotkit.network.Handshake;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the other side runs, as the handshake found it when the player joined.
 *
 * <p>A mod declares its sides in {@code fabric.mod.json}
 * ({@code "custom": { "slotkit": { "bothSides": "required" } }}, or {@code "preferred"});
 * the handshake lists every declaring mod and every slot-state channel, so either side
 * can ask about them here. Decided before the world loads, fixed until disconnect, reset
 * at the next configuration. In singleplayer every answer is true for every mod in the
 * game. Read it when you build your UI, or on Fabric's
 * {@code ClientPlayConnectionEvents.JOIN}; there is no extra event.
 *
 * <pre>{@code
 * // Gray out a shared setting the server can't hold.
 * toggle.disabledWhen(() -> !Presence.serverHasChannel(MY_CHANNEL_ID));
 * }</pre>
 *
 * <p>A client's answer about itself is its claim: it decides what the server sends it
 * and whether it is admitted, never whether a write is allowed.
 */
public final class Presence {

    private Presence() {}

    // Client side

    /** Whether this game's server runs SlotKit. False when not connected. */
    public static boolean serverHasSlotKit() {
        Handshake.ServerRecord s = Handshake.server();
        return s != null && s.slotKit();
    }

    /** Whether this game's server runs {@code modId}, a mod with a {@code slotkit} block. False when not connected. */
    public static boolean serverHas(String modId) {
        Handshake.ServerRecord s = Handshake.server();
        return s != null && s.slotKit() && s.has(modId);
    }

    /** Whether this game's server registers the slot-state channel {@code channelId}. False when not connected. */
    public static boolean serverHasChannel(Identifier channelId) {
        Handshake.ServerRecord s = Handshake.server();
        return s != null && s.slotKit() && s.hasChannel(channelId);
    }

    // Server side

    /** Whether {@code player}'s client has SlotKit. */
    public static boolean clientHasSlotKit(ServerPlayer player) {
        return Handshake.client(player).slotKit();
    }

    /** Whether {@code player}'s client has {@code modId}, a mod with a {@code slotkit} block. */
    public static boolean clientHas(ServerPlayer player, String modId) {
        Handshake.ClientRecord c = Handshake.client(player);
        return c.slotKit() && c.has(modId);
    }
}
