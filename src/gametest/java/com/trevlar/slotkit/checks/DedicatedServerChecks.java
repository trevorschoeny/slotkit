package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.menu.MenuSwitch;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MenuSwitch on a dedicated server, over a real connection: what happens to a set-aside
 * menu when the player dies, leaves, changes dimension, or the chest breaks under it,
 * whether a chest changed while set aside comes back as it is now, and whether a storm
 * of clicks sent while the server switches menus ever makes or loses an item. After
 * each, SlotKit holds no player that has left the world.
 */
public class DedicatedServerChecks implements FabricClientGameTest {

    private static final List<MenuSwitch.Reason> HEARD = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        MenuSwitch.onSetAsideClosed((player, menu, why) -> HEARD.add(why));
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
            BlockPos chest;
            try (TestDedicatedServerConnection conn = server.connect()) {
                server.runCommand("gamemode survival @a");
                chest = server.computeOnServer(s -> {
                    ServerPlayer p = player(s);
                    BlockPos pos = p.blockPosition().east(2);
                    p.level().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
                    p.level().setBlock(pos.west(4), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                    return pos;
                });
                ctx.waitTicks(5);

                outsideChangeShowsOnBack(ctx, server, chest);
                brokenChestClosesCleanly(ctx, server, chest);
                dimensionChangeClosesIt(ctx, server, chest);
                clickStormKeepsEveryItem(ctx, server, chest);
                deathHandsBackTheGrid(ctx, server, chest);
                // Leaves the world: the last check on this connection.
                setAsideGridBeforeLeaving(ctx, server, chest);
            }
            try (TestDedicatedServerConnection conn = server.connect()) {
                ctx.waitTicks(10);
                server.runOnServer(s -> {
                    ServerPlayer p = player(s);
                    int planks = count(p.getInventory(), Items.OAK_PLANKS) + dropped(s, p, Items.OAK_PLANKS);
                    check(planks == 4, "the set-aside crafting grid's planks after leaving and rejoining: " + planks + ", expected 4");
                    checkOpeners(s, chest, 0, "after the player left with the chest set aside");
                    checkNoLeftPlayers(s, "after rejoining");
                });
            }
        }
    }

    /** A hopper, a comparator's chest or another player changes the chest while it waits: back shows it as it is now. */
    private static void outsideChangeShowsOnBack(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        server.runOnServer(s -> chestAt(s, chest).setItem(0, new ItemStack(Items.COBBLESTONE, 17)));
        int id = openChest(ctx, server, chest);
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runOnServer(s -> chestAt(s, chest).setItem(0, new ItemStack(Items.COBBLESTONE, 3)));
        server.runOnServer(s -> check(MenuSwitch.back(player(s)), "back found nothing set aside"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen sc && sc.getMenu().containerId == id);
        ctx.waitTicks(3);
        int seen = ctx.computeOnClient(mc -> mc.player.containerMenu.getSlot(0).getItem().getCount());
        check(seen == 3, "after back the client sees " + seen + " cobblestone, the chest holds 3");
        closeScreen(ctx, server);
    }

    /** The chest breaks while set aside: it closes once, its items drop once, and back has nothing to return to. */
    private static void brokenChestClosesCleanly(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        HEARD.clear();
        openChest(ctx, server, chest);
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runOnServer(s -> player(s).level().destroyBlock(chest, true));
        server.waitFor(s -> MenuSwitch.setAside(player(s)).isEmpty());
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(!MenuSwitch.back(p), "back reopened a broken chest");
            int cobble = dropped(s, p, Items.COBBLESTONE) + count(p.getInventory(), Items.COBBLESTONE);
            check(cobble == 3, "the broken chest's cobblestone: " + cobble + ", expected 3");
            s.getAllLevels().forEach(l -> l.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(16)).forEach(ItemEntity::discard));
            p.level().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        });
        check(HEARD.equals(List.of(MenuSwitch.Reason.INVALID)), "the broken chest: listener heard " + HEARD);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "the broken chest closed the inventory in front");
        closeScreen(ctx, server);
    }

    /** Changing dimension with the chest set aside closes it, and the lid shuts. */
    private static void dimensionChangeClosesIt(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        HEARD.clear();
        openChest(ctx, server, chest);
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runCommand("execute in minecraft:the_nether run tp @a 0 120 0");
        server.waitFor(s -> MenuSwitch.setAside(player(s)).isEmpty());
        server.runCommand("execute in minecraft:overworld run tp @a " + (chest.getX() - 2) + " " + chest.getY() + " " + chest.getZ());
        ctx.waitTicks(20);
        server.runOnServer(s -> {
            checkOpeners(s, chest, 0, "after changing dimension");
            checkNoLeftPlayers(s, "after changing dimension");
        });
        check(HEARD.equals(List.of(MenuSwitch.Reason.INVALID)), "changing dimension: listener heard " + HEARD);
        closeScreen(ctx, server);
    }

    /**
     * Clicks sent without waiting for the client to catch up, while the server switches
     * between the chest, the inventory and the ender chest every tick. Whatever lands or
     * is dropped, no diamond is made or lost, and the client ends up agreeing with the server.
     */
    private static void clickStormKeepsEveryItem(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.getEnderChestInventory().clearContent();
            chestAt(s, chest).clearContent();
            for (int i = 9; i < 13; i++) p.getInventory().setItem(i, new ItemStack(Items.DIAMOND, 16));
            chestAt(s, chest).setItem(4, new ItemStack(Items.DIAMOND, 16));
            p.getEnderChestInventory().setItem(2, new ItemStack(Items.DIAMOND, 16));
        });
        final int total = 96;
        openChest(ctx, server, chest);
        Random random = new Random(20261031L);
        ContainerInput[] inputs = {ContainerInput.PICKUP, ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.SWAP};
        for (int step = 0; step < 80; step++) {
            for (int k = 0; k < 3; k++) {
                int r = random.nextInt(1 << 20);
                ctx.runOnClient(mc -> {
                    AbstractContainerMenu m = mc.player.containerMenu;
                    ContainerInput input = inputs[r % inputs.length];
                    int slot = (r >> 3) % m.slots.size();
                    int button = input == ContainerInput.SWAP ? (r >> 12) % 9 : (r >> 12) % 2;
                    mc.gameMode.handleContainerInput(m.containerId, slot, button, input, mc.player);
                });
            }
            int move = step % 4;
            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                switch (move) {
                    case 0 -> MenuSwitch.openInventory(p);
                    case 1 -> MenuSwitch.open(p, new SimpleMenuProvider(
                            (id, inv, pl) -> ChestMenu.threeRows(id, inv, pl.getEnderChestInventory()),
                            Component.literal("Ender chest")));
                    case 2 -> MenuSwitch.back(p);
                    default -> {
                        if (MenuSwitch.setAside(p).isEmpty() && p.containerMenu == p.inventoryMenu) {
                            p.openMenu(chestAt(s, chest));
                        }
                    }
                }
            });
            if (step % 7 == 0) ctx.waitTicks(1);
        }
        closeScreen(ctx, server);
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            int n = count(p.getInventory(), Items.DIAMOND) + count(p.getEnderChestInventory(), Items.DIAMOND)
                    + count(chestAt(s, chest), Items.DIAMOND) + dropped(s, p, Items.DIAMOND)
                    + p.containerMenu.getCarried().getCount();
            check(n == total, "diamonds after the click storm: " + n + ", expected " + total);
        });
        ctx.waitTicks(5);
        for (int i = 0; i < 41; i++) {
            int slot = i;
            ItemStack onServer = server.computeOnServer(s -> player(s).getInventory().getItem(slot).copy());
            ItemStack onClient = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(slot).copy());
            check(ItemStack.matches(onServer, onClient),
                    "after the click storm inventory slot " + slot + " is " + onClient + " on the client, " + onServer + " on the server");
        }
        server.runOnServer(s -> {
            player(s).getInventory().clearContent();
            chestAt(s, chest).clearContent();
            player(s).getEnderChestInventory().clearContent();
            s.getAllLevels().forEach(l -> l.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(16)).forEach(ItemEntity::discard));
        });
    }

    /**
     * The player dies with a crafting table set aside and items in its grid, the inventory
     * in front: the grid's items aren't lost, and once they respawn SlotKit holds nothing
     * of the player that died.
     */
    private static void deathHandsBackTheGrid(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        openCraftingWithPlanks(ctx, server, chest);
        // A switch with no click after it, as when the player dies right after a tab click.
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runCommand("kill @a");
        ctx.waitFor(mc -> mc.gui.screen() instanceof DeathScreen);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            int planks = count(p.getInventory(), Items.OAK_PLANKS) + dropped(s, p, Items.OAK_PLANKS);
            check(planks == 4, "the set-aside crafting grid's planks after death: " + planks + ", expected 4");
        });
        ctx.runOnClient(mc -> mc.player.respawn());
        ctx.waitFor(mc -> mc.player.isAlive() && mc.gui.screen() == null);
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            checkNoLeftPlayers(s, "after respawning");
            s.getAllLevels().forEach(l -> l.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(64)).forEach(ItemEntity::discard));
            player(s).teleportTo(chest.getX() - 2, chest.getY(), chest.getZ());
        });
        ctx.waitTicks(10);
    }

    /** A crafting table set aside with planks in its grid, then the player leaves. Checked on rejoining. */
    private static void setAsideGridBeforeLeaving(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        server.runOnServer(s -> player(s).getInventory().clearContent());
        openCraftingWithPlanks(ctx, server, chest);
        openChestOver(server, chest);
        server.runOnServer(s -> MenuSwitch.openInventory(player(s)));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
    }

    private static void openCraftingWithPlanks(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        BlockPos table = chest.west(4);
        int id = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            int menu = p.openMenu(p.level().getBlockState(table).getMenuProvider(p.level(), table)).orElseThrow();
            p.containerMenu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS, 4));
            return menu;
        });
        ctx.waitFor(mc -> mc.player.containerMenu.containerId == id);
    }

    /** The chest, opened as a tab over the crafting table, which waits behind it. */
    private static void openChestOver(TestDedicatedServerContext server, BlockPos chest) {
        server.runOnServer(s -> MenuSwitch.open(player(s), chestAt(s, chest)).orElseThrow());
    }

    private static int openChest(ClientGameTestContext ctx, TestDedicatedServerContext server, BlockPos chest) {
        int id = server.computeOnServer(s -> player(s).openMenu(chestAt(s, chest)).orElseThrow());
        ctx.waitFor(mc -> mc.gui.screen() instanceof ContainerScreen sc && sc.getMenu().containerId == id);
        return id;
    }

    private static void closeScreen(ClientGameTestContext ctx, TestDedicatedServerContext server) {
        ctx.runOnClient(mc -> { if (mc.gui.screen() != null) mc.gui.screen().onClose(); });
        server.waitFor(s -> player(s).containerMenu == player(s).inventoryMenu && MenuSwitch.setAside(player(s)).isEmpty());
        ctx.waitTicks(2);
    }

    /** SlotKit's per-player maps hold only players still in the world. */
    @SuppressWarnings("unchecked")
    private static void checkNoLeftPlayers(MinecraftServer s, String when) {
        for (String name : new String[] {"ASIDE", "LEFT"}) {
            try {
                Field f = MenuSwitch.class.getDeclaredField(name);
                f.setAccessible(true);
                for (Object key : ((Map<Object, Object>) f.get(null)).keySet()) {
                    ServerPlayer held = (ServerPlayer) key;
                    check(!held.isRemoved() && s.getPlayerList().getPlayers().contains(held),
                            "MenuSwitch." + name + " still holds a player that left the world, " + when);
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static ChestBlockEntity chestAt(MinecraftServer s, BlockPos pos) {
        return (ChestBlockEntity) player(s).level().getBlockEntity(pos);
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static int dropped(MinecraftServer s, ServerPlayer p, Item item) {
        int n = 0;
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64))) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void checkOpeners(MinecraftServer s, BlockPos chest, int expected, String when) {
        int n = ChestBlockEntity.getOpenCount(player(s).level(), chest);
        check(n == expected, "chest opener count " + when + ": " + n + ", expected " + expected);
    }

    private static void check(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
