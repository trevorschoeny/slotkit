package com.trevlar.slotkit.api.window;

import net.minecraft.resources.Identifier;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Names one behavior the window can resolve: gating, visibility, a panel's
 * opacity, a reaction and so on. The engine is generic over the key, so adding a
 * behavior means declaring a new {@code BehaviorKey} (id, value type, library
 * default, tier, applicable kinds), with no engine change.
 *
 * @param id            the stable name.
 * @param valueType     the resolved value's type, for type-safe storage.
 * @param libraryDefault the bottom of the cascade, a neutral value that matches
 *                       vanilla, returned when nothing overrides. It is why
 *                       {@code resolve} never returns null.
 * @param tier          {@link Tier#CLIENT} (works with MenuKit alone) or
 *                       {@link Tier#SERVER} (needs SlotKit on the server).
 * @param appliesTo     which {@link KindTag}s this behavior is valid on, checked
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
        appliesTo = EnumSet.copyOf(appliesTo); // EnumSet is mutable
    }

    /** Whether this behavior is valid on {@code kind}. Checked at the call boundary. */
    public boolean appliesTo(KindTag kind) {
        return appliesTo.contains(kind);
    }

    public static <V> BehaviorKey<V> of(Identifier id, Class<V> valueType, V libraryDefault,
                                        Tier tier, KindTag first, KindTag... rest) {
        return new BehaviorKey<>(id, valueType, libraryDefault, tier, EnumSet.of(first, rest));
    }
}
