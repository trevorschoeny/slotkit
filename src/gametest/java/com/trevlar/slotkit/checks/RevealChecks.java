package com.trevlar.slotkit.checks;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.StorageAttachment;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * A slot hidden only by the client's reveal still gets what the server puts in it,
 * with no screen open, and shows it once revealed. Creative can't wipe it by echoing
 * the hidden slot's EMPTY back to the server.
 */
public class RevealChecks implements ModInitializer, FabricClientGameTest {

    static StorageAttachment.PlayerStorageAttachment<NonNullList<ItemStack>> HIDDEN;
    static volatile boolean revealed;

    @Override
    public void onInitialize() {
        HIDDEN = StorageAttachment.playerAttached("slotkit_checks", "hidden", 1);
        ParityGroup.register("slotkit_checks:reveal", SlotSpec.at("hidden", SlotGroupCategory.PLAYER_INVENTORY)
                .storage(p -> HIDDEN.bind(p))
                .revealWhen(() -> revealed));
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            revealed = false;

            // in the world, no screen open: the server fills the hidden slot
            server.runOnServer(s -> HIDDEN.bind(player(s)).setStack(0, new ItemStack(Items.DIAMOND, 5)));
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                ItemStack held = HIDDEN.bind(mc.player).getStack(0);
                check(held.is(Items.DIAMOND) && held.getCount() == 5, "the client's attachment holds " + held);
                CreatedSlot slot = slot(mc);
                check(slot.isInert() && slot.getItem().isEmpty(), "the hidden slot shows " + slot.getItem());
            });

            revealed = true;
            ctx.runOnClient(mc -> check(slot(mc).getItem().is(Items.DIAMOND), "the revealed slot shows " + slot(mc).getItem()));
            revealed = false;

            // creative echoing the hidden slot's EMPTY is dropped before it's sent
            server.runOnServer(s -> player(s).setGameMode(GameType.CREATIVE));
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, slot(mc).index));
            ctx.waitTicks(3);
            ItemStack kept = server.computeOnServer(s -> HIDDEN.bind(player(s)).getStack(0));
            check(kept.is(Items.DIAMOND), "creative's echo wiped the server's stack: " + kept);
        }
    }

    private static CreatedSlot slot(Minecraft mc) {
        for (Slot s : mc.player.inventoryMenu.slots) {
            if (s instanceof CreatedSlot c && c.panelId().equals("slotkit_checks:reveal:hidden")) return c;
        }
        throw new AssertionError("no reveal slot on the client's inventory menu");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void check(boolean ok, String failure) {
        if (!ok) throw new AssertionError(failure);
    }
}
