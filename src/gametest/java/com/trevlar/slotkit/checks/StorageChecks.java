package com.trevlar.slotkit.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Items and slot state across the paths that end something: a block breaking, a grave
 * restoring, a death, a dimension change, and server code writing with no player.
 */
public class StorageChecks implements ModInitializer, FabricClientGameTest {

    static StorageAttachment<BlockEntity, NonNullList<ItemStack>> BOX;
    static StorageAttachment.PlayerStorageAttachment<NonNullList<ItemStack>> DROPPED;
    static SlotStateChannel<Boolean> MARK;
    static SlotStateChannel<Boolean> LOCK;
    static final String COUNTED = "slotkit_checks:counted";

    @Override
    public void onInitialize() {
        BOX = StorageAttachment.blockScoped("slotkit_checks", "box", 3);
        DROPPED = StorageAttachment.playerAttached("slotkit_checks", "dropped", 2);
        MARK = SlotState.register(ModeScope.BOTH, id("mark"), Codec.BOOL, ByteBufCodecs.BOOL.cast(), false,
                SlotStateChannel.Visibility.SHARED);
        LOCK = SlotState.register(ModeScope.BOTH, id("lock"), Codec.BOOL, ByteBufCodecs.BOOL.cast(), false);
        ActiveCounts.declare(COUNTED, 1, 3);
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
            graveCaptureFollowsTheDeathRule(server);
            deathResetsActiveCounts(ctx, server);
            serverWritesReachTheClient(ctx, server, chest);
            dimensionChangeKeepsTheLock(ctx, server);
            liveItemTakenByAHopperIsStale(server);
            openedBoxShowsItsItem(ctx, server);
            nestedBoxKeepsItsItem(server);
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

    // A grave takes only what would drop, by vanilla's rule (keepInventory is off here):
    // a plain stack goes in the grave, a Curse of Vanishing stack vanishes, both slots clear.
    private static void graveCaptureFollowsTheDeathRule(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            ItemStack cursed = new ItemStack(Items.IRON_SWORD);
            cursed.enchant(s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE), 1);
            DROPPED.bind(p).setStack(0, cursed);
            DROPPED.bind(p).setStack(1, new ItemStack(Items.GOLD_INGOT));
            List<PlayerSlotCapture.CapturedSlot> captured = PlayerSlotCapture.captureAndClear(p);
            check(captured.size() == 1 && captured.get(0).stack().is(Items.GOLD_INGOT), "the grave captured " + captured);
            check(DROPPED.bind(p).getStack(0).isEmpty(), "the cursed stack survived the capture: " + DROPPED.bind(p).getStack(0));
            check(DROPPED.bind(p).getStack(1).isEmpty(), "the captured slot wasn't cleared");
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

    // A box opened from the inventory: its slots' SHARED values are the box item's, read,
    // written and seen on the client, though the client's container is a plain copy.
    private static void openedBoxShowsItsItem(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            MARK.set(box, 2, true);
            p.getInventory().setItem(9, box);
            class Opened extends net.minecraft.world.SimpleContainer implements com.trevlar.slotkit.api.item.ItemContents {
                Opened() { super(27); }
                @Override public com.trevlar.slotkit.api.item.ItemPath itemPath(net.minecraft.world.entity.player.Player asker) {
                    return new com.trevlar.slotkit.api.item.ItemPath(com.trevlar.slotkit.api.item.ItemRef.Source.INVENTORY, 9, List.of());
                }
            }
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) ->
                    new net.minecraft.world.inventory.ShulkerBoxMenu(id, inv, new Opened()), net.minecraft.network.chat.Component.literal("box")));
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> check(MARK.get(mc.player.containerMenu.slots.get(2)) && !MARK.get(mc.player.containerMenu.slots.get(1)),
                "an opened box's client slots don't show the box item's marks"));
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            check(MARK.get(p, p.containerMenu.slots.get(2)), "an opened box's slot doesn't read its item's mark on the server");
            MARK.set(p, p.containerMenu.slots.get(1), true);
            MARK.set(p, p.containerMenu.slots.get(2), false);
            ItemStack box = p.getInventory().getItem(9);
            check(MARK.get(box, 1) && !MARK.get(box, 2), "a write on an opened box's slot didn't land on the box item");
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> check(MARK.get(mc.player.containerMenu.slots.get(1)) && !MARK.get(mc.player.containerMenu.slots.get(2)),
                "a change to the box item didn't reach the client"));
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(2);
    }

    // A box opened inside another box, as Open Anywhere holds it: a container bound to the
    // inner item by its own LiveItem. A write on its slot lands on the inner item through
    // that LiveItem, which stays bound, so the box stays open.
    private static void nestedBoxKeepsItsItem(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            ItemStack outer = new ItemStack(Items.SHULKER_BOX);
            outer.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(
                            ItemStack.EMPTY, new ItemStack(Items.SHULKER_BOX))));
            p.getInventory().setItem(10, outer);
            com.trevlar.slotkit.api.item.ItemPath inner = new com.trevlar.slotkit.api.item.ItemPath(
                    com.trevlar.slotkit.api.item.ItemRef.Source.INVENTORY, 10, List.of(1));
            com.trevlar.slotkit.api.item.LiveItem live = com.trevlar.slotkit.api.item.ItemSources.live(p, inner);
            check(live != null, "the inner box didn't bind");
            if (live == null) return;
            class Opened extends net.minecraft.world.SimpleContainer implements com.trevlar.slotkit.api.item.ItemContents {
                Opened() { super(27); }
                @Override public com.trevlar.slotkit.api.item.ItemPath itemPath(net.minecraft.world.entity.player.Player asker) {
                    return live.isStale() ? null : live.path();
                }
                @Override public ItemStack item(net.minecraft.world.entity.player.Player asker) { return live.stack(); }
                @Override public void setChanged() { live.commit(); }
            }
            Opened box = new Opened();
            MARK.set(p, new net.minecraft.world.inventory.Slot(box, 3, 0, 0), true);
            check(!live.isStale(), "a write on a nested box's slot unbound the box's own item");
            com.trevlar.slotkit.api.item.ItemRef now = com.trevlar.slotkit.api.item.ItemSources.at(p, inner);
            check(now != null && MARK.get(now.stack(), 3), "a write on a nested box's slot didn't land on the inner item");
            p.getInventory().setItem(10, ItemStack.EMPTY);
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
