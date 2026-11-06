package com.trevlar.slotkit.core.attachment;

import com.trevlar.slotkit.compat.GraveModPresence;
import com.trevlar.slotkit.api.slot.DropRule;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Drops, keeps or destroys the contents of every death-droppable player-scoped
 * attachment when a player dies. The player-death counterpart of
 * {@link BlockScopedDropHandler}. Called from
 * {@code com.trevlar.slotkit.mixin.SKPlayerDeathDropMixin} at the TAIL of
 * {@code Player.dropEquipment}, next to vanilla's own {@code keepInventory}-gated
 * {@code destroyVanishingCursedItems()} and {@code inventory.dropAll()}.
 *
 * <p>A {@code playerAttached} content attachment is registered {@code copyOnDeath},
 * so a kept bag carries across respawn. Fabric's copy ignores gamerules, though, so
 * the library does the {@code keepInventory} check and the per-stack drop here. Each
 * stack resolves a {@link DropRule}: {@code KEEP} stays (copyOnDeath carries it),
 * {@code DROP} spawns a vanilla player drop, {@code DESTROY} vanishes.
 *
 * <p>Clearing after the drop matters. Dropped and destroyed slots are written back
 * empty before respawn's {@code copyOnDeath} runs. Otherwise copyOnDeath would carry
 * the items that were just dropped, duplicating them. It also means a grave mod's
 * capture-and-clear suppresses this fallback drop on its own. Drop-and-clear runs at
 * death and copyOnDeath copies at respawn, so a DROP death copies an empty bag and a
 * KEEP death copies the full bag.
 */
public final class PlayerDeathDropHandler {

    private PlayerDeathDropHandler() {}

    /**
     * For every death-droppable player content attachment the player carries,
     * resolves each stack's {@link DropRule} and acts on it: drop (a vanilla player
     * drop) and clear, destroy and clear, or keep. Server side only.
     *
     * @param player the dying player (server-side)
     * @param level  the player's server level, read for the {@code keepInventory}
     *               gamerule (the same instance vanilla's {@code dropEquipment} reads)
     */
    public static void dropAllOnDeath(Player player, ServerLevel level) {
        if (player == null || level == null) return;
        // dropEquipment(ServerLevel) is already server-side; this is only a guard.
        if (level.isClientSide()) return;

        boolean keepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);

        for (AttachmentType<ItemContainerContents> type :
                StorageAttachments.playerDeathDropAttachments()) {
            if (!player.hasAttached(type)) continue;
            ItemContainerContents contents = player.getAttached(type);
            if (contents == null) continue;

            int slotCount = StorageAttachments.slotCountOf(type);
            if (slotCount <= 0) continue;

            DropRule configured = StorageAttachments.deathDropRuleOf(type);
            if (configured == null) configured = DropRule.DEFAULT;

            NonNullList<ItemStack> items = StorageAttachments.contentsToList(contents, slotCount);
            NonNullList<ItemStack> remaining = NonNullList.withSize(slotCount, ItemStack.EMPTY);
            boolean changed = false;

            for (int i = 0; i < slotCount; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;
                DropRule effective = configured.resolve(stack, keepInventory);
                switch (effective) {
                    case DROP -> {
                        // The vanilla player-death drop: eye-level spawn, random
                        // scatter, 40-tick pickup delay, no thrower (any player can
                        // pick it up), the same calls vanilla's Inventory.dropAll
                        // makes. The slot is left empty in `remaining`.
                        ItemEntity dropped = dropOnDeath(player, stack);
                        // With a grave-container mod installed, the player recovers
                        // their vanilla inventory from a grave that never expires. The
                        // slots that mod did not capture (ours) must not expire either,
                        // or they would vanish after 5 minutes while the grave stays.
                        // So they never despawn. With no grave mod, keep vanilla
                        // despawn so a registered slot matches a normal death drop.
                        if (dropped != null && GraveModPresence.anyGraveModPresent()) {
                            dropped.setUnlimitedLifetime();
                        }
                        changed = true;
                    }
                    case DESTROY ->
                        // Leave the slot empty in `remaining`, with no drop.
                        changed = true;
                    default ->
                        // KEEP (and the unreachable DEFAULT): the stack stays, and
                        // copyOnDeath carries it across respawn.
                        remaining.set(i, stack);
                }
            }

            // Write back only the kept stacks, so respawn's copyOnDeath never
            // carries dropped or destroyed ones.
            if (changed) {
                player.setAttached(type, StorageAttachments.listToContents(remaining));
            }
        }

    }

    // Since 26.3, Player.drop has no random-scatter option and always swings the
    // arm, so the death drop is built the way Inventory.dropAll builds it.
    // Null when nothing was spawned.
    private static ItemEntity dropOnDeath(Player player, ItemStack stack) {
        ItemEntity dropped = player.createItemStackToDrop(stack, true, false);
        if (dropped != null) player.level().addFreshEntity(dropped);
        return dropped;
    }
}
