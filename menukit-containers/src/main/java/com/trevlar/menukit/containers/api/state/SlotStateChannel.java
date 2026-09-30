package com.trevlar.menukit.containers.api.state;

import com.trevlar.menukit.api.window.PersistentContainerKey;
import com.trevlar.menukit.api.window.SlotRef;

import com.mojang.serialization.Codec;
import com.trevlar.menukit.api.window.Address;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * Typed per-slot state channel, the consumer-facing handle for reading and
 * writing slot state. Created via
 * {@link SlotState#register(Identifier, Codec, StreamCodec, Object)} (PRIVATE)
 * or {@link SlotState#register(Identifier, Codec, StreamCodec, Object, Visibility)}.
 *
 * <p>Dual-codec design (per THESIS.md principle 6): {@link #codec} is NBT-bound
 * for persistence (encoded to {@code Tag} via {@code NbtOps.INSTANCE}),
 * {@link #streamCodec} is binary for wire transport. Persisted values are
 * legible via vanilla's {@code /data get}; wire traffic stays lightweight.
 *
 * <h2>State is MENU-RESIDENT, unlike behavior</h2>
 *
 * The by-{@link Address} surface here ({@link #get(Address)} / {@link #set(Address, Object)}
 * and their explicit-viewer twins) is a deliberate asymmetry with how slot
 * <em>behavior</em> is armed, and the boundary matters:
 *
 * <ul>
 *   <li><b>Behavior</b> (gating, binding, mending, quick-move) is declared by
 *       Address <em>at init</em> and resolves the moment a slot with that address
 *       appears, it lives in the engine, independent of any open menu.</li>
 *   <li><b>State</b> read/written by Address resolves against the <em>viewer's
 *       currently-open menu</em>. If the address names no live slot in that open
 *       menu, a read returns {@link #defaultValue} and a write is a no-op. There is
 *       NO by-Address path for init-time or menu-free state.</li>
 * </ul>
 *
 * <p><b>For menu-free / server-automation / init-time created-slot state</b>, use the
 * persistent-key overloads instead of an Address, they name storage directly with no
 * open menu and no live slot:
 *
 * <ul>
 *   <li>{@link #get(Player, PersistentContainerKey, int)} /
 *       {@link #set(Player, PersistentContainerKey, int, Object)}, player- or
 *       BE/entity-scoped state via a {@link PersistentContainerKey} + container-slot
 *       index (server-only).</li>
 *   <li>{@link #get(Container, int)}, a SHARED value at a placed container by index,
 *       with no viewer, for hopper/dropper-style automation (§0055).</li>
 * </ul>
 *
 * <p>So: address slots for behavior and for in-menu state; reach for the
 * persistent-key recipe whenever there is no open menu to resolve against.
 *
 * <p>See {@code menukit/Design Docs/Phase 12/M1_PER_SLOT_STATE.md} §4.4.
 */
public record SlotStateChannel<T>(
        Identifier id,
        Codec<T> codec,
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec,
        T defaultValue,
        Visibility visibility,
        CanWrite canWrite) {

    /**
     * Who may write this channel from a client (§0067). The server asks it for every
     * write a client sends, after checking that the write names the player's open menu
     * and a live slot on it, and refuses the write when it answers {@code false}. Writes
     * made by server code are not asked: the server is trusted.
     *
     * <p>Defaults: a {@link Visibility#SHARED SHARED} channel denies every client write
     * ({@link #DENY}), since one player's write is another player's state; a
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
    }

    /** A channel with its visibility's default writer rule ({@link CanWrite}). */
    public SlotStateChannel(Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue, Visibility visibility) {
        this(id, codec, streamCodec, defaultValue, visibility,
                visibility == Visibility.SHARED ? CanWrite.DENY : CanWrite.ANY);
    }

    /**
     * Channel visibility (§0049).
     *
     * <ul>
     *   <li><b>PRIVATE</b> (default), each viewer has their own value, keyed by
     *       UUID. The shipped per-player model (§0034).</li>
     *   <li><b>SHARED</b>: one player-agnostic value per slot, synced to every
     *       viewer, and written from a client only by whom its {@link CanWrite}
     *       allows (§0067; nobody unless declared). For cross-player features like
     *       Inventory Max's Container Locks (one player locks slot 5, every player
     *       sees it locked).</li>
     * </ul>
     *
     * <p>SHARED is only meaningful on containers that are both multi-player and
     * fixed-slot (placed shulker / chest / barrel). It is degenerate on the
     * ender chest (inherently per-player) and inapplicable to bundles (no fixed
     * slots), see §0049 bounds.
     */
    public enum Visibility { PRIVATE, SHARED }

    /**
     * Backward-compatible constructor, produces a PRIVATE channel. Keeps every
     * existing {@code new SlotStateChannel<>(id, codec, streamCodec, default)}
     * call site (and the 4-arg {@link SlotState#register}) byte-for-byte
     * unchanged (§0049: private is the default).
     */
    public SlotStateChannel(Identifier id, Codec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, T defaultValue) {
        this(id, codec, streamCodec, defaultValue, Visibility.PRIVATE);
    }

    // ── By address ──────────────────────────────────────────────────────
    // Name the slot by its Address (Address.createdSlot, SlotRef.address). Resolves
    // the address to a live slot in the viewer's open menu and delegates to the
    // Slot-keyed path below.

    /**
     * Reads this channel's value at the slot named by {@code address}, in the
     * client player's currently open menu. The address-keyed counterpart of
     * {@link #get(Slot)}, the only difference is identity-by-{@link Address}
     * instead of a live {@code Slot}. Returns {@link #defaultValue} when the
     * address names no live slot in the open menu (see
     * {@code SlotState.readByAddress} for the resolution boundary). Use
     * {@link #get(Player, Address)} for an explicit viewer (e.g. server-side).
     */
    public T get(Address address) {
        return SlotState.readByAddress(this, null, address);
    }

    /**
     * Reads this channel's value at the slot named by {@code address} for an
     * explicit {@code player}, resolves the slot in that player's open menu.
     * The address-keyed counterpart of {@link #get(Player, Slot)}.
     */
    public T get(Player player, Address address) {
        return SlotState.readByAddress(this, player, address);
    }

    /**
     * Writes {@code value} at the slot named by {@code address}, in the client
     * player's open menu. The address-keyed counterpart of {@link #set(Slot, Object)};
     * a no-op when the address names no live slot in the open menu.
     */
    public void set(Address address, T value) {
        SlotState.writeByAddress(this, null, address, value);
    }

    /**
     * Writes {@code value} at the slot named by {@code address} for an explicit
     * {@code player}. The address-keyed counterpart of
     * {@link #set(Player, Slot, Object)}.
     */
    public void set(Player player, Address address, T value) {
        SlotState.writeByAddress(this, player, address, value);
    }

    // ── By slot ─────────────────────────────────────────────────────────
    // For code that already holds the live Slot (a render hook, a click handler, a
    // veto's SlotRef.slot()). Public since 6.0.0 (§0068).

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
     * Reads this channel's value at {@code slot} with explicit player context.
     * Required for server-side reads on BE/entity-backed containers (the
     * per-player-private model needs to know whose state to read; SHARED
     * channels ignore the player but the argument is still accepted).
     */
    public T get(Player player, Slot slot) {
        return SlotState.read(this, slot, player);
    }

    /**
     * Writes {@code value} at {@code slot}. See {@link #get(Slot)} for side
     * semantics. On the client the write goes to the server, which judges it
     * (§0067); writing the default removes the entry.
     */
    public void set(Slot slot, T value) {
        SlotState.write(this, slot, null, value);
    }

    /** Writes {@code value} at {@code slot} with explicit player context. */
    public void set(Player player, Slot slot, T value) {
        SlotState.write(this, slot, player, value);
    }

    // ── Slot-less (persistent-key) API, server-only ────────────────────
    // Kept public as an explicit server-automation seam: these legitimately have
    // no Address (no open menu, no live slot), a persistent key + container-slot
    // index names storage directly. The address-only rule covers menu-resident
    // slots; menu-free automation is its documented exception.

    /**
     * Reads this channel's value from persistent storage directly.
     * Server-only; the client cannot synthesize a persistent key outside a
     * menu session.
     *
     * @param player for per-player-private containers (BE, entity). May be
     *               {@code null} for player-scoped keys where the UUID is in the
     *               key itself, or for SHARED channels (player-agnostic).
     */
    public T get(Player player, PersistentContainerKey key, int containerSlotIndex) {
        return SlotState.readPersistent(this, player, key, containerSlotIndex);
    }

    /** Writes to persistent storage directly. Server-only. */
    public void set(Player player, PersistentContainerKey key, int containerSlotIndex, T value) {
        SlotState.writePersistent(this, player, key, containerSlotIndex, value);
    }

    // ── Menu-free shared read (§0055), server-only ─────────────────────
    // Also kept public as a server-automation seam (no Address, no open menu / no
    // viewer): a placed container touched directly by index. See the
    // persistent-key block above for why these are the documented exceptions to
    // the address-only rule.

    /**
     * Reads the <b>SHARED</b> value at {@code (container, containerSlotIndex)}
     * with no {@link Slot}, no open menu, and no viewer, for automation
     * (hopper / dropper / dispenser) that touches a placed container directly by
     * index. The library resolves the container internally (the resolver stays
     * internal) and composes with composite resolution, so a double chest
     * resolves to its owning half.
     *
     * <p>Returns {@link #defaultValue} off-server, for an unresolvable container,
     * or for a PRIVATE channel, a private value is viewer-scoped and has no
     * viewer on this path, so this is the shared-read primitive (§0055).
     * Read-only: writes stay owner/menu-driven (and shared writes broadcast,
     * §0049). The consumer composes this read into its own enforcement (e.g.
     * blocking a hopper extract), the library reports state, it does not enforce.
     */
    public T get(Container container, int containerSlotIndex) {
        return SlotState.readShared(this, container, containerSlotIndex);
    }
}
