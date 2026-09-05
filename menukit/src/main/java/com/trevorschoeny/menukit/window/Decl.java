package com.trevorschoeny.menukit.window;

import java.util.Objects;

/**
 * A behavior declaration at ONE specificity level (per-slot, per-group, …). The
 * tri-state of THE ONE WINDOW's cascade, made structural:
 *
 * <ul>
 *   <li>{@link Set} — this level declares an explicit value; the walk STOPS here
 *       and uses it (including an explicit OFF, e.g. {@code Set(TriBool.FALSE)}).</li>
 *   <li>{@link Inherit} — this level explicitly defers; the walk falls through to
 *       the next-lower level. Behaviorally identical to the absence of any
 *       declaration at this level, but nameable on purpose.</li>
 * </ul>
 *
 * <h2>FROZEN-CLOSED</h2>
 *
 * Unlike {@link KindTag} / {@link Token} (frozen-OPEN), the {@code Decl} permits
 * set {@code {Set, Inherit}} is frozen forever — there is never a third kind of
 * declaration. The resolver may switch on it exhaustively.
 */
public sealed interface Decl<V> permits Decl.Set, Decl.Inherit {

    /** An explicit declared value; the cascade stops here. */
    record Set<V>(V value) implements Decl<V> {
        public Set {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Explicit deferral; the cascade falls through to the next level. */
    record Inherit<V>() implements Decl<V> {}

    // Inherit carries no state, so one instance serves every V.
    Inherit<?> INHERIT = new Inherit<>();

    static <V> Decl<V> set(V value) {
        return new Set<>(value);
    }

    @SuppressWarnings("unchecked")
    static <V> Decl<V> inherit() {
        return (Decl<V>) (Decl<?>) INHERIT;
    }
}
