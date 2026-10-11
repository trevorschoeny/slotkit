package com.trevlar.menukit.containers.state;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * The data component that carries per-slot state between a block and its item.
 *
 * <p>Since 1.20.5 only declared data components travel between block and item. A
 * Fabric attachment, where slot state lives, does not, so per-slot state on a
 * shulker box would be lost the moment the box is broken. {@link #PORTABLE_SLOT_STATE}
 * carries the whole per-block-entity bag ({@link PerPlayerSlotStateBag}), meaning
 * every registered channel's per-slot, per-player state. A mixin on the shulker block
 * entity writes it in {@code collectImplicitComponents} (block entity to item) and
 * restores it in {@code applyImplicitComponents} (item to block entity), on the same
 * vanilla path the container contents take.
 *
 * <p>The component is generic. The bag round-trips through one
 * {@link PerPlayerSlotStateBag#CODEC}, so it carries whatever channels are present
 * with no schema of its own. A lock flag is a consumer-registered channel that lands
 * in this bag like any other: the library provides the transport, and consumers
 * decide what travels. There is deliberately no {@code menukit:slot_locks} here.
 *
 * <p>Persistent only: it travels on disk and with the item. Network sync is left
 * out, because a client-facing viewer would sync only the viewing player's slice,
 * not the whole per-player bag.
 *
 * <p>The Fabric attachment ({@link SlotStateAttachments#BLOCK_ENTITY}) stays the
 * source of truth for live enforcement and world save. The component is only for
 * travel.
 */
@ApiStatus.Internal
public final class SlotStateComponents {

    private SlotStateComponents() {}

    public static final DataComponentType<PerPlayerSlotStateBag> PORTABLE_SLOT_STATE =
            Registry.register(
                    BuiltInRegistries.DATA_COMPONENT_TYPE,
                    Identifier.fromNamespaceAndPath("menukit", "portable_slot_state"),
                    DataComponentType.<PerPlayerSlotStateBag>builder()
                            .persistent(PerPlayerSlotStateBag.CODEC)
                            // Not synchronised: the bag holds every player's
                            // private marks, and no client reads it (slot state reaches a
                            // client through its snapshots). On the wire it is an empty bag.
                            .networkSynchronized(StreamCodec.of((buf, bag) -> {}, buf -> new PerPlayerSlotStateBag()))
                            .build());

    /**
     * Loads the class so the static field above registers. Called from common init
     * on both sides, since data-component types must match on client and server.
     */
    public static void register() {
        // Empty: the static field does the registering.
    }
}
