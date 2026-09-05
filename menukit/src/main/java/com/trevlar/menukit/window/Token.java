package com.trevlar.menukit.window;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The within-owner identifier of an {@link Address} — paired with an
 * {@link OwnerRef} and a {@link KindTag} to name exactly one addressable thing.
 *
 * <h2>One token shape per identity source</h2>
 * <ul>
 *   <li>{@link IndexToken} — a slot's vanilla menu-space index. Used by BOTH
 *       vanilla and created slots (they share one menu space, which is why they
 *       "look identical through the window"). Reopen-stable because Mojang's
 *       menu index is deterministic.</li>
 *   <li>{@link DeclToken} — a deterministic <em>declaration</em> id, used as the
 *       durable identity of a created slot or a panel element. For a created slot
 *       it derives from its registration-order coordinate (group + localIndex);
 *       for a panel element it is the consumer-supplied {@code declId} or, absent
 *       that, its registration position in the panel's element list (see
 *       {@code PanelElement.getElementDeclId()}). String form keeps it
 *       deterministic + serializable; interning to a long is a later perf-only
 *       option.</li>
 *   <li>{@link RegToken} — a panel's registration key, identifying the panel
 *       itself (for its own properties).</li>
 * </ul>
 *
 * <h2>FROZEN-OPEN</h2>
 * New token shapes are additive; resolution mechanics switch on the token
 * subtype ONLY to fetch the live backing (a lookup, never a behavior/cascade
 * decision). The invariant: never cross {@code KindTag} (applicability) with the
 * token subtype (resolution).
 */
public sealed interface Token permits Token.IndexToken, Token.DeclToken, Token.RegToken {

    /** A slot's vanilla menu-space index (vanilla and created slots alike). */
    record IndexToken(int index) implements Token {}

    /** A deterministic declaration id (created slot / panel element). */
    record DeclToken(String declId) implements Token {
        // Fail loud: a null/blank decl id is a mint-site bug (e.g. a forgotten
        // position fallback for a null getElementDeclId()) that would otherwise
        // silently collide. Everything keys on this — refuse to mint it.
        public DeclToken {
            Objects.requireNonNull(declId, "declId");
            if (declId.isBlank()) throw new IllegalArgumentException("declId must not be blank");
        }
    }

    /** A panel's registration key (the panel itself). */
    record RegToken(Identifier regKey) implements Token {
        public RegToken {
            Objects.requireNonNull(regKey, "regKey");
        }
    }

    static Token index(int index) { return new IndexToken(index); }
    static Token decl(String declId) { return new DeclToken(declId); }
    static Token reg(Identifier regKey) { return new RegToken(regKey); }
}
