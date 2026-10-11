package com.trevlar.menukit.containers.mixin;

import com.trevlar.menukit.containers.core.CreatedSlotAccess;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes clicks on registered slots in the creative tabs other than the inventory
 * tab, so they sync. {@code MKCCreativeSlotItemPickerMixin} makes the slots appear on
 * those tabs; this makes clicks on them reach the server.
 *
 * <h3>Why routing is needed</h3>
 *
 * Off the inventory tab, vanilla's {@code slotClicked} sends a click into
 * {@code ItemPickerMenu.clicked(slot.index)}, a client-only edit on the throwaway
 * picker menu that never reaches the slot's backing {@code Storage} on the server.
 * An item placed in a pocket from the combat or tools tab would desync. The
 * inventory tab instead routes slot clicks to
 * {@code player.inventoryMenu.clicked(target.index)} and {@code broadcastChanges()}.
 * The {@code CreativeInventoryListener} vanilla keeps on {@code inventoryMenu} for the
 * whole creative screen then sends every changed slot in a
 * {@code ServerboundSetCreativeModeSlotPacket}, and the server-side
 * {@code CreativeSetSlotMixin} writes those out-of-range slot indices.
 *
 * <h3>What it does</h3>
 *
 * For a click on a registered slot while not on the inventory tab, it repeats that
 * inventory-tab path and cancels vanilla's client-only one. The listener fires per
 * changed slot, so every gesture (place, pick up, clone, number-swap, shift-move)
 * syncs just as on the inventory tab with no special cases. The one exception is the
 * throw (Q), whose dropped entity is its own server message, which vanilla's
 * inventory-tab throw also handles separately.
 *
 * <p>The inventory tab is left to vanilla, which already handles these slots there.
 * Other slots are never touched. Client only.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MKCCreativeSlotClickRouteMixin {

    @Shadow private static CreativeModeTab selectedTab;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void mk$routeSlotClick(Slot slot, int slotId, int button,
                                         ContainerInput clickType, CallbackInfo ci) {
        if (slot == null) return;
        CreatedSlot mkcSlot = CreatedSlotAccess.asMKCSlot(slot);
        if (mkcSlot == null) return;
        // Vanilla already routes inventory-tab clicks correctly; only the other tabs,
        // whose click path is client-only, need rerouting.
        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.hasInfiniteMaterials() || mc.gameMode == null) return;

        if (clickType == ContainerInput.THROW && mkcSlot.hasItem()) {
            // Drop (Q), as vanilla's inventory-tab throw does it: the dropped entity is
            // its own creative-drop server message, and the slot's remainder syncs below.
            ItemStack dropped = mkcSlot.remove(button == 0 ? 1 : mkcSlot.getItem().getMaxStackSize());
            player.drop(dropped, true, Prediction.PREDICTED);
            mc.gameMode.handleCreativeModeItemDrop(dropped);
        } else {
            // Place, pick up, clone, swap, shift-move: act on the real inventory menu by
            // the slot's own index, exactly as the inventory tab does.
            player.inventoryMenu.clicked(mkcSlot.index, button, clickType, player);
        }
        // The persistent CreativeInventoryListener publishes every changed slot.
        player.inventoryMenu.broadcastChanges();
        ci.cancel();
    }
}
