package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.menu.MenuSwitch;
import com.trevlar.slotkit.mixin.MerchantMenuAccessor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MerchantMenu;
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
 * the player walks away. A villager's trading screen and a donkey's inventory come
 * back whole: the trades, the trader's xp and level, the mount's saddle and chest.
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
            tradingComesBackWithItsTrades(ctx, server);
            mountComesBackWhole(ctx, server);
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

    /** A villager's trading screen, set aside and back: the same trades, xp and level, the trader still locked. */
    private static void tradingComesBackWithItsTrades(ClientGameTestContext ctx, TestServerContext server) {
        int id = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            Villager v = EntityTypes.VILLAGER.create(p.level(), EntitySpawnReason.COMMAND);
            v.setNoAi(true);
            v.setPos(p.getX() - 2, p.getY(), p.getZ());
            v.setVillagerData(v.getVillagerData().withProfession(s.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(3));
            v.setVillagerXp(77);
            p.level().addFreshEntity(v);
            v.setTradingPlayer(p);
            v.openTradingScreen(p, v.getDisplayName(), v.getVillagerData().level());
            return p.containerMenu.containerId;
        });
        ctx.waitFor(mc -> mc.player.containerMenu instanceof MerchantMenu m && m.containerId == id && !m.getOffers().isEmpty());
        int offers = ctx.computeOnClient(mc -> ((MerchantMenu) mc.player.containerMenu).getOffers().size());

        server.runOnServer(s -> check(MenuSwitch.openInventory(player(s)), "openInventory refused a SlotKit client"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(MenuSwitch.setAside(p).map(m -> m.containerId == id).orElse(false), "the trading menu isn't set aside");
            MerchantMenu aside = (MerchantMenu) MenuSwitch.setAside(p).orElseThrow();
            check(((MerchantMenuAccessor) aside).sk$getTrader().getTradingPlayer() == p, "the trader let go of the player while set aside");
        });

        server.runOnServer(s -> check(MenuSwitch.back(player(s)), "back found no trading menu set aside"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof MerchantScreen sc && sc.getMenu().containerId == id);
        ctx.runOnClient(mc -> {
            MerchantMenu m = (MerchantMenu) mc.player.containerMenu;
            check(m.containerId == id, "the client's menu after back is " + m.containerId + ", expected " + id);
            check(m.getOffers().size() == offers, "trades after back: " + m.getOffers().size() + ", expected " + offers);
            check(m.getTraderXp() == 77, "trader xp after back: " + m.getTraderXp());
            check(m.getTraderLevel() == 3, "trader level after back: " + m.getTraderLevel());
        });
        ctx.runOnClient(mc -> mc.gui.screen().onClose());
        server.waitFor(s -> player(s).containerMenu == player(s).inventoryMenu);
    }

    /** A donkey's inventory, set aside and back: the same container id and screen, its saddle and chest intact. */
    private static void mountComesBackWhole(ClientGameTestContext ctx, TestServerContext server) {
        int id = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            Donkey d = EntityTypes.DONKEY.create(p.level(), EntitySpawnReason.COMMAND);
            d.setNoAi(true);
            d.setPos(p.getX(), p.getY(), p.getZ() + 2);
            d.setTamed(true);
            d.getSlot(499).set(new ItemStack(Items.CHEST));   // as /item replace does: the chest and its inventory
            d.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            p.level().addFreshEntity(d);
            d.openCustomInventoryScreen(p);
            p.containerMenu.getSlot(2).set(new ItemStack(Items.WHEAT, 9));
            return p.containerMenu.containerId;
        });
        ctx.waitFor(mc -> mc.gui.screen() instanceof HorseInventoryScreen sc && sc.getMenu().containerId == id
                && sc.getMenu().getSlot(2).getItem().is(Items.WHEAT));

        server.runOnServer(s -> check(MenuSwitch.openInventory(player(s)), "openInventory refused a SlotKit client"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen);
        server.runOnServer(s -> check(MenuSwitch.setAside(player(s)).map(m -> m.containerId == id).orElse(false),
                "the donkey's inventory isn't set aside"));

        server.runOnServer(s -> check(MenuSwitch.back(player(s)), "back found no mount inventory set aside"));
        ctx.waitFor(mc -> mc.gui.screen() instanceof HorseInventoryScreen sc && sc.getMenu().containerId == id);
        ctx.runOnClient(mc -> {
            var menu = ((HorseInventoryScreen) mc.gui.screen()).getMenu();
            check(mc.player.containerMenu == menu, "the client's menu isn't the mount screen's");
            check(menu.getSlot(0).getItem().is(Items.SADDLE), "the saddle after back: " + menu.getSlot(0).getItem());
            ItemStack wheat = menu.getSlot(2).getItem();
            check(wheat.is(Items.WHEAT) && wheat.getCount() == 9, "the chest's wheat after back: " + wheat);
        });
        server.runOnServer(s -> check(player(s).containerMenu.containerId == id, "back didn't reopen the donkey's inventory"));
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
