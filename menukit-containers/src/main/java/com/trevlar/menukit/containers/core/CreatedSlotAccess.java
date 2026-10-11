package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.window.SlotGating;
import com.trevlar.menukit.inject.Slots;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a slot in a screen's {@code menu.slots} to the {@link CreatedSlot} it
 * represents, either directly or through the creative {@code SlotWrapper} around it.
 * This is the one place that answers "is this a registered slot?" for both the
 * survival inventory (raw {@code CreatedSlot}s in the menu) and the creative screen
 * (each slot wrapped in a {@code SlotWrapper}).
 *
 * <p>Uses MenuKit's generic {@link Slots#target} unwrap, the same one
 * {@link com.trevlar.menukit.api.slot.VanillaSlotResolver} uses for vanilla slots, so
 * there is one unwrap path for everything. This only adds the Containers question:
 * is the unwrapped target a created slot?
 *
 * <p>Runs on both sides. The creative {@code SlotWrapper} is a client type, but
 * {@link Slots#target} tests an interface in MenuKit's own jar and never loads that
 * client class. On the server no slot is a wrapper, so it returns the slot unchanged.
 * The window's server enforcement ({@link SlotAddresses}, {@link SlotGating}) calls
 * this on the logical server to detect a created slot, and the render and input
 * helpers call it on the client.
 *
 * <p>Internal. Consumers never hand MenuKit a raw vanilla {@code Slot}; they address
 * created slots by their {@link com.trevlar.menukit.api.window.Address}.
 */
@ApiStatus.Internal
public final class CreatedSlotAccess {

    private CreatedSlotAccess() {}

    /**
     * The registered slot {@code slot} is or wraps, or {@code null} if it is an
     * ordinary slot. Unwraps a creative {@code SlotWrapper} via
     * {@link Slots#target}, then tests whether the real slot is a created slot.
     */
    public static @Nullable CreatedSlot asMKCSlot(Slot slot) {
        return Slots.target(slot) instanceof CreatedSlot mk ? mk : null;
    }
}
