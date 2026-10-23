package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.menu.MenuSwitch;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuSwitch, end to end on a real client and server: a chest set aside while the
 * player tabs to their inventory and ender chest and back, the cursor stack riding
 * along, the crafting grid handing back, and the chest closing on Escape and when
 * the player walks away.
 */
public class MenuSwitchChecks implements FabricClientGameTest {

    private static final List<MenuSwitch.Reason> HEARD = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        MenuSwitch.onSetAsideClosed((player, menu, why) -> HEARD.add(why));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos chest = server.computeOnServer(s -> {
                ServerPlayer p = player(s);
                BlockPos pos = p.blockPosition().east(2);
                p.level().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity) p.level().getBlockEntity(pos)).setItem(0, new ItemStack(Items.COBBLESTONE, 17));
                return pos;
            });
            ctx.waitTicks(5);

            switchesKeepTheChestOpen(ctx, server, chest);
            escapeClosesBoth(ctx, server, chest);
            walkingAwayClosesTheChest(ctx, server, chest);
        }
    }

    /** Chest, then inventory, then ender chest, then back: the lid never shuts and the stack follows. */
    private static void switchesKeepTheChestOpen(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        int chestId = openChest(ctx, server, chest);
        server.runOnServer(s -> player(s).containerMenu.setCarried(new ItemStack(Items.DIAMOND, 5)));

        server.runOnServer(s -> check(MenuSwitch.openInventory(player(s)), "openInventory refused a SlotKit client"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        ctx.waitTicks(12);   // the chest's opener recheck runs every 5 ticks; it must still count the player
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(MenuSwitch.setAside(p).map(m -> m.containerId == chestId).orElse(false), "the chest isn't set aside");
            checkOpeners(s, chest, 1, "after switching to the inventory");
            checkCarried(p.inventoryMenu.getCarried(), "the inventory");
            // something in the crafting grid, to be handed back on leaving
            p.inventoryMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 3));
        });
        ctx.waitFor(mc -> mc.player.inventoryMenu.getCarried().is(Items.DIAMOND));

        int enderId = server.computeOnServer(s -> MenuSwitch.open(player(s), new SimpleMenuProvider(
                (id, inv, pl) -> ChestMenu.threeRows(id, inv, pl.getEnderChestInventory()),
                Component.literal("Ender chest"))).orElseThrow());
        ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen sc && sc.getMenu().containerId == enderId);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(enderId != chestId, "the ender chest took the set-aside chest's id");
            checkOpeners(s, chest, 1, "after switching to the ender chest");
            checkCarried(p.containerMenu.getCarried(), "the ender chest");
            check(p.inventoryMenu.getSlot(1).getItem().isEmpty(), "the crafting grid kept its planks");
            check(p.getInventory().countItem(Items.OAK_PLANKS) == 3, "the crafting grid's planks didn't come back");
        });

        server.runOnServer(s -> check(MenuSwitch.back(player(s)), "back found nothing set aside"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen sc && sc.getMenu().containerId == chestId
                && sc.getMenu().getSlot(0).getItem().getCount() == 17);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(MenuSwitch.setAside(p).isEmpty(), "the chest is still set aside after back");
            check(p.containerMenu.containerId == chestId, "back didn't reopen the chest");
            checkOpeners(s, chest, 1, "after back");
            checkCarried(p.containerMenu.getCarried(), "the chest, after back");
            p.containerMenu.setCarried(ItemStack.EMPTY);
        });
        ctx.waitFor(mc -> ((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().getCarried().isEmpty());
    }

    /** Escape on the inventory tab closes the inventory and the chest behind it. */
    private static void escapeClosesBoth(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        HEARD.clear();
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        ctx.runOnClient(mc -> mc.gui.screen().onClose());   // what Escape does
        server.waitFor(s -> MenuSwitch.setAside(player(s)).isEmpty());
        server.runOnServer(s -> {
            checkOpeners(s, chest, 0, "after Escape");
            check(player(s).containerMenu == player(s).inventoryMenu, "a menu is still open after Escape");
        });
        check(HEARD.equals(List.of(MenuSwitch.Reason.CLOSED)), "Escape: listener heard " + HEARD);
    }

    /** Walking away from the chest while on another tab closes it. */
    private static void walkingAwayClosesTheChest(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        HEARD.clear();
        openChest(ctx, server, chest);
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        // Just out of reach, as walking away leaves it: past stillValid's distance but inside
        // the opener recheck's box. (A long teleport passes both at once, and then vanilla's
        // recheck and close each take the opener off, for a chest in front as much as one set aside.)
        server.runOnServer(s -> player(s).teleportTo(chest.getX() + 7.5, chest.getY(), chest.getZ() + 7.5));
        server.waitFor(s -> MenuSwitch.setAside(player(s)).isEmpty());
        server.runOnServer(s -> checkOpeners(s, chest, 0, "after walking away"));
        check(HEARD.equals(List.of(MenuSwitch.Reason.INVALID)), "walking away: listener heard " + HEARD);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "walking away closed the inventory too");
        ctx.runOnClient(mc -> mc.gui.screen().onClose());
        ctx.waitTicks(2);
    }

    private static int openChest(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        int id = server.computeOnServer(s -> player(s).openMenu(
                (ChestBlockEntity) player(s).level().getBlockEntity(chest)).orElseThrow());
        ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen sc && sc.getMenu().containerId == id);
        server.runOnServer(s -> checkOpeners(s, chest, 1, "after opening the chest"));
        return id;
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void checkOpeners(MinecraftServer s, BlockPos chest, int expected, String when) {
        int n = ChestBlockEntity.getOpenCount(player(s).level(), chest);
        check(n == expected, "chest opener count " + when + ": " + n + ", expected " + expected);
    }

    private static void checkCarried(ItemStack carried, String where) {
        check(carried.is(Items.DIAMOND) && carried.getCount() == 5, "the cursor stack on " + where + ": " + carried);
    }

    private static void check(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
