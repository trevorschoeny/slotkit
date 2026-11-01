package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.menu.MenuSwitch;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.Window;
import com.trevlar.slotkit.window.ClickOperations;
import com.trevlar.slotkit.window.PlayerActions;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Clicks and switches: a Fabric extended menu isn't set aside, a provider that throws
 * keeps the cursor stack, a switch from inside a click doesn't mute the set-aside menu,
 * a take veto holds on an output slot, and inventory gates hold on a number-key swap, a
 * drop key and an item pickup, and a created shulker-storage group refuses a shulker box
 * by click, number key, shift-click and drag, as vanilla's shulker box slots do.
 */
public class ClickChecks implements ModInitializer, FabricClientGameTest {

    static ExtendedMenuType<ChestMenu, Integer> EXTENDED;
    // The gates and veto below apply only while this check runs, so they can't touch the others.
    static volatile boolean on;
    static final int NO_PICKUP = 7;   // hotbar slot nothing leaves
    static final int STICKS_ONLY = 8; // hotbar slot only sticks enter
    static StorageAttachment.PlayerStorageAttachment<net.minecraft.core.NonNullList<ItemStack>> BOX;

    @Override
    public void onInitialize() {
        EXTENDED = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath("slotkit_checks", "extended"),
                new ExtendedMenuType<ChestMenu, Integer>((id, inv, data) -> new ChestMenu(EXTENDED, id, inv, new SimpleContainer(9), 1),
                        ByteBufCodecs.VAR_INT.cast()));
        Window.slot(ModeScope.BOTH, Address.playerInventory(NO_PICKUP)).gate(gate(true, false));
        Window.slot(ModeScope.BOTH, Address.playerInventory(STICKS_ONLY)).gate(gate(false, true));
        BOX = StorageAttachment.playerAttached("slotkit_checks", "box", 3);
        ParityGroup.register("slotkit_checks:box", SlotSpec.at("box", SlotGroupCategory.SHULKER_STORAGE)
                .count(3).storage(p -> BOX.bind(p)).shiftClickIn(true));
        SlotOperations.veto(ModeScope.BOTH, (ref, op) -> on && op == BehaviorKeys.CLICK_TAKE
                && ref.slot() instanceof net.minecraft.world.inventory.ResultSlot);
    }

    private static SlotGate gate(boolean keepsItem, boolean sticksOnly) {
        return new SlotGate() {
            @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) { return !on || !sticksOnly || stack.is(Items.STICK); }
            @Override public boolean mayPickup(Player player, GatingContext ctx) { return !on || !keepsItem; }
        };
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos chest = server.computeOnServer(s -> player(s).blockPosition().offset(2, 0, 0));
            server.runOnServer(s -> player(s).level().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState()));
            ctx.waitTicks(2);

            extendedMenuIsNotSetAside(ctx, server);
            throwingProviderKeepsTheCursor(ctx, server, chest);
            switchInsideAClickKeepsSyncing(ctx, server, chest);
            on = true;
            try {
                takeVetoHoldsOnAnOutput(server);
                inventoryGatesHold(server);
                shulkerStorageRefusesShulkers(server);
            } finally {
                on = false;
            }
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    // back() would resend a vanilla open packet the client can't build an extended menu from.
    private static void extendedMenuIsNotSetAside(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> player(s).openMenu(new ExtendedMenuProvider<Integer>() {
            @Override public Integer getScreenOpeningData(ServerPlayer p) { return 7; }
            @Override public Component getDisplayName() { return Component.literal("Extended"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ChestMenu(EXTENDED, id, inv, new SimpleContainer(9), 1);
            }
        }));
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            MenuSwitch.open(player(s), tab());
            check(MenuSwitch.setAside(player(s)).isEmpty(), "an extended menu was set aside, and back() would resend a packet its client can't build");
            player(s).closeContainer();
        });
        ctx.waitTicks(3);
    }

    // The front is gone before the provider runs; the cursor stack must survive it throwing.
    private static void throwingProviderKeepsTheCursor(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> {
            player(s).getInventory().clearContent();
            player(s).openMenu((MenuProvider) player(s).level().getBlockEntity(chest));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.containerMenu.setCarried(new ItemStack(Items.DIAMOND, 3));
            try {
                MenuSwitch.open(p, new SimpleMenuProvider((id, inv, pl) -> { throw new IllegalStateException("provider failed"); },
                        Component.literal("Broken")));
            } catch (IllegalStateException expected) {
                // the error still reaches the caller
            }
            int kept = p.getInventory().countItem(Items.DIAMOND) + (p.containerMenu.getCarried().is(Items.DIAMOND) ? p.containerMenu.getCarried().getCount() : 0);
            check(kept == 3, "a provider that threw left the player " + kept + " of 3 cursor diamonds");
            p.closeContainer();
            p.getInventory().clearContent();
        });
        ctx.waitTicks(3);
    }

    // Vanilla unmutes only the menu in front after a click, so a switch inside one must unmute the one it sets aside.
    private static void switchInsideAClickKeepsSyncing(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> player(s).openMenu((MenuProvider) player(s).level().getBlockEntity(chest)));
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            p.containerMenu.suppressRemoteUpdates();   // what handleContainerClick does around clicked()
            MenuSwitch.open(p, tab());
            p.containerMenu.resumeRemoteUpdates();
            MenuSwitch.back(p);
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> ((Container) player(s).level().getBlockEntity(chest)).setItem(0, new ItemStack(Items.EMERALD)));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> check(mc.player.containerMenu.slots.get(0).getItem().is(Items.EMERALD),
                "the chest brought back after a switch inside a click shows " + mc.player.containerMenu.slots.get(0).getItem()));
        server.runOnServer(s -> {
            ((Container) player(s).level().getBlockEntity(chest)).clearContent();
            player(s).closeContainer();
        });
        ctx.waitTicks(3);
    }

    // With the same item on the cursor, a click on an output slot still lifts its stack: a take.
    private static void takeVetoHoldsOnAnOutput(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            AbstractContainerMenu menu = p.inventoryMenu;
            menu.slots.get(0).set(new ItemStack(Items.STICK, 4));
            menu.setCarried(new ItemStack(Items.STICK));
            check(ClickOperations.refuses(menu, 0, 0, ContainerInput.PICKUP, p), "a take veto let a click lift an output stack onto a matching cursor");
            menu.setCarried(ItemStack.EMPTY);
            menu.slots.get(0).set(ItemStack.EMPTY);
        });
    }

    // A number-key swap, a drop key and an item pickup write the inventory without asking its slot.
    private static void inventoryGatesHold(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            AbstractContainerMenu menu = p.inventoryMenu;

            inv.setItem(9, new ItemStack(Items.DIRT));
            check(ClickOperations.refuses(menu, 9, STICKS_ONLY, ContainerInput.SWAP, p), "a number-key swap put dirt in a sticks-only slot");
            inv.setItem(NO_PICKUP, new ItemStack(Items.GOLD_INGOT));
            inv.setItem(9, ItemStack.EMPTY);
            check(ClickOperations.refuses(menu, 9, NO_PICKUP, ContainerInput.SWAP, p), "a number-key swap took from a slot nothing leaves");

            inv.setSelectedSlot(NO_PICKUP);
            check(PlayerActions.refuses(p, ServerboundPlayerActionPacket.Action.DROP_ITEM), "the drop key took from a slot nothing leaves");
            inv.setSelectedSlot(0);

            // Every slot but the sticks-only one full: dirt has nowhere to go.
            for (int i = 0; i < 36; i++) if (i != STICKS_ONLY) inv.setItem(i, new ItemStack(Items.STONE, 64));
            boolean added = inv.add(new ItemStack(Items.DIRT));
            check(!added && inv.getItem(STICKS_ONLY).isEmpty(), "a pickup put dirt in a sticks-only slot");
            inv.clearContent();
        });
    }

    // A created SHULKER_STORAGE slot is a plain Slot, so it carries vanilla's ShulkerBoxSlot rule itself.
    private static void shulkerStorageRefusesShulkers(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            AbstractContainerMenu menu = p.inventoryMenu;
            Slot box = null;
            for (Slot slot : menu.slots) if (slot instanceof CreatedSlot c && c.panelId().equals("slotkit_checks:box:box")) { box = slot; break; }
            if (box == null) { check(false, "no shulker-storage slot on the inventory menu"); return; }
            int at = box.index;
            int hotbarSlot = 36;   // inventory menu slot of hotbar 0

            menu.setCarried(new ItemStack(Items.SHULKER_BOX));
            menu.clicked(at, 0, ContainerInput.PICKUP, p);
            check(!box.hasItem() && menu.getCarried().is(Items.SHULKER_BOX), "a click put a shulker box in a shulker-storage slot");
            menu.setCarried(ItemStack.EMPTY);

            inv.setItem(0, new ItemStack(Items.SHULKER_BOX));
            menu.clicked(at, 0, ContainerInput.SWAP, p);
            check(!box.hasItem(), "a number-key swap put a shulker box in a shulker-storage slot");

            menu.clicked(hotbarSlot, 0, ContainerInput.QUICK_MOVE, p);
            boolean anyBox = false;
            for (Slot slot : menu.slots) if (slot instanceof CreatedSlot c && c.panelId().equals("slotkit_checks:box:box") && slot.hasItem()) anyBox = true;
            check(!anyBox, "a shift-click put a shulker box in a shulker-storage slot");
            inv.clearContent();

            menu.setCarried(new ItemStack(Items.SHULKER_BOX));
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT, p);
            menu.clicked(at, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, p);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT, p);
            check(!box.hasItem(), "a drag put a shulker box in a shulker-storage slot");
            menu.setCarried(ItemStack.EMPTY);

            // anything else still goes in
            menu.setCarried(new ItemStack(Items.DIRT));
            menu.clicked(at, 0, ContainerInput.PICKUP, p);
            check(box.getItem().is(Items.DIRT), "a click didn't put dirt in a shulker-storage slot: " + box.getItem());
            box.set(ItemStack.EMPTY);
            menu.setCarried(ItemStack.EMPTY);
            inv.clearContent();
        });
    }

    private static MenuProvider tab() {
        return new SimpleMenuProvider((id, inv, p) -> ChestMenu.oneRow(id, inv), Component.literal("Tab"));
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static final List<String> FAILURES = new CopyOnWriteArrayList<>();

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
