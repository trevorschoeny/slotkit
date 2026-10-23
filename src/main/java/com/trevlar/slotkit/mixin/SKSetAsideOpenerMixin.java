package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.menu.MenuSwitch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A container's periodic opener recheck counts the players who have it open, asking each
 * block's {@code isOwnContainer}, which reads only {@code player.containerMenu}. A menu
 * {@link MenuSwitch} set aside is still open, so the recheck asks again with it in front;
 * without this, a chest set aside shuts its lid within a few ticks.
 */
@ApiStatus.Internal
@Mixin(Player.class)
public abstract class SKSetAsideOpenerMixin {

    @Inject(method = "hasContainerOpen(Lnet/minecraft/world/level/block/entity/ContainerOpenersCounter;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"), cancellable = true)
    private void sk$countSetAside(ContainerOpenersCounter counter, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !((Object) this instanceof ServerPlayer player)) return;
        AbstractContainerMenu aside = MenuSwitch.setAside(player).orElse(null);
        if (aside == null) return;
        // isOwnContainer has no menu parameter: put the set-aside menu in front for the one question.
        AbstractContainerMenu front = player.containerMenu;
        player.containerMenu = aside;
        try {
            cir.setReturnValue(counter.isOwnContainer(player));
        } finally {
            player.containerMenu = front;
        }
    }
}
