package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.Address;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Locale;

/**
 * Derives the stable {@link ScreenFamilyKey} and {@link OwnerScope} of a live
 * menu, which the client uses as an {@link Address}'s root owner. This is what
 * makes a handle held past its screen a no-op: the resolver compares an address's
 * root with what {@code WindowMint} derives from the open menu, and a mismatch
 * (another screen, or none) resolves to nothing.
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

    /** The player inventory menu's family. */
    public static ScreenFamilyKey inventoryFamily() {
        return ScreenFamilyKey.of(PLAYER_INVENTORY);
    }

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
            // Menus built directly (the creative menu, for one) throw here
            // instead of returning a MenuType, so use the synthetic id.
        }
        return ScreenFamilyKey.of(synthetic(menu));
    }

    /**
     * The scope of a live menu. Always {@code PRIMARY} for now. The two namespacing
     * sources are not wired yet: the active creative tab ({@code OwnerScope.tab(
     * BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab))}, client tier, through
     * a creative-screen accessor) and a double chest's backing
     * ({@code OwnerScope.sub(backingId)}). Until they are, a creative-tab or
     * composite address does not match and resolves empty, which is safe.
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
