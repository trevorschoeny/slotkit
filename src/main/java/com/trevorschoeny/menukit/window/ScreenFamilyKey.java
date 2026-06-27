package com.trevorschoeny.menukit.window;

import net.minecraft.resources.Identifier;

/**
 * Stable identity of a menu/screen <em>family</em> — the top of an
 * {@link Address}'s owner chain (a {@code RootOwner} pairs one of these with an
 * {@link OwnerScope}).
 *
 * <h2>Why a Identifier, not a screen Class</h2>
 *
 * Address identity must be deterministic, reopen-stable, and serializable across
 * the client/server boundary (the server has no {@code Screen}, only a menu). A
 * {@code Class} is none of those. The natural stable id is the menu family's
 * {@code Identifier} — derived from the {@code MenuType} id where the menu
 * has one, or a synthetic id for the typeless cases (the player inventory menu,
 * the creative menu) that are built directly rather than opened via a
 * {@code MenuType}. The live derivation is a mint-site concern (Phase 2): on the
 * server from the {@code MenuType}, on the client from the screen's menu.
 *
 * <h2>Relationship to ScreenMatcher</h2>
 *
 * {@code ScreenMatcher} (class-ancestry based) decides WHICH screens a panel
 * shows on; {@code ScreenFamilyKey} identifies a menu family for ADDRESSING.
 * They interoperate (a family maps to the screen[s] that host it) but are not the
 * same axis — addressing must be menu-derived and stable, screen-matching is a
 * client presentation filter.
 *
 * <p>Pure value (record equality over the id).
 */
public record ScreenFamilyKey(Identifier id) {

    public ScreenFamilyKey {
        java.util.Objects.requireNonNull(id, "id");
    }

    /** Names a menu family by its stable id (menu-type id, or a synthetic id). */
    public static ScreenFamilyKey of(Identifier id) {
        return new ScreenFamilyKey(id);
    }
}
