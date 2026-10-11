package com.trevlar.menukit.api.window;

/**
 * The value a {@link Decl.Set} carries for a boolean {@link BehaviorKey}, so
 * "explicitly off" is a declared value, distinct from "unset" or "inherit".
 *
 * <h2>Why not {@code boolean} or {@code Boolean}</h2>
 *
 * A cascade level either declares {@code Set(v)} (stop, use {@code v}) or does
 * not contribute ({@code Inherit} or absent, fall through). A primitive inside
 * {@code Set} makes {@code Set(FALSE)} easy to misread as "no declaration" and
 * invites a nullable {@code Boolean}. With an enum, {@code Set(FALSE)} is plainly
 * a declared off.
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
