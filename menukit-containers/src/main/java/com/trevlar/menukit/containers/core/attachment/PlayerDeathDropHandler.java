package com.trevlar.menukit.containers.core.attachment;

import com.trevlar.menukit.containers.api.storage.CustomAttachmentSpec;
import com.trevlar.menukit.containers.compat.GraveModPresence;
import com.trevlar.menukit.containers.api.slot.DropRule;

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
 * {@code com.trevlar.menukit.containers.mixin.PlayerDeathDropMixin} at the TAIL of
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
                        // The vanilla player-death drop: eye-level spawn, 40-tick
                        // pickup delay, no thrower (any player can pick it up), the
                        // same call vanilla's Inventory.dropAll uses. The slot is
                        // left empty in `remaining`.
                        ItemEntity dropped = player.drop(stack, true, false);
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

        // Custom (consumer-defined) player-anchored specs. These have no
        // library-owned AttachmentType, since the consumer owns the storage, so they
        // live in a parallel registry. Same per-stack DROP/DESTROY/KEEP resolution,
        // read through the spec. KEEP relies on the consumer's storage surviving
        // death (its own copyOnDeath); the library only does the gamerule-gated drop.
        for (var entry : StorageAttachments.customPlayerDeathSpecs().entrySet()) {
            dropCustomSpecOnDeath(player, keepInventory, entry.getKey(), entry.getValue());
        }
    }

    // Same per-stack resolution as the AttachmentType path, read and written through
    // the spec. The one unchecked cast is safe because dropsOnDeath requires a
    // Player owner; a non-player spec enrolled here fails with ClassCastException,
    // the documented usage error.
    @SuppressWarnings("unchecked")
    private static void dropCustomSpecOnDeath(Player player, boolean keepInventory,
            CustomAttachmentSpec<?, ?> rawSpec, DropRule configured) {
        CustomAttachmentSpec<Player, Object> spec =
                (CustomAttachmentSpec<Player, Object>) rawSpec;
        if (configured == null) configured = DropRule.DEFAULT;

        Object content = spec.read(player);
        if (content == null) return;
        NonNullList<ItemStack> items = spec.toItemList(content);
        NonNullList<ItemStack> remaining = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        boolean changed = false;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            DropRule effective = configured.resolve(stack, keepInventory);
            switch (effective) {
                case DROP -> {
                    // Same vanilla player-death drop as the AttachmentType path;
                    // the slot is left empty in `remaining`.
                    ItemEntity dropped = player.drop(stack, true, false);
                    if (dropped != null && GraveModPresence.anyGraveModPresent()) {
                        dropped.setUnlimitedLifetime();
                    }
                    changed = true;
                }
                case DESTROY -> changed = true;       // vanish; slot left empty
                default -> remaining.set(i, stack);   // KEEP: consumer storage must survive death
            }
        }

        // Write only the kept stacks back through the spec, so nothing is duplicated.
        // For KEEP to carry across respawn, that storage must itself survive death.
        if (changed) {
            spec.write(player, spec.fromItemList(remaining));
            spec.markDirty(player);
        }
    }
}
