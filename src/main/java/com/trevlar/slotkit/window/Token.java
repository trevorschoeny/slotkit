package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.KindTag;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The identifier of an {@link Address} within its owner. With an {@link OwnerRef}
 * and a {@link KindTag} it names exactly one addressable thing.
 *
 * <h2>One token shape per identity source</h2>
 * <ul>
 *   <li>{@link IndexToken}: a slot's vanilla menu index. Vanilla and created slots
 *       both use it, since they share one menu index space, which is why they look
 *       the same through the window. Stable across reopens because vanilla's menu
 *       index is deterministic.</li>
 *   <li>{@link DeclToken}: a deterministic declaration id, the lasting identity of
 *       a created slot or a panel element. For a created slot it comes from its
 *       registration coordinate (group and local index); for a panel element it is
 *       the consumer's {@code declId} or, without one, the element's position in
 *       the panel's element list (see {@code PanelElement.declId()}). A string keeps
 *       it deterministic and serializable.</li>
 *   <li>{@link RegToken}: a panel's registration key, naming the panel itself (for
 *       its own properties).</li>
 * </ul>
 *
 * <h2>Open to new shapes</h2>
 * New token shapes can be added. Resolution switches on the token type only to
 * fetch the live backing, never to make a behavior or cascade decision. Keep
 * {@code KindTag} (applicability) and the token type (resolution) apart.
 */
public sealed interface Token permits Token.IndexToken, Token.DeclToken, Token.RegToken {

    /** A slot's vanilla menu-space index (vanilla and created slots alike). */
    record IndexToken(int index) implements Token {}

    /** A deterministic declaration id (created slot or panel element). */
    record DeclToken(String declId) implements Token {
        // A null or blank decl id is a bug where the address is minted (a missing
        // position fallback for a null declId(), say) and would silently collide
        // with others, so it throws.
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
