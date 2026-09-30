package com.trevlar.menukit.containers.state;

import com.mojang.serialization.Codec;
import com.trevlar.menukit.containers.core.KeyedStorages;
import com.trevlar.menukit.window.PersistentContainerKey;
import com.trevlar.menukit.containers.core.SlotStateChannel;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Server-side persistence facade. Resolves a {@link PersistentContainerKey} and the
 * viewing player to the attachment owner (player, block entity, entity) and reads or
 * writes the library's {@link SlotStateBag}.
 *
 * <p><b>Reads never write</b> (§0067): a read looks at what is attached and answers
 * {@code null} when nothing is, so opening a container, or a snapshot of one, attaches
 * nothing. Only a write creates an owner's bag, and a write of the channel's default
 * removes the entry instead ({@link #clearTag}).
 */
@ApiStatus.Internal
public final class SlotStateServer {

    private SlotStateServer() {}

    // ── Codec helpers ───────────────────────────────────────────────────

    /** The canonical stored form of {@code value}. Throws if the channel's codec cannot encode it. */
    public static <T> Tag encode(SlotStateChannel<T> channel, T value) {
        Codec<T> codec = channel.codec();
        return codec.encodeStart(NbtOps.INSTANCE, value)
                    .getOrThrow(err -> new IllegalStateException(
                            "Codec failed to encode value for channel " + channel.id() + ": " + err));
    }

    /** The canonical stored form of {@code value}, or empty when the channel's codec refuses it. */
    public static <T> Optional<Tag> tryEncode(SlotStateChannel<T> channel, T value) {
        return channel.codec().encodeStart(NbtOps.INSTANCE, value).result();
    }

    /** The value a stored tag holds, or empty when it no longer parses (never throws). */
    public static <T> Optional<T> tryDecode(SlotStateChannel<T> channel, Tag tag) {
        return channel.codec().parse(NbtOps.INSTANCE, tag).result();
    }

    /** The value a stored tag holds, or the channel's default when it no longer parses. */
    public static <T> T decode(SlotStateChannel<T> channel, Tag tag) {
        return tryDecode(channel, tag).orElse(channel.defaultValue());
    }

    // ── Core read/write ─────────────────────────────────────────────────

    /**
     * The stored tag for a channel, key and slot, or {@code null} if none is stored, the
     * key cannot be resolved, or its owner is not reachable (player offline, chunk
     * unloaded). Attaches nothing.
     */
    public static @Nullable Tag readTag(PersistentContainerKey key, @Nullable Player viewer,
                                         Identifier channelId, int containerSlotIndex) {
        return readTag(key, viewer, null, channelId, containerSlotIndex);
    }

    /**
     * §0050: the menu-free read, with {@code explicitServer} when there is no viewer to
     * derive the server from (automation reading a placed container by index).
     */
    public static @Nullable Tag readTag(PersistentContainerKey key, @Nullable Player viewer,
                                         @Nullable MinecraftServer explicitServer,
                                         Identifier channelId, int containerSlotIndex) {
        SlotStateBag bag = resolveBag(key, viewer, false, isShared(channelId), explicitServer);
        return bag == null ? null : bag.read(channelId, containerSlotIndex);
    }

    /** Writes a tag for a channel, key and slot. False if the owner could not be resolved. */
    public static boolean writeTag(PersistentContainerKey key, @Nullable Player viewer,
                                    Identifier channelId, int containerSlotIndex, Tag value) {
        SlotStateBag bag = resolveBag(key, viewer, true, isShared(channelId), null);
        if (bag == null) return false;
        bag.write(channelId, containerSlotIndex, value);
        return true;
    }

    /** Removes the stored entry for a channel, key and slot. Attaches nothing when there is none. */
    public static void clearTag(PersistentContainerKey key, @Nullable Player viewer,
                                Identifier channelId, int containerSlotIndex) {
        if (readTag(key, viewer, channelId, containerSlotIndex) == null) return;
        SlotStateBag bag = resolveBag(key, viewer, true, isShared(channelId), null);
        if (bag != null) bag.clear(channelId, containerSlotIndex);
    }

    /** §0049: true if the channel with this id is registered SHARED. */
    private static boolean isShared(Identifier channelId) {
        SlotStateChannel<?> ch = SlotStateRegistry.getChannel(channelId);
        return ch != null && ch.visibility() == SlotStateChannel.Visibility.SHARED;
    }

    // ── Owner resolution ────────────────────────────────────────────────

    /**
     * An owner's attached value: for a read, only what is attached ({@code null} when
     * nothing is); for a write, a FRESH copy set back with {@code setAttached}. Fabric
     * only tracks an attachment updated to a new value, so in-place mutation saves but
     * fails to round-trip on load (docs.fabricmc.net/develop/data-attachments).
     */
    private static <A> @Nullable A attached(AttachmentTarget owner, AttachmentType<A> type, boolean forWrite,
                                            Function<A, A> copy, Supplier<A> fresh) {
        A current = owner.getAttached(type);
        if (!forWrite) return current;
        A next = current != null ? copy.apply(current) : fresh.get();
        owner.setAttached(type, next);
        return next;
    }

    private static @Nullable SlotStateBag resolveBag(PersistentContainerKey key, @Nullable Player viewer,
                                                     boolean forWrite, boolean shared,
                                                     @Nullable MinecraftServer explicitServer) {
        // §0050: prefer an explicit server (menu-free reads carry no viewer to derive
        // it from); otherwise derive it from the viewer.
        MinecraftServer server = explicitServer;
        if (server == null && viewer instanceof ServerPlayer sp) {
            Level lvl = sp.level();
            if (lvl instanceof ServerLevel sl) server = sl.getServer();
        }

        // Player-scoped keys resolve the owner via resolvePlayer (prefers the held
        // viewer): at connection JOIN the player is not in the player list yet (§0045).
        if (key instanceof PersistentContainerKey.PlayerInventory pi) {
            ServerPlayer target = resolvePlayer(server, viewer, pi.playerId());
            return target == null ? null : attached(target, SlotStateAttachments.PLAYER_INVENTORY, forWrite,
                    b -> new SlotStateBag(b.backing().copy()), SlotStateBag::new);
        }

        if (key instanceof PersistentContainerKey.EnderChest ec) {
            ServerPlayer target = resolvePlayer(server, viewer, ec.playerId());
            return target == null ? null : attached(target, SlotStateAttachments.ENDER_CHEST, forWrite,
                    b -> new SlotStateBag(b.backing().copy()), SlotStateBag::new);
        }

        if (key instanceof PersistentContainerKey.BlockEntityKey bek) {
            if (server == null) return null;
            ServerLevel level = server.getLevel(bek.dimension());
            if (level == null) return null;
            BlockEntity be = level.getBlockEntity(bek.pos());
            if (be == null) return null;
            UUID viewerId = shared ? null : (viewer != null ? viewer.getUUID() : null);
            if (!shared && viewerId == null) return null; // PRIVATE needs a known viewer
            PerPlayerSlotStateBag perPlayer = attached(be, SlotStateAttachments.BLOCK_ENTITY, forWrite,
                    b -> new PerPlayerSlotStateBag(b.backing().copy()), PerPlayerSlotStateBag::new);
            if (perPlayer == null) return null;
            if (forWrite) be.setChanged(); // saved with the chunk
            return select(perPlayer, forWrite, shared, viewerId);
        }

        if (key instanceof PersistentContainerKey.EntityKey ek) {
            if (server == null) return null;
            // EntityKey carries no dimension (minecarts cross them); search every level.
            Entity entity = server.overworld().getEntityInAnyDimension(ek.entityId());
            if (entity == null) return null;
            UUID viewerId = shared ? null : (viewer != null ? viewer.getUUID() : null);
            if (!shared && viewerId == null) return null;
            PerPlayerSlotStateBag perPlayer = attached(entity, SlotStateAttachments.ENTITY, forWrite,
                    b -> new PerPlayerSlotStateBag(b.backing().copy()), PerPlayerSlotStateBag::new);
            if (perPlayer == null) return null;
            return select(perPlayer, forWrite, shared, viewerId);
        }

        if (key instanceof PersistentContainerKey.Modded modded) {
            // §0045: player-scoped registered slots (pockets, equipment). Keys made by
            // KeyedStorages.player(...) carry the owning player; other Modded keys have
            // no library-known owner and resolve to nothing.
            CompoundTag payload = modded.payload();
            if (!KeyedStorages.SCOPE_PLAYER.equals(payload.getStringOr(KeyedStorages.SCOPE_KEY, ""))) return null;
            UUID ownerId;
            try {
                ownerId = UUID.fromString(payload.getStringOr(KeyedStorages.OWNER_KEY, ""));
            } catch (IllegalArgumentException e) {
                return null;
            }
            ServerPlayer target = resolvePlayer(server, viewer, ownerId);
            if (target == null) return null;
            NamespacedSlotStateBag bags = attached(target, SlotStateAttachments.MODDED_PLAYER, forWrite,
                    b -> new NamespacedSlotStateBag(b.backing().copy()), NamespacedSlotStateBag::new);
            if (bags == null) return null;
            return forWrite ? bags.getOrCreate(modded.resolverId()) : bags.get(modded.resolverId());
        }

        return null;
    }

    /** The shared or per-viewer bag inside a block entity's or entity's bag: created only for a write. */
    private static @Nullable SlotStateBag select(PerPlayerSlotStateBag perPlayer, boolean forWrite,
                                                 boolean shared, @Nullable UUID viewerId) {
        if (shared) return forWrite ? perPlayer.getOrCreateShared() : perPlayer.getShared();
        return forWrite ? perPlayer.getOrCreate(viewerId) : perPlayer.get(viewerId);
    }

    /**
     * The owning player for a player-scoped key. Prefers the viewer already held when
     * its UUID matches: at connection JOIN the player is not in the player list yet.
     */
    private static @Nullable ServerPlayer resolvePlayer(@Nullable MinecraftServer server,
                                                        @Nullable Player viewer, UUID ownerId) {
        if (viewer instanceof ServerPlayer sp && sp.getUUID().equals(ownerId)) return sp;
        return server == null ? null : server.getPlayerList().getPlayer(ownerId);
    }
}
