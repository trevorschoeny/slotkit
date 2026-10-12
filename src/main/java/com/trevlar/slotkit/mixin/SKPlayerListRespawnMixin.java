package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.state.SlotStateHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the same snapshot as on player join once {@link PlayerList#respawn}
 * completes. Respawn rebuilds {@code InventoryMenu} on the new {@code ServerPlayer},
 * so without this the player's inventory slot state (locks, for example) would show
 * empty until the next menu open.
 */
@ApiStatus.Internal
@Mixin(PlayerList.class)
public abstract class SKPlayerListRespawnMixin {

    @Inject(method = "respawn", at = @At("RETURN"))
    private void sk$sendJoinSnapshotOnRespawn(
            ServerPlayer serverPlayer, boolean removed, Entity.RemovalReason reason,
            CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer respawned = cir.getReturnValue();
        if (respawned == null) return;
        SlotStateHooks.sendPlayerJoinSnapshot(respawned);
    }
}
