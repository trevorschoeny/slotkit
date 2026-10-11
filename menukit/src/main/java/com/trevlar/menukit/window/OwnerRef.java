package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;

/**
 * The owner an {@link Address} is resolved relative to. The chain lets a handle
 * bind again to the live backing on each call, and makes a handle held past its
 * screen a no-op: an owner that doesn't resolve against the live session yields the
 * neutral default.
 *
 * <ul>
 *   <li>{@link RootOwner}: the bottom of the chain, a menu family and a scope.</li>
 *   <li>{@link NestedOwner}: a thing owned by another addressable (an element owned
 *       by its panel, a panel in a panel). Nests to any depth; every chain ends at
 *       a {@link RootOwner}.</li>
 * </ul>
 *
 * <p>The owner level is also the cascade scope: a default attached at an
 * {@code OwnerRef} is inherited by every {@link Address} whose owner chain passes
 * through it, which is how an element inherits from its panel.
 *
 * <p>Sealed, but open to new arms, like {@link Token}.
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
