package com.trevlar.menukit.containers.mixin;

import com.trevlar.menukit.containers.core.attachment.PlayerDeathDropHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops player-scoped slot content on death. The player-death counterpart of
 * {@link M7BlockDropMixin}, which handles drop-on-break.
 *
 * <p>Targets {@link Player#dropEquipment(net.minecraft.server.level.ServerLevel)} at
 * TAIL. In 26.2 this method is where vanilla, gated on {@code keepInventory}, runs
 * {@code destroyVanishingCursedItems()} and {@code inventory.dropAll()} (checked
 * against the jar). Dropping at TAIL means registered-attachment content drops
 * alongside the vanilla inventory, in the same death, frame and gamerule timing, so
 * it behaves exactly like vanilla.
 *
 * <p>The injection doesn't cancel anything. {@code dropEquipment} runs inside
 * {@code die()} after the totem check, so this fires only when the player actually
 * dies, never on a totem save. The {@code keepInventory} read and the per-stack
 * {@link com.trevlar.menukit.containers.api.slot.DropRule} resolution live in
 * {@link PlayerDeathDropHandler}. Server side only, since the method takes a
 * {@code ServerLevel}.
 */
@ApiStatus.Internal
@Mixin(Player.class)
public abstract class PlayerDeathDropMixin {

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void mk$dropPlayerSlotsOnDeath(ServerLevel level, CallbackInfo ci) {
        PlayerDeathDropHandler.dropAllOnDeath((Player) (Object) this, level);
    }
}
