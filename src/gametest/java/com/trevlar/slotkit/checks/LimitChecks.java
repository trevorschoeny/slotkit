package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.window.Automation;
import com.trevlar.slotkit.api.window.SlotOperations;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * A registered {@link SlotOperations.Limit} caps every hand that fills a slot: a click
 * place, a shift-click, an item pickup, a real hopper, and a slot inside a box item
 * ({@link Automation#room}). A limit that throws is skipped and the rest still cap.
 */
public class LimitChecks implements ModInitializer, FabricClientGameTest {

    // The limit speaks only for these, while the check runs: slot 0 of each, diamonds, 4 at most.
    static volatile Container chest;
    static volatile ItemStack box = ItemStack.EMPTY;
    static volatile boolean inventory;
    static volatile boolean on;

    @Override
    public void onInitialize() {
        // Registered first, so the good limit below has to survive it.
        SlotOperations.limit(ModeScope.BOTH, (ref, group, incoming) -> {
            if (on) throw new IllegalStateException("a broken consumer limit, thrown on purpose");
            return SlotOperations.NO_LIMIT;
        });
        SlotOperations.limit(ModeScope.BOTH, (ref, group, incoming) -> {
            if (!incoming.is(Items.DIAMOND) || ref.containerSlot() != 0) return SlotOperations.NO_LIMIT;
            Container c = ref.container();
            boolean capped = c == chest
                    || (inventory && c instanceof Inventory)
                    || (c instanceof ItemContents held && held.item(null) == box);
            return capped ? 4 : SlotOperations.NO_LIMIT;
        });
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        on = true;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos base = server.computeOnServer(s -> player(s).blockPosition().offset(-3, 0, 0));
            ctx.waitTicks(2);

            boxSlotIsCapped(server);
            clicksAreCapped(ctx, server, base);
            pickupIsCapped(ctx, server);
            returnIsCapped(server);
            swapIsCapped(server);
            hopperIsCapped(ctx, server, base.offset(0, 0, 4));
        } finally {
            on = false;
            chest = null;
            box = ItemStack.EMPTY;
            inventory = false;
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    private static void boxSlotIsCapped(TestServerContext server) {
        server.runOnServer(s -> {
            box = new ItemStack(Items.SHULKER_BOX);
            check(Automation.room(box, 0, new ItemStack(Items.DIAMOND, 9)) == 4, "box: room() doesn't cap diamonds at 4");
            check(Automation.room(box, 1, new ItemStack(Items.DIAMOND, 9)) == 9, "box: room() caps an unlimited slot");
            check(Automation.room(box, 0, new ItemStack(Items.DIRT, 9)) == 9, "box: room() caps an item the limit ignores");
            box = ItemStack.EMPTY;
        });
    }

    private static void clicksAreCapped(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
            chest = container(level, at);
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.openMenu((MenuProvider) level.getBlockEntity(at));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            AbstractContainerMenu menu = p.containerMenu;
            Container c = chest;
            menu.setCarried(new ItemStack(Items.DIAMOND, 10));
            menu.clicked(0, 0, ContainerInput.PICKUP, p);
            check(c.getItem(0).getCount() == 4 && menu.getCarried().getCount() == 6,
                    "click: the limit let " + c.getItem(0).getCount() + " in, cursor kept " + menu.getCarried().getCount());
            menu.setCarried(ItemStack.EMPTY);
            c.clearContent();
            menu.slots.get(27).set(new ItemStack(Items.DIAMOND, 10));
            menu.clicked(27, 0, ContainerInput.QUICK_MOVE, p);
            check(c.getItem(0).getCount() == 4 && c.getItem(1).getCount() == 6,
                    "shift-click: diamonds went " + c.getItem(0) + " / " + c.getItem(1) + ", not 4 / 6");
            p.closeContainer();
            p.getInventory().clearContent();
            chest = null;
        });
        ctx.waitTicks(3);
    }

    // A real item entity touched by the player: vanilla's pickup, into inventory slot 0 first.
    private static void pickupIsCapped(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            inventory = true;
            ItemEntity drop = new ItemEntity(level(s), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 10));
            drop.setNoPickUpDelay();
            level(s).addFreshEntity(drop);
            drop.playerTouch(p);
            Inventory inv = p.getInventory();
            check(inv.getItem(0).getCount() == 4 && inv.getItem(1).getCount() == 6,
                    "pickup: diamonds went " + inv.getItem(0) + " / " + inv.getItem(1) + ", not 4 / 6");
            drop.discard();
            inventory = false;
            inv.clearContent();
        });
        ctx.waitTicks(2);
    }

    // Items going back to the inventory: vanilla splits to the stack size and drops what
    // add() leaves, so without the cap on the split, slot 0 at 2 of 4 lost the rest.
    private static void returnIsCapped(TestServerContext server) {
        server.runOnServer(s -> {
            Inventory inv = player(s).getInventory();
            inv.clearContent();
            inventory = true;
            inv.setItem(0, new ItemStack(Items.DIAMOND, 2));
            inv.placeItemBackInInventory(new ItemStack(Items.DIAMOND, 10), net.minecraft.util.Prediction.SERVER_ONLY);
            check(inv.getItem(0).getCount() == 4 && inv.countItem(Items.DIAMOND) == 12,
                    "return: slot 0 holds " + inv.getItem(0) + " and the inventory " + inv.countItem(Items.DIAMOND) + " diamonds, not 4 and 12");
            inventory = false;
            inv.clearContent();
        });
    }

    // F with no screen: the offhand's ten diamonds would land whole in hotbar slot 0.
    private static void swapIsCapped(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            inventory = true;
            inv.setSelectedSlot(0);
            inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIAMOND, 10));
            check(com.trevlar.slotkit.window.PlayerActions.refuses(p, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND),
                    "swap: ten diamonds may swap into a slot limited to 4");
            inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIAMOND, 3));
            check(!com.trevlar.slotkit.window.PlayerActions.refuses(p, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND),
                    "swap: three diamonds are refused by a limit of 4");
            inventory = false;
            inv.clearContent();
        });
    }

    private static void hopperIsCapped(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
            chest = container(level, at);
            level.setBlockAndUpdate(at.above(), Blocks.HOPPER.defaultBlockState());
            container(level, at.above()).setItem(0, new ItemStack(Items.DIAMOND, 6));
        });
        ctx.waitTicks(80);
        server.runOnServer(s -> {
            Container c = chest;
            check(c.getItem(0).getCount() == 4 && c.getItem(1).getCount() == 2,
                    "hopper: diamonds went " + c.getItem(0) + " / " + c.getItem(1) + ", not 4 / 2");
            chest = null;
        });
    }

    private static Container container(ServerLevel level, BlockPos pos) {
        return (Container) level.getBlockEntity(pos);
    }

    private static ServerLevel level(MinecraftServer s) {
        return (ServerLevel) player(s).level();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static final List<String> FAILURES = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
