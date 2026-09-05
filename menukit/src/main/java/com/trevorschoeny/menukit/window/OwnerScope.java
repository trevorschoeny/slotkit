package com.trevorschoeny.menukit.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Namespaces an owner-coordinate when a bare {@code (family, index)} would alias
 * — the disambiguation tool inside a {@code RootOwner}.
 *
 * <h2>The two collision sources (kept distinct on purpose)</h2>
 *
 * Two genuinely-unlike sources can make the same flat index mean different
 * things; they are namespaced here but are NOT asserted to share a source:
 * <ul>
 *   <li>{@link Tab} — the active creative tab. The creative menu rebuilds its
 *       slot list per selected tab, so index 5 means different content per tab.
 *       Source: {@code BuiltInRegistries.CREATIVE_MODE_TAB.getKey(
 *       CreativeModeInventoryScreen.selectedTab)} (a stable {@code
 *       Identifier}). The discriminator lives on the SCREEN, not the menu,
 *       so {@code Tab} is <b>CLIENT-tier-only</b> — a server-tier address must
 *       never carry a tab scope.</li>
 *   <li>{@link Sub} — a composite/multi-backing menu (double chest, furnace
 *       segments). Source: the stable per-backing id from §0050 composite
 *       resolution ({@code ResolvedSlot.key()}), adapted to an MK-safe string at
 *       the (MKC) mint site.</li>
 * </ul>
 *
 * <h2>Sealed-extensible</h2>
 *
 * New scopes are additive; handlers treat this non-exhaustively. The two sources
 * stay separate constructors rather than one merged "scope id" so a future
 * collision source can be added without conflating it with these.
 */
public sealed interface OwnerScope permits OwnerScope.Primary, OwnerScope.Tab, OwnerScope.Sub {

    /** The default scope — no namespacing needed (the common case). */
    record Primary() implements OwnerScope {}

    /** Active-creative-tab scope (CLIENT-tier only). */
    record Tab(Identifier tabId) implements OwnerScope {
        public Tab { Objects.requireNonNull(tabId, "tabId"); }
    }

    /** Composite per-backing scope (server-derivable from §0050). */
    record Sub(String backingId) implements OwnerScope {
        public Sub {
            Objects.requireNonNull(backingId, "backingId");
            if (backingId.isBlank()) throw new IllegalArgumentException("backingId must not be blank");
        }
    }

    /** Shared instance of the default scope (Primary has no state). */
    Primary PRIMARY = new Primary();

    /** The default scope — no namespacing. */
    static OwnerScope primary() { return PRIMARY; }

    /** Creative-tab scope from a stable tab id. CLIENT-tier only. */
    static OwnerScope tab(Identifier tabId) { return new Tab(tabId); }

    /** Composite per-backing scope from a stable backing id. */
    static OwnerScope sub(String backingId) { return new Sub(backingId); }
}
