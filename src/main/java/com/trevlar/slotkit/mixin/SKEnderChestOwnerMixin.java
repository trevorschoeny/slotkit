package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.window.ContainerIdentity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records whose ender chest a player's {@link PlayerEnderChestContainer} is, as the
 * player is built, so {@link ContainerIdentity} resolves it to that player.
 * Vanilla's container keeps no link to its player.
 */
@Mixin(Player.class)
public abstract class SKEnderChestOwnerMixin {

    @Shadow protected PlayerEnderChestContainer enderChestInventory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sk$recordEnderChestOwner(CallbackInfo ci) {
        ContainerIdentity.recordEnderChestOwner(enderChestInventory, ((Player) (Object) this).getUUID());
    }
}
