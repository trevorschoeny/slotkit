package com.trevlar.menukit.containers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * Containers' common initializer, on both sides: slot-state attachments, the
 * network payloads, the presence check and the created-slot ports. Its client half
 * is {@code MenuKitContainersClient}. The consumer surface is the {@code api} packages,
 * and this class is not part of it.
 */
@ApiStatus.Internal
public class MenuKitContainers implements ModInitializer {

    /** Separate from MenuKit's own logger, so Containers' init lines can be told apart. */
    public static final Logger LOGGER = LoggerFactory.getLogger("menukit-containers");

    @Override
    public void onInitialize() {
        init();
    }

    /** Common-side init: slot-state attachment types, shared payloads and the
     *  server-side hooks. Must run before any consumer code that references
     *  {@code CustomContainerMenu}, {@code SlotState} or {@code StorageAttachment}. */
    public static void init() {
        LOGGER.info("[MenuKit-Containers] Initialized");
        // The kind-aware slot address rule, so a created slot resolves to its
        // CREATED address, and created slot groups published into MenuKit's
        // slot-group registry. Both sides, because the operation seams, vetoes and
        // SlotGroups.of run on the server that enforces them. (Before 6.0.0 this was
        // client-only, so a dedicated server addressed every created slot as a
        // vanilla one and saw no created groups.)
        com.trevlar.menukit.window.ClientSlotAddressing.install(
                com.trevlar.menukit.containers.core.SlotAddresses.RULE);
        // A custom menu runs a right click on a slot with a handler itself, so the
        // click names no take or put operation, neither at the client's send nor on the server.
        com.trevlar.menukit.window.ClickOperations.installOwnClick((menu, slotId, button, input) ->
                menu instanceof com.trevlar.menukit.containers.api.menu.CustomContainerMenu custom
                        && custom.rightClickHandler(slotId, button, input) != null);
        com.trevlar.menukit.containers.core.CreatedSlotCategories.install();
        // SlotGroups.of(SlotRef) answers a created slot's own group. Both sides, since
        // a veto that reads it runs wherever the operation does.
        com.trevlar.menukit.api.slot.SlotGroups.installCreatedGroupLookup(slot -> {
            com.trevlar.menukit.containers.api.slot.CreatedSlot mk = com.trevlar.menukit.containers.core.CreatedSlotAccess.asMKCSlot(slot);
            return mk == null ? null
                    : com.trevlar.menukit.api.slot.SlotGroupId.created(mk.panelId(), mk.groupId());
        });
        // Container identity belongs to MenuKit. A Containers storage that carries its
        // own key is recognised first, so a slot backed by it is addressed by that key.
        com.trevlar.menukit.window.ContainerIdentity.extend(container ->
                container instanceof com.trevlar.menukit.containers.api.storage.StorageContainerAdapter adapter
                        && adapter.getStorage() instanceof com.trevlar.menukit.containers.api.storage.KeyedStorage keyed
                        ? java.util.Optional.of(keyed.storageKey()) : java.util.Optional.empty());
        // Per-slot state. Attachment and payload-type registration both have to run
        // on both sides.
        com.trevlar.menukit.containers.state.SlotStateAttachments.register();
        // The component that carries slot state when a block is broken and placed.
        // Registered on both sides so the data-component registries match.
        com.trevlar.menukit.containers.state.SlotStateComponents.register();
        com.trevlar.menukit.containers.state.SlotStateHooks.registerCommon();
        com.trevlar.menukit.containers.state.SlotStateHooks.registerServer();

        // The one generic open request every CustomMenu shares. The server checks it:
        // the menu must declare validWhen, and it must hold for the player.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.TYPE,
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        com.trevlar.menukit.containers.api.menu.CustomMenu.handleOpenRequest(
                                context.player(), payload.menuId())));

        // A joining client must have Containers. Checked in the configuration phase.
        com.trevlar.menukit.containers.network.Presence.register();

        // Grave-mod compat is optional: a capture adapter registers only when its
        // grave mod is present, and Containers hard-depends on none. The adapter class
        // is referenced only inside the guard, so its grave-mod class references never
        // load when the mod is absent. The fallback (items drop beside the death spot)
        // covers that case. Universal Graves has an adapter. Pneumono's Gravestones
        // (blocked on its Loom version) and YIGD (no 1.21.11 build) use
        // the fallback for now.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("universal-graves")) {
            com.trevlar.menukit.containers.compat.UniversalGravesAdapter.register();
            LOGGER.info("[MenuKit-Containers] Universal Graves detected, player-slot grave capture enabled");
        }
    }

}
