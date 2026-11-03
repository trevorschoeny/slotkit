package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.network.Handshake;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Reservations on a server without SlotKit: the client keeps the inventory's own, refuses
 * a click that would break one before sending it, and finds it again after a relog. The
 * test server runs SlotKit, so the client is told it doesn't; the server, holding no
 * reservation, would take any click the client sent.
 */
public class LocalReservationChecks implements FabricClientGameTest {

    private static final List<String> FAILURES = new ArrayList<>();

    // Inventory menu slots 9 and 10 are inventory indexes 9 and 10.
    private static final int DIRT = 9, RESERVED = 10;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
            try (TestDedicatedServerConnection conn = server.connect()) {
                withoutSlotKit(ctx, server);
                ctx.runOnClient(mc -> Reservations.reserve(mc.player, mc.player.inventoryMenu.getSlot(RESERVED),
                        Reservation.of(new ItemStack(Items.DIAMOND)).withCap(4)));
                clickIsRefused(ctx, server, "first join");
            }
            try (TestDedicatedServerConnection conn = server.connect()) {
                withoutSlotKit(ctx, server);
                clickIsRefused(ctx, server, "after a relog");
                ctx.runOnClient(mc -> Reservations.reserve(mc.player, mc.player.inventoryMenu.getSlot(RESERVED), Reservation.NONE));
                check(reservedHere(ctx).isNone(), "clearing it left " + reservedHere(ctx));
            }
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    private static void withoutSlotKit(ClientGameTestContext ctx, TestDedicatedServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runOnServer(s -> {
            player(s).getInventory().clearContent();
            player(s).getInventory().setItem(DIRT, new ItemStack(Items.DIRT, 3));
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> Handshake.setServer(Handshake.ServerRecord.NONE));
    }

    // Picks the dirt up, clicks it onto the diamond slot, puts it back.
    private static void clickIsRefused(ClientGameTestContext ctx, TestDedicatedServerContext server, String when) {
        Reservation r = reservedHere(ctx);
        check(r.item().is(Items.DIAMOND) && r.cap() == 4, when + ": the client's reservation reads " + r);
        ctx.runOnClient(mc -> {
            click(mc, DIRT);
            click(mc, RESERVED);
            click(mc, DIRT);
        });
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ItemStack there = player(s).getInventory().getItem(RESERVED);
            check(there.isEmpty(), when + ": the click put " + there + " in the reserved slot");
            check(player(s).getInventory().getItem(DIRT).getCount() == 3, when + ": the dirt didn't go back");
        });
    }

    private static Reservation reservedHere(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> Reservations.get(new com.trevlar.slotkit.api.window.SlotRef(
                mc.player.getInventory(), RESERVED, mc.player.inventoryMenu.getSlot(RESERVED), mc.player.inventoryMenu,
                mc.player, null, null)));
    }

    private static void click(Minecraft mc, int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
