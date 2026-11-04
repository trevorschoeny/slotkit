package com.trevlar.slotkit.api.reserve;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.slotkit.api.window.SlotRef;
import com.trevlar.slotkit.core.Slots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * How a reserved slot shows its reservation: the reserved item as a half-size icon in the
 * slot's top-left corner, whether the slot is empty or holds an item. Normally the icon sits
 * under a faint veil of the slot's grey, which also tints the corner of an item under it.
 * While any mod {@linkplain #emphasize emphasizes} reservations (a "rules view" where the
 * player edits them) the icon is crisp.
 *
 * <pre>{@code
 * // Client init: crisp corner icons while edit mode is on.
 * ReservationDisplay.emphasize(EditMode::isOn);
 * }</pre>
 *
 * <p>The icon is an over decoration with id {@code "slotkit:reservation"}, so it draws above
 * the over decorations whose ids sort before it (MenuKit draws each layer in id order),
 * among them a veil another mod lays over items in its rules view.
 */
public final class ReservationDisplay {

    private ReservationDisplay() {}

    // Vanilla's slot background (0x8B8B8B), about 45% opaque.
    private static final int VEIL = 0x738B8B8B;

    private static final List<BooleanSupplier> EMPHASIS = new CopyOnWriteArrayList<>();

    /**
     * Shows reserved slots' icons crisp, without their veil, on any frame {@code when} answers
     * true. Each mod adds its own; any one true is enough. Asked every frame on the render thread.
     */
    public static void emphasize(BooleanSupplier when) {
        EMPHASIS.add(Objects.requireNonNull(when, "when"));
    }

    /** Whether reservations draw crisp this frame. */
    public static boolean emphasized() {
        for (BooleanSupplier s : EMPHASIS) if (s.getAsBoolean()) return true;
        return false;
    }

    @ApiStatus.Internal
    public static void register() {
        SlotDecorations.over("slotkit:reservation", ReservationDisplay::draw);
    }

    private static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
        Slot target = Slots.target(slot);
        Reservation r = Reservations.get(new SlotRef(target.container, target.getContainerSlot(), slot,
                screen.getMenu(), Minecraft.getInstance().player, null, null));
        if (r.isNone()) return;
        // Half size, its 8 px box from the slot frame's top-left pixel.
        g.pose().pushMatrix();
        g.pose().translate(x - 1, y - 1);
        g.pose().scale(0.5f, 0.5f);
        g.item(r.item(), 0, 0);
        g.pose().popMatrix();
        if (!emphasized()) g.fill(x - 1, y - 1, x + 7, y + 7, VEIL);
    }
}
