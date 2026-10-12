package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.CreativeSlotWrapper;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes registered slots (pockets, equipment) appear and work on the creative tabs
 * other than the inventory tab: combat, tools, search and the rest.
 *
 * <h3>Why only the inventory tab showed them</h3>
 *
 * The creative menu ({@code ItemPickerMenu}) has a fixed base set of slots, the 5x9
 * item grid and the player hotbar, built once in this constructor. Only on the
 * inventory tab does {@code selectTab} swap that base for wrappers of every
 * {@code player.inventoryMenu} slot (registered ones included, which
 * {@code SKCreativeSlotParkMixin} parks off-screen). Leaving the inventory tab
 * restores the base. So the slot dispatch, which walks the active {@code menu.slots}
 * and unwraps each with {@code Slots.target}, finds the slot on the inventory tab but
 * never in the base set the other tabs show. That is why creative pockets worked in
 * the inventory but not on the other tabs.
 *
 * <h3>The fix: add the slots to the base set, once</h3>
 *
 * At constructor TAIL this appends one {@link CreativeSlotWrapper} per registered
 * {@code inventoryMenu} slot to the base {@code menu.slots}. Vanilla's own
 * {@code originalSlots} save and restore then handles them: entering the inventory
 * tab, vanilla saves the base (these wrappers included) and rebuilds from
 * {@code inventoryMenu}; leaving it, vanilla restores what it saved. So the slot is
 * represented by vanilla's parked wrapper on the inventory tab and by this one on
 * every other tab, never both at once, and exactly one copy is drawn throughout.
 *
 * <p>{@code SKCreativeSlotClickRouteMixin} reroutes placement clicks on these
 * wrappers to the inventory-tab path, since the base set's click path is client-only
 * and would not reach the slot's backing storage.
 *
 * <p>Generic: detects a slot by {@code instanceof CreatedSlot}, with no per-consumer
 * knowledge. Client only, since the creative screen is a client type.
 */
@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class SKCreativeSlotItemPickerMixin {

    /** Far off-screen; the same parking coordinate the inventory-tab wrappers use. */
    private static final int SLOT_OFFSCREEN = -10000;

    /** The player's real inventory menu (where the slots live). */
    @Shadow @Final private AbstractContainerMenu inventoryMenu;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sk$appendSlotWrappers(Player player, CallbackInfo ci) {
        // The slots on inventoryMenu don't change: they are appended once at
        // registration, and revealing or hiding only toggles inertness. So one fixed
        // wrapper per slot is right for the menu's lifetime.
        //
        // addSlot is declared on AbstractContainerMenu (the superclass), not on
        // ItemPickerMenu. A local @Invoker on this subclass mixin can't see the
        // inherited method (Mixin searches the target class only) and fails at apply
        // time. AbstractContainerMenuInvoker targets the superclass, so it applies to
        // this subclass too.
        AbstractContainerMenuInvoker self = (AbstractContainerMenuInvoker) (Object) this;
        for (Slot slot : this.inventoryMenu.slots) {
            if (slot instanceof CreatedSlot mk) {
                self.sk$addSlot(new CreativeSlotWrapper(mk, SLOT_OFFSCREEN, SLOT_OFFSCREEN));
            }
        }
    }
}
