package com.trevlar.slotkit.api.reserve;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.Presence;
import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.item.LiveItem;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.core.Slots;
import com.trevlar.slotkit.network.Handshake;
import com.trevlar.slotkit.network.ReserveC2SPayload;
import com.trevlar.slotkit.window.ActingPlayer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Reserved slots: a slot that holds out for one item (a {@link Reservation}) and takes
 * nothing else, by any hand: clicks, shift-clicks, drags, number keys, pickups, hoppers,
 * droppers, copper golems. A reserved slot that runs out keeps its ghost, which every
 * client draws in the empty slot.
 *
 * <h2>Where a reservation lives</h2>
 * One format, a SHARED slot-state value per slot, so every mod that reserves (Inventory
 * Plus, Shulker Palette) reads and writes the same thing:
 * <ul>
 *   <li>a placed container (chest, barrel) holds it on its block entity; broken, it is dropped;</li>
 *   <li>a shulker box carries it on its item, and its block holds it while placed;</li>
 *   <li>the player's inventory holds it on the player.</li>
 * </ul>
 *
 * <h2>The rules</h2>
 * A put into a reserved slot is refused unless the stack {@linkplain Reservation#matches
 * matches}, and stops at the reservation's cap. The refusal is a {@link SlotOperations.PutVeto}
 * SlotKit registers for both modes, so it rides every put seam and {@code Automation.room}.
 *
 * <h2>A server without SlotKit</h2>
 * There the client keeps the inventory's reservations itself, per world or server (in
 * {@code config/slotkit/reservations.json}), and its clicks honor them before they are sent:
 * the same calls, the same values. Containers and boxes reserve only where the server runs
 * SlotKit, and automation is the server's.
 *
 * <p>Since 2.4.0.
 */
public final class Reservations {

    private Reservations() {}

    private static final Identifier ID = Identifier.fromNamespaceAndPath("slotkit", "reservation");

    // Written only through reserve(), which moves out what no longer belongs; a client
    // asks with ReserveC2SPayload, never with a raw slot-state write.
    private static final SlotStateChannel<Reservation> CHANNEL = SlotState.register(ModeScope.BOTH, ID,
            Reservation.CODEC, Reservation.STREAM_CODEC, Reservation.NONE,
            SlotStateChannel.Visibility.SHARED, SlotStateChannel.CanWrite.DENY);

    // Reading

    /** The reservation on the slot a veto or seam is asked about, wherever it lives. {@link Reservation#NONE} for none. */
    public static Reservation get(SlotRef ref) {
        if (local != null && ref.container() instanceof Inventory inventory && keptLocally(inventory.player)) {
            return local.get(ref.containerSlot());
        }
        return CHANNEL.get(ref);
    }

    /** The reservation on slot {@code index} of the container {@code box} carries (a shulker box item). Either side. */
    public static Reservation get(ItemStack box, int index) {
        return CHANNEL.get(box, index);
    }

    /** How many of {@code stack} the slot takes: the stack size, or a matching reservation's cap. */
    public static int limit(SlotRef ref, ItemStack stack) {
        Reservation r = get(ref);
        return r.isNone() || !r.matches(stack) ? stack.getMaxStackSize() : r.limit(stack);
    }

    // Writing, server side

    /**
     * Reserves {@code slot} of {@code player}'s open menu (or clears it with
     * {@link Reservation#NONE}), as the player's own action. Whatever no longer belongs
     * (a different item, or more than the cap) goes into the player's inventory, or is
     * dropped when it doesn't fit. On a client, asks the server to, and the slot shows the
     * result once the server applies it. Where the server lacks SlotKit, the client keeps an
     * inventory slot's reservation itself and moves nothing. What the editor calls.
     */
    public static void reserve(Player player, Slot slot, Reservation reservation) {
        AbstractContainerMenu menu = player.containerMenu;
        if (local != null && keptLocally(player)) {
            // No server to ask: the inventory's slots are reserved here; nothing is moved out.
            Slot target = Slots.target(slot);
            if (target.container instanceof Inventory) local.set(target.getContainerSlot(), reservation);
            return;
        }
        if (!(player instanceof ServerPlayer server)) {
            int index = menu.slots.indexOf(slot);
            if (index >= 0 && Presence.serverHasChannel(ID)) {
                clientSender.accept(new ReserveC2SPayload(menu.containerId, index, reservation));
            }
            return;
        }
        Slot target = Slots.target(slot);
        int index = target.getContainerSlot();
        if (target.container instanceof ItemContents contents) {
            // A box opened from the inventory: the reservation goes on the box item.
            ItemPath path = contents.itemPath(server);
            LiveItem live = path == null ? null : ItemSources.live(server, path);
            if (live == null) return;
            CHANNEL.set(live.stack(), index, reservation);
            live.commit();
        } else {
            CHANNEL.set(server, slot, reservation);
        }
        ItemStack there = target.getItem();
        if (reservation.isNone() || there.isEmpty()) return;
        int keep = reservation.matches(there) ? Math.min(there.getCount(), reservation.limit(there)) : 0;
        if (keep == there.getCount()) return;
        ItemStack out = target.remove(there.getCount() - keep);
        // Into the inventory, the rest dropped; the slot just reserved refuses it, so it doesn't come back.
        server.getInventory().placeItemBackInInventory(out, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /**
     * Sets slot {@code index} of a placed container from server code, with no player (a
     * blueprint loading). Viewers see it at once. Moves nothing out.
     */
    public static void set(Container container, int index, Reservation reservation) {
        CHANNEL.set(new Slot(container, index, 0, 0), reservation);
    }

    /** Sets slot {@code index} of the container {@code box} carries. The caller puts the item back where it lives. */
    public static void set(ItemStack box, int index, Reservation reservation) {
        CHANNEL.set(box, index, reservation);
    }

    // Wiring

    /** SlotKit's common init: the veto and the client's request. */
    @ApiStatus.Internal
    public static void init() {
        SlotOperations.vetoPut(ModeScope.BOTH, Reservations::refuses);
        PayloadTypeRegistry.serverboundPlay().register(ReserveC2SPayload.TYPE, ReserveC2SPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReserveC2SPayload.TYPE, (payload, ctx) -> {
            if (!Handshake.hears(ctx.player())) return;
            ctx.server().execute(() -> handle(payload, ctx.player()));
        });
    }

    /** A put the reservation refuses: another item, or the slot already at its cap. */
    private static boolean refuses(SlotRef ref, @Nullable SlotGroupId group, BehaviorKey<?> operation, ItemStack incoming) {
        Reservation r = get(ref);
        if (r.isNone()) return false;
        if (!r.matches(incoming)) return true;
        ItemStack there = ref.container().getItem(ref.containerSlot());
        return !there.isEmpty() && there.getCount() >= r.limit(incoming);
    }

    /** A slot's stack cap for a click: {@code max}, or lower under a matching reservation. */
    @ApiStatus.Internal
    public static int capAt(Slot slot, ItemStack stack, int max) {
        if (stack.isEmpty()) return max;
        Slot target = Slots.target(slot);
        Reservation r = get(new SlotRef(target.container, target.getContainerSlot(), slot, null,
                ActingPlayer.current(), null, null));
        return r.isNone() || !r.matches(stack) ? max : Math.min(max, r.limit(stack));
    }

    // A client's request: a slot of the menu it has open, as a click names one.
    private static void handle(ReserveC2SPayload payload, ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu.containerId == payload.containerId() ? player.containerMenu
                : player.inventoryMenu.containerId == payload.containerId() ? player.inventoryMenu : null;
        if (menu == null || payload.menuSlot() < 0 || payload.menuSlot() >= menu.slots.size()) return;
        reserve(player, menu.slots.get(payload.menuSlot()), payload.reservation());
    }

    // The client's own store, for a server without SlotKit; installed by SlotKitClient.

    /** The inventory's reservations a client keeps for a server without SlotKit, by inventory index. */
    @ApiStatus.Internal
    public interface LocalStore {
        Reservation get(int index);
        void set(int index, Reservation reservation);
    }

    private static volatile @Nullable LocalStore local;

    @ApiStatus.Internal
    public static void installLocalStore(LocalStore store) {
        local = store;
    }

    // A client player whose server can't hold reservations.
    private static boolean keptLocally(Player player) {
        return player.level().isClientSide() && !Presence.serverHasChannel(ID);
    }

    // The client's play-packet sender, installed by SlotKitClient: common code never names
    // the client networking class.
    private static volatile Consumer<CustomPacketPayload> clientSender = payload -> {};

    @ApiStatus.Internal
    public static void installClientSender(Consumer<CustomPacketPayload> sender) {
        clientSender = sender;
    }
}
