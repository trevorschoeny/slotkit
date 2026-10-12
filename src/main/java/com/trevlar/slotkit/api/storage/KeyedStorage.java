package com.trevlar.slotkit.api.storage;

import com.trevlar.slotkit.api.state.SlotStateChannel;
import com.trevlar.slotkit.api.storage.Storage;

import com.trevlar.slotkit.api.window.PersistentContainerKey;

/**
 * A {@link Storage} that knows its own persistent identity. Implementing
 * {@code KeyedStorage} is how a custom-panel slot opts in to persistent slot state:
 * once its container maps back to a {@link PersistentContainerKey}, the full
 * {@link SlotStateChannel} API works on that slot just as it does on vanilla
 * containers.
 *
 * <p>It is a separate interface, extending {@link Storage}, so that
 * {@link Storage} stays minimal. Consumers who want their custom-storage
 * slots to keep slot state implement {@code KeyedStorage} instead of {@link Storage}.
 *
 * <p>{@link com.trevlar.slotkit.window.ContainerIdentity} sees that the slot's
 * container is a {@link StorageContainerAdapter}, looks at the adapter's backing
 * {@link Storage}, and if it is a {@code KeyedStorage} uses {@link #storageKey()} as
 * the persistent identity. Other storages go through the usual resolution (player
 * inventory, ender chest, block entity, modded resolvers).
 *
 * <p>The implementer picks the {@link PersistentContainerKey} variant that matches
 * the storage's owner. A player-attached storage typically returns a
 * {@link PersistentContainerKey.Modded} key with the player UUID in the payload, and
 * a block-entity-backed storage a {@link PersistentContainerKey.BlockEntityKey}.
 */
public interface KeyedStorage extends Storage {

    /**
     * Returns this storage's persistent identity, used to find per-slot state in the
     * attachment for this owner.
     *
     * <p>The key must be stable: two calls on the same logical storage must return
     * keys that are {@code equals}, including on the server and client sides of the
     * same menu.
     *
     * @return the persistent key for this storage; must not be {@code null}.
     */
    PersistentContainerKey storageKey();
}
