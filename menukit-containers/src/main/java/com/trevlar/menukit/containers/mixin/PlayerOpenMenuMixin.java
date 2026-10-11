package com.trevlar.menukit.containers.mixin;

import com.trevlar.menukit.containers.state.SlotStateHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * After {@link ServerPlayer#openMenu} succeeds, sends the menu-open slot-state
 * snapshot so the client gets the per-slot state for the menu it just opened.
 *
 * <p>Skipped when {@code openMenu} returns empty (the open was rejected). Player
 * inventory and ender chest channels come from the player-join handler (see
 * {@code SlotStateHooks.registerServer}); this mixin covers every other container
 * (chests, furnaces, donkeys and so on).
 */
@ApiStatus.Internal
@Mixin(ServerPlayer.class)
public abstract class PlayerOpenMenuMixin {

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void mk$sendSlotStateSnapshot(MenuProvider menuProvider,
                                                 CallbackInfoReturnable<OptionalInt> cir) {
        OptionalInt result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;
        SlotStateHooks.sendSnapshotForOpenedMenu((ServerPlayer) (Object) this);
    }
}
