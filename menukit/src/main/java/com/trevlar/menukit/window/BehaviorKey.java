package com.trevlar.menukit.window;

import net.minecraft.resources.Identifier;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Names ONE behavior the window can resolve — gating, visibility, a panel's
 * opacity, a reaction, and so on. The engine is generic over the key, so adding a
 * behavior is exactly: declare a new {@code BehaviorKey} (id + value type +
 * library default + tier + applicable kinds). Zero engine change; the cascade,
 * store, tiers, and ports all key on this uniformly.
 *
 * @param id            the stable name.
 * @param valueType     the resolved value's type (for safe heterogeneous storage).
 * @param libraryDefault the always-present bottom of the cascade — a genuine
 *                       vanilla-equivalent neutral, returned when nothing
 *                       overrides. The reason {@code resolve} is total (never null).
 * @param tier          {@link Tier#CLIENT} (MK-alone) or {@link Tier#SERVER}
 *                       (MKC-unlocked).
 * @param appliesTo     which {@link KindTag}s this behavior is valid on — checked
 *                       at the call boundary (a slot verb is unreachable on a
 *                       panel), never inside resolution.
 */
public record BehaviorKey<V>(Identifier id, Class<V> valueType, V libraryDefault, Tier tier, EnumSet<KindTag> appliesTo) {

    public BehaviorKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(libraryDefault, "libraryDefault");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(appliesTo, "appliesTo");
        if (appliesTo.isEmpty()) throw new IllegalArgumentException("appliesTo must name at least one kind");
        appliesTo = EnumSet.copyOf(appliesTo); // defensive copy — EnumSet is mutable
    }

    /** Whether this behavior is valid on {@code kind} (the call-boundary applicability check). */
    public boolean appliesTo(KindTag kind) {
        return appliesTo.contains(kind);
    }

    /** Convenience constructor. */
    public static <V> BehaviorKey<V> of(Identifier id, Class<V> valueType, V libraryDefault,
                                        Tier tier, KindTag first, KindTag... rest) {
        return new BehaviorKey<>(id, valueType, libraryDefault, tier, EnumSet.of(first, rest));
    }
}
