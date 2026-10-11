package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.Token;
import java.util.Objects;

/**
 * A behavior declaration at one specificity level (per slot, per group and so on):
 *
 * <ul>
 *   <li>{@link Set}: this level declares a value, and the cascade stops here and
 *       uses it, an explicit off such as {@code Set(TriBool.FALSE)} included.</li>
 *   <li>{@link Inherit}: this level defers, and the cascade falls through to the
 *       next level down. It behaves exactly like no declaration, but can be
 *       written on purpose.</li>
 * </ul>
 *
 * <h2>Closed</h2>
 *
 * Unlike {@link KindTag} and {@link Token}, which are open to new values,
 * {@code Decl} will only ever be {@code Set} or {@code Inherit}, so the resolver
 * may switch on it exhaustively.
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
