package com.trevlar.slotkit.api.storage;

import com.trevlar.slotkit.api.storage.Storage;

import com.trevlar.slotkit.api.window.PersistentContainerKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Factories for {@link KeyedStorage}, a {@link Storage} that declares its own
 * {@link PersistentContainerKey} so per-slot state persists for registered slots.
 *
 * <p>The player-scoped factory is the supported way to register player-owned storage
 * (pockets or equipment slots, say) with per-slot state. It wraps a content
 * {@link Storage} (usually bound from a {@link StorageAttachment}) and tags it with a
 * {@link PersistentContainerKey.Modded} key whose payload names the owning player.
 * {@code ContainerIdentity} routes the registered slot to this key, and the
 * server resolves it to a bag on the player keyed by resolver id, so different slots
 * and different mods never collide.
 *
 * <p>The library owns the payload shape. These keys are made by the library, unlike
 * the opaque payloads consumers define for their own modded resolvers. The constants
 * below are the contract between this factory and {@code SlotStateServer}'s
 * {@code Modded} resolution.
 */
public final class KeyedStorages {

    private KeyedStorages() {}

    /** Payload key: slot ownership scope. */
    public static final String SCOPE_KEY = "menukit:scope";
    /** Payload key: owning player UUID (string form), when scope is {@link #SCOPE_PLAYER}. */
    public static final String OWNER_KEY = "menukit:owner";
    /** {@link #SCOPE_KEY} value for player-owned slots. */
    public static final String SCOPE_PLAYER = "player";

    /**
     * Wraps {@code backing} as a player-scoped {@link KeyedStorage}. The returned
     * storage passes all content operations to {@code backing} and reports a stable
     * {@link PersistentContainerKey.Modded} key for ({@code resolverId},
     * {@code playerId}), so per-slot state written on the registered slots persists on
     * the player under {@code resolverId}.
     *
     * <p>The key is equal on client and server (same resolver id, same payload), as
     * {@link KeyedStorage} requires.
     *
     * @param backing    the content storage (e.g. {@code POCKETS.bind(player)})
     * @param resolverId stable identity of this slot (e.g.
     *                   {@code "inventory-plus:pockets"}); namespaces the bag
     * @param playerId   the owning player's UUID
     */
    public static KeyedStorage player(Storage backing, Identifier resolverId, UUID playerId) {
        CompoundTag payload = new CompoundTag();
        payload.putString(SCOPE_KEY, SCOPE_PLAYER);
        payload.putString(OWNER_KEY, playerId.toString());
        PersistentContainerKey key = new PersistentContainerKey.Modded(resolverId, payload);
        return new DelegatingKeyedStorage(backing, key);
    }

    private record DelegatingKeyedStorage(Storage backing, PersistentContainerKey key)
            implements KeyedStorage {

        @Override public PersistentContainerKey storageKey() { return key; }

        @Override public ItemStack getStack(int localIndex) { return backing.getStack(localIndex); }
        @Override public void setStack(int localIndex, ItemStack stack) { backing.setStack(localIndex, stack); }
        @Override public int size() { return backing.size(); }
        @Override public void markDirty() { backing.markDirty(); }
    }
}
