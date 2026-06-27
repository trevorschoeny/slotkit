package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Optional;

/**
 * Resolves a <b>player-inventory slot's on-screen position</b> — slot-agnostic
 * and screen-agnostic. The consumer declares "anchor to player-inventory slot
 * N" and gets back the rect on whatever screen is open; the library owns the
 * slot loop and the creative {@code SlotWrapper}, so the consumer writes zero
 * per-screen geometry.
 *
 * <h3>The capability this adds</h3>
 *
 * MenuKit's panel anchoring ({@link SlotGroupCategories} / {@link SlotGroupPanelAdapter})
 * already positions panels against a <em>category</em> of slots by index-slicing
 * {@code menu.slots}. This resolver answers the complementary question a slot
 * consumer asks: "where is the single vanilla slot whose player-inventory index
 * is N, <em>on this screen</em>?" — by identity, not by slice. That identity test
 * (`container` + `getContainerSlot`) is exactly what creative's {@code SlotWrapper}
 * breaks, so this resolves through {@link Slots#target}: identity off the
 * unwrapped target, position off the in-menu slot (the wrapper, which carries the
 * creative coordinates).
 *
 * <h3>Slot-agnostic</h3>
 *
 * {@code containerIndex} is the slot's index within the player {@link Inventory}
 * (the value {@code Slot.getContainerSlot()} reports for a raw player slot):
 * {@code 0–8} hotbar, {@code 9–35} main inventory, and the player's equipment
 * indices for armor/offhand. Not a hotbar helper — any player-inventory slot.
 *
 * <h3>Screen-agnostic</h3>
 *
 * Works on any screen whose menu surfaces the player inventory — survival
 * inventory, the creative inventory tab (slots wrapped), and container screens
 * (chest/furnace, which build their own raw player-inventory slots). No
 * registered per-menu resolver required; the player {@link Inventory} identity
 * is intrinsic to the slots themselves.
 *
 * <p>Client-only: reads the screen frame + the creative wrapper, both client
 * types.
 */
public final class VanillaSlotResolver {

    private VanillaSlotResolver() {}

    /**
     * The in-menu {@link Slot} for player-inventory {@code containerIndex} on
     * {@code menu} — the raw slot on most screens, or the creative
     * {@code SlotWrapper} around it (so its {@code x}/{@code y} carry the creative
     * position). Empty when the menu doesn't surface that player slot.
     *
     * <p>The menu-level entry: for a consumer that already works in screen-frame
     * coordinates and wants the slot's frame-relative {@code x}/{@code y} (e.g.
     * the Pocket column reading the live hotbar position off the menu).
     */
    public static Optional<Slot> resolveSlot(AbstractContainerMenu menu, int containerIndex) {
        for (Slot s : menu.slots) {
            Slot target = Slots.target(s);
            if (target.container instanceof Inventory && target.getContainerSlot() == containerIndex) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /**
     * The absolute on-screen item box for player-inventory {@code containerIndex}
     * on {@code screen}, or empty when the screen doesn't surface that player slot.
     *
     * <p>The screen-level entry: the turnkey "register once, anchor anywhere" call
     * — returns a {@link SlotScreenRect} in absolute screen pixels, so a slot
     * consumer can place its slots/decoration against it with no per-screen
     * branching at all.
     */
    public static Optional<SlotScreenRect> resolve(AbstractContainerScreen<?> screen, int containerIndex) {
        return resolveSlot(screen.getMenu(), containerIndex).map(s -> {
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            return new SlotScreenRect(acc.menuKit$getLeftPos() + s.x,
                    acc.menuKit$getTopPos() + s.y, 16, 16);
        });
    }
}
