package com.trevlar.slotkit;

import net.fabricmc.api.ModInitializer;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * SlotKit's common initializer, on both sides: the vanilla slot vocabulary, slot-state
 * attachments, the network payloads, the handshake and grave-mod compat. Its client
 * half is {@code SlotKitClient}. The consumer surface is the {@code api} packages,
 * and this class is not part of it.
 */
@ApiStatus.Internal
public class SlotKit implements ModInitializer {

    /** Separate from MenuKit's own logger, so SlotKit's lines can be told apart. */
    public static final Logger LOGGER = LoggerFactory.getLogger("slotkit");

    @Override
    public void onInitialize() {
        init();
    }

    /** Common-side init: slot-state attachment types, shared payloads and the
     *  server-side hooks. Must run before any consumer code that references
     *  {@code CustomContainerMenu}, {@code SlotState} or {@code StorageAttachment}. */
    public static void init() {
        LOGGER.info("[SlotKit] Initialized");
        // The shared vocabularies, on both sides: every vanilla slot category, and
        // the operations vanilla itself ships (MenuKit declared these before 7.0.0). A
        // consumer reads them through SlotGroupCategories.all() / SlotOperations.all()
        // and adds its own with SlotGroupCategories.declare(...) / SlotOperations.define(...).
        declareVanillaVocabulary();
        // Per-slot state. Attachment and payload-type registration both have to run
        // on both sides.
        com.trevlar.slotkit.state.SlotStateAttachments.register();
        // Slot state that travels with an item rides in custom_data, never in a
        // registered component (a synced registry entry would refuse vanilla clients
        // before the handshake runs). Its key stays off the network both ways.
        com.trevlar.slotkit.state.PortableSlotState.installWireStrip();
        com.trevlar.slotkit.state.SlotStateHooks.registerCommon();
        com.trevlar.slotkit.state.SlotStateHooks.registerServer();
        com.trevlar.slotkit.core.ActiveCounts.registerCommon();

        // The one generic open request every CustomMenu shares. The server checks it:
        // the menu must declare validWhen, and it must hold for the player.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.trevlar.slotkit.network.OpenMenuC2SPayload.TYPE,
                com.trevlar.slotkit.network.OpenMenuC2SPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                com.trevlar.slotkit.network.OpenMenuC2SPayload.TYPE,
                (payload, context) -> {
                    if (!com.trevlar.slotkit.network.Handshake.hears(context.player())) return;
                    context.server().execute(() ->
                            com.trevlar.slotkit.api.menu.CustomMenu.handleOpenRequest(
                                    context.player(), payload.menuId()));
                });

        // The negotiation, in the configuration phase: refuse a client only for a mod
        // that needs SlotKit on both sides, and record what every client has.
        com.trevlar.slotkit.network.Handshake.register();

        // Grave-mod compat is optional: a capture adapter registers only when its
        // grave mod is present, and SlotKit hard-depends on none. The adapter class
        // is referenced only inside the guard, so its grave-mod class references never
        // load when the mod is absent. The fallback (items drop beside the death spot)
        // covers that case. Universal Graves has an adapter. Pneumono's Gravestones
        // (blocked on its Loom version) and YIGD (no 1.21.11 build) use
        // the fallback for now.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("universal-graves")) {
            com.trevlar.slotkit.compat.UniversalGravesAdapter.register();
            LOGGER.info("[SlotKit] Universal Graves detected, player-slot grave capture enabled");
        }
    }

    /**
     * Every vanilla slot group, one per category (the group a vanilla resolver
     * contributes), so SlotGroups lists them with no menu open; the vanilla menus'
     * resolvers (a server resolves categories too; creative's picker is the client's);
     * and the eleven vanilla operations, each with the role it plays on a slot, so a
     * settings screen can tell what takes items out from what puts them in.
     *
     * <p>Where each operation applies, from what vanilla does (26.2): an output slot
     * takes nothing in, the crafter's result can't be touched at all, double-click
     * collect skips the results whose menus override canTakeItemForPickAll, and a
     * pickup lands only in the hotbar, the main inventory and the offhand.
     */
    private static void declareVanillaVocabulary() {
        SlotGroupCategory.vanilla().forEach(c -> SlotGroups.declare(SlotGroupId.category(c), c));
        com.trevlar.slotkit.core.VanillaSlotGroupResolvers.registerAll();
        SlotGroupCategory[] outputs = {
                SlotGroupCategory.CRAFTING_OUTPUT, SlotGroupCategory.CRAFTER_RESULT, SlotGroupCategory.FURNACE_OUTPUT,
                SlotGroupCategory.ANVIL_OUTPUT, SlotGroupCategory.GRINDSTONE_OUTPUT, SlotGroupCategory.SMITHING_OUTPUT,
                SlotGroupCategory.LOOM_OUTPUT, SlotGroupCategory.STONECUTTER_OUTPUT,
                SlotGroupCategory.CARTOGRAPHY_OUTPUT, SlotGroupCategory.MERCHANT_RESULT};
        var takes = SlotOperations.AppliesTo.vanillaExcept(SlotGroupCategory.CRAFTER_RESULT);
        var puts = SlotOperations.AppliesTo.vanillaExcept(outputs);
        SlotOperations.define(BehaviorKeys.CLICK_TAKE, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.CLICK_PUT, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.SHIFT_CLICK_OUT, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.SHIFT_CLICK_IN, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.COLLECT, SlotOperations.Role.TAKE, SlotOperations.AppliesTo.vanillaExcept(
                SlotGroupCategory.CRAFTER_RESULT, SlotGroupCategory.CRAFTING_OUTPUT,
                SlotGroupCategory.CARTOGRAPHY_OUTPUT, SlotGroupCategory.MERCHANT_RESULT,
                SlotGroupCategory.SMITHING_OUTPUT, SlotGroupCategory.STONECUTTER_OUTPUT));
        SlotOperations.define(BehaviorKeys.DRAG_FILL, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.HOTBAR_SWAP, SlotOperations.Role.BOTH, takes);
        SlotOperations.define(BehaviorKeys.OFFHAND_SWAP, SlotOperations.Role.BOTH, takes);
        SlotOperations.define(BehaviorKeys.DROP, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.DROP_STACK, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.INVENTORY_INSERT, SlotOperations.Role.PUT, SlotOperations.AppliesTo.vanilla(
                SlotGroupCategory.PLAYER_HOTBAR, SlotGroupCategory.PLAYER_INVENTORY, SlotGroupCategory.PLAYER_OFFHAND));
    }
}
