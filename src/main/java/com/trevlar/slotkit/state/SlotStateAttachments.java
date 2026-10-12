package com.trevlar.slotkit.state;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * The library's Fabric attachments for per-slot state, one per kind of owner:
 *
 * <ul>
 *   <li>{@link #PLAYER_INVENTORY}: on {@code Player}, for
 *       {@code PlayerInventory(uuid)} keys.</li>
 *   <li>{@link #ENDER_CHEST}: on {@code Player}, for {@code EnderChest(uuid)}
 *       keys (a separate attachment so the two don't collide).</li>
 *   <li>{@link #BLOCK_ENTITY}: on {@code BlockEntity}, a per-player map of bags
 *       so each viewing player has private marks.</li>
 *   <li>{@link #ENTITY}: on {@code Entity}, the same per-player shape for
 *       donkeys, minecarts and so on.</li>
 *   <li>{@link #MODDED_PLAYER}: on {@code Player}, for registered slots.</li>
 * </ul>
 *
 * <p>No {@code syncWith(...)}: the library syncs through its own packets (snapshot
 * and update), not Fabric's auto-sync. Persistence uses each bag's own
 * {@code CODEC}.
 */
@ApiStatus.Internal
public final class SlotStateAttachments {

    private static final String MOD_ID = "menukit";

    public static final AttachmentType<SlotStateBag> PLAYER_INVENTORY =
            AttachmentRegistry.<SlotStateBag>builder()
                    .persistent(SlotStateBag.CODEC)
                    .initializer(SlotStateBag::new)
                    // Per-slot marks are slot configuration (a lock, say), not item
                    // state, so they survive death like the slot itself, whether or
                    // not the content drops.
                    .copyOnDeath()
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            MOD_ID, "slot_state_player_inventory"));

    public static final AttachmentType<SlotStateBag> ENDER_CHEST =
            AttachmentRegistry.<SlotStateBag>builder()
                    .persistent(SlotStateBag.CODEC)
                    .initializer(SlotStateBag::new)
                    // Ender chest content always survives death in vanilla, and its
                    // per-slot state goes with it.
                    .copyOnDeath()
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            MOD_ID, "slot_state_ender_chest"));

    public static final AttachmentType<PerPlayerSlotStateBag> BLOCK_ENTITY =
            AttachmentRegistry.<PerPlayerSlotStateBag>builder()
                    .persistent(PerPlayerSlotStateBag.CODEC)
                    .initializer(PerPlayerSlotStateBag::new)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            MOD_ID, "slot_state_block_entity"));

    public static final AttachmentType<PerPlayerSlotStateBag> ENTITY =
            AttachmentRegistry.<PerPlayerSlotStateBag>builder()
                    .persistent(PerPlayerSlotStateBag.CODEC)
                    .initializer(PerPlayerSlotStateBag::new)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            MOD_ID, "slot_state_entity"));

    /**
     * On {@code Player}, for player-scoped {@code Modded} slot keys (pockets or
     * equipment slots, for example). Holds a map of bags keyed by resolver id, so
     * different slots (and different mods) on the same player don't collide on slot
     * index. Separate from {@link #PLAYER_INVENTORY} so registered-slot state never
     * overwrites real-inventory state.
     */
    public static final AttachmentType<NamespacedSlotStateBag> MODDED_PLAYER =
            AttachmentRegistry.<NamespacedSlotStateBag>builder()
                    .persistent(NamespacedSlotStateBag.CODEC)
                    .initializer(NamespacedSlotStateBag::new)
                    // Registered-slot marks are slot configuration and survive
                    // death, matching the slot content's own copyOnDeath.
                    .copyOnDeath()
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            MOD_ID, "slot_state_modded_player"));

    /**
     * Loads the class so the static field initializers above run. Safe to call
     * from server or client init.
     */
    public static void register() {
        // Empty: the static fields do the registering.
    }

    private SlotStateAttachments() {}
}
