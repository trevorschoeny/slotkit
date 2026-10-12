package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.mixin.SlotPositionAccessor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's {@code protected} {@link AbstractContainerMenu#addSlot(Slot)} so
 * {@link com.trevlar.slotkit.api.slot.CreatedSlots} can append registered
 * slots to a vanilla menu it does not subclass.
 *
 * <p>Access only: it exposes an existing vanilla method, injects no behaviour,
 * registers no hook and owns no code path. The slot is added by whoever calls the
 * helper; this invoker only lets the helper reach a {@code protected} method from
 * outside the menu's class hierarchy. Same pattern as MenuKit's
 * {@code SlotPositionAccessor} ({@code @Accessor} on vanilla {@code Slot}).
 *
 * <p>Applies on both sides, since the vanilla menu is built on client and server
 * and slots are added on both. So it lives in the common mixin block, not the
 * client block.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {

    /**
     * Calls vanilla's {@code addSlot}, which appends the slot to {@code this.slots}
     * (assigning {@code slot.index}) and grows the parallel sync-tracking lists
     * ({@code lastSlots}, {@code remoteSlots}) so {@code broadcastChanges} stays
     * consistent. Doing this by hand would desync those lists.
     */
    @Invoker("addSlot")
    Slot sk$addSlot(Slot slot);

    /**
     * Calls vanilla's {@code moveItemStackTo}, the fill-partial-stacks-then-empty-slots
     * routine vanilla's own {@code quickMoveStack} uses. Exposed so a menu's own
     * quickMoveStack can route shift-clicks into and out of registered slots on a
     * foreign menu (chest, furnace) with vanilla's exact placement instead of
     * reimplementing the merge. Taken over from inventory-max's hand-built version so
     * consumers don't each declare it.
     */
    @Invoker("moveItemStackTo")
    boolean sk$moveItemStackTo(ItemStack stack, int startIndex, int endIndex,
                                    boolean reverseDirection);
}
