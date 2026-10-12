package com.trevlar.slotkit.api.window;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What a {@link ReactiveHook} receives when a slot's contents change. Carries the
 * {@link Address} of the slot, a before and after snapshot of its stack, and a
 * {@link ReactCause}. It never carries the raw {@code Slot}: the window works with
 * slots but does not hand them out.
 *
 * <p>The snapshots are copies taken when the event fires, so a hook sees no later
 * change and cannot change the live slot through them. {@code before}/{@code after} are
 * {@link ItemStack#EMPTY} when the slot was/became empty, so an insert reads as
 * {@code before.isEmpty()} and a take as {@code after.isEmpty()} (or shrunk).
 */
public record ReactEvent(Address address, ItemStack before, ItemStack after, ReactCause cause) {

    public ReactEvent {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(cause, "cause");
    }

    /** Builds an event, copying both stacks so the hook can't reach the live slot. */
    public static ReactEvent snapshot(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        return new ReactEvent(address, before.copy(), after.copy(), cause);
    }
}
