package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.ParitySlotRegistry;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes container-parity slots appear on the player's own {@code InventoryMenu}. This
 * is why a consumer using
 * {@link com.trevlar.slotkit.api.slot.ContainerPanel} writes no mixin.
 *
 * <h3>Why the library owns this one</h3>
 *
 * Foreign menus (chests, furnaces, modded) have no per-menu hook a consumer could
 * own, so {@link com.trevlar.slotkit.core.CreatedSlotProjection} already
 * appended to them from a library-owned player-lifecycle hook. The player's own
 * inventory menu is different. It is built in the {@code Player} constructor, never
 * through {@code openMenu}, so the projection hook can't reach it. Its constructor
 * does carry the {@code Player}, so consumers used to write an
 * {@code InventoryMenu.<init>} mixin themselves. Instead the library owns that
 * injection once and drives it from {@link ParitySlotRegistry}. A consumer who wants
 * custom {@code InventoryMenu} slots outside container parity can still write their
 * own mixin and {@code CreatedSlots} call; this only handles parity-registered
 * recipes.
 *
 * <h3>Sync safety</h3>
 *
 * Appends at constructor TAIL, so the slots come back on every menu rebuild (login,
 * respawn, dimension change) on both sides. {@link ParitySlotRegistry#applyTo} walks
 * the same registry in the same order on both sides and binds the same per-player
 * storage, so server and client build a byte-identical slot block before the first
 * content sync. Does nothing when no parity recipe is registered (no installed mod uses
 * container parity).
 */
@Mixin(InventoryMenu.class)
public abstract class SKInventoryMenuMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sk$applyParitySlots(Inventory inv, boolean active,
                                      Player player, CallbackInfo ci) {
        if (ParitySlotRegistry.isEmpty()) return;
        ParitySlotRegistry.applyTo((AbstractContainerMenu) (Object) this, player);
    }
}
