package com.trevlar.menukit.containers.state;

import com.trevlar.menukit.containers.api.state.SlotStateChannel;
import com.trevlar.menukit.containers.network.SlotStateSnapshotS2CPayload;
import com.trevlar.menukit.containers.network.SlotStateUpdateC2SPayload;
import com.trevlar.menukit.containers.network.SlotStateUpdateS2CPayload;
import com.trevlar.menukit.containers.network.SlotStateWire;
import com.trevlar.menukit.api.window.PersistentContainerKey;
import com.trevlar.menukit.window.ResolvedSlot;
import com.trevlar.menukit.api.window.SlotRef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The server's slot-state network: registration, the snapshots a menu gets when it
 * opens, the judgement of a client's write (§0067, {@link SlotStateWrites}), and the
 * updates co-viewers receive. The client's half is {@code SlotStateClient}.
 */
@ApiStatus.Internal
public final class SlotStateHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit-SlotState");

    private SlotStateHooks() {}

    // ── Registration ────────────────────────────────────────────────────

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(
                SlotStateSnapshotS2CPayload.TYPE, SlotStateSnapshotS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                SlotStateUpdateS2CPayload.TYPE, SlotStateUpdateS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SlotStateUpdateC2SPayload.TYPE, SlotStateUpdateC2SPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SlotStateUpdateC2SPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> handleWrite(payload, ctx.player())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> sendPlayerJoinSnapshot(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SlotStateWrites.RATE.forget(handler.getPlayer().getUUID()));
    }

    // ── Snapshots ───────────────────────────────────────────────────────

    /** Every stored value on the player's open menu, sent as the menu opens. */
    public static void sendSnapshotForOpenedMenu(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        List<SlotStateSnapshotS2CPayload.Entry> entries = buildEntries(menu, player, null);
        if (!entries.isEmpty()) {
            ServerPlayNetworking.send(player, new SlotStateSnapshotS2CPayload(menu.containerId, entries));
        }
    }

    /**
     * Player-scoped values on the player's inventory menu, on join and respawn: both
     * build the inventory menu without {@code openMenu}, so the open snapshot misses them.
     */
    public static void sendPlayerJoinSnapshot(ServerPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (menu == null) return;
        List<SlotStateSnapshotS2CPayload.Entry> entries = buildEntries(menu, player,
                key -> key instanceof PersistentContainerKey.PlayerInventory
                    || key instanceof PersistentContainerKey.EnderChest
                    // §0055: player-scoped created slots (pockets, equipment) resolve to Modded keys.
                    || key instanceof PersistentContainerKey.Modded);
        if (!entries.isEmpty()) {
            ServerPlayNetworking.send(player, new SlotStateSnapshotS2CPayload(menu.containerId, entries));
        }
    }

    /** Each slot's stored non-default values, as their channels' wire bytes. Reads only. */
    private static List<SlotStateSnapshotS2CPayload.Entry> buildEntries(
            AbstractContainerMenu menu, ServerPlayer player, @Nullable Predicate<PersistentContainerKey> keyFilter) {
        List<SlotStateSnapshotS2CPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            // §0055: a double chest splits each slot to its owning half's key.
            Optional<ResolvedSlot> resolved = SlotStateRegistry.resolve(slot.container, slot.getContainerSlot());
            if (resolved.isEmpty()) continue;
            if (keyFilter != null && !keyFilter.test(resolved.get().key())) continue;
            for (SlotStateChannel<?> channel : SlotStateRegistry.allChannels()) {
                byte[] bytes = storedBytes(channel, resolved.get(), player);
                if (bytes != null) entries.add(new SlotStateSnapshotS2CPayload.Entry(i, channel.id(), bytes));
            }
        }
        return entries;
    }

    /** A slot's stored value on a channel as wire bytes, or {@code null} when none (or unreadable). */
    private static <T> byte @Nullable [] storedBytes(SlotStateChannel<T> channel, ResolvedSlot slot, ServerPlayer player) {
        Tag tag = SlotStateServer.readTag(slot.key(), player, channel.id(), slot.localSlotIndex());
        if (tag == null) return null;
        Optional<T> value = SlotStateServer.tryDecode(channel, tag);
        if (value.isEmpty()) return null;
        try {
            return SlotStateWire.encode(channel, value.get(), player.registryAccess());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── A client's write, judged (§0067) ───────────────────────────────

    /**
     * Judges and applies one write a client sent. Public for the join probe, which
     * drives it with crafted payloads on a dedicated server. Main thread.
     */
    public static SlotStateWrites.Verdict handleWrite(SlotStateUpdateC2SPayload payload, ServerPlayer player) {
        return handleWrite(SlotStateRegistry.getChannelTyped(payload.channelId()), payload, player);
    }

    private static <T> SlotStateWrites.Verdict handleWrite(@Nullable SlotStateChannel<T> channel,
                                                          SlotStateUpdateC2SPayload payload, ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        int idx = payload.menuSlotIndex();
        boolean menuMatches = menu != null && menu.containerId == payload.containerId();
        Slot slot = menuMatches && idx >= 0 && idx < menu.slots.size() ? menu.slots.get(idx) : null;
        Optional<ResolvedSlot> resolved = slot == null ? Optional.empty()
                : SlotStateRegistry.resolve(slot.container, slot.getContainerSlot());

        SlotStateWrites.Judged<T> judged = SlotStateWrites.judge(channel,
                ch -> SlotStateWire.decode(ch, payload.value(), player.registryAccess()),
                () -> menuMatches,
                () -> slot != null && slot.isActive() && resolved.isPresent() && menu.stillValid(player),
                ch -> ch.canWrite().allows(player, SlotRef.of(menu, slot, player)),
                () -> SlotStateWrites.RATE.tryWrite(player.getUUID(), System.currentTimeMillis()));

        SlotStateWrites.Verdict verdict = judged.verdict();
        if (!verdict.accepted()) {
            LOGGER.debug("[SlotState] refused a write from {} on {}: {}",
                    player.getName().getString(), payload.channelId(), verdict);
            // The writer applied its value optimistically; send it the server's, so its
            // cache agrees again. Only when the slot is on the menu the client has open.
            if (channel != null && resolved.isPresent()) {
                sendValue(player, menu.containerId, idx, channel, currentValue(channel, resolved.get(), player));
            }
            return verdict;
        }

        ResolvedSlot rs = resolved.get();
        T value = judged.value();
        if (verdict == SlotStateWrites.Verdict.REMOVE) {
            SlotStateServer.clearTag(rs.key(), player, channel.id(), rs.localSlotIndex());
        } else {
            // The canonical re-encoding is what is stored, never the client's bytes.
            Optional<Tag> canonical = SlotStateServer.tryEncode(channel, value);
            if (canonical.isEmpty()) {
                sendValue(player, menu.containerId, idx, channel, currentValue(channel, rs, player));
                return SlotStateWrites.Verdict.MALFORMED;
            }
            SlotStateServer.writeTag(rs.key(), player, channel.id(), rs.localSlotIndex(), canonical.get());
        }
        // SHARED: every other viewer of the slot hears it (the writer already shows it).
        if (channel.visibility() == SlotStateChannel.Visibility.SHARED) {
            broadcastToViewers(player, rs.key(), rs.localSlotIndex(), channel, value, false);
        }
        return verdict;
    }

    /** The server's current value at a slot: stored, or the channel default. */
    private static <T> T currentValue(SlotStateChannel<T> channel, ResolvedSlot slot, ServerPlayer player) {
        Tag tag = SlotStateServer.readTag(slot.key(), player, channel.id(), slot.localSlotIndex());
        return tag == null ? channel.defaultValue() : SlotStateServer.decode(channel, tag);
    }

    // ── Updates to viewers ──────────────────────────────────────────────

    /** Sends one viewer the value at a slot of their open menu. */
    public static <T> void sendValue(ServerPlayer viewer, int containerId, int menuSlotIndex,
                                     SlotStateChannel<T> channel, T value) {
        byte[] bytes;
        try {
            bytes = SlotStateWire.encode(channel, value, viewer.registryAccess());
        } catch (RuntimeException e) {
            LOGGER.warn("[SlotState] channel {} could not encode a value for the wire: {}", channel.id(), e.toString());
            return;
        }
        ServerPlayNetworking.send(viewer, new SlotStateUpdateS2CPayload(containerId, menuSlotIndex, channel.id(), bytes));
    }

    /**
     * Sends a value to every player viewing the slot at {@code key} and
     * {@code localSlotIndex}, matched in each viewer's own open menu by the slot's
     * resolved container key (§0055, §0067), and sent with that viewer's own menu id and
     * slot index. {@code includeOrigin}: whether the writer hears it too (false when the
     * writer's client already applied it).
     */
    public static <T> void broadcastToViewers(ServerPlayer origin, PersistentContainerKey key, int localSlotIndex,
                                              SlotStateChannel<T> channel, T value, boolean includeOrigin) {
        if (origin == null || !(origin.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (!includeOrigin && p == origin) continue;
            sendIfViewing(p, key, localSlotIndex, channel, value);
        }
    }

    /** Sends {@code viewer} the value if the slot at {@code key} and {@code localSlotIndex} is on their open menu. */
    public static <T> void sendIfViewing(ServerPlayer viewer, PersistentContainerKey key, int localSlotIndex,
                                         SlotStateChannel<T> channel, T value) {
        AbstractContainerMenu menu = viewer.containerMenu;
        if (menu == null) return;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            Optional<ResolvedSlot> rs = SlotStateRegistry.resolve(s.container, s.getContainerSlot());
            if (rs.isPresent() && rs.get().key().equals(key) && rs.get().localSlotIndex() == localSlotIndex) {
                sendValue(viewer, menu.containerId, i, channel, value);
                return;
            }
        }
    }
}
