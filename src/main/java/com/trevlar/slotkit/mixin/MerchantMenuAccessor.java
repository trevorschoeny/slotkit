package com.trevlar.slotkit.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes a trading menu's trader, so {@code MenuSwitch.back} can send the trades again
 * from the trader itself. The menu's own progress-bar and restock getters read fields
 * only the client sets.
 */
@ApiStatus.Internal
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {

    @Accessor("trader")
    Merchant sk$getTrader();
}
