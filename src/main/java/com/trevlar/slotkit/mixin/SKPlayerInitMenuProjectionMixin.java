package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.CreatedSlotProjection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side projection hook: appends a player's registered projected slots onto a
 * just-opened foreign menu, before its first content sync.
 *
 * <h3>Why {@code initMenu} HEAD, not {@code openMenu} RETURN</h3>
 *
 * {@code ServerPlayer.openMenu} runs {@code createMenu}, sends OpenScreen, then calls
 * {@code initMenu(menu)}, and {@code initMenu} does the initial
 * {@code sendAllDataToRemote}. Injecting at {@code openMenu} RETURN would append the
 * slots after that content was sent. The first {@code ContainerSetContent} would
 * leave them out, and the client menu (which appends slots at {@code AFTER_INIT})
 * would be one block longer than the packet, which always desyncs
 * {@code remoteSlots}. Injecting at {@code initMenu} HEAD puts the slots on the menu
 * just before that method sends content, so the initial packet already has them. The
 * client's {@code AFTER_INIT} append, also before its content packet, matches this.
 * See {@link CreatedSlotProjection} for the full sync-safety rules.
 *
 * <p>Does nothing for a menu with no registered projection source. The player's own
 * {@code InventoryMenu} gets its slots from {@code SKInventoryMenuMixin} and is
 * never registered here. Server side only ({@code ServerPlayer}); the matching client
 * hook is in {@code SlotKitClient}.
 */
@ApiStatus.Internal
@Mixin(ServerPlayer.class)
public abstract class SKPlayerInitMenuProjectionMixin {

    @Inject(method = "initMenu", at = @At("HEAD"))
    private void sk$projectSlots(AbstractContainerMenu menu, CallbackInfo ci) {
        CreatedSlotProjection.appendProjectedSlots(menu, (ServerPlayer) (Object) this);
    }
}
