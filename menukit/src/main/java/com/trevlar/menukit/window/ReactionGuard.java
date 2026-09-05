package com.trevlar.menukit.window;

import java.util.HashSet;
import java.util.Set;

/**
 * The frozen re-entrancy bound of the reactive contract (architecture Part 3
 * §3.6 / #3b). A reaction may write back through the window, which can trigger
 * another reaction; without a bound, slot→element→panel→slot could loop forever.
 *
 * <h2>Two bounds, both per-propagation</h2>
 *
 * <ol>
 *   <li><b>Visited set keyed by {@code (Address, BehaviorKey)}</b> — not by slot
 *       alone, so a cross-kind chain (a slot's onTake → an element's observed
 *       reaction → a panel's visibility → re-touching the slot) terminates the
 *       same way an all-slot chain does: the first time an {@code (Address, key)}
 *       pair repeats on the current propagation <em>stack</em>, that branch ends.
 *       The pair is on the set only while it is live on the stack ({@link #run}
 *       removes it on the way out), so a sibling branch may fire the same pair.</li>
 *   <li><b>Depth limit</b> — an absolute ceiling on nesting, so even an
 *       all-distinct-pairs chain can't run away.</li>
 * </ol>
 *
 * <p>The state is a {@link ThreadLocal} (server reactions fire on the server
 * thread inside the menu transaction; observed reactions on the client thread).
 * It is naturally empty again once the outermost {@link #run} returns — that is
 * the "reset when the transaction/sync sweep completes" the architecture names —
 * and the ThreadLocal is removed at depth zero so nothing leaks across sweeps.
 */
public final class ReactionGuard {

    private ReactionGuard() {}

    /** Absolute nesting ceiling for one propagation. Generous; real chains are 1–2 deep. */
    static final int MAX_DEPTH = 16;

    private record Visit(Address address, BehaviorKey<?> key) {}

    private static final class Propagation {
        final Set<Visit> onStack = new HashSet<>();
        int depth = 0;
    }

    private static final ThreadLocal<Propagation> STATE = new ThreadLocal<>();

    /**
     * Run {@code body} as a reaction for {@code (address, key)} iff the bound
     * allows it — i.e. that pair is not already live on this propagation's stack
     * and the depth ceiling is not reached. Otherwise the branch is dropped
     * silently (the loop is broken, not crashed). Always balanced: the pair is
     * removed and depth restored even if {@code body} throws.
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
            // Bound hit: drop this branch. If we created the propagation just now
            // (the outermost call itself is over-deep — impossible at depth 0, but
            // keep the cleanup honest), tear it back down.
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
            if (outermost) STATE.remove(); // sweep complete → reset
        }
    }
}
