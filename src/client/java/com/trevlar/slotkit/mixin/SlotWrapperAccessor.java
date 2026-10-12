package com.trevlar.slotkit.mixin;

import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Unwraps a creative {@code CreativeModeInventoryScreen$SlotWrapper} back to the
 * vanilla {@link Slot} it delegates to (its {@code target} field).
 *
 * <p>The creative inventory tab rebuilds its menu by wrapping <em>every</em>
 * {@code player.inventoryMenu} slot in a package-private {@code SlotWrapper},
 * whose {@code getContainerSlot()} returns the <em>wrapper's</em> index, not the
 * target's. So on the creative screen {@code menu.slots} holds wrappers, and code
 * that wants the real slot's identity (container and container index) or its
 * registered type has to unwrap.
 *
 * <p>This is the only unwrap point, so a panel anchored to a vanilla slot and slot
 * code unwrap the same way. {@link com.trevlar.slotkit.core.Slots#target(Slot)}
 * wraps this accessor, and {@code CreatedSlotAccess.asMKCSlot} goes through the same
 * path. Both are {@code @ApiStatus.Internal}: consumers address
 * slots by {@code Address}, not by unwrapping raw {@code Slot}s.
 *
 * <p>Targets the inner class by binary name because it is not public.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface SlotWrapperAccessor {

    @Accessor("target")
    Slot sk$getTarget();
}
