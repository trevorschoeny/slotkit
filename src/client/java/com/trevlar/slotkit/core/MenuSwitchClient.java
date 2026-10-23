package com.trevlar.slotkit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The client half of {@code MenuSwitch}: a container screen the server opens over
 * another one waits, with the old screen still up, until its menu's first contents
 * arrive, so a switch never shows an empty screen for a frame. Any other screen change
 * in the meantime drops the waiting one, and it shows anyway after a second if its
 * contents never come.
 */
@ApiStatus.Internal
public final class MenuSwitchClient {

    private MenuSwitchClient() {}

    private static final int MAX_WAIT_TICKS = 20;

    // Set while handleOpenScreen builds its screen, so setScreen knows a server open from any other.
    private static boolean capturing;
    private static @Nullable Screen held;
    private static int heldId;
    private static int waited;

    /** Around {@code MenuScreens.create} in {@code handleOpenScreen}. */
    public static void capturing(boolean on) {
        capturing = on;
    }

    /**
     * {@code Gui.setScreen} is about to show {@code next}. Returns {@code true} to stop it:
     * the screen is held for its contents instead.
     */
    public static boolean onSetScreen(@Nullable Screen next) {
        held = null;
        if (!capturing || !(next instanceof AbstractContainerScreen<?> screen)) return false;
        return hold(screen, screen.getMenu().containerId);
    }

    /** The server opened the player's inventory ({@code OpenInventoryS2CPayload}). */
    public static void openInventory(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        player.containerMenu = player.inventoryMenu;   // the server already left the other menu
        InventoryScreen next = new InventoryScreen(player);
        if (!hold(next, 0)) mc.gui.setScreen(next);
    }

    /** A {@code ContainerSetContent} for {@code containerId} is about to apply: its screen shows first. */
    public static void contentArriving(int containerId) {
        if (held != null && containerId == heldId) release();
    }

    /** Each client tick. */
    public static void tick() {
        if (held != null && ++waited > MAX_WAIT_TICKS) release();
    }

    private static boolean hold(AbstractContainerScreen<?> next, int containerId) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>) || mc.player == null) return false;
        // The screen's init would project created slots onto its menu; that has to happen
        // now, before anything else the server sends for the menu (on-demand groups, contents).
        if (!(next instanceof InventoryScreen) && !(next instanceof CreativeModeInventoryScreen)) {
            CreatedSlotProjection.appendProjectedSlots(next.getMenu(), mc.player);
        }
        held = next;
        heldId = containerId;
        waited = 0;
        return true;
    }

    private static void release() {
        Screen next = held;
        held = null;
        Minecraft.getInstance().gui.setScreen(next);
    }
}
