package com.trevlar.menukit.containers.state;

import com.trevlar.menukit.containers.core.SlotStateChannel;
import com.trevlar.menukit.containers.network.SlotStateSnapshotS2CPayload;
import com.trevlar.menukit.containers.network.SlotStateUpdateS2CPayload;
import com.trevlar.menukit.containers.network.SlotStateWire;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's half of the slot-state network: snapshots and updates from the server,
 * decoded with each channel's {@code StreamCodec} into {@link SlotStateClientCache}.
 * An update applies only to the menu it names (§0067). A value this client cannot
 * decode, or a channel it does not have, is skipped and logged once; it never throws.
 */
@ApiStatus.Internal
public final class SlotStateClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit-SlotState");
    private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

    private SlotStateClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SlotStateSnapshotS2CPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    for (SlotStateSnapshotS2CPayload.Entry e : payload.entries()) {
                        apply(payload.containerId(), e.menuSlotIndex(), e.channelId(), e.value());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(SlotStateUpdateS2CPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        apply(payload.containerId(), payload.menuSlotIndex(), payload.channelId(), payload.value())));
    }

    private static void apply(int containerId, int menuSlotIndex, Identifier channelId, byte[] bytes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // The inventory menu's id names the inventory menu even while the creative picker
        // (a client-only menu with the same id) is open; the server knows only the former.
        AbstractContainerMenu menu = containerId == mc.player.inventoryMenu.containerId
                ? mc.player.inventoryMenu : mc.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return; // for a menu no longer open
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) return;
        SlotStateChannel<?> channel = SlotStateRegistry.getChannel(channelId);
        if (channel == null) {
            warnOnce(channelId, "a channel this game does not have");
            return;
        }
        applyTyped(channel, menu.slots.get(menuSlotIndex), bytes, mc);
    }

    private static <T> void applyTyped(SlotStateChannel<T> channel, Slot slot, byte[] bytes, Minecraft mc) {
        Optional<T> value = SlotStateWire.decode(channel, bytes, mc.level.registryAccess());
        if (value.isEmpty()) {
            warnOnce(channel.id(), "a value that does not decode");
            return;
        }
        SlotStateClientCache.write(channel, slot.container, slot.getContainerSlot(), value.get());
    }

    private static void warnOnce(Identifier channelId, String what) {
        if (WARNED.add(channelId)) {
            LOGGER.warn("[SlotState] skipped {} on {} (logged once per channel)", what, channelId);
        }
    }
}
