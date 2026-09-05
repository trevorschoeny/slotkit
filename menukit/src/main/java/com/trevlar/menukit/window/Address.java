package com.trevlar.menukit.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * THE keystone of THE ONE WINDOW: one kind-agnostic value that names ANY
 * addressable thing — a vanilla slot, a created slot, a panel element, or a panel
 * itself — so the engine can resolve every behavior by address without ever
 * asking what kind it is.
 *
 * <h2>The four shapes</h2>
 * <pre>
 *   vanilla slot   : RootOwner(family, scope)              + IndexToken(i)    + VANILLA_SLOT
 *   created slot   : NestedOwner(RootOwner(family,scope), RegToken(panel)) + DeclToken(id) + CREATED_SLOT
 *   panel element  : NestedOwner(RootOwner(family,scope), RegToken(panel)) + DeclToken(id) + PANEL_ELEMENT
 *   panel itself   : RootOwner(family, scope)              + RegToken(panel) + PANEL
 * </pre>
 * The old slot-only {@code SlotAddress(family, index)} is exactly the first line
 * — this is a strict superset, not a replacement.
 *
 * <h2>Identity contract</h2>
 *
 * Equality is component-wise over {@code (owner, token, kind)} — automatic from
 * the record. {@code KindTag} participates in equality so a panel and a slot at
 * the same coordinate can never collide, and a created slot vs a panel element at
 * the same declaration id are distinct. The address holds NO live object, so two
 * addresses naming the same thing are {@code .equals()} and interchangeable —
 * safe to store in a static map, compare, and pass across the client/server
 * boundary as a key. Reopen-stability follows: the same logical thing rebuilds to
 * an equal address as long as its sources are deterministic (Mojang's menu index;
 * the consumer's panel/decl ids; the registration order) — which the keystone
 * checks confirmed they are.
 *
 * <h2>Sources (wired at mint time — Phase 2)</h2>
 * The value-level constructors here are fed by the confirmed live sources: the
 * creative tab via {@code OwnerScope.tab(BuiltInRegistries.CREATIVE_MODE_TAB
 * .getKey(selectedTab))} (client-tier); the created-slot {@code DeclToken} from
 * its {@code (groupId, localIndex)} registration coordinate; the panel-element
 * {@code DeclToken} from {@code PanelElement.getElementDeclId()} or its list
 * position; the composite backing via {@code OwnerScope.sub(...)} from §0050.
 *
 * <p><b>Tier invariant (enforcement seat = the engine, Phase 3):</b> an
 * {@code OwnerScope.Tab} scope is CLIENT-tier only (the active tab lives on the
 * screen, never the menu). The pure value cannot enforce this (it has no notion
 * of tier); the engine's tier check must reject a {@code Tab}-scoped address on
 * SERVER-tier resolution. Flagged here so the Phase-2 mint sites and the Phase-3
 * engine honor it.
 */
public record Address(OwnerRef owner, Token token, KindTag kind) {

    public Address {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(kind, "kind");
    }

    // ── Convenience factories for the four shapes ──────────────────────

    /** A vanilla slot at a menu-space index. */
    public static Address vanillaSlot(ScreenFamilyKey family, OwnerScope scope, int index) {
        return new Address(OwnerRef.root(family, scope), Token.index(index), KindTag.VANILLA_SLOT);
    }

    /** A created slot, owned by its panel, identified by a declaration id. */
    public static Address createdSlot(ScreenFamilyKey family, OwnerScope scope,
                                      Identifier panelRegKey, String declId) {
        // Differs from panelElement(...) ONLY by the KindTag — keep them in sync.
        return new Address(panelOwner(family, scope, panelRegKey), Token.decl(declId), KindTag.CREATED_SLOT);
    }

    /** A non-slot panel element, owned by its panel, identified by a declaration id. */
    public static Address panelElement(ScreenFamilyKey family, OwnerScope scope,
                                       Identifier panelRegKey, String declId) {
        // Differs from createdSlot(...) ONLY by the KindTag — keep them in sync.
        return new Address(panelOwner(family, scope, panelRegKey), Token.decl(declId), KindTag.PANEL_ELEMENT);
    }

    /** A panel itself, for its own properties. */
    public static Address panel(ScreenFamilyKey family, OwnerScope scope, Identifier panelRegKey) {
        return new Address(OwnerRef.root(family, scope), Token.reg(panelRegKey), KindTag.PANEL);
    }

    /** The owner-ref a panel's children share: nested under the panel within its family+scope. */
    private static OwnerRef panelOwner(ScreenFamilyKey family, OwnerScope scope, Identifier panelRegKey) {
        return OwnerRef.nested(OwnerRef.root(family, scope), Token.reg(panelRegKey));
    }
}
