package com.trevorschoeny.menukit.mixin;

import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Unwraps a creative {@code CreativeModeInventoryScreen$SlotWrapper} back to the
 * vanilla {@link Slot} it delegates to (its {@code target} field).
 *
 * <h3>The general capability, lowest in the stack (MK)</h3>
 *
 * The creative inventory tab rebuilds its menu by wrapping <em>every</em>
 * {@code player.inventoryMenu} slot — vanilla slots and grafts alike — in a
 * package-private {@code SlotWrapper}, whose {@code getContainerSlot()} returns
 * the <em>wrapper's</em> index, not the target's. So on the creative screen
 * {@code menu.slots} holds {@code SlotWrapper}s, and any code that wants the real
 * slot behind one — to read its identity (container + container-index) or its
 * grafted type — has to unwrap.
 *
 * <p>This accessor is the single unwrap seam. It lives in <b>MenuKit</b> (not
 * MenuKit-Containers) because unwrapping a vanilla wrapper to a vanilla slot is a
 * vanilla-screen-geometry concern, not a graft concern: a pure-MK panel consumer
 * anchoring to a vanilla slot needs it just as much as a graft consumer does.
 * {@link com.trevorschoeny.menukit.inject.Slots#target(Slot)} is the public face;
 * MenuKit-Containers' {@code GraftSlots.asGraft} rides that same path so there is
 * one unwrap, not two.
 *
 * <p>Client-only mixin (the creative screen is a client type). Targets the inner
 * class by binary name since it is not public.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface SlotWrapperAccessor {

    /** The slot this wrapper delegates to (creative's {@code SlotWrapper.target}). */
    @Accessor("target")
    Slot menuKit$getTarget();
}
