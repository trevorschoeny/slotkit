package com.trevlar.slotkit.api.window;

import com.trevlar.slotkit.window.Token;
import com.trevlar.slotkit.window.KeyStrings;
import com.trevlar.slotkit.window.OwnerRef;
import com.trevlar.slotkit.window.OwnerScope;
import com.trevlar.slotkit.window.PanelAddressing;
import com.trevlar.slotkit.window.ScreenFamilyKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.trevlar.slotkit.api.group.SlotGroupId;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

import java.util.Objects;

/**
 * One value that names any addressable thing (a vanilla slot, a created slot, a
 * panel element or a panel itself), so the engine can resolve every behavior by
 * address without asking what kind it is.
 *
 * <h2>The four shapes</h2>
 * <pre>
 *   vanilla slot   : RootOwner(family, scope)              + IndexToken(i)    + VANILLA_SLOT
 *   created slot   : NestedOwner(RootOwner(family,scope), RegToken(panel)) + DeclToken(id) + CREATED_SLOT
 *   panel element  : NestedOwner(RootOwner(family,scope), RegToken(panel)) + DeclToken(id) + PANEL_ELEMENT
 *   panel itself   : RootOwner(family, scope)              + RegToken(panel) + PANEL
 * </pre>
 * The old slot-only {@code SlotAddress(family, index)} is exactly the first line.
 *
 * <h2>Identity contract</h2>
 *
 * Equality is over {@code (owner, token, kind)}, from the record. {@code KindTag}
 * takes part so a panel and a slot at the same coordinate never collide, and a
 * created slot and a panel element with the same declaration id stay distinct.
 * The address holds no live object, so two addresses naming the same thing are
 * equal and interchangeable: safe to keep in a static map, compare, and pass
 * across the client/server boundary as a key. The same thing rebuilds to an equal
 * address after a reopen, because its sources are deterministic (vanilla's menu
 * index, the consumer's panel and declaration ids, the registration order).
 *
 * <h2>Minting one</h2>
 *
 * <ul>
 *   <li>a created slot: {@link #createdSlot(SlotGroupId.Created, int)}, from the
 *       group's id ({@code ContainerPanel.groupId}, {@code CreatedSlots.groupId},
 *       {@code SlotGroupId.created}) and the slot's index in the group;</li>
 *   <li>a vanilla slot: from the live slot, where an operation or veto has it, or
 *       by its place in a menu type's slot list, {@link #menuSlot(MenuType, int)}.</li>
 * </ul>
 * Panel and panel-element addresses are no longer minted (MenuKit names its panels
 * by {@code Panel.id()}), but {@link #parse} still reads the text 6.x saved for them.
 * The value-level factories below the public ones are the library's plumbing and
 * are {@code @ApiStatus.Internal}.
 *
 * <h2>Saving one</h2>
 *
 * {@link #asString()} is a stable text form to save a choice about one thing
 * under, {@link #parse} reads it back, and {@link #CODEC} is the same form for
 * codec-based config. Never save an address's internals: their shape may change,
 * the text form's meaning does not.
 *
 * <h2>Sources</h2>
 * Where the value-level factories get their parts: the creative tab from
 * {@code OwnerScope.tab(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab))}
 * (client tier); a created slot's {@code DeclToken} from its
 * {@code (groupId, localIndex)} registration coordinate; a composite container's
 * backing from {@code OwnerScope.sub(...)}.
 *
 * <p>An {@code OwnerScope.Tab} scope is client tier only, since the active tab
 * lives on the screen, never the menu. The value itself has no notion of tier and
 * cannot enforce this, so the engine's tier check has to reject a
 * {@code Tab}-scoped address in server-tier resolution, and mint sites must
 * respect it.
 */
public record Address(OwnerRef owner, Token token, KindTag kind) {

    public Address {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(kind, "kind");
    }

    // Public minters, one per kind.

    /** Slot {@code index} of a created slot group. */
    public static Address createdSlot(SlotGroupId.Created group, int index) {
        return createdSlot(PanelAddressing.PANEL_FAMILY, OwnerScope.primary(),
                PanelAddressing.regKey(group.panelId()), group.groupId() + CREATED_SEP + index);
    }

    /**
     * Slot {@code index} of {@code menu.slots} on every menu of {@code type}: the
     * menu-based form of a vanilla slot's address. A panel registered once for a
     * menu type holds this, since a slot's container-based address (what
     * {@code SlotRef.address()} gives for a chest or a shulker box) names one
     * particular box. Not equal to that container-based address, so behaviors keyed
     * on a slot's address don't see this one; it is for placing slots
     * ({@code SlotElement.builder().slot(Address)}).
     */
    public static Address menuSlot(MenuType<?> type, int index) {
        Objects.requireNonNull(type, "type");
        if (index < 0) throw new IllegalArgumentException("index must not be negative, got " + index);
        return vanillaSlot(ScreenFamilyKey.of(BuiltInRegistries.MENU.getKey(type)), OwnerScope.primary(), index);
    }

    // A created slot's declaration id is groupId<NUL>index: a NUL never appears in
    // a mod's id, so no group id can end in something that looks like an index.
    private static final String CREATED_SEP = String.valueOf((char) 0);

    // The value-level factories, for the library's own use.

    /** A vanilla slot at a menu-space index. */
    @ApiStatus.Internal
    public static Address vanillaSlot(ScreenFamilyKey family, OwnerScope scope, int index) {
        return new Address(OwnerRef.root(family, scope), Token.index(index), KindTag.VANILLA_SLOT);
    }

    /** A created slot, owned by its panel, identified by a declaration id. */
    @ApiStatus.Internal
    public static Address createdSlot(ScreenFamilyKey family, OwnerScope scope,
                                      Identifier panelRegKey, String declId) {
        return new Address(panelOwner(family, scope, panelRegKey), Token.decl(declId), KindTag.CREATED_SLOT);
    }

    /** The owner a panel's children share: nested under the panel, within its family and scope. */
    private static OwnerRef panelOwner(ScreenFamilyKey family, OwnerScope scope, Identifier panelRegKey) {
        return OwnerRef.nested(OwnerRef.root(family, scope), Token.reg(panelRegKey));
    }

    // Text form.

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
