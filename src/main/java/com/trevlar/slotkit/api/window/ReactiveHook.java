package com.trevlar.slotkit.api.window;

/**
 * A consumer's reaction to a slot's synced contents changing on the client, the
 * value type of the {@code ON_INSERT_OBSERVED}/{@code ON_TAKE_OBSERVED} behavior keys.
 * Resolved by the same {@link WindowEngine} cascade as every other behavior, so a
 * reaction can be set per-slot, per-group, or as a library default, and overridden
 * the identical way.
 *
 * <p>The default for a reaction key is a true no-op, so a slot nobody reacts to
 * costs nothing and never crashes.
 */
@FunctionalInterface
public interface ReactiveHook {

    /** Reacts to a contents change. Keep it cheap. */
    void react(ReactEvent event);

    /** Does nothing. The library default for every reaction key. */
    ReactiveHook NONE = event -> {};
}
