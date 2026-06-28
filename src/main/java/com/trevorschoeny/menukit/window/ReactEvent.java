package com.trevorschoeny.menukit.window;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What a {@link ReactiveHook} receives when a slot's contents change. Carries the
 * {@link Address} of the slot, an immutable before/after snapshot of its stack,
 * and an extensible {@link ReactCause} — <b>never the raw {@code Slot}</b>
 * (architecture Part 2 §6: the window works with a slot, never hands one out).
 *
 * <p>The snapshots are defensive {@code copy()}s taken at fire time, so a hook can
 * read them freely without observing further mutation and without being able to
 * mutate the live slot through them. {@code before}/{@code after} are
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

    /** Build an event, snapshotting both stacks so the hook can't reach the live slot. */
    public static ReactEvent snapshot(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        return new ReactEvent(address, before.copy(), after.copy(), cause);
    }
}
