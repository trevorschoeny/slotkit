package com.trevlar.menukit.containers.api.state;


import com.trevlar.menukit.containers.core.CreatedSlotAdapter;
import com.trevlar.menukit.containers.core.SlotAddresses;
import com.trevlar.menukit.api.window.PersistentContainerKey;

import com.mojang.serialization.Codec;
import com.trevlar.menukit.mixin.CompoundContainerAccessor;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.window.ResolvedSlot;
import com.trevlar.menukit.containers.state.SlotStateClientCache;
import com.trevlar.menukit.containers.state.SlotStateRegistry;
import com.trevlar.menukit.containers.state.SlotStateServer;

import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

/**
 * Entry point for persistent per-slot state. Consumers register channels at mod init,
 * then read and write through the returned {@link SlotStateChannel} handles.
 */
public final class SlotState {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit-SlotState");

    private SlotState() {}

    // Channel registration

    /** Registers a PRIVATE channel. A second channel with the same id throws. */
    public static <T> SlotStateChannel<T> register(
            Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue) {
        return register(id, codec, streamCodec, defaultValue, SlotStateChannel.Visibility.PRIVATE);
    }

    /**
     * Registers a channel with explicit visibility. PRIVATE gives each player their own
     * value. SHARED gives one value per slot for all players, synced to and writable by
     * every viewer, for features that span players like Inventory Plus's container
     * locks. SHARED only makes sense on multi-player, fixed-slot containers (a placed
     * shulker, chest or barrel); see {@link SlotStateChannel.Visibility}.
     */
    public static <T> SlotStateChannel<T> register(
            Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue,
            SlotStateChannel.Visibility visibility) {
        return register(new SlotStateChannel<>(id, codec, streamCodec, defaultValue, visibility));
    }

    /**
     * Registers a channel with its writer rule: who may write it from a client.
     * A SHARED channel needs one for any client to write it; without one it keeps the
     * default, which refuses every client write. A PRIVATE channel defaults to letting a
     * player write their own value.
     *
     * <pre>{@code
     * LOCKS = SlotState.register(id, Codec.BOOL, ByteBufCodecs.BOOL.cast(), false,
     *         SlotStateChannel.Visibility.SHARED,
     *         (player, slot) -> MyLocks.handles(slot.container()));
     * }</pre>
     */
    public static <T> SlotStateChannel<T> register(
            Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue,
            SlotStateChannel.Visibility visibility, SlotStateChannel.CanWrite canWrite) {
        return register(new SlotStateChannel<>(id, codec, streamCodec, defaultValue, visibility, canWrite));
    }

    private static <T> SlotStateChannel<T> register(SlotStateChannel<T> channel) {
        SlotStateRegistry.registerChannel(channel);   // a second channel with one id throws
        LOGGER.info("[SlotState] registered channel {} ({})", channel.id(), channel.visibility());
        return channel;
    }

    /** Returns the channel with the given id, or empty if not registered. */
    public static Optional<SlotStateChannel<?>> getChannel(Identifier id) {
        return Optional.ofNullable(SlotStateRegistry.getChannel(id));
    }

    // Resolver registration for modded containers

    /** Registers a resolver for a custom block-entity class. */
    public static <T extends BlockEntity> void registerContainerResolver(
            Class<T> clazz, Function<T, PersistentContainerKey> resolver) {
        SlotStateRegistry.registerBlockEntityResolver(clazz, resolver);
    }

    /** Registers a resolver for a custom entity class. */
    public static <T extends Entity> void registerEntityResolver(
            Class<T> clazz, Function<T, PersistentContainerKey> resolver) {
        SlotStateRegistry.registerEntityResolver(clazz, resolver);
    }

    // State that travels with a block's item

    private static final java.util.Set<Class<? extends net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity>>
            PORTABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        PORTABLE.add(net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.class);
    }

    /**
     * Makes a container block entity's slot state travel with its item, the way a
     * shulker box's does: when the block is broken the state goes with the item, and
     * when it is placed the state comes back.
     * Shulker boxes are registered by default. Declare at init.
     */
    public static void registerPortable(
            Class<? extends net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity> type) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("SlotState.registerPortable " + type.getName());
        PORTABLE.add(java.util.Objects.requireNonNull(type, "type"));
    }

    /** Whether a block entity's slot state travels with its item. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean isPortable(BlockEntity be) {
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
            if (PORTABLE.contains(c)) return true;
        }
        return false;
    }

    // Internal read and write, called from SlotStateChannel

    static <T> T read(SlotStateChannel<T> channel, Slot slot, @Nullable Player player) {
        Player viewer = resolvePlayer(slot, player);
        if (isClientSide(viewer)) {
            return SlotStateClientCache.read(channel, slot);
        }
        // A composite container (double chest) resolves this slot to the key of the
        // half that owns it and the index within that half. For a single-owner
        // container the local index is the global one.
        Optional<ResolvedSlot> resolved = SlotStateRegistry.resolve(slot.container, slot.getContainerSlot());
        if (resolved.isEmpty()) return channel.defaultValue();
        Tag tag = SlotStateServer.readTag(
                resolved.get().key(), viewer, channel.id(), resolved.get().localSlotIndex());
        if (tag == null) return channel.defaultValue();
        return SlotStateServer.decode(channel, tag);
    }

    static <T> void write(SlotStateChannel<T> channel, Slot slot,
                           @Nullable Player player, T value) {
        Player viewer = resolvePlayer(slot, player);
        if (isClientSide(viewer)) {
            writeFromClient(channel, slot, viewer, value);
            return;
        }
        // Store under the owning (half's) key at the owner-local index.
        Optional<ResolvedSlot> resolved = SlotStateRegistry.resolve(slot.container, slot.getContainerSlot());
        if (resolved.isEmpty()) return;
        writeResolved(channel, resolved.get(), viewer, value);
        if (viewer instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Server code wrote it, so no client has it yet. A SHARED value reaches every
            // viewer of the slot, the writer included; a PRIVATE one only its own viewer.
            if (channel.visibility() == SlotStateChannel.Visibility.SHARED) {
                com.trevlar.menukit.containers.state.SlotStateHooks.broadcastToViewers(
                        sp, resolved.get().key(), resolved.get().localSlotIndex(), channel, value, true);
            } else {
                com.trevlar.menukit.containers.state.SlotStateHooks.sendIfViewing(
                        sp, resolved.get().key(), resolved.get().localSlotIndex(), channel, value);
            }
        }
    }

    /** Stores a value, or removes the entry when it is the channel's default. Server side. */
    private static <T> void writeResolved(SlotStateChannel<T> channel, ResolvedSlot slot,
                                          @Nullable Player viewer, T value) {
        if (java.util.Objects.equals(value, channel.defaultValue())) {
            SlotStateServer.clearTag(slot.key(), viewer, channel.id(), slot.localSlotIndex());
        } else {
            SlotStateServer.writeTag(slot.key(), viewer, channel.id(), slot.localSlotIndex(),
                    SlotStateServer.encode(channel, value));
        }
    }

    /**
     * A client's write: shown at once from the local cache, and sent to the server,
     * which checks it and sends back its own value if it refuses. Sent only when the
     * server runs Containers, and only for a slot of the menu the player has open,
     * which the write names.
     */
    private static <T> void writeFromClient(SlotStateChannel<T> channel, Slot slot, @Nullable Player viewer, T value) {
        SlotStateClientCache.write(channel, slot.container, slot.getContainerSlot(), value);
        if (viewer == null || !com.trevlar.menukit.containers.network.Presence.serverHasContainers()) return;
        // The menu the server has open. The creative picker is client-only and has the
        // inventory menu's id; the server sees the inventory menu there.
        net.minecraft.world.inventory.AbstractContainerMenu open = viewer.containerMenu;
        net.minecraft.world.inventory.AbstractContainerMenu menu =
                open == null || (open != viewer.inventoryMenu && open.containerId == viewer.inventoryMenu.containerId)
                        ? viewer.inventoryMenu : open;
        int index = indexOn(menu, slot);
        if (index < 0) return; // not a slot of the open menu, so the server couldn't check it
        byte[] bytes;
        try {
            bytes = com.trevlar.menukit.containers.network.SlotStateWire.encode(channel, value, viewer.registryAccess());
        } catch (RuntimeException e) {
            LOGGER.warn("[SlotState] channel {} could not encode a value for the wire: {}", channel.id(), e.toString());
            return;
        }
        clientSender.accept(new com.trevlar.menukit.containers.network.SlotStateUpdateC2SPayload(
                menu.containerId, index, channel.id(), bytes));
    }

    static <T> T readPersistent(SlotStateChannel<T> channel, @Nullable Player player,
                                 PersistentContainerKey key, int containerSlotIndex) {
        Tag tag = SlotStateServer.readTag(key, player, channel.id(), containerSlotIndex);
        if (tag == null) return channel.defaultValue();
        return SlotStateServer.decode(channel, tag);
    }

    static <T> void writePersistent(SlotStateChannel<T> channel, @Nullable Player player,
                                     PersistentContainerKey key, int containerSlotIndex, T value) {
        writeResolved(channel, new ResolvedSlot(key, containerSlotIndex), player, value);
    }

    // Read and write by address

    /**
     * Reads this channel's value at the slot named by {@code address}. The address is
     * resolved to a live slot through the same created-slot resolver the window uses,
     * then read as a {@link Slot}. Returns the channel default when the address
     * resolves to no live slot in the viewer's open menu.
     *
     * <p>An {@link Address} names an identity, not a live slot, so turning it into one
     * needs the menu it currently appears in. That menu is the viewing player's
     * {@code containerMenu} ({@code player} when given, else the client player), so an
     * address read or write only works where the addressed slot is open. With no open
     * menu holding that slot (server automation with no viewer, say) there is nothing
     * to resolve and this returns the default. Menu-free server code uses the
     * {@code (Container, int)} and {@code (PersistentContainerKey, int)} overloads.
     */
    static <T> T readByAddress(SlotStateChannel<T> channel, @Nullable Player player, Address address) {
        Slot slot = resolveSlotByAddress(player, address);
        if (slot == null) return channel.defaultValue();
        return read(channel, slot, player);
    }

    /**
     * Writes this channel's value at the slot named by {@code address}, resolving it
     * exactly as {@link #readByAddress} does. Does nothing when the address resolves
     * to no live slot in the viewer's open menu.
     */
    static <T> void writeByAddress(SlotStateChannel<T> channel, @Nullable Player player,
                                   Address address, T value) {
        Slot slot = resolveSlotByAddress(player, address);
        if (slot == null) return;
        write(channel, slot, player, value);
    }

    /**
     * The live in-menu {@link Slot} an {@link Address} names in the viewing player's
     * open menu, or {@code null}. The viewer is {@code player} when given, else the
     * client player (render thread). A created slot resolves through
     * {@link CreatedSlotAdapter}, which returns the in-menu slot so a creative wrapper
     * still routes correctly. A vanilla slot is found by scanning the menu for the slot
     * whose address matches under {@link SlotAddresses#of}, the encoding the engine
     * mints with. A panel or element is not a slot and resolves to nothing.
     */
    private static @Nullable Slot resolveSlotByAddress(@Nullable Player player, Address address) {
        Player viewer = player != null ? player : tryClientPlayer();
        if (viewer == null) return null;
        net.minecraft.world.inventory.AbstractContainerMenu menu = viewer.containerMenu;
        if (menu == null) return null;
        switch (address.kind()) {
            case CREATED_SLOT -> {
                return CreatedSlotAdapter.INSTANCE.resolve(menu, address);
            }
            case VANILLA_SLOT -> {
                for (Slot s : menu.slots) {
                    if (SlotAddresses.of(menu, s).equals(address)) return s;
                }
                return null;
            }
            default -> { // PANEL or PANEL_ELEMENT, not a slot
                return null;
            }
        }
    }

    /**
     * The index on {@code menu} of the slot that stores what {@code slot} stores (same
     * container, same index; a creative wrapper is looked through), or -1.
     */
    private static int indexOn(net.minecraft.world.inventory.AbstractContainerMenu menu, Slot slot) {
        Slot target = com.trevlar.menukit.inject.Slots.target(slot);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = com.trevlar.menukit.inject.Slots.target(menu.slots.get(i));
            if (s == target || (s.container == target.container && s.getContainerSlot() == target.getContainerSlot())) {
                return i;
            }
        }
        return -1;
    }

    // Menu-free shared read

    /**
     * Server-side read of the SHARED value at {@code (container, containerSlotIndex)}
     * with no {@link Slot}, open menu or viewer, for automation (hopper, dropper,
     * dispenser) that touches a placed container by index. The server comes from the
     * container's block entity. Returns the channel default off the server, for a
     * container that can't be resolved, or for a PRIVATE channel, which has no viewer
     * on this path. A double chest resolves to the half that owns the slot.
     */
    static <T> T readShared(SlotStateChannel<T> channel, Container container, int containerSlotIndex) {
        MinecraftServer server = serverOf(container);
        if (server == null) return channel.defaultValue();
        Optional<ResolvedSlot> resolved = SlotStateRegistry.resolve(container, containerSlotIndex);
        if (resolved.isEmpty()) return channel.defaultValue();
        Tag tag = SlotStateServer.readTag(
                resolved.get().key(), null, server, channel.id(), resolved.get().localSlotIndex());
        if (tag == null) return channel.defaultValue();
        return SlotStateServer.decode(channel, tag);
    }

    /**
     * The server, from a placed container's level: a block entity (single chest) or a
     * composite of block-entity halves (double chest). Null off the server or for a
     * container that isn't a block entity.
     */
    private static @Nullable MinecraftServer serverOf(Container container) {
        BlockEntity be = blockEntityOf(container);
        if (be != null && be.getLevel() instanceof ServerLevel level) {
            return level.getServer();
        }
        return null;
    }

    /** The block entity behind a placed container: itself, or a composite's first half. */
    private static @Nullable BlockEntity blockEntityOf(Container container) {
        if (container instanceof BlockEntity be) return be;
        if (container instanceof CompoundContainer compound) {
            // Both halves share a level; the first half is enough to reach the server.
            Container first = ((CompoundContainerAccessor) (Object) compound).mk$getContainer1();
            if (first instanceof BlockEntity be) return be;
        }
        return null;
    }

    // Side detection

    /**
     * The viewing player: the explicit one if given, {@code inv.player} for an
     * {@link Inventory} container, otherwise the client player if on the render
     * thread. Can be null, in which case reads fall back to defaults.
     */
    private static @Nullable Player resolvePlayer(Slot slot, @Nullable Player explicit) {
        if (explicit != null) return explicit;
        if (slot.container instanceof Inventory inv) return inv.player;
        return tryClientPlayer();
    }

    private static boolean isClientSide(@Nullable Player viewer) {
        if (viewer != null) return viewer.level().isClientSide();
        // Fall back to thread-name detection when no viewer is available.
        return "Render thread".equals(Thread.currentThread().getName());
    }

    /**
     * The client player, if available. Used when the slot's container has no player
     * and the caller passed none, as for block entity or entity slots on the client.
     */
    private static @Nullable Player tryClientPlayer() {
        if (!"Render thread".equals(Thread.currentThread().getName())) return null;
        try {
            return clientPlayer.get();
        } catch (Throwable t) {
            return null;
        }
    }

    // The client's player, installed by MenuKitContainersClient. Not read here directly:
    // a method returning Minecraft.getInstance().player makes the verifier load
    // LocalPlayer with this class, and on a dedicated server that crashed every mod
    // registering a slot-state channel (found by the production join test).
    private static volatile java.util.function.Supplier<@Nullable Player> clientPlayer = () -> null;

    /** MenuKitContainersClient installs the client player lookup at client init. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void installClientPlayer(java.util.function.Supplier<@Nullable Player> lookup) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("SlotState.installClientPlayer");
        clientPlayer = java.util.Objects.requireNonNull(lookup, "lookup");
    }

    // The client's play-packet sender, installed by MenuKitContainersClient for the same reason: the
    // client networking class must not be named in common code.
    private static volatile java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload>
            clientSender = payload -> {};

    /** MenuKitContainersClient installs the client's packet sender at client init. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void installClientSender(
            java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> sender) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("SlotState.installClientSender");
        clientSender = java.util.Objects.requireNonNull(sender, "sender");
    }

    // Helpers for packet receivers

    /** Finds the slot in {@code menu} whose container resolves to {@code key}, at {@code containerSlotIndex}. */
    public static @Nullable Slot findSlotByContainerIndex(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            PersistentContainerKey key, int containerSlotIndex) {
        for (Slot s : menu.slots) {
            if (s.getContainerSlot() != containerSlotIndex) continue;
            Container container = s.container;
            Optional<PersistentContainerKey> resolved = SlotStateRegistry.resolve(container);
            if (resolved.isEmpty()) continue;
            if (resolved.get().equals(key)) return s;
        }
        return null;
    }
}
