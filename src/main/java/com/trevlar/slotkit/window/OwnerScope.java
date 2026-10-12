package com.trevlar.slotkit.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Namespaces an owner coordinate when a bare {@code (family, index)} would be
 * ambiguous. Used inside a {@code RootOwner}.
 *
 * <h2>Two separate sources of collision</h2>
 *
 * Two unrelated things can make the same flat index mean different things. Both
 * are namespaced here, as separate scopes:
 * <ul>
 *   <li>{@link Tab}: the active creative tab. The creative menu rebuilds its slot
 *       list for each tab, so index 5 holds different content per tab. The id is
 *       {@code BuiltInRegistries.CREATIVE_MODE_TAB.getKey(
 *       CreativeModeInventoryScreen.selectedTab)}, a stable {@code Identifier}.
 *       It lives on the screen, not the menu, so {@code Tab} is client-tier only:
 *       a server-tier address must never carry a tab scope.</li>
 *   <li>{@link Sub}: a menu backed by more than one container (a double chest,
 *       furnace segments). The id is the stable per-backing key from composite
 *       resolution ({@code ResolvedSlot.key()}), turned into a string where
 *       MenuKit: Containers mints the address.</li>
 * </ul>
 *
 * <h2>Open to new scopes</h2>
 *
 * New scopes can be added; handlers always keep a default branch. The two sources
 * stay separate types rather than one merged "scope id", so a new source can be
 * added without being confused with these.
 */
public sealed interface OwnerScope permits OwnerScope.Primary, OwnerScope.Tab, OwnerScope.Sub {

    /** The default scope, with no namespacing (the common case). */
    record Primary() implements OwnerScope {}

    /** The active creative tab's scope. Client tier only. */
    record Tab(Identifier tabId) implements OwnerScope {
        public Tab { Objects.requireNonNull(tabId, "tabId"); }
    }

    /** A per-backing scope for a composite container. The server can derive it. */
    record Sub(String backingId) implements OwnerScope {
        public Sub {
            Objects.requireNonNull(backingId, "backingId");
            if (backingId.isBlank()) throw new IllegalArgumentException("backingId must not be blank");
        }
    }

    /** Shared instance of the default scope. */
    Primary PRIMARY = new Primary();

    /** The default scope, with no namespacing. */
    static OwnerScope primary() { return PRIMARY; }

    /** Creative-tab scope from a stable tab id. Client tier only. */
    static OwnerScope tab(Identifier tabId) { return new Tab(tabId); }

    /** Composite per-backing scope from a stable backing id. */
    static OwnerScope sub(String backingId) { return new Sub(backingId); }
}
