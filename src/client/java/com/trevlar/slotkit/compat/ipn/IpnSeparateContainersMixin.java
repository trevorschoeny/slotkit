package com.trevlar.slotkit.compat.ipn;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inventory Profiles Next treats a screen as "the player" and "everything else", so its
 * sort and move-all pull a SlotKit group's items into the chest beside it. This marks a
 * slot disabled for IPN's actions when it belongs to a SlotKit group other than the
 * container being acted on: over a group, every slot outside that group's container;
 * anywhere else, every SlotKit group. The menu's own slots and the player's are left to
 * IPN, so sorting a chest sorts the chest alone and restock still works.
 *
 * <p>The target is the predicate inside {@code AreaTypes.getDisabled(menu)} (IPN 2.3.8
 * for 26.3). Applied only when IPN is loaded ({@link IpnMixinPlugin}), and
 * {@code require = 0}, so a changed IPN makes this a no-op, never a crash. It runs at the
 * predicate's head and only ever adds slots: IPN's compiled predicate reuses its menu
 * argument's local for another value, so an injection at its return can't read the menu.
 */
@ApiStatus.Internal
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.AreaTypes", remap = false)
public abstract class IpnSeparateContainersMixin {

    @Inject(method = "_get_disabled_$lambda$0(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/inventory/Slot;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void slotkit$separateContainers(AbstractContainerMenu menu, Slot slot,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (slot.container instanceof Inventory) return;
        Slot hovered = Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen
                ? ((AbstractContainerScreenAccessor) screen).sk$getHoveredSlot() : null;
        boolean overGroup = hovered != null && CreatedSlot.of(hovered) != null;
        boolean disabled = overGroup ? slot.container != hovered.container : CreatedSlot.of(slot) != null;
        if (disabled) cir.setReturnValue(true);
    }
}
