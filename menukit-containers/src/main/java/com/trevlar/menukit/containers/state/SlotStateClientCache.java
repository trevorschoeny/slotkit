package com.trevlar.menukit.containers.state;

import com.trevlar.menukit.containers.core.SlotStateChannel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The client's slot-state values, by container and index, as the server last sent
 * them or as this client last wrote them (optimistically; the server's answer
 * overwrites it). A value equal to the channel's default is no entry at all. Only
 * decoded values of registered channels are kept.
 */
@ApiStatus.Internal
public final class SlotStateClientCache {

    // Weak outer keys: a container's values go with its menu.
    private static final Map<Container, Map<Integer, Map<Identifier, Object>>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SlotStateClientCache() {}

    @SuppressWarnings("unchecked")
    public static <T> T read(SlotStateChannel<T> channel, Slot slot) {
        synchronized (CACHE) {
            Map<Integer, Map<Identifier, Object>> byIndex = CACHE.get(slot.container);
            Map<Identifier, Object> byChannel = byIndex == null ? null : byIndex.get(slot.getContainerSlot());
            Object v = byChannel == null ? null : byChannel.get(channel.id());
            return v == null ? channel.defaultValue() : (T) v;
        }
    }

    public static <T> void write(SlotStateChannel<T> channel, Container container, int containerSlotIndex, T value) {
        synchronized (CACHE) {
            if (Objects.equals(value, channel.defaultValue())) {
                Map<Integer, Map<Identifier, Object>> byIndex = CACHE.get(container);
                Map<Identifier, Object> byChannel = byIndex == null ? null : byIndex.get(containerSlotIndex);
                if (byChannel != null) byChannel.remove(channel.id());
                return;
            }
            CACHE.computeIfAbsent(container, c -> new HashMap<>())
                 .computeIfAbsent(containerSlotIndex, i -> new HashMap<>())
                 .put(channel.id(), value);
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
