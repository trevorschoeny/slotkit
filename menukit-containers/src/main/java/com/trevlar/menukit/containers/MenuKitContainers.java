package com.trevlar.menukit.containers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.ApiStatus;

/**
 * Containers' common initializer, on both sides: slot-state attachments, the
 * network payloads, the presence check and the created-slot ports. Its client half
 * is {@code MenuKitContainersClient}. The consumer surface is the {@code api} packages
 * (§0068); this class is not part of it.
 */
@ApiStatus.Internal
public class MenuKitContainers implements ModInitializer {

    /** Logger for the MenuKit: Containers artifact — distinct from MenuKit's
     *  own logger so init traces are distinguishable in the log output. */
    public static final Logger LOGGER = LoggerFactory.getLogger("menukit-containers");

    @Override
    public void onInitialize() {
        init();
    }

    /** Common-side initialization. Registers M1 attachment types + shared
     *  networking payloads + the verification harness's server-side hooks.
     *  Must run before any consumer-mod code that references
     *  {@code CustomContainerMenu}, {@code SlotState}, or
     *  {@code StorageAttachment}. */
    public static void init() {
        LOGGER.info("[MenuKit-Containers] Initialized");
        // The kind-aware slot address rule, so a created slot resolves to its
        // CREATED address, and created slot groups published into MenuKit's
        // slot-group registry. Both sides: the operation seams, vetoes and
        // SlotGroups.of run on the server that enforces them. (Client init only
        // until 6.0.0, so a dedicated server addressed every created slot as a
        // vanilla one and saw no created groups.)
        com.trevlar.menukit.window.ClientSlotAddressing.install(
                com.trevlar.menukit.containers.core.SlotAddresses.RULE);
        // The one fact a gate reads that only Containers knows: whether a server
        // player's client can receive slot state (§0064's capability port).
        com.trevlar.menukit.api.window.GatingContext.installCapability(
                com.trevlar.menukit.containers.api.state.SlotState::isSlotStateCapable);
        com.trevlar.menukit.containers.core.CreatedSlotCategories.install();
        // SlotGroups.of(SlotRef) answers a created slot's own group. Both sides: a
        // veto that reads it runs wherever the operation does.
        com.trevlar.menukit.api.slot.SlotGroups.installCreatedGroupLookup(slot -> {
            com.trevlar.menukit.containers.api.slot.CreatedSlot mk = com.trevlar.menukit.containers.core.CreatedSlotAccess.asMKCSlot(slot);
            return mk == null ? null
                    : com.trevlar.menukit.api.slot.SlotGroupId.created(mk.panelId(), mk.groupId());
        });
        // Container identity is MenuKit's (§0062); a Containers storage that carries
        // its own key is recognised first, so a slot backed by it is addressed by it.
        com.trevlar.menukit.window.ContainerIdentity.extend(container ->
                container instanceof com.trevlar.menukit.containers.api.storage.StorageContainerAdapter adapter
                        && adapter.getStorage() instanceof com.trevlar.menukit.containers.api.storage.KeyedStorage keyed
                        ? java.util.Optional.of(keyed.storageKey()) : java.util.Optional.empty());
        // M1 per-slot state — attachments + shared networking types register
        // here (attachment registration must run on both sides; networking
        // payload-type registration is also symmetric).
        com.trevlar.menukit.containers.state.SlotStateAttachments.register();
        // M1 block-portable bridge component (§0048) — must register on both
        // sides for data-component registry consistency.
        com.trevlar.menukit.containers.state.SlotStateComponents.register();
        com.trevlar.menukit.containers.state.SlotStateHooks.registerCommon();
        com.trevlar.menukit.containers.state.SlotStateHooks.registerServer();

        // The one generic open request every CustomMenu shares. The server judges it
        // (§0067): the menu must declare validWhen and it must hold for the player.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.TYPE,
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                com.trevlar.menukit.containers.network.OpenMenuC2SPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        com.trevlar.menukit.containers.api.menu.CustomMenu.handleOpenRequest(
                                context.player(), payload.menuId())));

        // §0069: a joining client must have Containers; checked in the configuration phase.
        com.trevlar.menukit.containers.network.Presence.register();

        // §0055 Phase 2 — grave-mod compat. OPTIONAL: register a capture adapter
        // only when the grave mod is present; MKC hard-depends on none. The
        // adapter class is referenced ONLY inside the guard, so its grave-mod
        // class references never load when the mod is absent — the Phase-1 floor
        // (drop beside the death spot) covers that case. Universal Graves is the
        // shippable 1.21.11 adapter; Pneumono's Gravestones is Loom-version-blocked
        // (floor for now); YIGD has no 1.21.11 build (floor) — see
        // MOD_INTEGRATION_TRACKING.md.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("universal-graves")) {
            com.trevlar.menukit.containers.compat.UniversalGravesAdapter.register();
            LOGGER.info("[MenuKit-Containers] Universal Graves detected — player-slot grave capture enabled");
        }
    }

}
