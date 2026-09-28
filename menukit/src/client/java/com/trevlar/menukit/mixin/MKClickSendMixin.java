package com.trevlar.menukit.mixin;

import com.trevlar.menukit.window.ClickOperations;
import com.trevlar.menukit.window.ClickTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's send boundary for a click: every click the player makes, and every
 * click a mod simulates, goes out through {@code handleContainerInput}.
 *
 * <ul>
 *   <li><b>Refuse before sending.</b> A click whose own slot refuses the operation
 *       it names is dropped here: no local prediction, no packet. Without this a
 *       refusal on a server that does not run MenuKit would only be a prediction;
 *       the server would move the items and the client would snap to match.</li>
 *   <li><b>Carry the tag.</b> A click sent inside {@code SlotOperations.as(...)}
 *       records its tag just before the packet goes, so the integrated server can
 *       claim it ({@link ClickTags}). Only the send itself records, so a click
 *       vanilla drops (a stale menu id) leaves nothing behind.</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MKClickSendMixin {

    @Inject(method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true)
    private void mk$refuseBeforeSending(int containerId, int slotId, int button, ContainerInput input,
                                        Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return; // vanilla logs and drops it
        if (ClickOperations.refuses(menu, slotId, button, input, player)) ci.cancel();
    }

    @Inject(method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mk$carryTag(int containerId, int slotId, int button, ContainerInput input,
                             Player player, CallbackInfo ci) {
        ClickTags.Tag tag = ClickTags.current();
        if (tag != null && Minecraft.getInstance().hasSingleplayerServer()) {
            ClickTags.record(player.getUUID(), containerId, slotId, button, input, tag);
        }
    }
}
