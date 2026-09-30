package com.trevlar.menukit.containers.api.menu;

import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.containers.api.menu.MenuPanel;
import com.trevlar.menukit.containers.api.slot.SlotGroup;
import com.trevlar.menukit.containers.api.storage.StorageContainerAdapter;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A custom container menu: its created slots, its panels as the server knows them,
 * and whether each is shown (§0067). Built by {@link SlotLayout}
 * ({@link #builder(MenuType)}); shift-click routes through {@link QuickMoveRouter}.
 *
 * <p>A proper subclass of {@link AbstractContainerMenu}: vanilla's sync machinery
 * walks {@code this.slots}, and anything that breaks {@code instanceof Slot} breaks
 * the world. It runs on both sides with the same declaration, so both build the same
 * slots in the same order.
 *
 * <h3>What it does not hold</h3>
 * How a panel looks (its elements, style, placement, toggle key) is MenuKit's client
 * {@code Panel}, declared on the client with {@code ClientMenu.of(menu).panel(id, ...)}
 * and built by the screen. This class names no client type, so it builds on a
 * dedicated server.
 *
 * <h3>Panel visibility is the server's</h3>
 * Whether a panel is shown is server state, synced to the client by one vanilla
 * {@link DataSlot} per panel. {@link #setPanelVisible} is called on the server; the
 * client follows. A player may ask to show or hide only a panel declared
 * {@link SlotLayout.PanelBuilder#toggleable()} (vanilla's {@code clickMenuButton});
 * any other request is refused. A hidden panel's slots are inert: they read empty and
 * refuse placement and pickup, on both sides.
 *
 * <h3>Right clicks</h3>
 * A right click on a shown slot whose group has a {@link SlotLayout.PanelBuilder#rightClick}
 * handler runs the handler instead of vanilla's click. It is a click like any other: the
 * client runs it as its prediction and sends it, and the server runs it too, which is the
 * run that counts (§0067). Vanilla's take and put operations do not apply to it.
 *
 * <h3>Validity</h3>
 * {@link #stillValid} is real: the menu stays open while its validity holds, checked
 * by vanilla on the server every tick. A {@link CustomMenu} sets it from
 * {@link CustomMenu.Builder#validWhen}; without one the menu stays valid while the player
 * is alive.
 */
public class CustomContainerMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    /**
     * Sentinel returned by {@link #getPanelButtonId(String)} when no panel matches.
     * A raw {@code -1} must never be sent as a button id.
     */
    public static final int PANEL_NOT_FOUND = -1;

    // The panels in declaration order, their shown flags (synced by DataSlot), and
    // whether a player may toggle each. Indexes line up; a panel's index is its
    // clickMenuButton id.
    private final List<MenuPanel> panels;
    private final boolean[] shown;
    private final boolean[] toggleable;
    private final Map<String, Integer> indexById = new LinkedHashMap<>();

    // Panel id -> its slot groups, in declaration order. Frozen at construction.
    private final Map<String, List<SlotGroup>> groupsByPanel = new LinkedHashMap<>();

    // One per group, kept so the slots' containers are not collected.
    private final List<StorageContainerAdapter> adapters = new ArrayList<>();

    private final QuickMoveRouter router = new QuickMoveRouter(this);

    private Predicate<Player> validity = Player::isAlive;

    /**
     * Allocates the created slots in declaration order (panel, then group, then index)
     * and one visibility {@link DataSlot} per panel. {@link SlotLayout#build} calls it;
     * a subclass calls it with the layout's panels.
     */
    protected CustomContainerMenu(MenuType<?> type, int syncId, List<SlotLayout.PanelSlots> declared) {
        super(type, syncId);
        int n = declared.size();
        this.shown = new boolean[n];
        this.toggleable = new boolean[n];
        List<MenuPanel> built = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            SlotLayout.PanelSlots decl = declared.get(i);
            if (indexById.putIfAbsent(decl.id(), i) != null) {
                throw new IllegalStateException("CustomContainerMenu: two panels named '" + decl.id() + "'");
            }
            final int index = i;
            shown[i] = decl.startsShown();
            toggleable[i] = decl.toggleable();
            built.add(new MenuPanel(decl.id(), () -> shown[index]));
            groupsByPanel.put(decl.id(), List.copyOf(decl.groups()));
            // Server -> client: the server's shown flag, sent when it changes.
            addDataSlot(new DataSlot() {
                @Override public int get() { return shown[index] ? 1 : 0; }
                @Override public void set(int value) { shown[index] = value != 0; }
            });
        }
        this.panels = List.copyOf(built);

        int flatIndex = 0;
        for (MenuPanel panel : panels) {
            for (SlotGroup group : groupsByPanel.get(panel.id())) {
                StorageContainerAdapter adapter = new StorageContainerAdapter(group.getStorage());
                adapters.add(adapter);
                int groupStart = flatIndex;
                for (int local = 0; local < group.getStorage().size(); local++) {
                    // A placeholder grid; the client screen positions slots every frame.
                    int x = 8 + (flatIndex % 9) * 18;
                    int y = 18 + (flatIndex / 9) * 18;
                    addSlot(new CreatedSlot(adapter, local, x, y, group, panel, group.id(), local));
                    flatIndex++;
                }
                group.setFlatIndexRange(groupStart, flatIndex);
            }
        }
        LOGGER.debug("[CustomContainerMenu] Constructed: {} panels, {} slots", panels.size(), slots.size());
    }

    /** Starts a slot layout for a menu of {@code menuType}: the declaration this handler is built from. */
    public static SlotLayout builder(MenuType<?> menuType) {
        return new SlotLayout(menuType);
    }

    // ── Panels ──────────────────────────────────────────────────────────

    /** The panels in declaration order, as the menu knows them. */
    public List<MenuPanel> getPanels() { return panels; }

    /** The panel with {@code panelId}, or {@code null}. */
    public @Nullable MenuPanel getPanel(String panelId) {
        Integer i = indexById.get(panelId);
        return i == null ? null : panels.get(i);
    }

    /** Whether the panel is shown now (on the client: as the server last said). */
    public boolean isPanelVisible(String panelId) {
        Integer i = indexById.get(panelId);
        return i != null && shown[i];
    }

    /** Whether a player may show and hide the panel ({@link SlotLayout.PanelBuilder#toggleable()}). */
    public boolean isPanelToggleable(String panelId) {
        Integer i = indexById.get(panelId);
        return i != null && toggleable[i];
    }

    /**
     * Shows or hides a panel. <b>Server side</b>: the change syncs to the client with
     * the slots' new contents (a hidden panel's slots read empty). A call on the client
     * changes only the client's copy until the server next changes the panel.
     */
    public void setPanelVisible(String panelId, boolean visible) {
        Integer i = indexById.get(panelId);
        if (i == null || shown[i] == visible) return;
        shown[i] = visible;
        broadcastChanges();
    }

    /**
     * A player's request to toggle panel {@code buttonId} (vanilla's container button
     * packet). Honoured only for a panel declared {@code toggleable()}; any other
     * request is refused, so a client cannot show a panel the server hides (§0067).
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= 0 && buttonId < panels.size()) {
            if (!toggleable[buttonId]) {
                LOGGER.debug("[CustomContainerMenu] refused a toggle of panel '{}' from {}: not toggleable",
                        panels.get(buttonId).id(), player.getName().getString());
                return false;
            }
            shown[buttonId] = !shown[buttonId];
            return true; // vanilla broadcasts the change
        }
        return super.clickMenuButton(player, buttonId);
    }

    /** The button id that toggles {@code panelId}, or {@link #PANEL_NOT_FOUND}. */
    public int getPanelButtonId(String panelId) {
        Integer i = indexById.get(panelId);
        return i == null ? PANEL_NOT_FOUND : i;
    }

    // ── Groups ──────────────────────────────────────────────────────────

    /** The slot groups on {@code panelId}, in declaration order; empty if none or unknown. */
    public List<SlotGroup> getGroupsFor(String panelId) {
        return groupsByPanel.getOrDefault(panelId, List.of());
    }

    /** The group owning the slot at {@code flatIndex}, or {@code null}. */
    public @Nullable SlotGroup getGroupContaining(int flatIndex) {
        if (flatIndex < 0 || flatIndex >= slots.size()) return null;
        return slots.get(flatIndex) instanceof CreatedSlot mk ? mk.getGroup() : null;
    }

    // ── Clicks, shift-click and validity ────────────────────────────────

    /** A right click on a slot with a handler runs the handler, on each side; any other click is vanilla's. */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        BiConsumer<Player, CreatedSlot> handler = rightClickHandler(slotId, button, input);
        if (handler != null) {
            handler.accept(player, (CreatedSlot) slots.get(slotId));
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    /**
     * The handler this click runs instead of vanilla's, or {@code null}: a plain right
     * click on a shown created slot whose group declared one.
     */
    @ApiStatus.Internal
    public @Nullable BiConsumer<Player, CreatedSlot> rightClickHandler(int slotId, int button, ContainerInput input) {
        if (input != ContainerInput.PICKUP || button != 1 || slotId < 0 || slotId >= slots.size()) return null;
        return slots.get(slotId) instanceof CreatedSlot slot && slot.isActive()
                ? slot.getGroup().getRightClickHandler() : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return router.route(player, index);
    }

    /** {@code moveItemStackTo} for the router, forward, over {@code [start, end)}. */
    boolean moveInto(ItemStack stack, int start, int end) {
        return moveItemStackTo(stack, start, end, false);
    }

    @Override
    public boolean stillValid(Player player) {
        return validity.test(player);
    }

    /** Set by {@link CustomMenu} from its {@code validWhen}, as the handler is built. */
    void validWhen(Predicate<Player> validity) {
        this.validity = Objects.requireNonNull(validity, "validity");
    }
}
