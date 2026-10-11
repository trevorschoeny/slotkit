package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Mints the {@link Address} of a panel and of a panel element. The counterparts
 * are {@link VanillaAddressing} for vanilla slots and MenuKit: Containers'
 * created-slot adapter for created slots.
 *
 * <h2>One family for the whole panel subtree</h2>
 *
 * A panel is identified by its globally unique id, not by the screens it appears
 * on, so it has one stable address everywhere it shows and a setting on it holds
 * on all of them. Its address roots at the constant {@link #PANEL_FAMILY} with its
 * id as the {@code RegToken}, never at a per-screen family, which would split one
 * panel's config. A panel element and a created slot both nest under their panel
 * through this family and the same {@link #regKey} encoding, so the owner chain
 * links each child to its panel and a panel-level default cascades down.
 */
public final class PanelAddressing {

    private PanelAddressing() {}

    /** The constant family rooting every panel, panel element, and created slot. */
    public static final ScreenFamilyKey PANEL_FAMILY =
            ScreenFamilyKey.of(Identifier.fromNamespaceAndPath("menukit", "panel"));

    /**
     * A panel's id as a deterministic {@link Identifier}. Panel and element minting
     * (here) and created-slot minting (MenuKit: Containers' adapter) share this one
     * encoding, so a created slot and a {@code PanelHandle} for its panel produce
     * the same {@code RegToken}. The owner-chain cascade depends on it.
     */
    public static Identifier regKey(String panelId) {
        Identifier parsed = Identifier.tryParse(panelId);
        if (parsed != null) return parsed;
        return Identifier.fromNamespaceAndPath("menukit",
                "panel/" + panelId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
    }

    /** The {@link Address} of a panel by id. */
    public static Address ofPanel(String panelId) {
        return Address.panel(PANEL_FAMILY, OwnerScope.primary(), regKey(panelId));
    }

    /** The {@link Address} of a panel element by ids. */
    public static Address ofElement(String panelId, String elementDeclId) {
        return Address.panelElement(PANEL_FAMILY, OwnerScope.primary(), regKey(panelId), elementDeclId);
    }

}
