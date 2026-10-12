package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.ReactiveHook;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.ReactCause;
import com.trevlar.slotkit.api.window.ReactEvent;
import com.trevlar.slotkit.api.window.WindowEngine;
import net.minecraft.world.item.ItemStack;

/**
 * Fires the observed reactions: the one place a synced contents change becomes a
 * {@link ReactiveHook} call, resolved through the {@link WindowEngine} cascade like
 * every behavior and bounded by {@link ReactionGuard}. Its one caller is
 * {@link ObservedReactions}, on the client.
 *
 * <p>Insert and take are separate calls rather than one classifying entry, because
 * the caller knows what it saw: a swap is <em>both</em> a take of the old stack and
 * an insert of the new, so the caller fires {@link #fireTake} then {@link #fireInsert}.
 */
public final class WindowReactions {

    private WindowReactions() {}

    /** Fires the observed insert reaction for a gain of content at {@code address}. */
    public static void fireInsert(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        fire(address, BehaviorKeys.ON_INSERT_OBSERVED, before, after, cause);
    }

    /** Fires the observed take reaction for a loss of content at {@code address}. */
    public static void fireTake(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        fire(address, BehaviorKeys.ON_TAKE_OBSERVED, before, after, cause);
    }

    private static void fire(Address address, BehaviorKey<ReactiveHook> key,
                             ItemStack before, ItemStack after, ReactCause cause) {
        ReactiveHook hook = WindowEngine.resolve(address, key);
        if (hook == ReactiveHook.NONE) return; // nobody reacts here

        ReactEvent event = ReactEvent.snapshot(address, before, after, cause);
        ReactionGuard.run(address, key, () -> hook.react(event));
    }
}
