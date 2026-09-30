package com.trevlar.menukit.window;

import com.trevlar.menukit.mixin.CompoundContainerAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Which persistent container a {@link Container} is, and so which slot of it a
 * vanilla slot is, independent of the menu it is seen through. This is what makes
 * a chest slot's {@link Address} the same whether it is reached through its menu,
 * by a hopper, or after a reopen.
 *
 * <p>Lives in MenuKit, both sides (§0062). Until 6.0.0 Containers installed it
 * through a port, so a vanilla slot's address changed depending on whether
 * Containers was loaded.
 *
 * <h3>Resolution order</h3>
 *
 * <ol>
 *   <li>Extensions: containers a library recognises as its own. Containers
 *       registers one for its key-carrying storages.</li>
 *   <li>The player's inventory.</li>
 *   <li>A block entity: a registered resolver for its class, else its position.
 *       (Until 6.0.0 a registered block-entity resolver was never reached: the
 *       position case returned first.)</li>
 *   <li>An entity (the minecart family is its own container): a registered
 *       resolver for its class, else its UUID.</li>
 * </ol>
 *
 * A player's ender chest resolves to its player (the player records it as it is
 * built, since the container has no owner link; §0067). Horse-family storage has
 * no link back to its entity and falls through to no identity, so its slots are
 * addressed through the menu. A double chest splits its global index to the half
 * that owns the slot.
 */
@ApiStatus.Internal
public final class ContainerIdentity {

    private ContainerIdentity() {}

    private static final List<Function<Container, Optional<PersistentContainerKey>>> EXTENSIONS =
            new CopyOnWriteArrayList<>();
    private static final Map<Class<? extends BlockEntity>, Function<? extends BlockEntity, PersistentContainerKey>>
            BE_RESOLVERS = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Entity>, Function<? extends Entity, PersistentContainerKey>>
            ENTITY_RESOLVERS = new ConcurrentHashMap<>();

    /** A library's own containers (Containers' key-carrying storages); consulted first. */
    public static void extend(Function<Container, Optional<PersistentContainerKey>> extension) {
        com.trevlar.menukit.window.Declarations.requireOpen("ContainerIdentity.extend");
        EXTENSIONS.add(Objects.requireNonNull(extension, "extension"));
    }

    /** A modded block entity class resolves through {@code resolver} instead of its position. */
    // A player's ender chest container carries no link to its player, so the player
    // records it as it is built (MKEnderChestOwnerMixin). Weak keys: the container goes
    // with its player. Server side is what reads it; the client's ender chest menu is
    // over a plain container.
    private static final Map<Container, UUID> ENDER_OWNERS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Records whose ender chest {@code container} is. Called as a player is built. */
    public static void recordEnderChestOwner(Container container, UUID owner) {
        ENDER_OWNERS.put(Objects.requireNonNull(container, "container"), Objects.requireNonNull(owner, "owner"));
    }

    public static <T extends BlockEntity> void registerBlockEntityResolver(
            Class<T> type, Function<T, PersistentContainerKey> resolver) {
        com.trevlar.menukit.window.Declarations.requireOpen("a block-entity container resolver for " + type.getName());
        if (BE_RESOLVERS.putIfAbsent(type, resolver) != null) {
            throw new IllegalStateException("A block-entity container resolver for " + type.getName() + " is already registered");
        }
    }

    /** A modded entity class resolves through {@code resolver} instead of its UUID. */
    public static <T extends Entity> void registerEntityResolver(
            Class<T> type, Function<T, PersistentContainerKey> resolver) {
        com.trevlar.menukit.window.Declarations.requireOpen("an entity container resolver for " + type.getName());
        if (ENTITY_RESOLVERS.putIfAbsent(type, resolver) != null) {
            throw new IllegalStateException("An entity container resolver for " + type.getName() + " is already registered");
        }
    }

    /** One slot of {@code container}: its owner and its index within that owner. */
    public static Optional<ResolvedSlot> resolve(Container container, int containerSlotIndex) {
        if (container == null) return Optional.empty();
        if (container instanceof CompoundContainer compound) {
            // A double chest: the first half owns [0, firstSize), the second the rest,
            // each a real placed chest with its own key and local index.
            CompoundContainerAccessor halves = (CompoundContainerAccessor) (Object) compound;
            Container first = halves.mk$getContainer1();
            int firstSize = first.getContainerSize();
            if (containerSlotIndex < firstSize) {
                return resolve(first).map(key -> new ResolvedSlot(key, containerSlotIndex));
            }
            return resolve(halves.mk$getContainer2())
                    .map(key -> new ResolvedSlot(key, containerSlotIndex - firstSize));
        }
        return resolve(container).map(key -> new ResolvedSlot(key, containerSlotIndex));
    }

    /** The persistent container {@code container} is, when it has one. */
    public static Optional<PersistentContainerKey> resolve(Container container) {
        if (container == null) return Optional.empty();
        for (Function<Container, Optional<PersistentContainerKey>> extension : EXTENSIONS) {
            Optional<PersistentContainerKey> own = extension.apply(container);
            if (own.isPresent()) return own;
        }
        if (container instanceof Inventory inv) {
            return Optional.of(new PersistentContainerKey.PlayerInventory(inv.player.getUUID()));
        }
        if (container instanceof PlayerEnderChestContainer) {
            UUID owner = ENDER_OWNERS.get(container);
            return owner == null ? Optional.empty() : Optional.of(new PersistentContainerKey.EnderChest(owner));
        }
        if (container instanceof BlockEntity be) {
            Optional<PersistentContainerKey> registered = byClass(BE_RESOLVERS, be);
            if (registered.isPresent()) return registered;
            if (be.getLevel() == null) return Optional.empty();
            BlockPos pos = be.getBlockPos();
            ResourceKey<Level> dim = be.getLevel().dimension();
            return Optional.of(new PersistentContainerKey.BlockEntityKey(pos, dim));
        }
        if (container instanceof Entity entity) {
            Optional<PersistentContainerKey> registered = byClass(ENTITY_RESOLVERS, entity);
            if (registered.isPresent()) return registered;
            return Optional.of(new PersistentContainerKey.EntityKey(entity.getUUID()));
        }
        return Optional.empty();
    }

    /** A registered resolver's answer for {@code target}'s class. */
    public static Optional<PersistentContainerKey> resolveBlockEntity(BlockEntity target) {
        return byClass(BE_RESOLVERS, target);
    }

    /** A registered resolver's answer for {@code target}'s class. */
    public static Optional<PersistentContainerKey> resolveEntity(Entity target) {
        return byClass(ENTITY_RESOLVERS, target);
    }

    /**
     * The resolver registered for {@code target}'s own class, else its nearest
     * superclass with one: the most specific wins, the same answer whatever order
     * the resolvers were registered in.
     */
    @SuppressWarnings("unchecked")
    private static <B, T extends B> Optional<PersistentContainerKey> byClass(
            Map<Class<? extends B>, Function<? extends B, PersistentContainerKey>> resolvers, T target) {
        if (resolvers.isEmpty()) return Optional.empty();
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            Function<? extends B, PersistentContainerKey> fn = resolvers.get(c);
            if (fn != null) return Optional.ofNullable(((Function<T, PersistentContainerKey>) fn).apply(target));
        }
        return Optional.empty();
    }

    // ── The text a key becomes in an address ───────────────────────────────

    /** A slot's owner as the scope id an {@link Address} carries, and its local index. */
    public record Scoped(String scopeId, int localIndex) {}

    /** {@code container}'s slot as an address scope, when the container has an identity. */
    public static Optional<Scoped> scope(Container container, int containerSlotIndex) {
        return resolve(container, containerSlotIndex)
                .map(rs -> new Scoped(stableId(rs.key()), rs.localSlotIndex()));
    }

    // Built once per key and reused, since addresses are minted per slot per frame.
    // ponytail: bounded by clearing at 4096 keys; an LRU if a server ever churns more.
    private static final int MAX_IDS = 4096;
    private static final Map<PersistentContainerKey, String> IDS = new ConcurrentHashMap<>();

    /** A deterministic, side-stable string for a key. */
    public static String stableId(PersistentContainerKey key) {
        String id = IDS.get(key);
        if (id != null) return id;
        if (IDS.size() >= MAX_IDS) IDS.clear();
        id = buildId(key);
        IDS.put(key, id);
        return id;
    }

    private static String buildId(PersistentContainerKey key) {
        return switch (key) {
            case PersistentContainerKey.PlayerInventory p -> "player:" + p.playerId();
            case PersistentContainerKey.EnderChest e -> "ender:" + e.playerId();
            case PersistentContainerKey.BlockEntityKey b -> {
                BlockPos pos = b.pos();
                yield "be:" + b.dimension() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            }
            case PersistentContainerKey.EntityKey en -> "entity:" + en.entityId();
            case PersistentContainerKey.Modded m -> "modded:" + m.resolverId() + ":" + m.payload();
        };
    }
}
