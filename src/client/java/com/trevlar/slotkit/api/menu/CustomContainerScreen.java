package com.trevlar.slotkit.api.menu;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.slotkit.api.slot.CreatedSlotGroup;
import com.trevlar.slotkit.core.*;
import com.trevlar.slotkit.core.*;
import com.trevlar.menukit.api.host.OwnHost;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.slotkit.mixin.SlotPositionAccessor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client-side partner to {@link CustomContainerMenu}: a standalone screen whose panels
 * hold slot groups. Owns slot positioning, hover events, key actions and drag modes;
 * placement, render and element input belong to its MenuKit {@link OwnHost}.
 *
 * <h3>Its panels</h3>
 * The handler knows each panel's id, slot groups and whether the server shows it; how
 * a panel looks is declared on the client with {@link ClientMenu#panel} on MenuKit's
 * {@code Panel.Builder}. This screen builds one {@link Panel} per handler panel
 * (a raised panel of its slots when nothing is declared) and shows it while the server
 * says so.
 *
 * <h3>Its host</h3>
 * The panels go into a standalone host with the title band inside the frame. The host
 * places them by their {@link PanelPosition}; an unplaced one takes the standalone
 * default (the first is the {@code main()} frame, later ones stack below it). This
 * screen tells the host what it cannot know on its own: a panel's content size includes its slot grid,
 * its slot frames draw between its background and its elements, the main panel
 * auto-fits its height only when it holds no slots, and a panel's own slots stay
 * live under the panel's own claim. The host is attached to MenuKit's screen, so
 * claims and modals see these panels together with anything injected onto it.
 *
 * <h3>Frame order</h3>
 * <ol>
 *   <li>{@code extractContents} HEAD: layout, slot positions, then the host's FLOW
 *       layer (backgrounds, slot frames, elements), under every slot;</li>
 *   <li>vanilla: labels, slot highlight, slots (created slots included);</li>
 *   <li>{@code ContainerScreenLayers}: injected FLOW panels, then the dim and every
 *       host's OVERLAY layer, this screen's included.</li>
 * </ol>
 */
public class CustomContainerScreen extends AbstractContainerScreen<CustomContainerMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    /** Size of one slot cell (18x18: 16px item + 1px border each side). */
    private static final int SLOT_SIZE = 18;

    /** Places, renders and routes input for this screen's panels. */
    private final OwnHost host;

    /** One client panel per handler panel, in the handler's order. */
    private final List<Panel> panels;

    /** Each panel's shown state as last seen, to tell listeners when the server changes it. */
    private final Map<String, Boolean> lastShown = new LinkedHashMap<>();

    /** Panel id -> this frame's placement (absolute screen coordinates). Rebuilt each frame. */
    private Map<String, OwnHost.Placement> placements = new LinkedHashMap<>();

    // The CreatedSlot hovered last frame, for enter and exit events. Also readable by
    // consumers as the current hover.
    private @Nullable CreatedSlot previouslyHoveredMkSlot = null;

    /**
     * The CreatedSlot under the mouse, or null if it isn't over one. A HUD
     * overlay, for example, can read this each frame without subscribing to events.
     */
    public @Nullable CreatedSlot hoveredCreatedSlot() {
        return previouslyHoveredMkSlot;
    }

    /**
     * The CreatedSlotGroup of the hovered CreatedSlot, or null.
     */
    public @Nullable CreatedSlotGroup getHoveredGroup() {
        return previouslyHoveredMkSlot != null
                ? previouslyHoveredMkSlot.getGroup() : null;
    }

    /**
     * A "return to this container" action for the custom container screen now
     * showing, or {@code null} if the current screen is not one.
     *
     * <p>Use it when opening a temporary client
     * {@link net.minecraft.client.gui.screens.Screen} over an open custom container:
     * pass the action to {@code MKScreen.setReturnAction} so the temporary screen's
     * Back or Escape opens the container again. It has to reopen rather than just
     * restore the screen on the client, because vanilla closes the server-synced menu
     * when you {@code setScreen} away from a container. Coming back means sending the
     * open request again, which is what the returned {@link Runnable} does: it finds
     * this menu's {@link CustomMenu} handle and calls {@link ClientMenu#requestOpen()}.
     *
     * <p>Returns {@code null} when there is nothing to return to (not in a custom
     * container, or the menu's type isn't a registered handle), so callers fall back
     * to their usual navigation. This only restores custom containers; a vanilla
     * container can't be cleanly reopened from the client after the close packet.
     */
    public static @Nullable Runnable captureReturnAction() {
        net.minecraft.client.gui.screens.Screen current =
                net.minecraft.client.Minecraft.getInstance().gui.screen();
        if (!(current instanceof CustomContainerScreen mkc)) return null;
        net.minecraft.world.inventory.MenuType<?> type;
        try {
            type = mkc.getMenu().getType();
        } catch (UnsupportedOperationException e) {
            return null;  // a menu with no registered type (e.g. the inventory menu)
        }
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type);
        if (id == null) return null;
        CustomMenu handle = CustomMenu.byId(id);
        if (handle == null) return null;
        return () -> ClientMenu.of(handle).requestOpen();
    }

    // Vanilla made imageWidth/imageHeight final in 26.x, fixed at construction. This
    // screen's layout changes them every frame, so it keeps its own pair, and its own
    // leftPos/topPos (the main frame the host resolved). recenter() applies them over
    // super.init()'s centering, and hasClickedOutside is overridden.
    private int mkcImageWidth  = 176;
    private int mkcImageHeight = 100;
    private int mkcLeftPos = 0;
    private int mkcTopPos = 0;

    public CustomContainerScreen(CustomContainerMenu handler, Inventory inventory,
                                Component title) {
        super(handler, inventory, title);
        // A container screen (it is an AbstractContainerScreen), so its panels draw
        // in the container look, as every inventory's do (ScreenKind.of).
        this.host = OwnHost.of(this, ScreenKind.CONTAINER, OwnHost.TitleBand.IN_FRAME)
                .contentSize(this::contentSize)
                // A slot-bearing main can't scroll: its slots sit in absolute
                // coordinates with no scroll hook. A pure-element main can.
                .autoFitMain(p -> menu.getGroupsFor(p.id()).isEmpty())
                .slotOwnership(this::ownsSlotAt)
                .decoration(this::drawSlotFrames);
        this.panels = buildPanels(handler);
        List<PanelPosition> positions = PanelPosition.standaloneDefaults(
                panels.stream().map(Panel::getPosition).toList());
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            host.add(panel, positions.get(i), panel.interiorPadding());
            lastShown.put(panel.id(), panel.isVisible());
        }
        // Initial layout sets the image size before init() runs.
        computeLayout();
    }

    /**
     * One {@link Panel} per handler panel: raised by default, then the look declared with
     * {@link ClientMenu#panel}, shown while the server shows the panel.
     */
    private static List<Panel> buildPanels(CustomContainerMenu handler) {
        MenuType<?> type;
        try {
            type = handler.getType();
        } catch (UnsupportedOperationException e) {
            type = null; // a handler built without a menu type
        }
        List<Panel> built = new ArrayList<>();
        for (MenuPanel server : handler.getPanels()) {
            String id = server.id();
            Panel.Builder b = Panel.builder(id).style(PanelStyle.RAISED);
            Consumer<Panel.Builder> look = ClientMenu.lookFor(type, id);
            if (look != null) look.accept(b);
            Panel panel = b.visible(true).build();
            panel.visibleWhen(() -> handler.isPanelVisible(id));
            built.add(panel);
        }
        return List.copyOf(built);
    }

    @Override
    protected void init() {
        computeLayout();
        super.init(); // sets leftPos = (width - imageWidth) / 2, etc.
        recenter();   // the host's main frame wins over vanilla's centering
        positionSlots();

        // Rebuild the key table from the panels on every init, including after a resize.
        keyRegistry.clear();
        registerPanelToggleKeys();

        // Claims and modals see this screen's panels; widget-wrapping elements
        // (TextField etc.) register their vanilla widget (detach-then-attach, so a
        // resize re-registers them).
        host.attach();
    }

    @Override
    public void removed() {
        host.detach();
        super.removed();
    }

    // Layout runs every frame. It is cheap, and handles visibility toggles with no
    // invalidation step.

    /**
     * A panel's content size, padding excluded: the larger of its slot grid and its
     * elements' extent on each axis, or its pinned size where pinned (the consumer's
     * explicit budget wins). Pure measure: the host feeds each panel its size budget
     * per role before asking. Slot grids are author-fixed and do not reflow; wrapping
     * slots is {@code SlotFlowElement}'s job.
     */
    private int[] contentSize(Panel panel) {
        int maxCols = 0;
        int totalSlotHeight = 0;
        for (CreatedSlotGroup group : menu.getGroupsFor(panel.id())) {
            int cols = group.getColumns();
            maxCols = Math.max(maxCols, cols);
            int rows = (group.getStorage().size() + cols - 1) / cols; // ceiling division
            totalSlotHeight += rows * SLOT_SIZE;
            if (group.getRowGapAfter() >= 0 && group.getRowGapAfter() < rows - 1) {
                totalSlotHeight += group.getRowGapSize();
            }
        }
        // Elements' child coordinates are relative to the content area.
        int elementWidth = 0;
        int elementHeight = 0;
        for (PanelElement element : panel.getElements()) {
            elementWidth  = Math.max(elementWidth,  element.getChildX() + element.getWidth());
            elementHeight = Math.max(elementHeight, element.getChildY() + element.getHeight());
        }
        int width  = Math.max(maxCols * SLOT_SIZE, elementWidth);
        int height = Math.max(totalSlotHeight, elementHeight);
        if (panel.getPinnedWidth() >= 0) width = panel.getPinnedWidth();
        if (panel.getPinnedHeight() >= 0) height = panel.getPinnedHeight();
        return new int[]{width, height};
    }

    /**
     * Resolves this frame's placements through the host and takes the main frame as
     * the screen's image (leftPos, topPos, imageWidth, imageHeight), so a vanilla
     * container's {@code leftPos + slot.x} math holds. With no main panel (only
     * chrome and overlays), vanilla's 176x100 minimum stays, centred.
     */
    private void computeLayout() {
        OwnHost.Layout layout = host.layout();
        Map<String, OwnHost.Placement> byId = new LinkedHashMap<>();
        for (OwnHost.Placement p : layout.placed()) byId.put(p.panel().id(), p);
        this.placements = byId;

        Reference main = layout.main();
        if (main != null) {
            mkcImageWidth = main.imageWidth();
            mkcImageHeight = main.imageHeight();
            mkcLeftPos = main.leftPos();
            mkcTopPos = main.topPos();
        } else {
            mkcImageWidth = 176;
            mkcImageHeight = 100;
            mkcLeftPos = (this.width - mkcImageWidth) / 2;
            mkcTopPos = (this.height - mkcImageHeight) / 2;
        }

        // The "Inventory" label: 8px right of the player panel's left edge and 11px
        // above its top, the standard vanilla offsets, in leftPos/topPos-relative
        // coordinates (vanilla draws labels translated by leftPos/topPos).
        OwnHost.Placement player = placements.get("player");
        if (player != null) {
            this.inventoryLabelX = player.x() - mkcLeftPos + 8;
            this.inventoryLabelY = player.y() - mkcTopPos - 11;
        }
    }

    /** Applies the host's main frame as the screen's origin (over vanilla's centering). */
    private void recenter() {
        this.leftPos = mkcLeftPos;
        this.topPos = mkcTopPos;
    }

    /**
     * Sets each slot's x/y from its panel's placement and grid. Slot.x/y are
     * container-relative (vanilla adds leftPos/topPos). A slot whose panel is hidden
     * or not placed goes off-screen, so vanilla neither draws nor hit-tests it; its
     * content keeps syncing server-side (CreatedSlot.isInert reads the panel's own
     * visibility, not the client window).
     */
    private void positionSlots() {
        for (Panel panel : panels) {
            OwnHost.Placement placed = placements.get(panel.id());
            int groupOffsetY = 0;
            for (CreatedSlotGroup group : menu.getGroupsFor(panel.id())) {
                int cols = group.getColumns();
                int rows = (group.getStorage().size() + cols - 1) / cols;
                for (int s = group.getFlatIndexStart(); s < group.getFlatIndexEnd(); s++) {
                    int localIdx = s - group.getFlatIndexStart();
                    int col = localIdx % cols;
                    int row = localIdx / cols;
                    int extraY = (group.getRowGapAfter() >= 0 && row > group.getRowGapAfter())
                            ? group.getRowGapSize() : 0;
                    Slot slot = menu.slots.get(s);
                    if (placed != null) {
                        // +1: slot.x/y name the 16x16 item box, 1px inside the 18x18 frame.
                        // The content origin is the placement inset by the padding this
                        // screen gave the panel when it added it.
                        int contentX = placed.x() + panel.interiorPadding();
                        int contentY = placed.y() + panel.interiorPadding();
                        ((SlotPositionAccessor) slot).sk$setX(
                                contentX - mkcLeftPos + col * SLOT_SIZE + 1);
                        ((SlotPositionAccessor) slot).sk$setY(
                                contentY - mkcTopPos + groupOffsetY + row * SLOT_SIZE + extraY + 1);
                    } else {
                        ((SlotPositionAccessor) slot).sk$setX(-9999);
                        ((SlotPositionAccessor) slot).sk$setY(-9999);
                    }
                }
                int groupGapExtra = (group.getRowGapAfter() >= 0 && group.getRowGapAfter() < rows - 1)
                        ? group.getRowGapSize() : 0;
                groupOffsetY += rows * SLOT_SIZE + groupGapExtra;
            }
        }
    }

    /**
     * The host's decoration hook: a panel's slot frames, drawn after its background
     * and before its elements (18x18 frames around the 16x16 item boxes).
     */
    private void drawSlotFrames(GuiGraphicsExtractor graphics, OwnHost.Placement placed) {
        for (CreatedSlotGroup group : menu.getGroupsFor(placed.panel().id())) {
            for (int s = group.getFlatIndexStart(); s < group.getFlatIndexEnd(); s++) {
                Slot slot = menu.slots.get(s);
                if (!slot.isActive()) continue;
                SlotRendering.drawSlotBackground(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, SlotRendering.DEFAULT_SIZE, false);
            }
        }
    }

    /**
     * The host's slot-ownership hook: whether {@code panel}'s own live slot is under
     * the point, so the panel's claim routes the point to that slot (hover, click,
     * tooltip) rather than making it inert. The hover frame is vanilla's: the 18x18
     * cell around the item box.
     */
    private boolean ownsSlotAt(Panel panel, double mouseX, double mouseY) {
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;
        for (CreatedSlotGroup group : menu.getGroupsFor(panel.id())) {
            for (int s = group.getFlatIndexStart(); s < group.getFlatIndexEnd(); s++) {
                Slot slot = menu.slots.get(s);
                if (!slot.isActive()) continue;
                if (relX >= slot.x - 1 && relX < slot.x + 17 && relY >= slot.y - 1 && relY < slot.y + 17) {
                    return true;
                }
            }
        }
        return false;
    }

    // Rendering

    /**
     * Layout, slot positions, then this screen's FLOW layer, all before vanilla's
     * content pass (widgets, labels, slot highlight, slots), where the old
     * {@code renderBg} sat. Recomputed each frame so visibility toggles take effect
     * at once. This screen's OVERLAY layer draws later, above the slots, through
     * {@code ContainerScreenLayers}.
     */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        computeLayout();
        recenter();
        positionSlots();
        host.renderFlow(graphics, mouseX, mouseY);
        super.extractContents(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        fireServerToggles();

        // Hover enter and exit events. Vanilla has set hoveredSlot by now.
        CreatedSlot currentHovered = (this.hoveredSlot instanceof CreatedSlot mk) ? mk : null;
        if (currentHovered != previouslyHoveredMkSlot) {
            if (previouslyHoveredMkSlot != null) {
                fireSlotHoverExit(previouslyHoveredMkSlot);
            }
            if (currentHovered != null) {
                fireSlotHoverEnter(currentHovered);
            }
            previouslyHoveredMkSlot = currentHovered;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 26.2 requires explicit ARGB colors (alpha 0 draws nothing). White with
        // shadow for readability on the dark overlay.
        graphics.text(this.font, this.title,
                this.titleLabelX, this.titleLabelY, 0xFFFFFFFF, true);
        // The "Inventory" label only when there is a player panel on screen.
        if (placements.get("player") != null) {
            graphics.text(this.font, this.playerInventoryTitle,
                    this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFFFF, true);
        }
    }

    // Panel visibility. Consumers can toggle a panel from anywhere (HUD buttons, chat
    // commands, game events); it goes through the same server request as the toggle
    // key.

    /**
     * Asks the server to show or hide a panel (vanilla's container button packet). The
     * server honours it only for a panel declared {@code toggleable()} and the change
     * arrives with the next sync; the client never changes it on its own.
     * Listeners hear {@code onPanelToggle} when the server's answer arrives.
     */
    public void togglePanel(String panelId) {
        int buttonId = this.menu.getPanelButtonId(panelId);
        if (buttonId != CustomContainerMenu.PANEL_NOT_FOUND && this.minecraft != null
                && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    /** Tells listeners about each panel the server showed or hid since the last frame. */
    private void fireServerToggles() {
        for (Panel panel : panels) {
            boolean now = panel.isVisible();
            Boolean before = lastShown.put(panel.id(), now);
            if (before != null && before != now) firePanelToggle(panel, now);
        }
    }

    // Key actions, looked up by key code. Panel toggle keys are registered from the
    // panels automatically; registerKey() adds custom ones.

    /** Action triggered by a key press while this screen is open. */
    @FunctionalInterface
    public interface KeyAction {
        boolean onKey(KeyEvent event, CustomContainerScreen screen);
    }

    private final Map<Integer, KeyAction> keyRegistry = new LinkedHashMap<>();

    /** Registers an action for a key code ({@code InputConstants.KEY_*}). */
    public void registerKey(int keyCode, KeyAction action) {
        keyRegistry.put(keyCode, action);
    }

    /**
     * Registers each panel's toggle key (declared on its look). The key asks the server,
     * which honours it only for a {@code toggleable()} panel.
     */
    private void registerPanelToggleKeys() {
        for (Panel panel : panels) {
            if (panel.getToggleKey() < 0) continue;
            String panelId = panel.id();
            registerKey(panel.getToggleKey(), (event, screen) -> {
                screen.togglePanel(panelId);
                return true;
            });
        }
    }

    // Events for this screen instance only, about what happens inside it. For slot
    // events across all screens, use Fabric's event API.

    /**
     * Listener for events inside this screen. Every method does nothing by
     * default, so override only what you need.
     *
     * <p>The callbacks pass a {@link CreatedSlot}, the library's handle for a created
     * slot, rather than a raw vanilla {@code Slot}. It carries
     * {@link CreatedSlot#address()} (the slot's {@code Address}) plus
     * {@code groupId()} and {@code getLocalIndex()} for display.
     */
    public interface ScreenEventListener {
        /**
         * A CreatedSlot was clicked. {@code button} is vanilla's numbering: compare it
         * with {@code Click.LEFT}, {@code Click.RIGHT} and {@code Click.MIDDLE}.
         */
        default void onSlotClick(CreatedSlot slot, int button) {}
        /** An empty CreatedSlot was clicked with an empty cursor. */
        default void onEmptySlotClick(CreatedSlot slot, int button) {}
        /** Mouse entered a CreatedSlot's hover area. */
        default void onSlotHoverEnter(CreatedSlot slot) {}
        /** Mouse left a CreatedSlot's hover area. */
        default void onSlotHoverExit(CreatedSlot slot) {}
        /** A panel's visibility changed. */
        default void onPanelToggle(Panel panel, boolean nowVisible) {}
        /**
         * A shift-click (quick move) is about to happen on a CreatedSlot. Fires before
         * vanilla routes the items, so the destination isn't known yet. movedStack is a
         * copy of what is in the source.
         *
         * <p>Shift-click only. Other ways items move (drag-collect, double-click
         * collect, hopper insertion, cursor placement, creative middle-click) do not
         * fire it; the name matches vanilla's {@code quickMoveStack} on purpose. For
         * those, use the relevant hooks directly.
         *
         * <p>For logging, analytics or checks before the transfer. Seeing where the
         * items ended up would need a later onAfterQuickMove event fired from
         * quickMoveStack.
         */
        default void onQuickMove(CreatedSlot sourceSlot,
                                 net.minecraft.world.item.ItemStack movedStack) {}
    }

    private final List<ScreenEventListener> eventListeners = new ArrayList<>();

    /** Registers a screen event listener. Cleared on screen close. */
    public void addEventListener(ScreenEventListener listener) {
        eventListeners.add(listener);
    }

    private void fireSlotClick(CreatedSlot slot, int button) {
        for (var listener : eventListeners) listener.onSlotClick(slot, button);
    }

    private void fireEmptySlotClick(CreatedSlot slot, int button) {
        for (var listener : eventListeners) listener.onEmptySlotClick(slot, button);
    }

    private void fireSlotHoverEnter(CreatedSlot slot) {
        for (var listener : eventListeners) listener.onSlotHoverEnter(slot);
    }

    private void fireSlotHoverExit(CreatedSlot slot) {
        for (var listener : eventListeners) listener.onSlotHoverExit(slot);
    }

    private void firePanelToggle(Panel panel, boolean nowVisible) {
        for (var listener : eventListeners) listener.onPanelToggle(panel, nowVisible);
    }

    private void fireQuickMove(CreatedSlot sourceSlot, net.minecraft.world.item.ItemStack movedStack) {
        for (var listener : eventListeners) listener.onQuickMove(sourceSlot, movedStack);
    }

    // Drag modes for this screen. When a mouse drag starts on a CreatedSlot, the
    // registered modes are asked in turn whether to take it, and the one that does
    // gets the drag and end callbacks.

    /**
     * A custom drag behaviour for created slots. The screen sends drag events to the
     * active mode, and only one mode is active at a time.
     */
    public interface DragMode {
        /**
         * Called when a drag starts on a CreatedSlot. Return true to take the drag, so
         * the following drag and end calls come to this mode.
         */
        boolean onDragStart(CreatedSlot slot, int button);
        /** Called when the mouse moves over a new slot during a drag. */
        void onDrag(CreatedSlot slot);
        /** Called when the mouse button is released, ending the drag. */
        void onDragEnd();
    }

    private final List<DragMode> dragModes = new ArrayList<>();
    private @Nullable DragMode activeDrag = null;

    /** Registers a drag mode. Checked in order when a drag starts. */
    public void registerDragMode(DragMode mode) {
        dragModes.add(mode);
    }

    // Input

    /**
     * "Inside" is any placed panel, not just the image rectangle: a region panel
     * beside the frame is inside, so a click there is a slot interaction, not a drop.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int leftPos, int topPos) {
        for (OwnHost.Placement placed : placements.values()) {
            if (placed.contains(mouseX, mouseY)) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, leftPos, topPos);
    }

    /**
     * Key dispatch: panel elements first (a Dropdown's arrows), then the key
     * registry, then vanilla.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (host.keyPressed(event.key(), event.keycode(), event.modifiers())) {
            return true;
        }
        KeyAction action = keyRegistry.get(event.key());
        if (action != null && action.onKey(event, this)) {
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Click dispatch: element, drag mode, right-click handler, events, vanilla.
     * Elements go first so a button never triggers the slot under it.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        // An open Dropdown closes when the click falls outside it, even if a slot or
        // another element takes the click.
        host.notifyOutsideClick(event.x(), event.y());

        boolean modal = host.hasModal();
        if (host.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        // A modal of this screen's own claims everything.
        if (modal) return true;

        // Start a drag if a registered mode takes it.
        if (this.hoveredSlot instanceof CreatedSlot mkSlot && activeDrag == null) {
            for (DragMode mode : dragModes) {
                if (mode.onDragStart(mkSlot, event.button())) {
                    activeDrag = mode;
                    return true; // drag claimed, skip vanilla
                }
            }
        }

        // A right click on a slot with a handler is sent as a plain right click whatever
        // the cursor holds (vanilla would start a drag). The menu runs the handler on
        // both sides (CustomContainerMenu.clicked), and the server's run is the one that
        // counts.
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && this.hoveredSlot instanceof CreatedSlot mkSlot
                && menu.rightClickHandler(mkSlot.index, 1, ContainerInput.PICKUP) != null) {
            slotClicked(mkSlot, mkSlot.index, 1, ContainerInput.PICKUP);
            fireSlotClick(mkSlot, event.button());
            return true;
        }

        // Slot click events; the click still goes on to vanilla.
        if (this.hoveredSlot instanceof CreatedSlot mkSlot) {
            fireSlotClick(mkSlot, event.button());
            if (!mkSlot.hasItem() && this.menu.getCarried().isEmpty()) {
                fireEmptySlotClick(mkSlot, event.button());
            }
            // Shift-click captures the stack before vanilla routes it.
            if (event.hasShiftDown() && mkSlot.hasItem()) {
                fireQuickMove(mkSlot, mkSlot.getItem().copy());
            }
        }

        return super.mouseClicked(event, flag);
    }

    /** Drag dispatch: an active drag mode gets the slots it crosses. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeDrag != null && this.hoveredSlot instanceof CreatedSlot mkSlot) {
            activeDrag.onDrag(mkSlot);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Drag end: a slot drag mode ends, and every shown element hears the release
     * (a ScrollContainer's scrollbar drag ends wherever the cursor is).
     */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean dragModeWasActive = false;
        if (activeDrag != null) {
            activeDrag.onDragEnd();
            activeDrag = null;
            dragModeWasActive = true;
        }
        host.mouseReleased(event.x(), event.y(), event.button());
        if (dragModeWasActive) {
            return true; // drag claimed the release
        }
        return super.mouseReleased(event);
    }

    /** Scroll: panel elements first (a ScrollContainer), then vanilla. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double scrollX, double scrollY) {
        if (host.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
