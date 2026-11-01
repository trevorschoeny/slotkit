package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotRef;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Clicks and switches: a Fabric extended menu isn't set aside, a provider that throws
 * keeps the cursor stack, a switch from inside a click doesn't mute the set-aside menu,
 * a take veto holds on an output slot, and inventory gates hold on a number-key swap, a
 * drop key and an item pickup, and a created shulker-storage group refuses a shulker box
 * by click, number key, shift-click and drag, as vanilla's shulker box slots do. A put
 * veto sees the incoming stack at every put seam, and "only where this item already is"
 * (SlotGroups.holds) holds by click, number key and drag; holds answers for a menu group
 * and for the player's inventory with no menu.
 */
public class ClickChecks implements ModInitializer, FabricClientGameTest {

    static ExtendedMenuType<ChestMenu, Integer> EXTENDED;
    // The gates and veto below apply only while this check runs, so they can't touch the others.
    static volatile boolean on;
    static final int NO_PICKUP = 7;   // hotbar slot nothing leaves
    static final int STICKS_ONLY = 8; // hotbar slot only sticks enter
    // The put veto below speaks only while this is set; it records what each put operation was handed.
    static volatile boolean matching;
    static final Map<BehaviorKey<?>, ItemStack> SEEN = new ConcurrentHashMap<>();
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
        // Matching, as Inventory Plus's Reach checklists use it: the box takes only an item it already holds.
        SlotOperations.vetoPut(ModeScope.BOTH, (ref, group, op, incoming) -> {
            if (!matching) return false;
            if (group != null) SEEN.put(op, incoming.copy());
            return isBox(ref.slot()) && !SlotGroups.holds(ref, incoming);
        });
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
            matching = true;
            try {
                putVetoSeesTheIncomingStack(server);
                holdsAnswersForMenuAndInventory(server);
            } finally {
                matching = false;
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

    private static boolean isBox(Slot slot) {
        return slot instanceof CreatedSlot c && c.panelId().equals("slotkit_checks:box:box");
    }

    private static void seen(BehaviorKey<?> op, net.minecraft.world.item.Item item, String seam) {
        ItemStack got = SEEN.remove(op);
        check(got != null && got.is(item), "a put veto at the " + seam + " seam was handed " + got + ", not " + item);
    }

    // Every put seam hands the put veto the stack about to land; Matching refuses dirt and lets cobblestone in.
    private static void putVetoSeesTheIncomingStack(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            AbstractContainerMenu menu = p.inventoryMenu;
            List<Slot> box = menu.slots.stream().filter(ClickChecks::isBox).toList();
            if (box.size() < 3) { check(false, "the box group isn't on the inventory menu"); return; }
            SEEN.clear();

            // click
            menu.setCarried(new ItemStack(Items.DIRT));
            menu.clicked(box.get(0).index, 0, ContainerInput.PICKUP, p);
            seen(BehaviorKeys.CLICK_PUT, Items.DIRT, "click");
            check(!box.get(0).hasItem(), "Matching let a click put dirt in an empty box");
            box.get(0).set(new ItemStack(Items.COBBLESTONE));
            menu.setCarried(new ItemStack(Items.COBBLESTONE, 5));
            menu.clicked(box.get(1).index, 0, ContainerInput.PICKUP, p);
            check(box.get(1).getItem().is(Items.COBBLESTONE), "Matching refused cobblestone in a box that holds cobblestone");
            menu.setCarried(ItemStack.EMPTY);

            // number key
            inv.setItem(0, new ItemStack(Items.DIRT));
            menu.clicked(box.get(2).index, 0, ContainerInput.SWAP, p);
            seen(BehaviorKeys.HOTBAR_SWAP, Items.DIRT, "number-key");
            check(!box.get(2).hasItem(), "Matching let a number-key swap put dirt in the box");
            inv.setItem(0, ItemStack.EMPTY);

            // drag
            menu.setCarried(new ItemStack(Items.DIRT));
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT, p);
            menu.clicked(box.get(2).index, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, p);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT, p);
            seen(BehaviorKeys.DRAG_FILL, Items.DIRT, "drag");
            check(!box.get(2).hasItem(), "Matching let a drag put dirt in the box");
            menu.setCarried(ItemStack.EMPTY);

            // shift-click
            inv.setItem(0, new ItemStack(Items.DIRT));
            menu.clicked(36, 0, ContainerInput.QUICK_MOVE, p);
            seen(BehaviorKeys.SHIFT_CLICK_IN, Items.DIRT, "shift-click");
            check(box.stream().noneMatch(b -> b.getItem().is(Items.DIRT)), "Matching let a shift-click put dirt in the box");
            inv.clearContent();

            // a mod's own click, sent as an operation: the carried put operation is judged with the stack
            menu.setCarried(new ItemStack(Items.DIRT));
            SlotOperations.as(BehaviorKeys.COLLECT, BehaviorKeys.DRAG_FILL,
                    () -> menu.clicked(box.get(2).index, 0, ContainerInput.PICKUP, p));
            seen(BehaviorKeys.DRAG_FILL, Items.DIRT, "as(...) click");
            check(!box.get(2).hasItem(), "Matching let a mod's own click put dirt in the box");
            menu.setCarried(ItemStack.EMPTY);

            // world pickup
            inv.add(new ItemStack(Items.APPLE));
            seen(BehaviorKeys.INVENTORY_INSERT, Items.APPLE, "world pickup");

            for (Slot b : box) b.set(ItemStack.EMPTY);
            inv.clearContent();
        });
    }

    private static void holdsAnswersForMenuAndInventory(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Inventory inv = p.getInventory();
            inv.clearContent();
            AbstractContainerMenu menu = p.inventoryMenu;
            List<Slot> box = menu.slots.stream().filter(ClickChecks::isBox).toList();
            if (box.size() < 3) return;
            box.get(0).set(new ItemStack(Items.COBBLESTONE));
            SlotRef emptyBoxSlot = SlotRef.of(menu, box.get(2), p);
            check(SlotGroups.holds(emptyBoxSlot, new ItemStack(Items.COBBLESTONE)), "holds missed cobblestone elsewhere in the box");
            check(!SlotGroups.holds(emptyBoxSlot, new ItemStack(Items.DIRT)), "holds found dirt in a box with none");
            box.get(0).set(ItemStack.EMPTY);

            // no menu: the hotbar holds stone, the main inventory doesn't
            inv.setItem(3, new ItemStack(Items.STONE));
            check(SlotGroups.holds(SlotRef.of(inv, 5, p), new ItemStack(Items.STONE)), "holds missed stone in the hotbar with no menu");
            check(!SlotGroups.holds(SlotRef.of(inv, 20, p), new ItemStack(Items.STONE)), "holds counted the hotbar's stone as the main inventory's");
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
