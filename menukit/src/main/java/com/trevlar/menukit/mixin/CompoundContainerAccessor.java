package com.trevlar.menukit.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes vanilla's two {@code private final} halves of a {@link CompoundContainer}
 * (a double chest's backing pair), so the container resolver can split a global
 * slot index into the owning half's block entity and its local index.
 *
 * <p>It only reads two vanilla fields: no injection, no hook. Which half owns which
 * slot, and where the value is stored, is decided in {@code ContainerIdentity}. Same
 * pattern as {@link AbstractContainerMenuInvoker} and {@code SlotPositionAccessor}.
 *
 * <p>It is registered in the common mixin block, not the client block. Only the
 * server backs a double chest with a {@code CompoundContainer}; the client builds a
 * flat {@code SimpleContainer} for the same menu, so resolution never meets a
 * compound there.
 */
@ApiStatus.Internal
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {

    /** The first half: slots {@code 0} to {@code container1.size - 1} of the global range. */
    @Accessor("container1")
    Container mk$getContainer1();

    /** The second half: the rest of the global range. */
    @Accessor("container2")
    Container mk$getContainer2();
}
