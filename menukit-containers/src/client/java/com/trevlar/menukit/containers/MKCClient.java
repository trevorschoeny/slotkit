package com.trevlar.menukit.containers;

import com.trevlar.menukit.containers.core.ClientContainerPanel;
import com.trevlar.menukit.containers.core.MKCSlotProjection;
import com.trevlar.menukit.containers.core.MKCSlotScreenHook;
import com.trevlar.menukit.containers.network.PresenceClient;
import com.trevlar.menukit.containers.screen.ClientMenu;
import com.trevlar.menukit.containers.state.SlotStateClient;
import com.trevlar.menukit.inject.SlotScreenDispatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client entry point for MenuKit: Containers. Everything here is client-only and lives
 * in Containers' client source set (§0062, §0067); common init is {@link MKC}.
 */
@ApiStatus.Internal
public class MKCClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MKC.LOGGER.info("[MenuKit-Containers] Client initialized");

        // Slot state: the server's snapshots and updates, and the client's own writes
        // (sent through a port, since common code must not name the client's networking).
        SlotStateClient.register();
        com.trevlar.menukit.containers.core.MKSlotState.installClientPlayer(() -> Minecraft.getInstance().player);
        com.trevlar.menukit.containers.core.MKSlotState.installClientSender(ClientPlayNetworking::send);

        // §0069: answer the server's hello; with no hello, turn created slots off.
        PresenceClient.register();

        // A panel-hosted created slot (a SlotElement) resolves hover and click on every
        // screen that shows it, creative included, through MenuKit's screen dispatch.
        SlotScreenDispatcher.setHook(new MKCSlotScreenHook());

        // THE ONE WINDOW's created-slot resolver (§0042).
        com.trevlar.menukit.window.SlotWindowResolver.setCreatedSlotResolver(
                com.trevlar.menukit.containers.core.CreatedSlotAdapter.INSTANCE);

        // At client start, after every client initializer: menus' screens (a consumer's
        // ClientMenu.screen may come from its own client initializer, which Fabric does
        // not order against this one), and container panels' presentation (likewise
        // ClientContainerPanel). MenuKit freezes declarations in a later phase.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ClientMenu.registerScreens();
            ClientContainerPanel.wireAll();
        });

        // Parity projection, the client's seam: append the player's projected slots to
        // every menu the server opened, as its screen is built (before the first content
        // packet), matching the server's ServerPlayer.initMenu seam, which runs for every
        // opened menu (§0067: a lectern too, whose screen is not a container screen). The
        // player's own inventory screens are served by MKCInventoryMenuMixin and the
        // creative wrapper; projecting there would add the slots twice.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return;
            if (screen instanceof MenuAccess<?> access && client.player != null) {
                MKCSlotProjection.appendProjectedSlots(access.getMenu(), client.player);
            }
        });
    }
}
