package com.trevlar.slotkit.core;


import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one place a slot in a screen's {@code menu.slots} is resolved to the real
 * vanilla {@link Slot} it stands for: itself on most screens, or the slot a
 * {@code SlotWrapper} delegates to on the creative inventory tab.
 *
 * <h3>Why everything goes through it</h3>
 *
 * The creative inventory tab wraps every player-inventory slot in a
 * {@code SlotWrapper} whose {@code getContainerSlot()} reports the wrapper's own
 * index. So any code reading a slot's identity off {@code menu.slots} gets the
 * wrong answer on creative unless it unwraps first, whether it asks "which
 * player-inventory slot is this?" (anchoring a panel to the hotbar, say) or "is
 * this one of my slots?" (created slots). {@link #target} is that unwrap,
 * shared by both:
 *
 * <ul>
 *   <li>{@link VanillaSlotResolver} reads {@code target.container} +
 *       {@code target.getContainerSlot()} to find a vanilla player-inventory
 *       slot's on-screen position;</li>
 *   <li>{@code CreatedSlotAccess.asMKCSlot} tests whether
 *       {@code target} is a registered slot.</li>
 * </ul>
 *
 * Before this, only created slots could see through the wrapper
 * ({@code asMKCSlot}), and a plain vanilla slot was unreachable on creative, which
 * left hotbar-anchored slots (pockets) dark there.
 *
 * <p>Runs on both sides: common code (operations, slot references) calls it, and
 * the client installs the unwrap, since the creative wrapper is a client type.
 * Consumers address slots by {@link com.trevlar.slotkit.api.window.Address} and
 * never call this.
 */
@ApiStatus.Internal
public final class Slots {

    private Slots() {}

    /**
     * The real vanilla {@link Slot} {@code slot} is or wraps: the slot a creative
     * {@code SlotWrapper} delegates to, or {@code slot} itself when it is not a
     * wrapper. Unwraps one level, since creative wraps each slot exactly once.
     *
     * <p>Read <em>identity</em> (container, container-index, registered type) off the
     * returned target; read <em>on-screen position</em> ({@code x}/{@code y}) off
     * the original {@code slot}, which is the wrapper carrying the creative
     * coordinates.
     */
    public static Slot target(Slot slot) {
        return unwrap.apply(slot);
    }

    // The creative wrapper is a client class, so the client installs the unwrap
    // (SlotKitClient); on a server there are no wrappers and a slot is its own target.
    private static volatile java.util.function.UnaryOperator<Slot> unwrap = java.util.function.UnaryOperator.identity();

    /** SlotKitClient installs the creative-wrapper unwrap at client init. */
    public static void installUnwrap(java.util.function.UnaryOperator<Slot> impl) {
        com.trevlar.menukit.api.Declarations.requireOpen("Slots.installUnwrap");
        unwrap = java.util.Objects.requireNonNull(impl, "impl");
    }
}
