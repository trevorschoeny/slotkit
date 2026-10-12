package com.trevlar.slotkit.mixin;

import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Makes {@code Slot.x} and {@code Slot.y} writable. Vanilla declares them
 * {@code public final int}; this accessor drops the {@code final} so slots can be
 * moved at runtime.
 *
 * <p>{@code SlotElement} uses it to write a created slot's position (or its creative
 * {@code SlotWrapper}'s) each frame, and {@code CustomContainerScreen} to lay out its
 * own slots.
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void sk$setX(int x);

    @Mutable
    @Accessor("y")
    void sk$setY(int y);
}
