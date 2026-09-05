package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.SlotWrapperAccessor;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one place a slot in a screen's {@code menu.slots} is resolved to the real
 * vanilla {@link Slot} it represents — itself on most screens, or the slot a
 * creative {@code SlotWrapper} delegates to on the creative inventory tab.
 *
 * <h3>Why this is the foundation, not a slot detail</h3>
 *
 * The creative inventory tab wraps every player-inventory slot in a
 * {@code SlotWrapper} whose {@code getContainerSlot()} reports the wrapper's own
 * index. So <em>any</em> code reading a slot's identity off {@code menu.slots} —
 * "which player-inventory slot is this?" (a vanilla concern, e.g. anchoring a
 * panel to the hotbar) or "is this one of my slots?" (a containers concern) —
 * gets the wrong answer on creative unless it unwraps first. {@link #target}
 * is that unwrap, shared by both:
 *
 * <ul>
 *   <li>{@link VanillaSlotResolver} reads {@code target.container} +
 *       {@code target.getContainerSlot()} to find a vanilla player-inventory
 *       slot's on-screen position;</li>
 *   <li>MenuKit-Containers' {@code MKCSlotAccess.asMKCSlot} tests whether
 *       {@code target} is a registered slot.</li>
 * </ul>
 *
 * Before this, only slots could see through the wrapper ({@code asMKCSlot}); a
 * non-slot vanilla slot was unreachable on creative — the gap that left
 * hotbar-anchored slots (pockets) dark there. One unwrap path closes it for
 * everyone.
 *
 * <p>Client-only: the creative wrapper is a client type. Only client-side render
 * + input + anchoring code calls this.
 *
 * <p><b>Internal plumbing.</b> The creative-wrapper unwrap is a library-internal
 * detail of how MK resolves a raw vanilla {@code Slot}'s identity; it is not a
 * consumer surface. Consumers address slots by {@link com.trevlar.menukit.window.Address},
 * never by handing MK a raw {@code Slot} to unwrap.
 */
@ApiStatus.Internal
public final class Slots {

    private Slots() {}

    /**
     * The real vanilla {@link Slot} {@code slot} is or wraps: the slot a creative
     * {@code SlotWrapper} delegates to, or {@code slot} itself when it is not a
     * wrapper. Single-level — creative wraps each slot exactly once.
     *
     * <p>Read <em>identity</em> (container, container-index, registered type) off the
     * returned target; read <em>on-screen position</em> ({@code x}/{@code y}) off
     * the original {@code slot}, which is the wrapper carrying the creative
     * coordinates.
     */
    public static Slot target(Slot slot) {
        if (slot instanceof SlotWrapperAccessor wrapper) {
            Slot t = wrapper.mk$getTarget();
            if (t != null) return t;
        }
        return slot;
    }
}
