package com.trevlar.slotkit.core;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

/**
 * A reserved slot that is empty draws its ghost: the reserved item under a veil of the
 * slot's own grey, so it reads as a placeholder, not an item. Under the item layer, so a
 * drag's preview stack draws over it.
 */
@ApiStatus.Internal
public final class ReservationGhosts {

    private ReservationGhosts() {}

    // Vanilla's slot background (0x8B8B8B), three quarters opaque.
    private static final int VEIL = 0xC08B8B8B;

    public static void register() {
        SlotDecorations.under("slotkit:reservation_ghost", ReservationGhosts::draw);
    }

    private static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) return;
        Slot target = Slots.target(slot);
        Reservation r = Reservations.get(new SlotRef(target.container, target.getContainerSlot(), slot,
                screen.getMenu(), Minecraft.getInstance().player, null, null));
        if (r.isNone()) return;
        g.item(r.item(), x, y);
        g.fill(x, y, x + 16, y + 16, VEIL);
    }
}
