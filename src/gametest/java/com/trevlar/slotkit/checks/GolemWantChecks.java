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
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A slot a {@link SlotOperations.Preference} prefers for an item makes its chest a copper
 * golem's target for that item ({@link Automation#wants}), though the chest holds none of it,
 * and the golem's put lands in that slot. Also when the slot is inside a shulker box in the
 * chest. A real golem carries cobblestone from a copper chest to a chest holding only dirt,
 * which vanilla would turn it away from.
 */
public class GolemWantChecks implements ModInitializer, FabricClientGameTest {

    // Which slot wants cobblestone while a phase runs: chest slot 13, or slot 5 of a box in it.
    static volatile boolean chestSlot, boxSlot;

    @Override
    public void onInitialize() {
        SlotOperations.prefer(ModeScope.BOTH, (ref, group, incoming) -> incoming.is(Items.COBBLESTONE)
                && (chestSlot && ref.container() instanceof ChestBlockEntity && ref.containerSlot() == 13
                    || boxSlot && ref.container() instanceof ItemContents && ref.containerSlot() == 5));
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            BlockPos origin = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition().offset(6, 0, 0));
            ctx.waitTicks(2);

            // Nothing wants it: the dirt chest isn't a place for cobblestone.
            server.runOnServer(s -> {
                Container chest = setUp(s, origin, false);
                if (Automation.wants(chest, new ItemStack(Items.COBBLESTONE)))
                    failures.add("wants: true with no preference speaking");
            });

            chestSlot = true;
            run(ctx, server, origin, false, chest -> chest.getItem(13).is(Items.COBBLESTONE) && chest.getItem(13).getCount() == 16,
                    "chest: the golem didn't put its cobblestone into the wanted slot 13", failures);
            chestSlot = false;

            boxSlot = true;
            run(ctx, server, origin, true, chest -> {
                ItemStack five = boxSlot5(chest);
                return five.is(Items.COBBLESTONE) && five.getCount() == 16;
            }, "box: the golem didn't put its cobblestone into slot 5 of the shulker box in the chest", failures);
        } finally {
            chestSlot = boxSlot = false;
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    /** Builds the scene, summons a golem, and waits up to 60 s for {@code done} on the target chest. */
    private static void run(ClientGameTestContext ctx, TestServerContext server, BlockPos origin, boolean withBox,
                            Predicate<Container> done, String failure, List<String> failures) {
        server.runOnServer(s -> {
            setUp(s, origin, withBox);
            BlockPos at = origin.offset(0, 0, 2);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),
                    "summon minecraft:copper_golem " + at.getX() + " " + at.getY() + " " + at.getZ());
        });
        for (int i = 0; i < 60; i++) {
            ctx.waitTicks(20);
            if (server.computeOnServer(s -> done.test(target(s, origin)))) {
                server.runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=copper_golem]"));
                return;
            }
        }
        failures.add(failure + " (target holds " + server.computeOnServer(s -> describe(target(s, origin))) + ")");
        server.runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "kill @e[type=copper_golem]"));
    }

    // A copper chest with 16 cobblestone at origin, and the target chest four blocks along: dirt
    // in it, and with withBox a shulker box (holding dirt) in its slot 0.
    private static Container setUp(MinecraftServer s, BlockPos origin, boolean withBox) {
        ServerLevel level = s.overworld();
        level.setBlockAndUpdate(origin, Blocks.COPPER_CHEST.asList().get(0).defaultBlockState());
        ((Container) level.getBlockEntity(origin)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        BlockPos to = origin.offset(0, 0, 4);
        level.setBlockAndUpdate(to, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(to, Blocks.CHEST.defaultBlockState());
        Container chest = target(s, origin);
        if (withBox) {
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT))));
            chest.setItem(0, box);
        }
        chest.setItem(1, new ItemStack(Items.DIRT, 4));
        return chest;
    }

    private static Container target(MinecraftServer s, BlockPos origin) {
        return (Container) s.overworld().getBlockEntity(origin.offset(0, 0, 4));
    }

    private static ItemStack boxSlot5(Container chest) {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        chest.getItem(0).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items.get(5);
    }

    private static String describe(Container c) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack it = c.getItem(i);
            if (!it.isEmpty()) out.append(i).append('=').append(it.getCount()).append(' ').append(it.getItem()).append(' ');
        }
        return out.append("; box slot 5 = ").append(boxSlot5(c)).toString();
    }
}
