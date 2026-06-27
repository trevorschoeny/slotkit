package com.trevorschoeny.menukit.window;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Locale;

/**
 * Derives the stable {@link ScreenFamilyKey} and {@link OwnerScope} of a LIVE
 * menu — the client mint of an {@link Address}'s root owner. This is the seat of
 * the held-handle no-op: the resolver compares an address's root against what
 * {@code WindowMint} derives from the currently-open menu, and a mismatch (wrong
 * or no screen) resolves to nothing.
 *
 * <h2>Family</h2>
 *
 * The stable family id is the menu's {@code MenuType} id (via
 * {@code BuiltInRegistries.MENU}) where it has one. Two cases have no menu type
 * and get a synthetic id: the player inventory ({@link InventoryMenu}, built in
 * the Player constructor) and the creative menu. The synthetic fallback for any
 * other typeless menu is derived deterministically from its class name.
 */
public final class WindowMint {

    private WindowMint() {}

    private static final Identifier PLAYER_INVENTORY =
            Identifier.fromNamespaceAndPath("menukit", "player_inventory");

    /** The stable family of a live menu. */
    public static ScreenFamilyKey familyOf(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            return ScreenFamilyKey.of(PLAYER_INVENTORY);
        }
        try {
            MenuType<?> type = menu.getType();
            Identifier id = BuiltInRegistries.MENU.getKey(type);
            if (id != null) return ScreenFamilyKey.of(id);
        } catch (UnsupportedOperationException typeless) {
            // Menus built directly (e.g. the creative menu) throw rather than
            // expose a MenuType — fall through to the synthetic id.
        }
        return ScreenFamilyKey.of(synthetic(menu));
    }

    /**
     * The scope of a live menu.
     *
     * <p><b>Phase 2a:</b> {@code PRIMARY} (the common case). Phase 2b wires the
     * two namespacing sources: the active creative tab → {@code OwnerScope.tab(
     * BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab))} (client-tier; via
     * a creative-screen accessor), and a composite/double-chest backing →
     * {@code OwnerScope.sub(backingId)} from §0050. Until then a creative-tab or
     * composite address simply won't match (resolves empty) — safe, never wrong.
     */
    public static OwnerScope scopeOf(AbstractContainerMenu menu) {
        return OwnerScope.primary();
    }

    /** Deterministic synthetic family id for a typeless menu, from its class name. */
    private static Identifier synthetic(AbstractContainerMenu menu) {
        String path = "menu/" + menu.getClass().getName()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return Identifier.fromNamespaceAndPath("menukit", path);
    }
}
