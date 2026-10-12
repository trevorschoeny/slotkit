package com.trevlar.slotkit.api.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Why a reaction fired. It is an open, id-tagged value rather than a sealed enum a
 * consumer has to {@code switch} over. A {@link ReactiveHook} that cares compares
 * {@link #id()}; one that doesn't ignores it. The library fires one cause,
 * {@link #SYNC}, and a consumer mints its own with {@link #of(Identifier)}, the
 * same open shape as {@link BehaviorKey}.
 */
public record ReactCause(Identifier id) {

    public ReactCause {
        Objects.requireNonNull(id, "id");
    }

    /** Mints a cause from any identifier. */
    public static ReactCause of(Identifier id) {
        return new ReactCause(id);
    }

    private static ReactCause builtin(String path) {
        return new ReactCause(Identifier.fromNamespaceAndPath("menukit", path));
    }

    /** The client saw synced slot contents change. Observed only, with no authority. */
    public static final ReactCause SYNC = builtin("sync");
}
