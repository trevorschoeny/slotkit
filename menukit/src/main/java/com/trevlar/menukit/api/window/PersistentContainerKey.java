package com.trevlar.menukit.api.window;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Persistent identity for a {@code Container}, the owner a slot's state
 * attaches to, in a form that survives session boundaries.
 *
 * <p>Each variant names a stable owner (player UUID, block position and dimension,
 * entity UUID, or a mod-defined scope) that the library uses to find the slot's
 * state in a Fabric attachment on the natural owner (§0034).
 */
public sealed interface PersistentContainerKey {

    /** The player's main inventory, hotbar, armor, and offhand slots. */
    record PlayerInventory(UUID playerId) implements PersistentContainerKey {}

    /** The player's ender chest (distinct from their main inventory). */
    record EnderChest(UUID playerId) implements PersistentContainerKey {}

    /** Block-entity-backed container (chest, shulker box, furnace, hopper, etc.). */
    record BlockEntityKey(BlockPos pos, ResourceKey<Level> dimension)
            implements PersistentContainerKey {}

    /** Entity-backed container (donkey, llama, mule, minecart-with-chest, etc.). */
    record EntityKey(UUID entityId) implements PersistentContainerKey {}

    /**
     * Modded container type. The {@code payload} CompoundTag is opaque to the
     * library; the mod defines its shape. {@code resolverId} identifies the
     * mod's registered resolver and attachment binding.
     *
     * <p>An immutable value (§0067): the payload is copied in and copied out, so a
     * key used as a map key never changes under the map.
     */
    record Modded(Identifier resolverId, CompoundTag payload)
            implements PersistentContainerKey {
        public Modded {
            java.util.Objects.requireNonNull(resolverId, "resolverId");
            payload = payload.copy();
        }

        /** A copy of the payload; changing it does not change the key. */
        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }
}
