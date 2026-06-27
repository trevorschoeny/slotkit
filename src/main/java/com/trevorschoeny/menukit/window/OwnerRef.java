package com.trevorschoeny.menukit.window;

/**
 * The owner an {@link Address} is resolved relative to — the chain that lets a
 * handle re-bind to the live backing each call (and that makes the held-handle
 * "wrong/no screen" case a clean no-op: an owner that doesn't resolve against the
 * live session yields the neutral default).
 *
 * <ul>
 *   <li>{@link RootOwner} — bottoms out the chain at a menu family + scope.</li>
 *   <li>{@link NestedOwner} — a thing owned by another addressable (a panel
 *       element owned by its panel; a panel-in-panel). Composes to arbitrary
 *       depth; every chain terminates at a {@link RootOwner}.</li>
 * </ul>
 *
 * <p>The owner level also IS the cascade scope: a group/inheritance default
 * attached at an {@code OwnerRef} is inherited by every {@link Address} whose
 * owner chain passes through it (the nesting gives panel→element inheritance for
 * free). Pure value (record equality).
 *
 * <p>Sealed-extensible; FROZEN-OPEN like {@link Token}.
 */
public sealed interface OwnerRef permits OwnerRef.RootOwner, OwnerRef.NestedOwner {

    /** Chain root: a menu family namespaced by a scope. */
    record RootOwner(ScreenFamilyKey family, OwnerScope scope) implements OwnerRef {
        public RootOwner {
            java.util.Objects.requireNonNull(family, "family");
            java.util.Objects.requireNonNull(scope, "scope");
        }
    }

    /** A thing owned by another addressable, identified by the parent's token. */
    record NestedOwner(OwnerRef parent, Token parentToken) implements OwnerRef {
        public NestedOwner {
            java.util.Objects.requireNonNull(parent, "parent");
            java.util.Objects.requireNonNull(parentToken, "parentToken");
        }
    }

    static OwnerRef root(ScreenFamilyKey family, OwnerScope scope) {
        return new RootOwner(family, scope);
    }

    static OwnerRef nested(OwnerRef parent, Token parentToken) {
        return new NestedOwner(parent, parentToken);
    }
}
