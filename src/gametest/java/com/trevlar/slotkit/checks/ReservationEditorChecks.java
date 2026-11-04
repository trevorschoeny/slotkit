package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.ReservationEditor;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The reserve popup on a chest: opens for a slot, picks an item, sets a count and a match
 * field, and Done reserves the slot on the server; it reopens pre-filled; Escape closes it
 * without changing the slot and leaves the chest open; Remove clears it.
 */
public class ReservationEditorChecks implements FabricClientGameTest {

    private static final List<String> FAILURES = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            BlockPos chest = server.computeOnServer(s -> player(s).blockPosition().offset(2, 0, 0));
            server.runOnServer(s -> {
                s.overworld().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
                player(s).openMenu((MenuProvider) s.overworld().getBlockEntity(chest));
            });
            ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen);

            // Open, pick diamonds, cap 4, match enchantments, Done.
            openOnSlot0(ctx);
            check(ctx.computeOnClient(mc -> ReservationEditor.isOpen()), "the popup didn't open");
            check(ctx.computeOnClient(mc -> ReservationEditor.draft().isNone()), "an empty slot opened with " + draft(ctx));
            ctx.runOnClient(mc -> {
                ItemStack diamond = ReservationEditor.entries("diamond").stream()
                        .filter(e -> e.is(Items.DIAMOND)).findFirst().orElse(ItemStack.EMPTY);
                ReservationEditor.pick(diamond);
                ReservationEditor.setCount(4);
                ReservationEditor.setMatch(Reservation.Match.ENCHANTMENTS, true);
            });
            ctx.waitTicks(2);
            ctx.takeScreenshot(TestScreenshotOptions.of("reserve-popup").disableCounterPrefix()
                    .withDestinationDir(Path.of(System.getProperty("java.io.tmpdir"), "slotkit-checks")));
            ctx.runOnClient(mc -> ReservationEditor.done());
            ctx.waitTicks(3);
            Reservation expected = Reservation.of(new ItemStack(Items.DIAMOND)).withCap(4).withMatch(Reservation.Match.ENCHANTMENTS);
            check(onServer(server, chest).equals(expected), "Done reserved " + onServer(server, chest));
            check(!ctx.computeOnClient(mc -> ReservationEditor.isOpen()), "Done left the popup open");

            // Reopens pre-filled; Escape leaves the slot and the chest as they were.
            openOnSlot0(ctx);
            check(draft(ctx).equals(expected), "reopened with " + draft(ctx));
            ctx.runOnClient(mc -> ReservationEditor.pick(new ItemStack(Items.DIRT)));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            check(!ctx.computeOnClient(mc -> ReservationEditor.isOpen()), "Escape left the popup open");
            check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "Escape closed the chest too");
            check(onServer(server, chest).equals(expected), "Escape changed the slot to " + onServer(server, chest));

            // Remove clears it.
            openOnSlot0(ctx);
            ctx.runOnClient(mc -> ReservationEditor.remove());
            ctx.waitTicks(3);
            check(onServer(server, chest).isNone(), "Remove left " + onServer(server, chest));
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    private static void openOnSlot0(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var menu = ((ContainerScreen) mc.gui.screen()).getMenu();
            ReservationEditor.open(SlotRef.of(menu, menu.getSlot(0), mc.player));
        });
        ctx.waitTicks(1);
    }

    private static Reservation draft(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ReservationEditor.draft());
    }

    private static Reservation onServer(TestServerContext server, BlockPos chest) {
        return server.computeOnServer(s -> Reservations.get(SlotRef.of(
                (Container) s.overworld().getBlockEntity(chest), 0, null)));
    }

    private static net.minecraft.server.level.ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
