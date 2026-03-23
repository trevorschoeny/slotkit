package com.trevorschoeny.menukit.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * MenuKit internal mixin — makes Slot.x and Slot.y mutable.
 *
 * <p>Vanilla declares these as {@code public final int}. This accessor
 * removes the {@code final} modifier so we can reposition slots at runtime.
 *
 * <p>Used by MKCreativeMixin to fix MKSlot positions on the creative
 * inventory tab, where vanilla's selectTab wraps them in SlotWrapper
 * with wrong coordinates.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void menuKit$setX(int x);

    @Mutable
    @Accessor("y")
    void menuKit$setY(int y);
}
