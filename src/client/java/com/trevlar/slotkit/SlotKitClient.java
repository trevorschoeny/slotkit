package com.trevlar.slotkit;

import com.trevlar.slotkit.core.CreatedSlotProjection;
import com.trevlar.slotkit.core.CreatedSlotScreenHook;
import com.trevlar.slotkit.network.HandshakeClient;
import com.trevlar.slotkit.api.menu.ClientMenu;
import com.trevlar.slotkit.state.SlotStateClient;
import com.trevlar.menukit.api.host.SlotScreenHook;

import com.trevlar.slotkit.api.window.WindowSignals;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client entry point for SlotKit. Everything here is client-only and lives in the
 * client source set. Common init is {@link SlotKit}.
 */
@ApiStatus.Internal
public class SlotKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SlotKit.LOGGER.info("[SlotKit] Client initialized");

        // Seeing through creative's slot wrapper: a client class, so the client
        // installs the unwrap (common code may not name it). Then creative's picker
        // slot group; every other vanilla resolver registers in common init.
        com.trevlar.slotkit.core.Slots.installUnwrap(slot -> {
            if (slot instanceof com.trevlar.slotkit.mixin.SlotWrapperAccessor wrapper) {
                net.minecraft.world.inventory.Slot t = wrapper.sk$getTarget();
                if (t != null) return t;
            }
            return slot;
        });
        com.trevlar.slotkit.core.CreativeSlotGroupResolver.registerClient();

        // Client-observed reactions (ON_*_OBSERVED): diff the open container menu's
        // synced contents and fire UI feedback. Slot interaction signals: which slot
        // the cursor is over, read from the screen's hoveredSlot.
        com.trevlar.slotkit.window.SlotGating.clientViewer(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return mc.isSameThread() ? mc.player : null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AbstractContainerScreen<?> acs =
                    client.gui.screen() instanceof AbstractContainerScreen<?> s ? s : null;
            com.trevlar.slotkit.window.ObservedReactions.tick(acs != null ? acs.getMenu() : null, client.player);
            WindowSignals.tickHover(
                    acs != null ? acs.getMenu() : null,
                    acs != null ? ((AbstractContainerScreenAccessor) (Object) acs).sk$getHoveredSlot() : null);
        });

        // Slot interaction signals: record the last-clicked slot on any container
        // screen (the slot under the cursor at click time) and clear it on close.
        // Observation only; the click still does what vanilla does.
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> acs) {
                ScreenMouseEvents.afterMouseClick(screen).register((scr, event, consumed) -> {
                    WindowSignals.recordClick(acs.getMenu(),
                            ((AbstractContainerScreenAccessor) (Object) acs).sk$getHoveredSlot());
                    return false; // observation only, never consume
                });
                ScreenEvents.remove(screen).register(scr -> WindowSignals.clearSelection());
                // Double-clicks a mod takes: the real mouse, read before the screen sees it.
                ScreenMouseEvents.allowMouseClick(screen).register((scr, event) ->
                        mc.player == null || !com.trevlar.slotkit.api.slot.SlotDoubleClick.click(acs, event, mc.player));
            }
        });
        // Back in the world, no double-click pairs across it.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.gui.screen() == null) com.trevlar.slotkit.api.slot.SlotDoubleClick.reset();
        });

        // Slot state: the server's snapshots and updates, and the client's own writes
        // (sent through a port, since common code must not name the client's networking).
        SlotStateClient.register();
        com.trevlar.slotkit.api.state.SlotState.installClientPlayer(() -> Minecraft.getInstance().player);
        com.trevlar.slotkit.api.state.SlotState.installClientSender(ClientPlayNetworking::send);

        // The server's active counts for this player's groups.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.trevlar.slotkit.network.ActiveCountS2CPayload.TYPE,
                (payload, ctx) -> com.trevlar.slotkit.core.ActiveCounts.applyOnClient(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> com.trevlar.slotkit.core.ActiveCounts.clearClient());
        // On-demand group instances on the open menu, before any of their slots' contents.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.trevlar.slotkit.network.GroupInstanceS2CPayload.TYPE,
                (payload, ctx) -> com.trevlar.slotkit.api.slot.OnDemandGroup.applyOnClient(payload, ctx.player()));

        // Menu switching: the server opens the inventory screen, and a screen the server
        // opens over another waits for its contents.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.trevlar.slotkit.network.OpenInventoryS2CPayload.TYPE,
                (payload, ctx) -> com.trevlar.slotkit.core.MenuSwitchClient.openInventory(ctx.client()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> com.trevlar.slotkit.core.MenuSwitchClient.tick());

        // Answer the server's hello. With no hello, turn created slots off.
        HandshakeClient.register();

        // A panel-hosted created slot (a SlotElement) resolves hover and click on every
        // screen that shows it, creative included, through MenuKit's screen dispatch.
        SlotScreenHook.install(CreatedSlotScreenHook.INSTANCE);

        // At client start, after every client initializer: menus' screens (a consumer's
        // ClientMenu.screen may come from its own client initializer, which Fabric does
        // not order against this one). MenuKit freezes declarations in a later phase.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ClientMenu.registerScreens());

        // Parity projection, the client's seam: append the player's projected slots to
        // every menu the server opened, as its screen is built (before the first content
        // packet), matching the server's ServerPlayer.initMenu seam, which runs for every
        // opened menu (a lectern too, whose screen is not a container screen). The
        // player's own inventory screens are served by SKInventoryMenuMixin and the
        // creative wrapper; projecting there would add the slots twice.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return;
            if (screen instanceof MenuAccess<?> access && client.player != null) {
                CreatedSlotProjection.appendProjectedSlots(access.getMenu(), client.player);
            }
        });
    }
}
