package com.trevlar.slotkit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * SlotKit's own read of a container screen's position ({@code leftPos} and
 * {@code topPos}, which the recipe book shifts, so nothing re-derives them from the
 * screen size) and its hovered slot. MenuKit has an accessor of its own on the same class; accessors never
 * conflict, and this one keeps SlotKit off MenuKit's internals.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int sk$getLeftPos();

    @Accessor("topPos")
    int sk$getTopPos();

    @Accessor("hoveredSlot")
    Slot sk$getHoveredSlot();
}
