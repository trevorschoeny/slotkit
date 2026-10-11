package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.BehaviorKey;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounds re-entry for reactions. A reaction may write back through the window,
 * which can trigger another reaction; without a bound, a chain from slot to element
 * to panel and back to the slot could loop forever.
 *
 * <h2>Two bounds, both per propagation</h2>
 *
 * <ol>
 *   <li><b>A visited set keyed by {@code (Address, BehaviorKey)}</b>, not by slot
 *       alone, so a chain across kinds (a slot's observed take, then an element's
 *       reaction, then a panel's visibility, then the slot again) ends the same way
 *       an all-slot chain does: the first time a pair repeats on the current
 *       propagation stack, that branch ends. A pair is in the set only while it is
 *       on the stack ({@link #run} removes it on the way out), so a sibling branch
 *       may fire the same pair.</li>
 *   <li><b>A depth limit</b> on nesting, so even a chain of all-distinct pairs
 *       can't run away.</li>
 * </ol>
 *
 * <p>The state is a {@link ThreadLocal}, since observed reactions fire on the
 * client thread. It is empty again once the outermost {@link #run} returns, and the
 * ThreadLocal is removed at depth zero so nothing leaks from one sweep to the next.
 */
public final class ReactionGuard {

    private ReactionGuard() {}

    /** Nesting ceiling for one propagation. Generous: real chains are one or two deep. */
    static final int MAX_DEPTH = 16;

    private record Visit(Address address, BehaviorKey<?> key) {}

    private static final class Propagation {
        final Set<Visit> onStack = new HashSet<>();
        int depth = 0;
    }

    private static final ThreadLocal<Propagation> STATE = new ThreadLocal<>();

    /**
     * Runs {@code body} as a reaction for {@code (address, key)} if the pair is not
     * already on this propagation's stack and the depth ceiling is not reached.
     * Otherwise the branch is dropped silently, which breaks the loop without a
     * crash. The pair is removed and the depth restored even if {@code body} throws.
     *
     * @return {@code true} if {@code body} ran, {@code false} if the bound dropped it.
     */
    public static boolean run(Address address, BehaviorKey<?> key, Runnable body) {
        Propagation p = STATE.get();
        boolean outermost = (p == null);
        if (outermost) {
            p = new Propagation();
            STATE.set(p);
        }
        Visit visit = new Visit(address, key);
        if (p.depth >= MAX_DEPTH || !p.onStack.add(visit)) {
            // Bound hit: drop this branch. If this call created the propagation
            // (which can't happen at depth 0), remove it again anyway.
            if (outermost) STATE.remove();
            return false;
        }
        p.depth++;
        try {
            body.run();
            return true;
        } finally {
            p.depth--;
            p.onStack.remove(visit);
            if (outermost) STATE.remove(); // sweep complete, reset
        }
    }
}
