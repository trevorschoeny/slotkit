package com.trevlar.slotkit.api.group;

import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;
import com.trevlar.slotkit.core.Slots;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Optional;

/**
 * Finds where a <b>player-inventory slot</b> is on whatever screen is open. You
 * name player-inventory slot N and get back its rectangle; MenuKit handles the slot
 * loop and creative's {@code SlotWrapper}, so you write no per-screen geometry.
 *
 * <p>Panel anchoring ({@link SlotGroupCategories}, {@link SlotGroupPanelAdapter})
 * places panels against a <em>category</em> of slots by slicing {@code menu.slots}.
 * This answers a different question: where on this screen is the one slot whose
 * player-inventory index is N? It matches by identity ({@code container} and
 * {@code getContainerSlot}), which creative's {@code SlotWrapper} hides, so it
 * goes through {@link Slots#target}: identity from the unwrapped slot, position
 * from the in-menu slot (the wrapper, which has the creative coordinates).
 *
 * <p>{@code containerIndex} is the slot's index in the player {@link Inventory},
 * the value {@code Slot.getContainerSlot()} reports for a raw player slot:
 * {@code 0 to 8} for the hotbar, {@code 9 to 35} for the main inventory, and the
 * player's equipment indices for armour and offhand. Any player-inventory slot
 * works, not just the hotbar.
 *
 * <p>It works on any screen whose menu shows the player inventory: the survival
 * inventory, the creative inventory tab (wrapped slots), and container screens
 * such as chests and furnaces, which build their own player-inventory slots. No
 * per-menu resolver is needed, because the slots themselves know they belong to
 * the player {@link Inventory}.
 *
 * <p>Client only: it reads the screen frame and the creative wrapper.
 */
public final class VanillaSlotResolver {

    private VanillaSlotResolver() {}

    // The in-menu Slot for the player-inventory index: the raw slot on most screens,
    // or creative's SlotWrapper around it, whose x/y are the creative position.
    // Kept private so the public API never hands out raw Slots; resolve() returns a
    // SlotScreenRect instead.
    private static Optional<Slot> resolveSlot(AbstractContainerMenu menu, int containerIndex) {
        for (Slot s : menu.slots) {
            Slot target = Slots.target(s);
            if (target.container instanceof Inventory && target.getContainerSlot() == containerIndex) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /**
     * The on-screen item box, in absolute screen pixels, of the player-inventory slot
     * {@code containerIndex} on {@code screen}, or empty when the screen doesn't show
     * that slot. Place slots or decoration against it with no per-screen code.
     */
    public static Optional<SlotScreenRect> resolve(AbstractContainerScreen<?> screen, int containerIndex) {
        return resolveSlot(screen.getMenu(), containerIndex).map(s -> {
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            return new SlotScreenRect(acc.sk$getLeftPos() + s.x,
                    acc.sk$getTopPos() + s.y, 16, 16);
        });
    }
}
