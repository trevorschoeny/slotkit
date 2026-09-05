package com.trevlar.menukit.window;

/**
 * An explicit two-valued boolean for boolean {@link BehaviorKey}s — the value a
 * {@link Decl.Set} carries, so "explicitly off" is a real declared value
 * distinct from "unset / inherit".
 *
 * <h2>Why not {@code boolean}/{@code Boolean}</h2>
 *
 * The cascade's tri-state is structural: a level either declares {@code Set(v)}
 * (stop, use {@code v}) or doesn't contribute ({@code Inherit} / absent, fall
 * through). Carrying a primitive {@code boolean} inside {@code Set} would make
 * {@code Set(FALSE)} and "no declaration" look the same to a careless reader and
 * invites a nullable {@code Boolean}. A dedicated enum makes {@code Set(FALSE)}
 * unmistakably a declared OFF — never confused with {@code Inherit} or absence.
 */
public enum TriBool {
    TRUE,
    FALSE;

    public boolean asBoolean() {
        return this == TRUE;
    }

    public static TriBool of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
