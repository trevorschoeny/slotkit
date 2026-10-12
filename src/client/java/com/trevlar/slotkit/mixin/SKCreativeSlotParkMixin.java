package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Parks registered slots' creative {@code SlotWrapper}s off-screen so vanilla draws
 * and hover-tests nothing for them.
 *
 * <h3>Why</h3>
 *
 * The creative inventory tab wraps every {@code player.inventoryMenu} slot, registered
 * ones included, in a {@code SlotWrapper} positioned by index. Registered slot indices
 * have no place in the creative layout, so vanilla would draw them over the hotbar.
 * Rather than each consumer placing its slots by hand in creative (which used to need
 * a {@code CreativeEquipmentSlotMixin}), the library parks all of these wrappers
 * off-screen when they are built. Each frame the {@code SlotElement} showing the slot
 * writes the wrapper's real {@code x/y}, just as it does for the raw slot in the
 * survival inventory, and vanilla draws and hit-tests the wrapper there. Both modes
 * work the same way, and the wrapper still lives in the creative menu, so a click on
 * the slot still goes through it to the real backing slot.
 *
 * <p>The park is applied with {@link ModifyArgs} on the
 * {@code new SlotWrapper(target, index, x, y)} call (arg 2 is x, arg 3 is y), so the
 * wrapper never sits at vanilla's index-based hotbar position, even for one frame.
 * Vanilla parks its own 2x2 crafting slots at extreme coordinates the same way.
 *
 * <p>Generic: detects a slot by {@code target instanceof CreatedSlot}, with no
 * per-consumer knowledge. Client only, since the creative screen is a client type.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class SKCreativeSlotParkMixin {

    /** Far off-screen; the same parking coordinate the survival slot uses. */
    private static final int SLOT_OFFSCREEN = -10000;

    @ModifyArgs(
            method = "selectTab",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;"
                            + "<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private void sk$parkSlotWrappers(Args args) {
        // arg 0 is the wrapped target slot; registered slots wrap a raw CreatedSlot.
        if (args.<Object>get(0) instanceof CreatedSlot) {
            args.set(2, SLOT_OFFSCREEN); // x
            args.set(3, SLOT_OFFSCREEN); // y
        }
    }
}
