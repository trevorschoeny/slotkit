package com.trevorschoeny.menukit.window;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Mints the {@link Address} of a panel and a panel element — the panel-subtree
 * half of THE ONE WINDOW's addressing, the counterpart to {@link VanillaAddressing}
 * (vanilla slots) and the MKC created-slot adapter (created slots).
 *
 * <h2>One family for the whole panel subtree</h2>
 *
 * A panel is ONE logical thing identified by its globally-unique id, not by which
 * screen(s) it appears on — so it has one stable address everywhere it shows, and
 * a setting on it is consistent across all of them. Its identity therefore roots at
 * a CONSTANT {@link #PANEL_FAMILY} namespace + its id as the {@code RegToken}, never
 * a per-screen family (which would fragment one panel's config). A panel element AND
 * a created slot both nest under their panel via this same family + the same
 * {@link #regKey} encoding, so the owner chain connects child → panel coherently and
 * a panel-level default cascades down (the engine's owner-ancestor walk). (Taxonomy
 * is Lead-owned per architecture §3.9 #5 — recorded for §0055.)
 */
public final class PanelAddressing {

    private PanelAddressing() {}

    /** The constant family rooting every panel, panel element, and created slot. */
    public static final ScreenFamilyKey PANEL_FAMILY =
            ScreenFamilyKey.of(Identifier.fromNamespaceAndPath("menukit", "panel"));

    /**
     * A panel's id as a deterministic {@link Identifier} — the SINGLE encoding
     * shared by panel/element minting (here) and created-slot minting (MKC's
     * adapter), so a created slot and a {@code PanelHandle} for its panel produce
     * the SAME {@code RegToken}. The owner-chain cascade depends on this identity.
     */
    public static Identifier regKey(String panelId) {
        Identifier parsed = Identifier.tryParse(panelId);
        if (parsed != null) return parsed;
        return Identifier.fromNamespaceAndPath("menukit",
                "panel/" + panelId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
    }

    /** The {@link Address} of {@code panel} itself (its own visibility/opacity/inertness). */
    public static Address of(Panel panel) {
        return ofPanel(panel.getId());
    }

    /** The {@link Address} of a panel by id. */
    public static Address ofPanel(String panelId) {
        return Address.panel(PANEL_FAMILY, OwnerScope.primary(), regKey(panelId));
    }

    /** The {@link Address} of {@code element} within {@code panel}. */
    public static Address of(Panel panel, PanelElement element) {
        return ofElement(panel.getId(), elementDeclId(panel, element));
    }

    /** The {@link Address} of a panel element by ids. */
    public static Address ofElement(String panelId, String elementDeclId) {
        return Address.panelElement(PANEL_FAMILY, OwnerScope.primary(), regKey(panelId), elementDeclId);
    }

    /**
     * An element's durable declaration id — its explicit {@code elementDeclId} when
     * given (Phase 0), else its registration-order position in the panel (stable
     * across reopen as long as the element list order is). Never a runtime counter.
     */
    private static String elementDeclId(Panel panel, PanelElement element) {
        String declId = element.getElementDeclId();
        if (declId != null) return declId;
        return "idx:" + panel.getRawElements().indexOf(element);
    }
}
