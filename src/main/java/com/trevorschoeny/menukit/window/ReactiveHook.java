package com.trevorschoeny.menukit.window;

/**
 * A consumer's reaction to a slot's contents changing — the value type of the
 * {@code ON_INSERT}/{@code ON_TAKE} behavior keys (and their observed variants).
 * Resolved by the same {@link WindowEngine} cascade as every other behavior, so a
 * reaction can be set per-slot, per-group, or as a library default, and overridden
 * the identical way.
 *
 * <p>The default for a reaction key is a genuine no-op (Bloch Item 21: default
 * methods / Null-Object only as true no-ops), so a slot nobody reacts to costs
 * nothing and never crashes — the architecture's "designed complete, the firing
 * seams are the one honest gap."
 */
@FunctionalInterface
public interface ReactiveHook {

    /** React to a contents change. Implementations should be cheap and side-effect-honest. */
    void react(ReactEvent event);

    /** The Null-Object reaction — does nothing. The library default for every reaction key. */
    ReactiveHook NONE = event -> {};
}
