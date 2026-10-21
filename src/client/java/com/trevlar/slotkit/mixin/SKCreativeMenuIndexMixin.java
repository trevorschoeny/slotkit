package com.trevlar.slotkit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every slot on creative's menu its real index after a tab change.
 *
 * <p>The inventory tab fills the menu with {@code menu.slots.add}, not {@code addSlot},
 * so each wrapper (and the bin) keeps index 0. A mod that names a slot by
 * {@code slot.index}, as every simulated click does, then names slot 0 for all of
 * them. Vanilla itself never reads a wrapper's index: it clicks the wrapped slot.
 */
@ApiStatus.Internal
@Mixin(CreativeModeInventoryScreen.class)
public abstract class SKCreativeMenuIndexMixin {

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void sk$indexSlots(CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        for (int i = 0; i < menu.slots.size(); i++) menu.slots.get(i).index = i;
    }
}
