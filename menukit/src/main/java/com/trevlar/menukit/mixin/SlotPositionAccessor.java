package com.trevlar.menukit.mixin;

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
 * <p>MKCreativeMixin uses it to fix MKSlot positions on the creative inventory
 * tab, where vanilla's selectTab wraps them in SlotWrapper with wrong coordinates.
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void mk$setX(int x);

    @Mutable
    @Accessor("y")
    void mk$setY(int y);
}
