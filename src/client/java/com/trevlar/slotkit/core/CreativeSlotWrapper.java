package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.mixin.SlotWrapperAccessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The library's stand-in for vanilla's package-private
 * {@code CreativeModeInventoryScreen$SlotWrapper}. It carries a registered
 * {@link CreatedSlot} into the creative tabs other than the inventory tab, so it
 * draws and takes clicks there just as it does on the inventory tab.
 *
 * <h3>Why a custom wrapper</h3>
 *
 * Vanilla wraps {@code player.inventoryMenu}'s slots, registered ones included, into
 * the {@code ItemPickerMenu} only on the inventory tab. The other tabs carry a fixed
 * set of slots (the item grid and hotbar) that never includes the registered slot, so
 * it is invisible and unclickable there. {@code SKCreativeSlotItemPickerMixin}
 * appends one of these wrappers per slot to that set, so the slot dispatch, which
 * walks {@code menu.slots} and unwraps with
 * {@link com.trevlar.slotkit.core.Slots#target}, finds the slot on every tab.
 * Vanilla's {@code SlotWrapper} is package-private and can't be constructed here, so
 * this copies its delegation and implements the same {@link SlotWrapperAccessor}
 * unwrap SlotKit applies to vanilla's wrapper. That way {@code Slots.target} and
 * {@code CreatedSlotAccess.asMKCSlot} resolve it to the slot with no special case.
 *
 * <h3>Parked, like the inventory-tab wrappers</h3>
 *
 * Built off-screen (the {@code SlotElement} showing the slot writes its real
 * {@code x/y} each frame), and {@code SKCreativeSlotClickRouteMixin}
 * reroutes its placement clicks to the inventory-tab path. The delegation below keeps
 * vanilla's own reads and writes (item, may-place, take and so on) correct for
 * anything that touches the wrapper directly.
 *
 * <p>Has no client types, so it is safe on either side, though only the client's
 * creative path builds it.
 */
public final class CreativeSlotWrapper extends Slot implements SlotWrapperAccessor {

    private final CreatedSlot target;

    /**
     * @param target the registered slot on {@code player.inventoryMenu} this stands in for
     * @param x      parked screen x (the slot's {@code SlotElement} moves it each frame)
     * @param y      parked screen y
     */
    public CreativeSlotWrapper(CreatedSlot target, int x, int y) {
        super(target.container, target.getContainerSlot(), x, y);
        this.target = target;
    }

    /** Unwraps this wrapper back to its slot (see {@link SlotWrapperAccessor}). */
    @Override
    public Slot sk$getTarget() {
        return this.target;
    }

    // Delegation to the wrapped slot, as vanilla's SlotWrapper does

    @Override
    public void onTake(Player player, ItemStack stack) {
        this.target.onTake(player, stack);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.target.mayPlace(stack);
    }

    @Override
    public ItemStack getItem() {
        return this.target.getItem();
    }

    @Override
    public boolean hasItem() {
        return this.target.hasItem();
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        this.target.setByPlayer(newStack, oldStack);
    }

    @Override
    public void set(ItemStack stack) {
        this.target.set(stack);
    }

    @Override
    public void setChanged() {
        this.target.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return this.target.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.target.getMaxStackSize(stack);
    }

    @Override
    public ItemStack remove(int amount) {
        return this.target.remove(amount);
    }

    @Override
    public boolean isActive() {
        return this.target.isActive();
    }

    @Override
    public boolean mayPickup(Player player) {
        return this.target.mayPickup(player);
    }
}
