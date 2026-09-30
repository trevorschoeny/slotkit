package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.Token;
import com.trevlar.menukit.window.KeyStrings;
import com.trevlar.menukit.window.OwnerRef;
import com.trevlar.menukit.window.OwnerScope;
import com.trevlar.menukit.window.PanelAddressing;
import com.trevlar.menukit.window.ScreenFamilyKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.trevlar.menukit.api.slot.SlotGroupId;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

import java.util.Objects;

/**
 * THE keystone of THE ONE WINDOW: one kind-agnostic value that names ANY
 * addressable thing, a vanilla slot, a created slot, a panel element, or a panel
 * itself, so the engine can resolve every behavior by address without ever
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
 * this is a strict superset, not a replacement.
 *
 * <h2>Identity contract</h2>
 *
 * Equality is component-wise over {@code (owner, token, kind)}, automatic from
 * the record. {@code KindTag} participates in equality so a panel and a slot at
 * the same coordinate can never collide, and a created slot vs a panel element at
 * the same declaration id are distinct. The address holds NO live object, so two
 * addresses naming the same thing are {@code .equals()} and interchangeable.
 * safe to store in a static map, compare, and pass across the client/server
 * boundary as a key. Reopen-stability follows: the same logical thing rebuilds to
 * an equal address as long as its sources are deterministic (Mojang's menu index;
 * the consumer's panel/decl ids; the registration order), which the keystone
 * checks confirmed they are.
 *
 * <h2>Minting one</h2>
 *
 * One public minter per kind (§0063):
 * <ul>
 *   <li>a created slot: {@link #createdSlot(SlotGroupId.Created, int)}, from the
 *       group's id ({@code ContainerPanel.groupId}, {@code CreatedSlots.groupId},
 *       {@code SlotGroupId.created}) and the slot's index in the group;</li>
 *   <li>a panel: {@link #panel(String)}; a panel element:
 *       {@link #panelElement(String, String)};</li>
 *   <li>a vanilla slot: from the live slot, where an operation or veto has it.</li>
 * </ul>
 * The four value-level factories below the public ones are the library's
 * plumbing and are {@code @ApiStatus.Internal}.
 *
 * <h2>Saving one</h2>
 *
 * {@link #asString()} is a stable text form to save a choice about one thing
 * under, {@link #parse} reads it back, and {@link #CODEC} is the same form for
 * codec-based config. Never save an address's internals: their shape may change,
 * the text form's meaning does not.
 *
 * <h2>Sources (wired at mint time)</h2>
 * The value-level constructors here are fed by the confirmed live sources: the
 * creative tab via {@code OwnerScope.tab(BuiltInRegistries.CREATIVE_MODE_TAB
 * .getKey(selectedTab))} (client-tier); the created-slot {@code DeclToken} from
 * its {@code (groupId, localIndex)} registration coordinate; the panel-element
 * {@code DeclToken} from {@code PanelElement.declId()} or its list
 * position; the composite backing via {@code OwnerScope.sub(...)} from §0055.
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

    // ── Public minters, one per kind (§0063) ───────────────────────────

    /** Slot {@code index} of a created slot group. */
    public static Address createdSlot(SlotGroupId.Created group, int index) {
        return createdSlot(PanelAddressing.PANEL_FAMILY, OwnerScope.primary(),
                PanelAddressing.regKey(group.panelId()), group.groupId() + CREATED_SEP + index);
    }

    /** A panel, by the id it was built with. */
    public static Address panel(String panelId) {
        return PanelAddressing.ofPanel(panelId);
    }

    /** An element of a panel, by the panel's id and the element's declaration id. */
    public static Address panelElement(String panelId, String elementDeclId) {
        return PanelAddressing.ofElement(panelId, elementDeclId);
    }

    // A created slot's declaration id is groupId<NUL>index: a NUL never appears in
    // a mod's id, so no group id can end in something that looks like an index.
    private static final String CREATED_SEP = String.valueOf((char) 0);

    // ── The value-level factories for the four shapes (plumbing) ───────


    /** A vanilla slot at a menu-space index. */
    @ApiStatus.Internal
    public static Address vanillaSlot(ScreenFamilyKey family, OwnerScope scope, int index) {
        return new Address(OwnerRef.root(family, scope), Token.index(index), KindTag.VANILLA_SLOT);
    }

    /** A created slot, owned by its panel, identified by a declaration id. */
    @ApiStatus.Internal
    public static Address createdSlot(ScreenFamilyKey family, OwnerScope scope,
                                      Identifier panelRegKey, String declId) {
        // Differs from panelElement(...) ONLY by the KindTag, keep them in sync.
        return new Address(panelOwner(family, scope, panelRegKey), Token.decl(declId), KindTag.CREATED_SLOT);
    }

    /** A non-slot panel element, owned by its panel, identified by a declaration id. */
    @ApiStatus.Internal
    public static Address panelElement(ScreenFamilyKey family, OwnerScope scope,
                                       Identifier panelRegKey, String declId) {
        // Differs from createdSlot(...) ONLY by the KindTag, keep them in sync.
        return new Address(panelOwner(family, scope, panelRegKey), Token.decl(declId), KindTag.PANEL_ELEMENT);
    }

    /** A panel itself, for its own properties. */
    @ApiStatus.Internal
    public static Address panel(ScreenFamilyKey family, OwnerScope scope, Identifier panelRegKey) {
        return new Address(OwnerRef.root(family, scope), Token.reg(panelRegKey), KindTag.PANEL);
    }

    /** The owner-ref a panel's children share: nested under the panel within its family+scope. */
    private static OwnerRef panelOwner(ScreenFamilyKey family, OwnerScope scope, Identifier panelRegKey) {
        return OwnerRef.nested(OwnerRef.root(family, scope), Token.reg(panelRegKey));
    }

    // ── Text form ──────────────────────────────────────────────────────

    /**
     * The stable text form: the kind, the root family and scope, each nesting
     * token, and the address's own token, as escaped {@code |}-separated fields.
     * {@link #parse} reads it back to an equal address.
     */
    public String asString() {
        List<String> parts = new ArrayList<>();
        parts.add("address");
        parts.add(kind.name().toLowerCase(java.util.Locale.ROOT));
        List<Token> chain = new ArrayList<>();
        OwnerRef o = owner;
        while (o instanceof OwnerRef.NestedOwner n) {
            chain.add(0, n.parentToken());
            o = n.parent();
        }
        OwnerRef.RootOwner root = (OwnerRef.RootOwner) o;
        parts.add(root.family().id().toString());
        parts.add(scopeText(root.scope()));
        parts.add(Integer.toString(chain.size()));
        for (Token t : chain) parts.add(tokenText(t));
        parts.add(tokenText(token));
        return KeyStrings.join(parts.toArray(String[]::new));
    }

    /**
     * Reads {@link #asString()} back.
     *
     * @throws IllegalArgumentException for text {@code asString} did not write
     */
    public static Address parse(String text) {
        List<String> p = KeyStrings.split(text);
        try {
            if (p.size() < 6 || !p.get(0).equals("address")) throw new IllegalArgumentException("not an address");
            KindTag kind = KindTag.valueOf(p.get(1).toUpperCase(java.util.Locale.ROOT));
            OwnerRef owner = OwnerRef.root(ScreenFamilyKey.of(Identifier.parse(p.get(2))), scope(p.get(3)));
            int depth = Integer.parseInt(p.get(4));
            if (p.size() != 6 + depth) throw new IllegalArgumentException("wrong field count");
            for (int i = 0; i < depth; i++) owner = OwnerRef.nested(owner, token(p.get(5 + i)));
            return new Address(owner, token(p.get(5 + depth)), kind);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not an address: " + text + " (" + e.getMessage() + ")", e);
        }
    }

    /** The {@link #asString()} form as a codec. */
    public static final Codec<Address> CODEC = Codec.STRING.comapFlatMap(Address::read, Address::asString);

    private static DataResult<Address> read(String text) {
        try {
            return DataResult.success(parse(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    // A field inside a field: the kind of token or scope, a colon, the value. The
    // outer escaping already protects '|'; the first colon is the split point.

    private static String scopeText(OwnerScope scope) {
        return switch (scope) {
            case OwnerScope.Primary p -> "primary";
            case OwnerScope.Tab t -> "tab:" + t.tabId();
            case OwnerScope.Sub s -> "sub:" + s.backingId();
        };
    }

    private static OwnerScope scope(String text) {
        if (text.equals("primary")) return OwnerScope.primary();
        int c = text.indexOf(':');
        String kind = c < 0 ? text : text.substring(0, c), value = c < 0 ? "" : text.substring(c + 1);
        return switch (kind) {
            case "tab" -> OwnerScope.tab(Identifier.parse(value));
            case "sub" -> OwnerScope.sub(value);
            default -> throw new IllegalArgumentException("unknown scope " + text);
        };
    }

    private static String tokenText(Token token) {
        return switch (token) {
            case Token.IndexToken i -> "index:" + i.index();
            case Token.DeclToken d -> "decl:" + d.declId();
            case Token.RegToken r -> "reg:" + r.regKey();
        };
    }

    private static Token token(String text) {
        int c = text.indexOf(':');
        if (c < 0) throw new IllegalArgumentException("unknown token " + text);
        String kind = text.substring(0, c), value = text.substring(c + 1);
        return switch (kind) {
            case "index" -> Token.index(Integer.parseInt(value));
            case "decl" -> Token.decl(value);
            case "reg" -> Token.reg(Identifier.parse(value));
            default -> throw new IllegalArgumentException("unknown token " + text);
        };
    }
}
