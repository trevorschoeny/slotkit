package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.containers.api.slot.ContainerPanel;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.containers.api.slot.CreatedSlots;
import com.trevlar.menukit.containers.api.slot.SlotSpec;
import com.trevlar.menukit.api.slot.Storage;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The side-neutral registry of container-parity slot recipes. Both menu-build hooks
 * read it, so a registered slot appears on every menu the player opens, the same way
 * on both sides.
 *
 * <h3>Three kinds of menu, one registry</h3>
 *
 * A registered {@link SlotSpec} has to become a real {@link CreatedSlot} on three
 * kinds of menu, and each path applies this registry:
 * <ul>
 *   <li>The player's own {@code InventoryMenu}: appended by the library-owned
 *       {@code MKCInventoryMenuMixin} at the menu constructor's TAIL, on both sides.
 *       The inventory menu is built in the player constructor, never through
 *       {@code openMenu}, so the projection hook can't reach it. Rather than have
 *       every consumer write the same {@code InventoryMenu.<init>} mixin, the library
 *       owns it once and drives it from this registry. (Foreign menus have no
 *       per-menu hook a consumer could own, so {@link CreatedSlotProjection} already
 *       covered them.)</li>
 *   <li>Every foreign menu (chest, furnace, donkey, modded): appended by a single
 *       {@link CreatedSlotProjection} source, registered once by
 *       {@link ContainerPanel}, whose factory calls {@link #applyTo}. Its
 *       {@code appliesTo} excludes {@code InventoryMenu} so the inventory menu
 *       isn't served twice.</li>
 *   <li>The creative item picker gets the slots with no extra work:
 *       {@code MKCCreativeSlotItemPickerMixin} wraps whatever {@link CreatedSlot}s
 *       the {@code InventoryMenu} mixin already put on {@code player.inventoryMenu}.</li>
 * </ul>
 *
 * <h3>Sync safety</h3>
 *
 * Both hooks build through {@link CreatedSlots} with the same recipes in the same
 * order (sorted by slot panel id, so the order matches on both sides whatever order
 * mods initialised in), and the {@code storage} factory gives the same size per
 * player on both sides. So the appended slot blocks are byte-identical, as
 * {@link CreatedSlotProjection} requires. {@link #applyTo} runs at most once per menu
 * instance: the inventory mixin fires once per construction, and
 * {@code CreatedSlotProjection} guards the projection source per menu.
 */
@ApiStatus.Internal
public final class ParitySlotRegistry {

    private ParitySlotRegistry() {}

    private record Entry(String slotPanelId, SlotSpec spec) {}

    // Strong references, since consumers register once at init. CopyOnWriteArrayList
    // so the build hooks, which iterate during menu construction, never see a partial
    // registration and the iteration order is stable on both sides.
    private static final List<Entry> RECIPES = new CopyOnWriteArrayList<>();

    /**
     * Registers one parity slot recipe. The {@code slotPanelId} is the id the built
     * {@link CreatedSlot}s carry and the {@code SlotElement}s resolve against.
     * {@link ContainerPanel} derives it from the container-panel id and group, so it
     * is stable and never collides.
     */
    public static synchronized void register(String slotPanelId, SlotSpec spec) {
        for (Entry e : RECIPES) {
            if (e.slotPanelId().equals(slotPanelId)) {
                throw new IllegalStateException("A parity slot group " + slotPanelId + " is already registered");
            }
        }
        // Sorted by slot panel id, never registration order: the slots are appended
        // to the menu in this order on both sides, and a client and a server with
        // different mod sets can run their initializers in different orders, which
        // would give the same slot two indexes.
        List<Entry> sorted = new java.util.ArrayList<>(RECIPES);
        sorted.add(new Entry(slotPanelId, spec));
        sorted.sort(java.util.Comparator.comparing(Entry::slotPanelId));
        RECIPES.clear();
        RECIPES.addAll(sorted);
    }

    /** Whether no parity recipe is registered, so the build hooks can skip work. */
    public static boolean isEmpty() {
        return RECIPES.isEmpty();
    }

    /**
     * Builds every registered recipe's slots onto {@code menu} for {@code player},
     * through {@link CreatedSlots}, so rendering, clicks into the storage, creative and
     * sync use the same vanilla-slot machinery as the inventory slot. Called by the
     * inventory-menu mixin (every {@code InventoryMenu}) and by the projection source
     * (every foreign menu). Each caller applies a given menu instance at most once.
     */
    public static void applyTo(AbstractContainerMenu menu, Player player) {
        if (menu == null || player == null || RECIPES.isEmpty()) return;
        for (Entry e : RECIPES) {
            SlotSpec spec = e.spec();
            var factory = spec.storageFactory();
            if (factory == null) continue;   // mis-declared recipe, skip it
            Storage storage = factory.apply(player);
            if (storage == null) continue;

            CreatedSlots.Builder b = CreatedSlots.onto(menu, player)
                    .panel(e.slotPanelId())
                    .group(spec.groupId())
                    .category(spec.category())
                    .storage(storage)
                    // Initial layout from the spec's panel-local origin. The SlotElement
                    // moves the slot to its panel spot every frame, so this starting
                    // position is never shown.
                    .layout(spec.childX(), spec.childY(), spec.columns());
            // Creation carries no behaviour: gating, quick-move, binding and mending
            // are set later through the window by the slot's address.
            if (spec.label() != null) b.label(spec.label());     // MenuKit naming (client-guarded in register)
            if (spec.revealWhen() != null) b.revealWhen(spec.revealWhen());
            b.register();
        }
    }
}
