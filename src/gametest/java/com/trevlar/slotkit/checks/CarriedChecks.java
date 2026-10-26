package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.storage.StorageAttachment;
import com.trevlar.slotkit.api.storage.StorageContainerAdapter;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * A carried attachment's stacks keep their identity: from tick to tick, across a new
 * container from ItemSources, and across writes through a menu slot's own binding. Open
 * Anywhere tracks the box in front of the player that way. The client's player never
 * shares the server's stacks.
 */
public class CarriedChecks implements ModInitializer, FabricClientGameTest {

    static StorageAttachment.PlayerStorageAttachment<NonNullList<ItemStack>> POCKET;

    @Override
    public void onInitialize() {
        POCKET = StorageAttachment.playerAttached("slotkit_checks", "pocket", 3).carried();
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            ItemPath path = new ItemPath(ItemRef.Source.CARRIED, 0, List.of(), net.minecraft.resources.Identifier.fromNamespaceAndPath("slotkit_checks", "pocket"));
            ItemStack top = server.computeOnServer(s -> {
                POCKET.bind(player(s)).setStack(0, new ItemStack(Items.ENDER_CHEST));
                return ItemSources.containerOf(player(s), path).getItem(0);
            });
            check(top.is(Items.ENDER_CHEST), "the pocket holds " + top);
            ctx.waitTicks(3);

            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                Container kept = ItemSources.containerOf(p, path);
                check(kept.getItem(0) == top, "a later container's stack isn't the same object");

                // the menu's slot, over its own binding: a write to another slot, then a stack grown in place
                Slot slot1 = new Slot(new StorageContainerAdapter(POCKET.bind(p)), 1, 0, 0);
                slot1.set(new ItemStack(Items.DIRT, 4));
                check(kept.getItem(0) == top, "a write to slot 1 replaced slot 0's stack");
                check(kept.getItem(1).getCount() == 4, "the kept container doesn't see slot 1: " + kept.getItem(1));
                slot1.getItem().grow(3);
                slot1.setChanged();
                check(kept.getItem(1).getCount() == 7, "an in-place grow didn't reach the kept container: " + kept.getItem(1));
                check(POCKET.bind(p).getStack(1).getCount() == 7, "the grow wasn't saved");
                check(kept.getItem(0) == top, "the grow replaced slot 0's stack");

                // taking the box out reads as gone
                new Slot(new StorageContainerAdapter(POCKET.bind(p)), 0, 0, 0).set(ItemStack.EMPTY);
                check(kept.getItem(0).isEmpty(), "the kept container still shows the taken box");
                POCKET.bind(p).setStack(0, top);
            });
            ctx.waitTicks(3);

            ItemStack clientTop = ctx.computeOnClient(mc -> POCKET.bind(mc.player).getStack(0));
            check(clientTop != top, "the client player shares the server's stack");
        }
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void check(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
