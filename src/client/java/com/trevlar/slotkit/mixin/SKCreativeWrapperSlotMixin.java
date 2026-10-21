package com.trevlar.slotkit.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A creative inventory-tab wrapper reports the container slot of the slot it wraps.
 *
 * <p>Vanilla builds each wrapper with its menu index as its container slot, so
 * {@code getContainerSlot()} on a wrapper for hotbar slot 0 says 36. Every read and
 * write of the wrapper already goes to its target, so that number is only ever seen
 * by code looking a player slot up by its inventory index, as a mod finding the slot
 * to click does, and there it names the wrong slot (the crafting grid or armour for
 * the hotbar).
 */
@ApiStatus.Internal
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public abstract class SKCreativeWrapperSlotMixin extends Slot {

    @Shadow @Final private Slot target;

    private SKCreativeWrapperSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public int getContainerSlot() {
        return target.getContainerSlot();
    }
}
