package com.trevlar.menukit.containers.state;

import com.trevlar.menukit.window.ContainerIdentity;
import com.trevlar.menukit.window.ResolvedSlot;

import com.trevlar.menukit.window.PersistentContainerKey;
import com.trevlar.menukit.containers.core.SlotStateChannel;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal registry for M1 channels + container resolvers. Not public API —
 * consumers reach it indirectly via
 * {@link com.trevlar.menukit.containers.core.MKSlotState}.
 */
@ApiStatus.Internal
public final class SlotStateRegistry {

    private SlotStateRegistry() {}

    // ── Channels ────────────────────────────────────────────────────────

    private static final Map<Identifier, SlotStateChannel<?>> CHANNELS = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> @Nullable SlotStateChannel<T> getChannelTyped(Identifier id) {
        return (SlotStateChannel<T>) CHANNELS.get(id);
    }

    public static @Nullable SlotStateChannel<?> getChannel(Identifier id) {
        return CHANNELS.get(id);
    }

    public static void registerChannel(SlotStateChannel<?> channel) {
        com.trevlar.menukit.window.Declarations.requireOpen("a slot-state channel " + channel.id());
        CHANNELS.put(channel.id(), channel);
    }

    public static Iterable<SlotStateChannel<?>> allChannels() {
        return CHANNELS.values();
    }

    // ── Container resolvers ─────────────────────────────────────────────
    //
    // Container identity lives in MenuKit since 6.0.0 (§0062, ContainerIdentity);
    // these delegate so slot state and addressing agree on every key.

    public static <T extends BlockEntity> void registerBlockEntityResolver(
            Class<T> clazz, Function<T, PersistentContainerKey> resolver) {
        ContainerIdentity.registerBlockEntityResolver(clazz, resolver);
    }

    public static <T extends Entity> void registerEntityResolver(
            Class<T> clazz, Function<T, PersistentContainerKey> resolver) {
        ContainerIdentity.registerEntityResolver(clazz, resolver);
    }

    public static <T extends BlockEntity> Optional<PersistentContainerKey> resolveBlockEntity(T be) {
        return ContainerIdentity.resolveBlockEntity(be);
    }

    public static <T extends Entity> Optional<PersistentContainerKey> resolveEntity(T entity) {
        return ContainerIdentity.resolveEntity(entity);
    }

    /** The persistent key of {@code container}, when it has one. */
    public static Optional<PersistentContainerKey> resolve(Container container) {
        return ContainerIdentity.resolve(container);
    }

    /** One slot of {@code container}: its owner and its index within that owner. */
    public static Optional<ResolvedSlot> resolve(Container container, int containerSlotIndex) {
        return ContainerIdentity.resolve(container, containerSlotIndex);
    }
}
