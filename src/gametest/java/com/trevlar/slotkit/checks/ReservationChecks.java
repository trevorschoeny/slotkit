package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.Automation;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Reserved slots: each hand that puts items in a slot (a click, a shift-click, a hopper, a
 * dropper, a copper golem, a mod writing into a box inside a chest) puts only the reserved
 * item, up to its cap; reserving pushes out what doesn't belong; a box carries its
 * reservations when broken and to the client, and a chest drops them.
 */
public class ReservationChecks implements FabricClientGameTest {

    // Made per use: a stack made when the class loads predates the items' bound components.
    private static Reservation diamonds4() {
        return Reservation.of(new ItemStack(Items.DIAMOND)).withCap(4);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos base = server.computeOnServer(s -> player(s).blockPosition().offset(3, 0, 0));
            ctx.waitTicks(2);

            hopperPutsOnlyTheReservedItem(ctx, server, base);
            dropperSkipsTheReservedSlot(ctx, server, base.offset(0, 0, 3));
            golemSkipsTheReservedSlot(server, base.offset(0, 0, 6));
            writeIntoANestedBoxIsSettled(server);
            // Within reach of the player, so the menus it opens stay open.
            clicksHonorTheReservation(ctx, server, base.offset(-6, 0, 0));
            reservingPushesOutWhatDoesNotBelong(ctx, server, base.offset(-6, 0, 2));
            boxKeepsItsReservationsAndChestDropsThem(ctx, server, base.offset(-3, 0, -3));
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    // A real hopper over a chest whose slot 0 is reserved for diamonds, 4 at most.
    private static void hopperPutsOnlyTheReservedItem(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            Reservations.set(container(level, chest), 0, diamonds4());
            level.setBlockAndUpdate(chest.above(), Blocks.HOPPER.defaultBlockState());
            Container hopper = container(level, chest.above());
            hopper.setItem(0, new ItemStack(Items.DIRT, 2));
            hopper.setItem(1, new ItemStack(Items.DIAMOND, 6));
        });
        ctx.waitTicks(120);
        server.runOnServer(s -> {
            Container c = container(level(s), chest);
            check(c.getItem(0).is(Items.DIAMOND) && c.getItem(0).getCount() == 4, "hopper: reserved slot holds " + c.getItem(0) + ", not 4 diamonds");
            check(c.getItem(1).is(Items.DIRT) && c.getItem(1).getCount() == 2, "hopper: dirt went to " + c.getItem(1) + ", not slot 1");
            check(c.getItem(2).is(Items.DIAMOND) && c.getItem(2).getCount() == 2, "hopper: the diamonds over the cap went to " + c.getItem(2));
        });
    }

    // A real dropper firing dirt into a chest whose slot 0 is reserved for diamonds.
    private static void dropperSkipsTheReservedSlot(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            Reservations.set(container(level, chest), 0, diamonds4());
            BlockPos dropper = chest.west();
            level.setBlockAndUpdate(dropper, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            container(level, dropper).setItem(0, new ItemStack(Items.DIRT));
            level.setBlockAndUpdate(dropper.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            Container c = container(level(s), chest);
            check(c.getItem(0).isEmpty(), "dropper: reserved slot took " + c.getItem(0));
            check(c.getItem(1).is(Items.DIRT), "dropper: dirt isn't in slot 1 but " + c.getItem(1));
        });
    }

    // Vanilla's golem walk would put its dirt in slot 0; the settle moves it on.
    private static void golemSkipsTheReservedSlot(TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            Container c = container(level, chest);
            Reservations.set(c, 0, diamonds4());
            PathfinderMob golem = (PathfinderMob) net.minecraft.world.entity.EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
            golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 5));
            try {
                Method put = TransportItemsBetweenContainers.class.getDeclaredMethod("addItemsToContainer", PathfinderMob.class, Container.class);
                put.setAccessible(true);
                ItemStack left = (ItemStack) put.invoke(null, golem, c);
                check(left.isEmpty(), "golem: kept " + left);
            } catch (ReflectiveOperationException e) {
                check(false, "golem: " + e);
            }
            check(c.getItem(0).isEmpty(), "golem: reserved slot took " + c.getItem(0));
            check(c.getItem(1).is(Items.DIRT) && c.getItem(1).getCount() == 5, "golem: dirt isn't in slot 1 but " + c.getItem(1));
            golem.discard();
        });
    }

    // What Savvy Copper Golems does: writes the golem's dirt straight into a box inside the chest.
    private static void writeIntoANestedBoxIsSettled(TestServerContext server) {
        server.runOnServer(s -> {
            SimpleContainer chest = new SimpleContainer(3);
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            Reservations.set(box, 0, diamonds4());
            chest.setItem(0, box);
            check(Automation.room(box, 0, new ItemStack(Items.DIRT, 5)) == 0, "nested: room() lets dirt into the box's reserved slot");
            check(Automation.room(box, 0, new ItemStack(Items.DIAMOND, 9)) == 4, "nested: room() doesn't cap diamonds at 4");

            ItemStack held = new ItemStack(Items.DIRT, 5);
            ItemStack[] before = {chest.getItem(0).copy(), ItemStack.EMPTY, ItemStack.EMPTY};
            SimpleContainer inner = new SimpleContainer(27);
            inner.setItem(0, held.copy());
            chest.getItem(0).set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inner.getItems()));
            ItemStack left = Automation.settle(chest, before, held, ItemStack.EMPTY);

            SimpleContainer after = new SimpleContainer(27);
            ItemStack settled = chest.getItem(0);
            settled.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(after.getItems());
            check(left.isEmpty(), "nested: the mover got back " + left);
            check(after.getItem(0).isEmpty(), "nested: the box's reserved slot kept " + after.getItem(0));
            check(after.getItem(1).is(Items.DIRT) && after.getItem(1).getCount() == 5, "nested: dirt isn't in box slot 1 but " + after.getItem(1));
            check(Reservations.get(settled, 0).equals(diamonds4()), "nested: the box lost its reservation: " + Reservations.get(settled, 0) + " " + settled.get(DataComponents.CUSTOM_DATA));
        });
    }

    private static void clicksHonorTheReservation(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            Reservations.set(container(level, chest), 0, diamonds4());
            ServerPlayer p = player(s);
            p.getInventory().clearContent();
            p.openMenu((MenuProvider) level.getBlockEntity(chest));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            AbstractContainerMenu menu = p.containerMenu;
            Container c = container(level(s), chest);
            menu.setCarried(new ItemStack(Items.DIRT, 3));
            menu.clicked(0, 0, ContainerInput.PICKUP, p);
            check(c.getItem(0).isEmpty(), "click: dirt went into the reserved slot");
            menu.setCarried(ItemStack.EMPTY);
            menu.slots.get(27).set(new ItemStack(Items.DIRT, 3));
            menu.clicked(27, 0, ContainerInput.QUICK_MOVE, p);
            check(c.getItem(0).isEmpty() && c.getItem(1).is(Items.DIRT), "shift-click: dirt went to " + c.getItem(0) + " / " + c.getItem(1));
            menu.setCarried(new ItemStack(Items.DIAMOND, 10));
            menu.clicked(0, 0, ContainerInput.PICKUP, p);
            check(c.getItem(0).getCount() == 4 && menu.getCarried().getCount() == 6,
                    "click: the cap let " + c.getItem(0).getCount() + " in, cursor kept " + menu.getCarried().getCount());
            menu.setCarried(ItemStack.EMPTY);
            p.closeContainer();
            p.getInventory().clearContent();
        });
        ctx.waitTicks(3);
    }

    private static void reservingPushesOutWhatDoesNotBelong(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            container(level, chest).setItem(2, new ItemStack(Items.DIRT, 3));
            container(level, chest).setItem(3, new ItemStack(Items.DIAMOND, 10));
            player(s).getInventory().clearContent();
            player(s).openMenu((MenuProvider) level.getBlockEntity(chest));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Container c = container(level(s), chest);
            Reservations.reserve(p, p.containerMenu.slots.get(2), diamonds4());
            Reservations.reserve(p, p.containerMenu.slots.get(3), diamonds4());
            check(c.getItem(2).isEmpty() && p.getInventory().countItem(Items.DIRT) == 3, "reserve: the dirt wasn't moved to the inventory");
            check(c.getItem(3).getCount() == 4 && p.getInventory().countItem(Items.DIAMOND) == 6, "reserve: over the cap, " + c.getItem(3) + " stayed");
            p.closeContainer();
            p.getInventory().clearContent();
        });
        ctx.waitTicks(3);
    }

    private static void boxKeepsItsReservationsAndChestDropsThem(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
        BlockPos box = at.offset(0, 0, 2);
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
            Reservations.set(container(level, at), 0, diamonds4());
            level.destroyBlock(at, false);
            level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
            check(Reservations.limit(com.trevlar.slotkit.api.window.SlotRef.of(container(level, at), 0, null), new ItemStack(Items.DIAMOND, 64)) == 64,
                    "chest: a new chest in its place still has the reservation");

            level.setBlockAndUpdate(box, Blocks.SHULKER_BOX.defaultBlockState());
            Reservations.set(container(level, box), 0, diamonds4());
            level.destroyBlock(box, true);
        });
        ctx.waitTicks(2);
        server.runOnServer(s -> {
            ItemStack dropped = ItemStack.EMPTY;
            for (ItemEntity e : level(s).getEntitiesOfClass(ItemEntity.class, new AABB(box).inflate(3))) {
                if (e.getItem().is(Items.SHULKER_BOX)) dropped = e.getItem().copy();
                e.discard();
            }
            check(Reservations.get(dropped, 0).equals(diamonds4()), "box: broken, its item has " + Reservations.get(dropped, 0) + " " + dropped.getComponentsPatch());
            player(s).getInventory().setItem(9, dropped);
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> check(Reservations.get(mc.player.getInventory().getItem(9), 0).equals(diamonds4()),
                "box: the client doesn't see its reservation, so it can't draw the ghost"));
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
