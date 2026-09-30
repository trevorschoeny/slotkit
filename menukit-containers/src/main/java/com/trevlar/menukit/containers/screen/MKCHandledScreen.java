package com.trevlar.menukit.containers.screen;

import com.trevlar.menukit.core.*;
import com.trevlar.menukit.containers.core.*;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.Reference;
import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.mixin.SlotPositionAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client-side partner to {@link MKCScreenHandler}: a standalone screen whose panels
 * hold slot groups. Owns slot positioning, hover events, key actions and drag modes;
 * placement, render and element input belong to its {@link PanelHost} (§0065).
 *
 * <h3>Its host</h3>
 * The handler's panels go into a standalone host with the title band inside the
 * frame. The host places them by their {@link PanelPosition} (the handler's
 * builder has already given unplaced ones the standalone default: the first is the
 * {@code main()} frame, later ones stack below it). This screen tells the host what
 * the host cannot know on its own: a panel's content size includes its slot grid,
 * its slot frames draw between its background and its elements, the main panel
 * auto-fits its height only when it holds no slots, and a panel's own slots stay
 * live under the panel's own claim. The host is attached to
 * {@link ScreenPanelRegistry}, so claims and modals see these panels together with
 * anything injected onto this screen.
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
public class MKCHandledScreen extends AbstractContainerScreen<MKCScreenHandler> {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    /** Size of one slot cell (18x18: 16px item + 1px border each side). */
    private static final int SLOT_SIZE = 18;

    // ── The host ───────────────────────────────────────────────────────

    /** This screen's panels, placed, rendered and routed (§0065). */
    private final PanelHost host;

    /** Panel id -> this frame's placement (absolute screen coordinates). Rebuilt each frame. */
    private Map<String, PanelHost.Placed> placements = new LinkedHashMap<>();

    // ── Hover Tracking ─────────────────────────────────────────────────
    // Tracks the previously hovered MKCSlot to fire enter/exit events.
    // Also exposes current hover state as a queryable property for consumers.
    private @Nullable MKCSlot previouslyHoveredMkSlot = null;

    /**
     * Returns the currently hovered MKCSlot, or null if the mouse
     * isn't over a MenuKit slot. Synchronous query — consumers like HUD
     * overlays can read this each frame without subscribing to events.
     */
    public @Nullable MKCSlot getHoveredMKCSlot() {
        return previouslyHoveredMkSlot;
    }

    /**
     * Returns the SlotGroup of the currently hovered MKCSlot, or
     * null. Convenience for consumers that care about group identity.
     */
    public @Nullable SlotGroup getHoveredGroup() {
        return previouslyHoveredMkSlot != null
                ? previouslyHoveredMkSlot.getGroup() : null;
    }

    /**
     * Captures a "return to this container" action for the currently-displayed
     * MKC container screen, or {@code null} if the current screen is not one.
     *
     * <p>Use when opening a transient client {@link net.minecraft.client.gui.screens.Screen}
     * OVER a live MKC container: pass the captured action to
     * {@code MKScreen.setReturnAction} so the transient screen's Back/Escape
     * RE-OPENS the container. Re-open (not a client-only screen restore) is
     * required because vanilla closes the server-synced menu when you
     * {@code setScreen} away from a container — so coming back means re-issuing
     * the open request, which is exactly what the returned {@link Runnable}
     * does (it resolves this menu's {@link MKCMenu} handle and calls
     * {@link MKCMenu#requestOpen()}).
     *
     * <p>Returns {@code null} when there is nothing to return to (not in an MKC
     * container, or the menu's type doesn't resolve to a registered handle), so
     * callers fall back to their default navigation. Honest limit: this only
     * restores MKC-backed containers — a vanilla container can't be cleanly
     * re-opened client-side after the close packet.
     */
    public static @Nullable Runnable captureReturnAction() {
        net.minecraft.client.gui.screens.Screen current =
                net.minecraft.client.Minecraft.getInstance().gui.screen();
        if (!(current instanceof MKCHandledScreen mkc)) return null;
        net.minecraft.world.inventory.MenuType<?> type;
        try {
            type = mkc.getMenu().getType();
        } catch (UnsupportedOperationException e) {
            return null;  // a menu with no registered type (e.g. the inventory menu)
        }
        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type);
        if (id == null) return null;
        MKCMenu handle = MKCMenu.byId(id);
        if (handle == null) return null;
        return handle::requestOpen;
    }

    // ── Construction ───────────────────────────────────────────────────

    // ── MKC-owned image dimensions (26.2 migration) ───────────────────
    // Vanilla made imageWidth/imageHeight FINAL at 26.x (dims are a
    // construction-time contract now). MKC's reactive layout changes them
    // per frame, so MKC keeps its own pair, and its own leftPos/topPos target
    // (the main frame the host resolved). recenter() applies them over
    // super.init()'s centering; hasClickedOutside is overridden.
    private int mkcImageWidth  = 176;
    private int mkcImageHeight = 100;
    private int mkcLeftPos = 0;
    private int mkcTopPos = 0;

    public MKCHandledScreen(MKCScreenHandler handler, Inventory inventory,
                                Component title) {
        super(handler, inventory, title);
        this.host = PanelHost.standalone(
                        () -> new PanelHost.Frame(this.width, this.height, null),
                        PanelHost.TitleBand.IN_FRAME)
                .contentSize(this::contentSize)
                // A slot-bearing main can't scroll: its slots sit in absolute
                // coordinates with no scroll hook. A pure-element main can.
                .autoFitMain(p -> menu.getGroupsFor(p.getId()).isEmpty())
                .slotOwnership(this::ownsSlotAt)
                .decoration(this::drawSlotFrames);
        // The builder already applied the standalone default; applying it again is
        // a no-op for placed panels and covers a handler built another way.
        List<Panel> panels = menu.getPanels();
        List<PanelPosition> positions = PanelPosition.standaloneDefaults(
                panels.stream().map(Panel::getPosition).toList());
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            host.add(panel, positions.get(i), panel.interiorPadding(), "", i);
        }
        // Initial layout sets the image size before init() runs.
        computeLayout();
    }

    @Override
    protected void init() {
        computeLayout();
        super.init(); // sets leftPos = (width - imageWidth) / 2, etc.
        recenter();   // the host's main frame wins over vanilla's centering
        positionSlots();

        // Build the key dispatch table from the panel tree.
        // Cleared and rebuilt on each init (handles screen resize re-init).
        keyRegistry.clear();
        registerPanelToggleKeys();

        // Claims and modals see this screen's panels; widget-wrapping elements
        // (TextField etc.) register their vanilla widget (detach-then-attach, so a
        // resize re-registers them).
        ScreenPanelRegistry.attachOwnHost(this, host);
        host.attach(this);
    }

    @Override
    public void removed() {
        host.detach(this);
        super.removed();
    }

    // ── Layout ─────────────────────────────────────────────────────────
    // Runs each frame. Cheap. Handles visibility toggles with no invalidation step.

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
        for (SlotGroup group : menu.getGroupsFor(panel.getId())) {
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
        PanelHost.Layout layout = host.layout();
        Map<String, PanelHost.Placed> byId = new LinkedHashMap<>();
        for (PanelHost.Placed p : layout.placed()) byId.put(p.panel().getId(), p);
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
        PanelHost.Placed player = placements.get("player");
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
     * content keeps syncing server-side (MKCSlot.isInert reads the panel's own
     * visibility, not the client window).
     */
    private void positionSlots() {
        for (Panel panel : menu.getPanels()) {
            PanelHost.Placed placed = placements.get(panel.getId());
            int groupOffsetY = 0;
            for (SlotGroup group : menu.getGroupsFor(panel.getId())) {
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
                        ((SlotPositionAccessor) slot).mk$setX(
                                placed.contentX() - mkcLeftPos + col * SLOT_SIZE + 1);
                        ((SlotPositionAccessor) slot).mk$setY(
                                placed.contentY() - mkcTopPos + groupOffsetY + row * SLOT_SIZE + extraY + 1);
                    } else {
                        ((SlotPositionAccessor) slot).mk$setX(-9999);
                        ((SlotPositionAccessor) slot).mk$setY(-9999);
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
    private void drawSlotFrames(GuiGraphicsExtractor graphics, PanelHost.Placed placed) {
        for (SlotGroup group : menu.getGroupsFor(placed.panel().getId())) {
            for (int s = group.getFlatIndexStart(); s < group.getFlatIndexEnd(); s++) {
                Slot slot = menu.slots.get(s);
                if (!slot.isActive()) continue;
                PanelRendering.renderSlotBackground(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
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
        for (SlotGroup group : menu.getGroupsFor(panel.getId())) {
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

    // ── Rendering ──────────────────────────────────────────────────────

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
        host.render(graphics, mouseX, mouseY, LayerPlan.Layer.FLOW,
                ScreenPanelRegistry.pointerPolicy(this, mouseX, mouseY));
        super.extractContents(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // ── Hover tracking (fire enter/exit events) ────────────────────
        // After the super pass, hoveredSlot is set by vanilla's pipeline.
        MKCSlot currentHovered = (this.hoveredSlot instanceof MKCSlot mk) ? mk : null;
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

    // ── Panel Visibility (public API) ──────────────────────────────────
    // Entry point for consumers to toggle panel visibility from anywhere
    // (HUD buttons, chat commands, game events). Routes through the
    // same C2S sync mechanism as the keybind path.

    /**
     * Toggles a panel's visibility with full client+server sync.
     *
     * <p>Consumers can call this from any context (HUD button click,
     * chat command, game event) — not just from keybinds. Routes through
     * the same {@code clickMenuButton} C2S mechanism.
     */
    public void togglePanel(String panelId) {
        int buttonId = this.menu.getPanelButtonId(panelId);
        if (buttonId != MKCScreenHandler.PANEL_NOT_FOUND && this.minecraft != null
                && this.minecraft.gameMode != null
                && this.minecraft.player != null) {
            this.menu.clickMenuButton(this.minecraft.player, buttonId);
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId, buttonId);
            // Fire event
            Panel panel = this.menu.getPanel(panelId);
            if (panel != null) {
                firePanelToggle(panel, panel.isVisible());
            }
        }
    }

    // ── Key Registry ──────────────────────────────────────────────────
    // Lookup-based dispatch — not a hardcoded switch. Panel toggle keys
    // are auto-registered from the panel tree. Custom actions can be
    // added via registerKey(). Task 4's event bus can hook into this.

    /** Action triggered by a key press while this screen is open. */
    @FunctionalInterface
    public interface KeyAction {
        boolean onKey(KeyEvent event, MKCHandledScreen screen);
    }

    private final Map<Integer, KeyAction> keyRegistry = new LinkedHashMap<>();

    /** Registers a key action. GLFW key code → action. */
    public void registerKey(int glfwKey, KeyAction action) {
        keyRegistry.put(glfwKey, action);
    }

    /**
     * Auto-registers panel toggle keys from the panel tree.
     * Called during init(). Each panel with a toggleKey gets a
     * registry entry that syncs visibility via the C2S mechanism.
     */
    private void registerPanelToggleKeys() {
        for (Panel panel : menu.getPanels()) {
            if (panel.getToggleKey() < 0) continue;
            String panelId = panel.getId();
            registerKey(panel.getToggleKey(), (event, screen) -> {
                screen.togglePanel(panelId);
                return true;
            });
        }
    }

    // ── Screen Event Bus ─────────────────────────────────────────────
    // Per-screen-instance event system. MenuKit-scoped — fires events
    // about things happening inside this MenuKit screen. Consumers
    // wanting ecosystem-wide slot events use Fabric's event API.
    //
    // Typed callbacks, not generic Object... varargs. Each method has
    // a default no-op so listeners only override what they care about.

    /**
     * Listener for events happening within a MenuKit screen.
     * All methods have default no-ops — override only what you need.
     *
     * <p>These callbacks carry {@link MKCSlot} — the library's own slot handle for
     * a created slot, not a raw vanilla {@code Slot}. It is the right handle for
     * own-slot event callbacks: it carries {@link MKCSlot#address()} (the slot's
     * {@code Address} for the address world) plus {@code getGroupId()} /
     * {@code getLocalIndex()} for display.
     */
    public interface ScreenEventListener {
        /** A MKCSlot was clicked. Button: 0=left, 1=right, 2=middle. */
        default void onSlotClick(MKCSlot slot, int button) {}
        /** An empty MKCSlot was clicked with an empty cursor. */
        default void onEmptySlotClick(MKCSlot slot, int button) {}
        /** Mouse entered a MKCSlot's hover area. */
        default void onSlotHoverEnter(MKCSlot slot) {}
        /** Mouse left a MKCSlot's hover area. */
        default void onSlotHoverExit(MKCSlot slot) {}
        /** A panel's visibility changed. */
        default void onPanelToggle(Panel panel, boolean nowVisible) {}
        /**
         * A shift-click (quick move) is about to happen on a MKCSlot.
         * Fires BEFORE vanilla routes the items — destination is unknown
         * at this point. The movedStack is a copy of what's in the source.
         *
         * <p><b>Scope:</b> shift-click only. Other item-movement paths
         * (drag-collect, double-click collect, hopper insertion, cursor
         * placement, creative middle-click) do NOT fire this event — the
         * name intentionally matches vanilla's {@code quickMoveStack}.
         * For those paths, observe the relevant ecosystem hooks directly.
         *
         * <p>Use for logging, analytics, or pre-transfer checks. For
         * post-routing observation (where items ended up), a future
         * onAfterQuickMove event would fire from quickMoveStack.
         */
        default void onQuickMove(MKCSlot sourceSlot,
                                 net.minecraft.world.item.ItemStack movedStack) {}
    }

    private final List<ScreenEventListener> eventListeners = new ArrayList<>();

    /** Registers a screen event listener. Cleared on screen close. */
    public void addEventListener(ScreenEventListener listener) {
        eventListeners.add(listener);
    }

    private void fireSlotClick(MKCSlot slot, int button) {
        for (var listener : eventListeners) listener.onSlotClick(slot, button);
    }

    private void fireEmptySlotClick(MKCSlot slot, int button) {
        for (var listener : eventListeners) listener.onEmptySlotClick(slot, button);
    }

    private void fireSlotHoverEnter(MKCSlot slot) {
        for (var listener : eventListeners) listener.onSlotHoverEnter(slot);
    }

    private void fireSlotHoverExit(MKCSlot slot) {
        for (var listener : eventListeners) listener.onSlotHoverExit(slot);
    }

    private void firePanelToggle(Panel panel, boolean nowVisible) {
        for (var listener : eventListeners) listener.onPanelToggle(panel, nowVisible);
    }

    private void fireQuickMove(MKCSlot sourceSlot, net.minecraft.world.item.ItemStack movedStack) {
        for (var listener : eventListeners) listener.onQuickMove(sourceSlot, movedStack);
    }

    // ── Drag Modes ────────────────────────────────────────────────────
    // Per-screen drag mode registry. When a mouse drag starts on a
    // MKCSlot, the registry is checked for a mode that accepts
    // the drag. The active mode receives drag/end callbacks.

    /**
     * A custom drag behavior for MenuKit slots. The screen dispatches
     * drag events to the active mode. Only one mode is active at a time.
     */
    public interface DragMode {
        /**
         * Called when a drag starts on a MKCSlot.
         * Return true to claim the drag (subsequent drag/end go to this mode).
         */
        boolean onDragStart(MKCSlot slot, int button);
        /** Called when the mouse moves over a new slot during a drag. */
        void onDrag(MKCSlot slot);
        /** Called when the mouse button is released, ending the drag. */
        void onDragEnd();
    }

    private final List<DragMode> dragModes = new ArrayList<>();
    private @Nullable DragMode activeDrag = null;

    /** Registers a drag mode. Checked in order when a drag starts. */
    public void registerDragMode(DragMode mode) {
        dragModes.add(mode);
    }

    // ── Input ──────────────────────────────────────────────────────────

    /**
     * "Inside" is any placed panel, not just the image rectangle: a region panel
     * beside the frame is inside, so a click there is a slot interaction, not a drop.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY,
                                        int leftPos, int topPos) {
        for (PanelHost.Placed placed : placements.values()) {
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
        if (host.keyPressed(event.key(), event.scancode(), event.modifiers(), host.topmostModal() != null)) {
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
     * Elements get first crack so a button never triggers the slot under it.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        // Popover janitor: an open Dropdown closes when the click falls outside it,
        // even if a slot or another element consumes the click.
        host.notifyOutsideClick(event.x(), event.y());

        boolean modal = host.topmostModal() != null;
        if (host.mouseClicked(event.x(), event.y(), event.button(), modal)) {
            return true;
        }
        // A modal of this screen's own claims everything.
        if (modal) return true;

        // Drag mode start — check if a registered mode claims this drag
        if (this.hoveredSlot instanceof MKCSlot mkSlot && activeDrag == null) {
            for (DragMode mode : dragModes) {
                if (mode.onDragStart(mkSlot, event.button())) {
                    activeDrag = mode;
                    return true; // drag claimed — skip vanilla
                }
            }
        }

        // Right-click handler dispatch (group-level capability)
        if (event.button() == 1 && this.hoveredSlot instanceof MKCSlot mkSlot) {
            BiConsumer<net.minecraft.world.entity.player.Player, MKCSlot> handler =
                    mkSlot.getGroup().getRightClickHandler();
            if (handler != null && this.minecraft != null && this.minecraft.player != null) {
                handler.accept(this.minecraft.player, mkSlot);
                fireSlotClick(mkSlot, event.button());
                return true; // consumed — don't let vanilla place an item
            }
        }

        // Fire slot click event (doesn't consume — vanilla still processes)
        if (this.hoveredSlot instanceof MKCSlot mkSlot) {
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
        if (activeDrag != null && this.hoveredSlot instanceof MKCSlot mkSlot) {
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
        if (host.mouseScrolled(mouseX, mouseY, scrollX, scrollY, host.topmostModal() != null)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
