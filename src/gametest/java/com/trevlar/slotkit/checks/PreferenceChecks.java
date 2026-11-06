package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.window.SlotOperations;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
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
 * A registered {@link SlotOperations.Preference} reorders where a pickup and a
 * shift-click land, Inventory Plus's reservation order: partial stacks first, then an
 * empty preferred slot, then any other empty slot. With nothing preferred, vanilla's
 * order holds.
 */
public class PreferenceChecks implements ModInitializer, FabricClientGameTest {

    // The preference speaks only while the check runs: inventory slot PREFERRED, diamonds.
    static final int PREFERRED = 20;
    static volatile boolean on;

    @Override
    public void onInitialize() {
        SlotOperations.prefer(ModeScope.BOTH, (ref, group, incoming) -> on && incoming.is(Items.DIAMOND)
                && ref.container() instanceof Inventory && ref.containerSlot() == PREFERRED);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos at = server.computeOnServer(s -> player(s).blockPosition().offset(-3, 0, 0));
            ctx.waitTicks(2);

            // Pickups. Slot 12 holds a partial stack; slot 0 is the earliest empty slot.
            on = true;
            pickup(ctx, server, 12, "pickup: a partial stack fills before an empty preferred slot",
                    inv -> inv.getItem(12).getCount() == 15 && inv.getItem(PREFERRED).isEmpty());
            pickup(ctx, server, -1, "pickup: an empty preferred slot beats an earlier empty slot",
                    inv -> inv.getItem(PREFERRED).getCount() == 5 && inv.getItem(0).isEmpty());
            on = false;
            pickup(ctx, server, -1, "pickup: with nothing preferred, vanilla's first empty slot",
                    inv -> inv.getItem(0).getCount() == 5 && inv.getItem(PREFERRED).isEmpty());

            // Shift-click from a chest. Vanilla walks the inventory hotbar end first (slot 8).
            server.runOnServer(s -> {
                level(s).setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                player(s).openMenu((MenuProvider) level(s).getBlockEntity(at));
            });
            ctx.waitTicks(3);
            on = true;
            shiftClick(server, 12, "shift-click: a partial stack fills before an empty preferred slot",
                    inv -> inv.getItem(12).getCount() == 15 && inv.getItem(PREFERRED).isEmpty());
            shiftClick(server, -1, "shift-click: an empty preferred slot beats vanilla's first empty slot",
                    inv -> inv.getItem(PREFERRED).getCount() == 5 && inv.getItem(8).isEmpty());
            on = false;
            shiftClick(server, -1, "shift-click: with nothing preferred, vanilla's first empty slot",
                    inv -> inv.getItem(8).getCount() == 5 && inv.getItem(PREFERRED).isEmpty());
            server.runOnServer(s -> player(s).closeContainer());
        } finally {
            on = false;
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    // Five diamonds touched by the player, with {@code partial} holding ten first (-1: none).
    private static void pickup(ClientGameTestContext ctx, TestServerContext server, int partial, String what,
                               java.util.function.Predicate<Inventory> ok) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            if (partial >= 0) inv.setItem(partial, new ItemStack(Items.DIAMOND, 10));
            ItemEntity drop = new ItemEntity(level(s), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 5));
            drop.setNoPickUpDelay();
            level(s).addFreshEntity(drop);
            drop.playerTouch(p);
            check(ok.test(inv), what + " (got " + layout(inv) + ")");
            drop.discard();
            inv.clearContent();
        });
        ctx.waitTicks(2);
    }

    // Five diamonds shift-clicked out of the open chest's slot 0.
    private static void shiftClick(TestServerContext server, int partial, String what,
                                   java.util.function.Predicate<Inventory> ok) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            AbstractContainerMenu menu = p.containerMenu;
            Inventory inv = p.getInventory();
            inv.clearContent();
            if (partial >= 0) inv.setItem(partial, new ItemStack(Items.DIAMOND, 10));
            Container chest = menu.slots.get(0).container;
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
            check(ok.test(inv) && chest.getItem(0).isEmpty(), what + " (got " + layout(inv) + ")");
            inv.clearContent();
        });
    }

    private static String layout(Inventory inv) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) out.append(i).append('=').append(inv.getItem(i).getCount()).append(' ');
        }
        return out.toString().trim();
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
