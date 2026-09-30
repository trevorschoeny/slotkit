package com.trevlar.menukit.api.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Why a reaction fired, an <b>open</b>, identity-tagged descriptor, deliberately
 * <em>not</em> a sealed enum a consumer must {@code switch} over (architecture
 * Part 2 §6). A {@link ReactiveHook} that cares can compare {@link #id()}; one
 * that doesn't can ignore it entirely. The library fires one cause, {@link #SYNC};
 * a consumer mints its own with {@link #of(Identifier)} without touching this type,
 * the same open-for-extension shape as {@link BehaviorKey}.
 */
public record ReactCause(Identifier id) {

    public ReactCause {
        Objects.requireNonNull(id, "id");
    }

    /** Mint a cause from any identifier (consumer- or future-verb-defined). */
    public static ReactCause of(Identifier id) {
        return new ReactCause(id);
    }

    private static ReactCause builtin(String path) {
        return new ReactCause(Identifier.fromNamespaceAndPath("menukit", path));
    }

    /** The client observed synced slot contents change (no authority; observed tier). */
    public static final ReactCause SYNC = builtin("sync");
}
