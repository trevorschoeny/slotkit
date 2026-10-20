package com.trevlar.slotkit.core;

import com.trevlar.slotkit.network.ActiveCountS2CPayload;
import com.trevlar.slotkit.network.Handshake;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's active count per opted-in group ({@code SlotSpec.activeCount}). The
 * server owns it, per {@code ServerPlayer}: a new login or a death starts every
 * group at its declared start, and the client is sent a snapshot then. The client
 * keeps one copy for the local player, replaced by snapshots, changed by updates and
 * cleared on disconnect. Not saved: a session doesn't outlive a connection.
 */
@ApiStatus.Internal
public final class ActiveCounts {

    private ActiveCounts() {}

    /** Each opted-in group's start, by slot panel id. */
    private static final Map<String, Integer> STARTS = new ConcurrentHashMap<>();
    /** Each opted-in group's declared maximum, by slot panel id. */
    private static final Map<String, Integer> MAXIMA = new ConcurrentHashMap<>();

    // Server: per player object, so a respawned player starts fresh. Server thread only.
    private static final Map<ServerPlayer, Map<String, Integer>> SERVER = new WeakHashMap<>();
    // Client: the local player's, whichever LocalPlayer instance is current.
    private static final Map<String, Integer> CLIENT = new ConcurrentHashMap<>();

    public static void declare(String slotPanelId, int start, int max) {
        if (start < 0 || start > max) {
            throw new IllegalArgumentException("Slot group " + slotPanelId + ": activeCount(" + start
                    + ") must be from 0 to its count, " + max);
        }
        STARTS.put(slotPanelId, start);
        MAXIMA.put(slotPanelId, max);
    }

    public static boolean optedIn(String slotPanelId) {
        return STARTS.containsKey(slotPanelId);
    }

    /** {@code player}'s count for the group, on its own side. */
    public static int get(Player player, String slotPanelId) {
        int start = STARTS.getOrDefault(slotPanelId, Integer.MAX_VALUE);
        if (player.level().isClientSide()) return CLIENT.getOrDefault(slotPanelId, start);
        if (!(player instanceof ServerPlayer sp)) return start;
        synchronized (SERVER) {
            Map<String, Integer> m = SERVER.get(sp);
            return m == null ? start : m.getOrDefault(slotPanelId, start);
        }
    }

    /** Sets the count on the server and tells the player's client at once (before the next content sync). */
    public static void set(ServerPlayer player, String slotPanelId, int count) {
        Integer max = MAXIMA.get(slotPanelId);
        if (max == null) {
            throw new IllegalStateException("Slot group " + slotPanelId + " didn't opt in to an active count "
                    + "(SlotSpec.activeCount)");
        }
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Slot group " + slotPanelId + ": active count " + count
                    + " is outside 0.." + max);
        }
        if (get(player, slotPanelId) == count) return;   // the same count again sends nothing
        synchronized (SERVER) {
            SERVER.computeIfAbsent(player, p -> new HashMap<>()).put(slotPanelId, count);
        }
        Handshake.send(player, new ActiveCountS2CPayload(false, Map.of(slotPanelId, count)));
    }

    /** Client: applies what the server sent. */
    public static void applyOnClient(ActiveCountS2CPayload payload) {
        if (payload.snapshot()) CLIENT.clear();
        CLIENT.putAll(payload.counts());
    }

    /** Client: forgets the server's counts. */
    public static void clearClient() {
        CLIENT.clear();
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(ActiveCountS2CPayload.TYPE, ActiveCountS2CPayload.STREAM_CODEC);
        // A new login or a death starts at the declared counts; the client hears so.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSnapshot(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Leaving the End also builds a new ServerPlayer, but nothing ended: keep the counts.
            if (alive) {
                synchronized (SERVER) {
                    Map<String, Integer> old = SERVER.get(oldPlayer);
                    if (old != null) SERVER.put(newPlayer, new HashMap<>(old));
                }
            }
            sendSnapshot(newPlayer);
        });
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (STARTS.isEmpty()) return;
        Map<String, Integer> counts = new HashMap<>();
        for (String id : STARTS.keySet()) counts.put(id, get(player, id));
        Handshake.send(player, new ActiveCountS2CPayload(true, counts));
    }
}
