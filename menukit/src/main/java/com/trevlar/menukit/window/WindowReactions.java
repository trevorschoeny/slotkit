package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.ReactiveHook;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.ReactCause;
import com.trevlar.menukit.api.window.ReactEvent;
import com.trevlar.menukit.api.window.WindowEngine;
import net.minecraft.world.item.ItemStack;

/**
 * The fire-entry for the observed reactions: the one place a synced contents change
 * is turned into a {@link ReactiveHook} invocation, going through the same
 * {@link WindowEngine} cascade as every behavior and bounded by the same
 * {@link ReactionGuard}. Its one caller is {@link ObservedReactions}, on the client.
 *
 * <h2>Explicit verbs, not a guess</h2>
 *
 * Insert and take are separate calls rather than one auto-classified entry, because
 * the caller knows what it saw: a swap is <em>both</em> a take of the old stack and
 * an insert of the new, so the caller fires {@link #fireTake} then {@link #fireInsert}.
 */
public final class WindowReactions {

    private WindowReactions() {}

    /** Fire the observed insert reaction for a gain of content at {@code address}. */
    public static void fireInsert(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        fire(address, BehaviorKeys.ON_INSERT_OBSERVED, before, after, cause);
    }

    /** Fire the observed take reaction for a loss of content at {@code address}. */
    public static void fireTake(Address address, ItemStack before, ItemStack after, ReactCause cause) {
        fire(address, BehaviorKeys.ON_TAKE_OBSERVED, before, after, cause);
    }

    // ── internal: resolve, guard, invoke ────────────────────────────────────

    private static void fire(Address address, BehaviorKey<ReactiveHook> key,
                             ItemStack before, ItemStack after, ReactCause cause) {
        ReactiveHook hook = WindowEngine.resolve(address, key);
        if (hook == ReactiveHook.NONE) return; // nobody reacts here, zero cost

        ReactEvent event = ReactEvent.snapshot(address, before, after, cause);
        ReactionGuard.run(address, key, () -> hook.react(event));
    }
}
