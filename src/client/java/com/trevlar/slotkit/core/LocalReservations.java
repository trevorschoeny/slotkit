package com.trevlar.slotkit.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.trevlar.slotkit.SlotKit;
import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.RegistryOps;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The inventory's reservations a client keeps for a server without SlotKit, one set per
 * world or server, in {@code config/slotkit/reservations.json}:
 * {@code {"server:<address>": {"<inventory index>": <Reservation>}}}, each value in
 * {@link Reservation#CODEC}'s form, the one a slot holds on a server.
 *
 * <p>Read once per connection (the items need the server's registries) and written on
 * every change; a reservation is a rare, deliberate edit.
 */
@ApiStatus.Internal
public final class LocalReservations implements Reservations.LocalStore {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("slotkit/reservations.json");

    // This connection's set, decoded; null until first asked, cleared on disconnect.
    private @Nullable Map<Integer, Reservation> current;

    public static void register() {
        LocalReservations store = new LocalReservations();
        Reservations.installLocalStore(store);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> store.current = null);
    }

    @Override
    public Reservation get(int index) {
        return loaded().getOrDefault(index, Reservation.NONE);
    }

    @Override
    public void set(int index, Reservation reservation) {
        Map<Integer, Reservation> map = loaded();
        if (reservation.isNone()) map.remove(index);
        else map.put(index, reservation);
        save(map);
    }

    private Map<Integer, Reservation> loaded() {
        if (current != null) return current;
        Map<Integer, Reservation> map = new HashMap<>();
        String world = world();
        RegistryOps<JsonElement> ops = ops();
        JsonObject entries = world == null || ops == null ? null : read().getAsJsonObject(world);
        if (entries != null) {
            entries.entrySet().forEach(e -> Reservation.CODEC.parse(ops, e.getValue())
                    .resultOrPartial(err -> SlotKit.LOGGER.warn("[SlotKit] skipped a saved reservation: {}", err))
                    .ifPresent(r -> map.put(Integer.parseInt(e.getKey()), r)));
        }
        // Not connected yet: answer empty without caching, so the real set loads once we are.
        if (world != null && ops != null) current = map;
        return map;
    }

    private void save(Map<Integer, Reservation> map) {
        String world = world();
        RegistryOps<JsonElement> ops = ops();
        if (world == null || ops == null) return;
        JsonObject root = read();
        JsonObject entries = new JsonObject();
        map.forEach((index, r) -> Reservation.CODEC.encodeStart(ops, r).ifSuccess(json -> entries.add(index.toString(), json)));
        if (entries.isEmpty()) root.remove(world);
        else root.add(world, entries);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, root.toString());
        } catch (IOException e) {
            SlotKit.LOGGER.error("[SlotKit] couldn't save reservations to {}", FILE, e);
        }
    }

    private static JsonObject read() {
        if (!Files.exists(FILE)) return new JsonObject();
        try {
            return JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            SlotKit.LOGGER.error("[SlotKit] couldn't read {}, starting empty", FILE, e);
            return new JsonObject();
        }
    }

    // Where the player is: a server by its address, a singleplayer world by its name.
    private static @Nullable String world() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) return "singleplayer:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        ServerData server = mc.getCurrentServer();
        return server == null ? null : "server:" + server.ip;
    }

    private static @Nullable RegistryOps<JsonElement> ops() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess());
    }
}
