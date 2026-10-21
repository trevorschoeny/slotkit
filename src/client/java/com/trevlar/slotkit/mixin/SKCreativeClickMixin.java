package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.core.Slots;
import com.trevlar.slotkit.window.ClickOperations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Judges the operations a click names in the creative screen, on the client, where
 * creative runs them.
 *
 * <p>Creative applies every click on the client ({@code slotClicked} calls
 * {@code inventoryMenu.clicked} or the picker menu's {@code clicked} there) and sends
 * the server only the resulting slot contents. The server's seam in {@code doClick}
 * never sees the click, and the client's send seam on {@code handleContainerInput}
 * is never called. So without this a veto or a slot's own rules held nowhere in
 * creative. The click is judged on the real slot under the creative wrapper, through
 * the same classifier the other seams use, and refused before vanilla touches
 * anything.
 *
 * <p>Shift-clicking the bin empties every slot of the player's inventory menu. Slots
 * that refuse a take keep their items.
 */
@ApiStatus.Internal
@Mixin(CreativeModeInventoryScreen.class)
public abstract class SKCreativeClickMixin {

    @Shadow private @Nullable Slot destroyItemSlot;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void sk$judgeClick(@Nullable Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (slot == null) return;   // a click outside the slots moves only the cursor
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (slot == destroyItemSlot) {
            if (input == ContainerInput.QUICK_MOVE) {
                clearUnlocked(player);
                ci.cancel();
            }
            return;
        }

        // On the inventory tab vanilla clicks the wrapped slot on the inventory menu by
        // its own index; anywhere else it clicks the slot on the screen's own menu.
        Slot target = Slots.target(slot);
        AbstractContainerMenu menu = target != slot ? player.inventoryMenu
                : ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (ClickOperations.refuses(menu, target.index, button, input, player)) ci.cancel();
    }

    // Vanilla's bin shift-click, minus the slots that refuse a take.
    private static void clearUnlocked(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;
        AbstractContainerMenu menu = player.inventoryMenu;
        for (int i = 0; i < menu.getItems().size(); i++) {
            Slot s = menu.getSlot(i);
            if (s.hasItem() && !SlotOperations.allowsGesture(SlotRef.of(menu, s, player), BehaviorKeys.CLICK_TAKE)) continue;
            s.set(ItemStack.EMPTY);
            mc.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, i);
        }
    }
}
