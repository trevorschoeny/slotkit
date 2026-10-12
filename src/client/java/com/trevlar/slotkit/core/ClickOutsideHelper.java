package com.trevlar.slotkit.core;


import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;

/**
 * Tells the {@code SKHasClickedOutside*Mixin} family whether a click lands on an
 * active slot, so the {@link AbstractContainerScreen},
 * {@link net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen} and
 * {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}
 * overrides behave the same.
 *
 * <p>Vanilla's {@code hasClickedOutside} counts clicks on slots outside the
 * container frame (common for registered and decoration slots) as outside clicks,
 * which turns PICKUP into THROW and drops the item as an entity. The mixins use
 * this to exempt any click on an active slot, wherever the frame is.
 *
 * <p>The 1px tolerance matches vanilla's {@code AbstractContainerScreen.isHovering}.
 */
@ApiStatus.Internal
public final class ClickOutsideHelper {

    private ClickOutsideHelper() {}

    /**
     * Whether the click lands on an active slot, in which case the caller's
     * hasClickedOutside should return false. {@code leftPos} and {@code topPos} are
     * the values passed to hasClickedOutside.
     */
    public static boolean clickLandsOnActiveSlot(
            AbstractContainerScreen<?> self,
            double mouseX, double mouseY,
            int leftPos, int topPos) {
        // Registered slots: ask the same resolution getHoveredSlot uses, which knows
        // which panels show them and unwraps the creative wrapper. If it claims a slot
        // or an in-panel point, the click is on the menu.
        if (CreatedSlotScreenHook.INSTANCE.resolveHover(self, mouseX, mouseY).handled()) {
            return true;
        }

        // Vanilla-positioned slots. Slot.x/y are relative to the container frame.
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;
        for (Slot slot : self.getMenu().slots) {
            if (!slot.isActive()) continue;
            // 1px tolerance on each side (17 = 16 item area + 1 tolerance).
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                return true;
            }
        }
        return false;
    }
}
