package com.trevlar.slotkit.state;

import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.state.SlotStateChannel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Slot state for slots inside an item (a box opened from the inventory, {@link ItemContents}):
 * the item holds the SHARED values, and the client's container behind those slots is a plain
 * copy that names nothing, so it can't read them. Each tick, the server compares each such
 * item's custom_data with what this viewer was last sent and sends the slots whose values
 * changed. That covers opening, any write to the item (by a channel or a mod's own request),
 * and the item being swapped, with no path having to remember to sync.
 */
@ApiStatus.Internal
public final class ItemSlotStateSync {

    private ItemSlotStateSync() {}

    /** What one viewer was last sent: the menu, and per item-backed container a copy of its item then. */
    private record Watch(AbstractContainerMenu menu, Map<Container, ItemStack> sent) {}

    // Server thread only. Weak: a player who leaves takes their watch along.
    private static final Map<ServerPlayer, Watch> WATCHES = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) tick(p);
        });
    }

    private static void tick(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        Watch watch = WATCHES.get(player);
        if (watch == null || watch.menu() != menu) {
            // A new menu is a new client container, whose cache starts empty: compare with nothing.
            watch = new Watch(menu, new IdentityHashMap<>());
            WATCHES.put(player, watch);
        }
        Map<Container, ItemStack> now = null;
        for (Slot s : menu.slots) {
            if (!(com.trevlar.slotkit.core.Slots.target(s).container instanceof ItemContents contents)) continue;
            Container c = (Container) contents;
            if (now == null) now = new IdentityHashMap<>();
            if (now.containsKey(c)) continue;
            ItemStack item = contents.item(player);
            now.put(c, item == null ? ItemStack.EMPTY : item);
        }
        if (now == null) return;
        for (Map.Entry<Container, ItemStack> e : now.entrySet()) {
            ItemStack before = watch.sent().getOrDefault(e.getKey(), ItemStack.EMPTY);
            ItemStack item = e.getValue();
            if (Objects.equals(before.get(DataComponents.CUSTOM_DATA), item.get(DataComponents.CUSTOM_DATA))) continue;
            send(player, menu, e.getKey(), before, item);
            watch.sent().put(e.getKey(), item.copy());
        }
    }

    /** Sends each of the container's slots whose SHARED value differs between {@code before} and {@code item}. */
    private static void send(ServerPlayer player, AbstractContainerMenu menu, Container container,
                             ItemStack before, ItemStack item) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = com.trevlar.slotkit.core.Slots.target(menu.slots.get(i));
            if (s.container != container) continue;
            for (SlotStateChannel<?> channel : SlotStateRegistry.allChannels()) {
                sendIfChanged(player, menu.containerId, i, channel, before, item, s.getContainerSlot());
            }
        }
    }

    private static <T> void sendIfChanged(ServerPlayer player, int containerId, int menuSlot, SlotStateChannel<T> channel,
                                          ItemStack before, ItemStack item, int index) {
        if (channel.visibility() != SlotStateChannel.Visibility.SHARED) return;   // an item carries only SHARED values
        T value = channel.get(item, index);
        if (Objects.equals(value, channel.get(before, index))) return;
        SlotStateHooks.sendValue(player, containerId, menuSlot, channel, value);
    }
}
