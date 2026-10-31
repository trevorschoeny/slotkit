package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps creative from wiping a created slot the client hides. Creative's screen listens
 * to the inventory menu and sends every slot whose {@code getItem} changed to the
 * server as the slot's new contents. A slot hidden by its reveal reads as EMPTY on the
 * client while the server still shows it, so hiding it would send that EMPTY and the
 * server would clear the real stack. A hidden slot can't be clicked or edited, so any
 * creative write for it is that echo, and it's dropped here before it's sent.
 */
@ApiStatus.Internal
@Mixin(MultiPlayerGameMode.class)
public abstract class SKCreativeHiddenSlotMixin {

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void sk$dropHiddenSlotEcho(ItemStack clicked, int slot, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        AbstractContainerMenu menu = player.inventoryMenu;
        if (slot < 0 || slot >= menu.slots.size()) return;
        if (menu.getSlot(slot) instanceof CreatedSlot created && created.isInert()) ci.cancel();
    }
}
