package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.Address;
import net.minecraft.resources.Identifier;

/**
 * Stable identity of a menu or screen family: the top of an {@link Address}'s owner
 * chain (a {@code RootOwner} pairs one of these with an {@link OwnerScope}).
 *
 * <h2>Why an Identifier, not a screen class</h2>
 *
 * An address must be deterministic, the same after a reopen, and serializable
 * across the client/server boundary (the server has no {@code Screen}, only a
 * menu). A {@code Class} is none of those. So the id is the menu family's
 * {@code Identifier}: the {@code MenuType} id where the menu has one, or a synthetic
 * id for menus built directly without a {@code MenuType} (the player inventory, the
 * creative menu). The id is derived where the address is minted: on the server
 * from the {@code MenuType}, on the client from the screen's menu.
 *
 * <h2>Relationship to ScreenMatcher</h2>
 *
 * {@code ScreenMatcher} (by class ancestry) decides which screens a panel shows on;
 * {@code ScreenFamilyKey} identifies a menu family for addressing. A family maps to
 * the screens that host it, but they answer different questions. Addressing must
 * be menu-derived and stable, while screen matching is a client presentation
 * filter.
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
