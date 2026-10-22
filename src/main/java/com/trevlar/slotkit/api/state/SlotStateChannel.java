package com.trevlar.slotkit.api.state;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.window.PersistentContainerKey;
import com.trevlar.slotkit.api.window.SlotRef;

import com.mojang.serialization.Codec;
import com.trevlar.slotkit.api.window.Address;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * A typed per-slot state channel, the handle consumers use to read and write slot
 * state. Created with {@link SlotState#register(Identifier, Codec, StreamCodec, Object)}
 * (PRIVATE) or {@link SlotState#register(Identifier, Codec, StreamCodec, Object, Visibility)}.
 *
 * <p>A channel has two codecs. {@link #codec} is for persistence and encodes to NBT
 * through {@code NbtOps.INSTANCE}, so stored values are readable with vanilla's
 * {@code /data get}. {@link #streamCodec} is the compact binary form sent over the
 * network.
 *
 * <h2>State lives in the open menu; behavior does not</h2>
 *
 * The {@link Address} methods here ({@link #get(Address)}, {@link #set(Address, Object)}
 * and their explicit-viewer versions) work differently from how slot behavior is set
 * up, on purpose:
 *
 * <ul>
 *   <li>Behavior (gating, binding, mending, quick-move) is declared by address at
 *       init and applies as soon as a slot with that address appears. It lives in the
 *       engine, independent of any open menu.</li>
 *   <li>State read or written by address resolves against the viewer's currently
 *       open menu. If the address names no live slot in that menu, a read returns
 *       {@link #defaultValue} and a write does nothing. There is no address-based path
 *       for init-time or menu-free state.</li>
 * </ul>
 *
 * <p>For menu-free state, server automation, or a created slot's state at init time,
 * use the persistent-key overloads instead. They name storage directly, with
 * no open menu and no live slot:
 *
 * <ul>
 *   <li>{@link #get(Player, PersistentContainerKey, int)} and
 *       {@link #set(Player, PersistentContainerKey, int, Object)}: player-, block entity-
 *       or entity-scoped state through a {@link PersistentContainerKey} and a
 *       container-slot index (server only).</li>
 *   <li>{@link #get(Container, int)}: a SHARED value at a placed container by index,
 *       with no viewer, for hopper or dropper automation.</li>
 * </ul>
 *
 * <p>In short, use addresses for behavior and for state in an open menu, and the
 * persistent keys whenever there is no open menu to resolve against.
 *
 * <h2>Mode scope</h2>
 *
 * A channel is in effect in its {@link #scope()}: while the viewing player's mode is
 * outside it, reads give {@link #defaultValue} and a client's writes are neither shown
 * nor sent (the server refuses them too). Stored values are kept, and show again when
 * the mode comes back. Writes made by server code are stored whatever the mode. A read
 * with no viewing player (server code with no player at hand, a hopper seam) has no mode
 * to be in, so a {@code SURVIVAL} or {@code CREATIVE} channel reads its default there;
 * pass the player, or use a {@code BOTH} channel for state automation reads.
 */
public record SlotStateChannel<T>(
        Identifier id,
        Codec<T> codec,
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec,
        T defaultValue,
        Visibility visibility,
        CanWrite canWrite,
        ModeScope scope) {

    /**
     * Who may write this channel from a client. The server asks it for every
     * write a client sends, after checking that the write names the player's open menu
     * and a live slot on it, and refuses the write when it answers {@code false}. Writes
     * made by server code are not asked: the server is trusted.
     *
     * <p>By default a {@link Visibility#SHARED SHARED} channel denies every client
     * write ({@link #DENY}), since one player's write is another player's state. A
     * {@link Visibility#PRIVATE PRIVATE} channel lets a player write their own value on
     * any live slot of their open menu ({@link #ANY}).
     */
    @FunctionalInterface
    public interface CanWrite {
        /** Refuses every client write. The SHARED default. */
        CanWrite DENY = (player, slot) -> false;
        /** Allows a write on any live slot of the writer's open menu. The PRIVATE default. */
        CanWrite ANY = (player, slot) -> true;

        /** Whether {@code player} may write this channel at {@code slot} (a live slot of their open menu). */
        boolean allows(ServerPlayer player, SlotRef slot);
    }

    public SlotStateChannel {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(codec, "codec");
        java.util.Objects.requireNonNull(streamCodec, "streamCodec");
        java.util.Objects.requireNonNull(defaultValue, "defaultValue");
        java.util.Objects.requireNonNull(visibility, "visibility");
        java.util.Objects.requireNonNull(canWrite, "canWrite");
        java.util.Objects.requireNonNull(scope, "scope");
    }

    /**
     * A channel in both modes, with an explicit writer rule.
     *
     * @deprecated since 2.1.0, name the mode: the canonical constructor, with a {@code ModeScope}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public SlotStateChannel(Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue,
            Visibility visibility, CanWrite canWrite) {
        this(id, codec, streamCodec, defaultValue, visibility, canWrite, ModeScope.BOTH);
    }

    /**
     * A channel in both modes with its visibility's default writer rule ({@link CanWrite}).
     *
     * @deprecated since 2.1.0, name the mode: the canonical constructor, with a {@code ModeScope}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public SlotStateChannel(Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue, Visibility visibility) {
        this(id, codec, streamCodec, defaultValue, visibility,
                visibility == Visibility.SHARED ? CanWrite.DENY : CanWrite.ANY);
    }

    /**
     * Channel visibility.
     *
     * <ul>
     *   <li>PRIVATE (the default): each viewer has their own value, keyed by UUID.</li>
     *   <li>SHARED: one value per slot for all players, synced to every viewer, and
     *       written from a client only by those its {@link CanWrite} allows (nobody
     *       unless declared). For features that span players, like Inventory Plus's
     *       container locks: one player locks slot 5 and every player sees it
     *       locked.</li>
     * </ul>
     *
     * <p>SHARED only makes sense on containers that are both multi-player and
     * fixed-slot (a placed shulker, chest or barrel). It means nothing on the ender
     * chest, which is per-player anyway, and does not apply to bundles, which have no
     * fixed slots.
     */
    public enum Visibility { PRIVATE, SHARED }

    /**
     * Creates a PRIVATE channel, the default. Kept so existing
     * {@code new SlotStateChannel<>(id, codec, streamCodec, default)} calls (and the
     * four-argument {@link SlotState#register}) keep working unchanged.
     *
     * @deprecated since 2.1.0, name the mode: the canonical constructor, with a {@code ModeScope}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public SlotStateChannel(Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue) {
        this(id, codec, streamCodec, defaultValue, Visibility.PRIVATE);
    }

    // By address. Name the slot by its Address (Address.createdSlot,
    // SlotRef.address). Resolves the address to a live slot in the viewer's open
    // menu, then uses the Slot-keyed path below.

    /**
     * Reads this channel's value at the slot named by {@code address}, in the client
     * player's currently open menu. Same as {@link #get(Slot)}, but the slot is named
     * by {@link Address} instead of a live {@code Slot}. Returns {@link #defaultValue}
     * when the address names no live slot in the open menu (see
     * {@code SlotState.readByAddress}). Use {@link #get(Player, Address)} for an
     * explicit viewer, for example on the server.
     */
    public T get(Address address) {
        return SlotState.readByAddress(this, null, address);
    }

    /**
     * Reads this channel's value at the slot named by {@code address} for an explicit
     * {@code player}, resolving the slot in that player's open menu. The address
     * version of {@link #get(Player, Slot)}.
     */
    public T get(Player player, Address address) {
        return SlotState.readByAddress(this, player, address);
    }

    /**
     * Writes {@code value} at the slot named by {@code address}, in the client
     * player's open menu. The address version of {@link #set(Slot, Object)}. Does
     * nothing when the address names no live slot in the open menu.
     */
    public void set(Address address, T value) {
        SlotState.writeByAddress(this, null, address, value);
    }

    /**
     * Writes {@code value} at the slot named by {@code address} for an explicit
     * {@code player}. The address version of {@link #set(Player, Slot, Object)}.
     */
    public void set(Player player, Address address, T value) {
        SlotState.writeByAddress(this, player, address, value);
    }

    // By slot. For code that already holds the live Slot (a render hook, a click
    // handler, a veto's SlotRef.slot()). Public since 6.0.0.

    /**
     * Reads this channel's value at {@code slot}. Works on both sides when the slot
     * belongs to a player-scoped container ({@code Inventory},
     * {@code PlayerEnderChestContainer}). On the server, a block-entity or entity
     * slot with no player to resolve returns {@link #defaultValue}; pass the player
     * with {@link #get(Player, Slot)}. A creative slot wrapper is seen through.
     */
    public T get(Slot slot) {
        return SlotState.read(this, slot, null);
    }

    /**
     * Reads this channel's value at {@code slot} for an explicit player. Needed for
     * server-side reads on block entity or entity containers, where private state
     * depends on whose it is. SHARED channels ignore the player but still accept it.
     */
    public T get(Player player, Slot slot) {
        return SlotState.read(this, slot, player);
    }

    /**
     * Writes {@code value} at {@code slot}. See {@link #get(Slot)} for how each side
     * behaves. On the client the write goes to the server, which checks it. Writing
     * the default removes the entry.
     */
    public void set(Slot slot, T value) {
        SlotState.write(this, slot, null, value);
    }

    /** Writes {@code value} at {@code slot} for an explicit player. */
    public void set(Player player, Slot slot, T value) {
        SlotState.write(this, slot, player, value);
    }

    // Persistent keys, server only. Public for server automation: with no open menu
    // and no live slot there is no Address, so a persistent key and container-slot
    // index name the storage directly. Addresses cover slots in an open menu; this
    // is the documented exception for menu-free code.

    /**
     * Reads this channel's value straight from persistent storage. Server only; the
     * client cannot build a persistent key outside an open menu.
     *
     * @param player for per-player private containers (block entity, entity). May be
     *               {@code null} for player-scoped keys, where the UUID is in the key,
     *               or for SHARED channels.
     */
    public T get(Player player, PersistentContainerKey key, int containerSlotIndex) {
        return SlotState.readPersistent(this, player, key, containerSlotIndex);
    }

    /** Writes straight to persistent storage. Server only. */
    public void set(Player player, PersistentContainerKey key, int containerSlotIndex, T value) {
        SlotState.writePersistent(this, player, key, containerSlotIndex, value);
    }

    // Menu-free shared read, server only. Also public for server automation that
    // touches a placed container by index with no open menu or viewer (see the
    // persistent-key methods above).

    /**
     * Reads the SHARED value at {@code (container, containerSlotIndex)} with no
     * {@link Slot}, open menu or viewer, for automation (hopper, dropper, dispenser)
     * that touches a placed container directly by index. The library resolves the
     * container internally, including composite containers, so a double chest
     * resolves to the half that owns the slot.
     *
     * <p>Returns {@link #defaultValue} off the server, for a container that can't be
     * resolved, or for a PRIVATE channel, since a private value belongs to a viewer
     * and this path has none. Read only: writes still go through the owner or menu
     * (and shared writes are broadcast). The consumer builds its own enforcement on
     * this read, such as blocking a hopper extract; the library only reports state.
     */
    public T get(Container container, int containerSlotIndex) {
        return SlotState.readShared(this, container, containerSlotIndex);
    }
}
