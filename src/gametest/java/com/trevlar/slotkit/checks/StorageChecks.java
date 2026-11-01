package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.menu.CustomContainerMenu;
import com.trevlar.slotkit.api.menu.CustomMenu;
import com.trevlar.slotkit.api.slot.DropRule;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.window.PersistentContainerKey;
import com.trevlar.slotkit.core.ActiveCounts;
import com.trevlar.slotkit.core.attachment.PlayerSlotCapture;

import com.mojang.serialization.Codec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Items and slot state across the paths that end something: a block breaking, a grave
 * restoring, a death, a custom menu closing, a panel showing, a dimension change, and
 * server code writing with no player.
 */
public class StorageChecks implements ModInitializer, FabricClientGameTest {

    static StorageAttachment<BlockEntity, NonNullList<ItemStack>> BOX;
    static StorageAttachment.PlayerStorageAttachment<NonNullList<ItemStack>> DROPPED;
    static StorageAttachment.PlayerStorageAttachment<NonNullList<ItemStack>> KEPT;
    static SlotStateChannel<Boolean> MARK;
    static SlotStateChannel<Boolean> LOCK;
    static CustomMenu MENU;
    static final String COUNTED = "slotkit_checks:counted";

    @Override
    public void onInitialize() {
        BOX = StorageAttachment.blockScoped("slotkit_checks", "box", 3);
        DROPPED = StorageAttachment.playerAttached("slotkit_checks", "dropped", 2).dropsOnDeath(DropRule.DROP);
        KEPT = StorageAttachment.playerAttached("slotkit_checks", "kept", 1).dropsOnDeath(DropRule.KEEP);
        MARK = SlotState.register(ModeScope.BOTH, id("mark"), Codec.BOOL, ByteBufCodecs.BOOL.cast(), false,
                SlotStateChannel.Visibility.SHARED);
        LOCK = SlotState.register(ModeScope.BOTH, id("lock"), Codec.BOOL, ByteBufCodecs.BOOL.cast(), false);
        ActiveCounts.declare(COUNTED, 1, 3);
        MENU = CustomMenu.define(id("menu"), (type, syncId, inv) -> CustomContainerMenu.builder(type)
                        .panel("slotkit_checks:menu:main", p -> p.group("items", SlotGroupCategory.CHEST_STORAGE, EphemeralStorage.of(2)))
                        .panel("slotkit_checks:menu:extra", p -> p.group("extra", SlotGroupCategory.CHEST_STORAGE, EphemeralStorage.of(1))
                                .hidden().toggleable())
                        .build(syncId))
                .title(Component.literal("Checks"))
                .validWhen(p -> p.isAlive())
                .register();
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            BlockPos chest = server.computeOnServer(s -> player(s).blockPosition().offset(2, 0, 0));
            server.runOnServer(s -> player(s).level().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState()));
            ctx.waitTicks(2);

            brokenBlockDropsOnce(ctx, server, chest);
            graveRestoreKeepsWhatIsThere(server);
            graveCaptureFollowsDropRules(server);
            deathResetsActiveCounts(ctx, server);
            customMenuHandsBackItsItems(ctx, server);
            shownPanelShowsItsItems(ctx, server);
            serverWritesReachTheClient(ctx, server, chest);
            dimensionChangeKeepsTheLock(ctx, server);
            liveItemTakenByAHopperIsStale(server);
        }
        // Every check runs, so one run names every path that broke.
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
    }

    // A block-scoped storage empties when its block breaks, so an open menu can't hand the items out again.
    private static void brokenBlockDropsOnce(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
        BlockPos pos = at.offset(0, 0, 3);
        server.runOnServer(s -> {
            var level = player(s).level();
            level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
            BOX.bind(level.getBlockEntity(pos)).setStack(0, new ItemStack(Items.DIAMOND, 4));
        });
        BlockEntity be = server.computeOnServer(s -> player(s).level().getBlockEntity(pos));
        server.runOnServer(s -> player(s).level().destroyBlock(pos, false));
        ctx.waitTicks(2);
        int dropped = server.computeOnServer(s -> diamondsNear(s, pos));
        ItemStack left = server.computeOnServer(s -> BOX.bind(be).getStack(0));
        check(dropped == 4, "the broken block dropped " + dropped + " diamonds, not 4");
        check(left.isEmpty(), "the broken block's storage still holds " + left);
        server.runOnServer(s -> player(s).level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8)).forEach(ItemEntity::discard));
    }

    // A grave's stack doesn't overwrite what the player put in the slot since.
    private static void graveRestoreKeepsWhatIsThere(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            DROPPED.bind(p).setStack(0, new ItemStack(Items.EMERALD));
            boolean restored = PlayerSlotCapture.restore(p, new PlayerSlotCapture.CapturedSlot(
                    id("dropped"), 0, new ItemStack(Items.DIAMOND)));
            check(!restored, "restore reported success over a full slot");
            check(DROPPED.bind(p).getStack(0).is(Items.EMERALD), "restore overwrote the slot: " + DROPPED.bind(p).getStack(0));
            DROPPED.bind(p).setStack(0, ItemStack.EMPTY);
        });
    }

    // A grave takes only what would drop: a kept stack stays with the player.
    private static void graveCaptureFollowsDropRules(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            DROPPED.bind(p).setStack(1, new ItemStack(Items.GOLD_INGOT));
            KEPT.bind(p).setStack(0, new ItemStack(Items.IRON_INGOT));
            List<PlayerSlotCapture.CapturedSlot> captured = PlayerSlotCapture.captureAndClear(p);
            check(captured.size() == 1 && captured.get(0).stack().is(Items.GOLD_INGOT), "the grave captured " + captured);
            check(KEPT.bind(p).getStack(0).is(Items.IRON_INGOT), "the grave took a kept stack: " + KEPT.bind(p).getStack(0));
            check(DROPPED.bind(p).getStack(1).isEmpty(), "the captured slot wasn't cleared");
            KEPT.bind(p).setStack(0, ItemStack.EMPTY);
        });
    }

    // A respawned player keeps its entity id, and a death starts its counts over.
    private static void deathResetsActiveCounts(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> ActiveCounts.set(player(s), COUNTED, 3));
        server.runCommand("kill @a");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.player.respawn());
        ctx.waitTicks(10);
        int count = server.computeOnServer(s -> ActiveCounts.get(player(s), COUNTED));
        check(count == 1, "after death the count is " + count + ", not its start of 1");
        server.runCommand("gamemode survival @a");
    }

    // An ephemeral group's items go back to the player when the menu closes.
    private static void customMenuHandsBackItsItems(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            player(s).getInventory().clearContent();
            MENU.open(player(s));
        });
        ctx.waitTicks(3);
        server.runOnServer(s -> player(s).containerMenu.slots.get(0).set(new ItemStack(Items.DIAMOND, 2)));
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(3);
        int held = server.computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND));
        check(held == 2, "closing the menu left the player " + held + " of 2 diamonds");
        server.runOnServer(s -> player(s).getInventory().clearContent());
    }

    // A panel the server shows arrives with its items, not empty.
    private static void shownPanelShowsItsItems(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> MENU.open(player(s)));
        ctx.waitTicks(3);
        server.runOnServer(s -> {
            CustomContainerMenu menu = (CustomContainerMenu) player(s).containerMenu;
            menu.getGroupsFor("slotkit_checks:menu:extra").get(0).getStorage().setStack(0, new ItemStack(Items.EMERALD));
            menu.broadcastChanges();
            menu.setPanelVisible("slotkit_checks:menu:extra", true);
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            check(menu instanceof CustomContainerMenu, "the client has no custom menu open");
            check(menu.slots.get(2).getItem().is(Items.EMERALD), "the shown panel's slot shows " + menu.slots.get(2).getItem());
        });
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(2);
        server.runOnServer(s -> player(s).getInventory().clearContent());
    }

    // Server code writing with no player, or by key, reaches the client viewing the slot.
    private static void serverWritesReachTheClient(ClientGameTestContext ctx, TestServerContext server, BlockPos chest) {
        server.runOnServer(s -> player(s).openMenu((net.minecraft.world.MenuProvider) player(s).level().getBlockEntity(chest)));
        ctx.waitTicks(3);
        server.runOnServer(s -> MARK.set(player(s).containerMenu.slots.get(0), true));
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> check(MARK.get(mc.player.containerMenu.slots.get(0)), "a mark set with no player never reached the client"));
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(2);
        server.runOnServer(s -> LOCK.set(player(s), new PersistentContainerKey.PlayerInventory(player(s).getUUID()), 0, true));
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> check(LOCK.get(mc.player.inventoryMenu.slots.get(36)), "a lock set by key never reached the client"));
    }

    // A dimension change gives the client a new inventory; the player's values come back with it.
    private static void dimensionChangeKeepsTheLock(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("execute in minecraft:the_nether run tp @a 0 120 0");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> check(LOCK.get(mc.player.inventoryMenu.slots.get(36)), "the lock is gone on the client after changing dimension"));
    }

    // A hopper takes with removeItem, which splits the stack off and leaves the same object, emptied.
    private static void liveItemTakenByAHopperIsStale(TestServerContext server) {
        server.runOnServer(s -> {
            net.minecraft.world.SimpleContainer chest = new net.minecraft.world.SimpleContainer(3);
            chest.setItem(0, new ItemStack(Items.SHULKER_BOX));
            com.trevlar.slotkit.api.item.LiveItem box = com.trevlar.slotkit.api.item.ItemSources.live(chest,
                    new com.trevlar.slotkit.api.item.ItemPath(com.trevlar.slotkit.api.item.ItemRef.Source.CONTAINER, 0, List.of(), null));
            check(box != null && !box.isStale(), "the box in the chest didn't bind");
            if (box == null) return;
            ItemStack pulled = chest.removeItem(0, 1);   // what a hopper below the chest does
            check(pulled.is(Items.SHULKER_BOX), "the hopper pulled " + pulled);
            check(box.isStale(), "the box a hopper took is still live, so its open menu keeps taking items into nothing");
        });
    }

    private static int diamondsNear(MinecraftServer s, BlockPos pos) {
        int n = 0;
        for (ItemEntity e : player(s).level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8))) {
            if (e.getItem().is(Items.DIAMOND)) n += e.getItem().getCount();
        }
        return n;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("slotkit_checks", path);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static final List<String> FAILURES = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static void check(boolean ok, String failure) {
        if (!ok) FAILURES.add(failure);
    }
}
