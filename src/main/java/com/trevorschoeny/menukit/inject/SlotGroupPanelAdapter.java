package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelRendering;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RegionConstants;
import com.trevorschoeny.menukit.core.RenderContext;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.core.SlotGroupRegion;
import com.trevorschoeny.menukit.core.SlotGroupRegionMath;
import com.trevorschoeny.menukit.window.ClientWindowVisibility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Adapter that anchors a {@link Panel} to a slot group's bounding box.
 * Parallel to {@link ScreenPanelAdapter} in shape — same background-render +
 * content-padding + origin + render + click machinery — but the bounds
 * input is a {@link SlotGroupBounds} (the bounding box of a category's
 * slots within a screen) rather than a screen frame.
 *
 * <p>See {@code Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §5 for
 * design and §7.2 for targeting semantics.
 *
 * <h3>Targeting</h3>
 *
 * Adapters declare target categories via {@code .on(SlotGroupCategory...)}.
 * Exact-category match, not inheritance — categories are flat tags. A single
 * adapter can target multiple categories; the panel renders once per
 * category that resolves in the current screen (so an adapter targeting
 * both {@code PLAYER_INVENTORY} and {@code FURNACE_INPUT} renders twice in
 * a furnace screen: once anchored to the player inventory, once to the
 * furnace input).
 *
 * <p>No {@code .onAny()} — SlotGroupContext targeting is always explicit
 * category enumeration; "any slot group" isn't a meaningful consumer mental
 * model (see M8 §5.6). Construction without a {@code .on(...)} call leaves
 * the adapter in {@link SlotGroupPanelRegistry}'s pending set; the boot
 * checkpoint fails with {@link IllegalStateException} naming the panel ID.
 */
public final class SlotGroupPanelAdapter {

    /** Default content padding — matches {@link ScreenPanelAdapter#DEFAULT_PADDING}. */
    public static final int DEFAULT_PADDING = ScreenPanelAdapter.DEFAULT_PADDING;

    private final Panel panel;
    private final SlotGroupRegion region;
    private final int padding;
    private final int priority;

    /** Declared targets; null until {@link #on} is called. */
    private @Nullable List<SlotGroupCategory> targets = null;

    // ── Constructors ────────────────────────────────────────────────────
    //
    // Phase 5 (B2) — RegionAnchor<SlotGroupRegion> overloads added so
    // SlotGroupRegion.priority(int) reaches a real adapter/registry pathway,
    // mirroring ScreenPanelAdapter(RegionAnchor<MenuRegion>) /
    // VanillaScreenPanelAdapter(RegionAnchor<ScreenRegion>). All four
    // region enums now behave identically.

    /**
     * Constructs an adapter with default content padding. Registration into
     * the per-(category, region) slot-group map happens lazily in {@link #on}
     * — at construction we don't yet know which categories this adapter
     * targets. Uses {@link RegionAnchor#DEFAULT_PRIORITY} for sibling
     * ordering; pair with the {@link RegionAnchor} constructor below for
     * explicit priority.
     *
     * <p>Padding defers to {@link Panel#interiorPadding()} — {@code 0} for
     * {@link com.trevorschoeny.menukit.core.PanelStyle#NONE} (element edge
     * = panel edge), {@link #DEFAULT_PADDING} for styled panels. Consumers
     * who want a different value pass it via the explicit-padding
     * constructor overload.
     */
    public SlotGroupPanelAdapter(Panel panel, SlotGroupRegion region) {
        this(panel, region, panel.interiorPadding(), RegionAnchor.DEFAULT_PRIORITY);
    }

    /** Constructor with explicit content padding. Uses
     *  {@link RegionAnchor#DEFAULT_PRIORITY} for sibling ordering. */
    public SlotGroupPanelAdapter(Panel panel, SlotGroupRegion region, int padding) {
        this(panel, region, padding, RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Region-aware constructor accepting a {@link RegionAnchor} — a slot-group
     * region paired with an explicit stacking priority (e.g.
     * {@code SlotGroupRegion.RIGHT_ALIGN_TOP.priority(50)}). Use when sibling
     * slot-group panels in the same (category, region) pair need deterministic
     * ordering relative to each other.
     *
     * <p>Padding defers to {@link Panel#interiorPadding()} (0 for NONE, 7
     * otherwise) — same style-conditional default as the
     * {@link ScreenPanelAdapter}/{@link VanillaScreenPanelAdapter}
     * {@code RegionAnchor} constructors.
     */
    public SlotGroupPanelAdapter(Panel panel, RegionAnchor<SlotGroupRegion> anchor) {
        this(panel, anchor.region(), panel.interiorPadding(), anchor.priority());
    }

    /** Region-aware constructor with both explicit padding and priority. */
    public SlotGroupPanelAdapter(Panel panel, RegionAnchor<SlotGroupRegion> anchor,
                                  int padding) {
        this(panel, anchor.region(), padding, anchor.priority());
    }

    /** Internal canonical constructor — public overloads delegate here. */
    private SlotGroupPanelAdapter(Panel panel, SlotGroupRegion region, int padding,
                                   int priority) {
        this.panel = panel;
        this.region = region;
        this.padding = padding;
        this.priority = priority;
        SlotGroupPanelRegistry.trackPending(this);
    }

    // ── Targeting API ───────────────────────────────────────────────────

    /**
     * Declares the slot-group categories this adapter applies to. Resolution
     * is exact-match — each category named here triggers one render pass per
     * frame on any screen where the category resolves to a non-empty slot
     * list. See M8 §5.2 for why categories are flat tags (no inheritance).
     *
     * <p>Registers the adapter's panel under each (category, region) pair in
     * {@link RegionRegistry}'s slot-group stacking map so multi-adapter
     * stacking within the same (category, region) pair works consistently.
     *
     * <p>Call exactly once per adapter. Duplicate declarations throw
     * {@link IllegalStateException}.
     */
    public SlotGroupPanelAdapter on(SlotGroupCategory... categories) {
        if (targets != null) {
            throw new IllegalStateException(
                    "SlotGroupPanelAdapter for panel '" + panel.getId() +
                    "' already declared targeting. Call .on(...) exactly once.");
        }
        if (categories.length == 0) {
            throw new IllegalArgumentException(
                    "SlotGroupPanelAdapter for panel '" + panel.getId() +
                    "': .on() requires at least one category. SlotGroupContext " +
                    "has no .onAny() — 'any slot group' isn't a meaningful target.");
        }
        this.targets = List.of(categories);
        for (SlotGroupCategory category : this.targets) {
            SlotGroupRegionRegistry.registerSlotGroup(panel, category, region, padding, priority);
        }
        SlotGroupPanelRegistry.markTargetingDeclared(this);
        return this;
    }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Phase 16j R5 — removes this adapter from every internal collection:
     * the per-(category, region) bucket in {@link SlotGroupRegionRegistry},
     * the PENDING + REGISTERED sets in {@link SlotGroupPanelRegistry}, and
     * the per-panel padding metadata. After {@code unregister()} this
     * adapter contributes nothing to layout, dispatch, or rendering.
     * Idempotent.
     */
    public void unregister() {
        SlotGroupRegionRegistry.unregisterSlotGroup(panel);
        SlotGroupPanelRegistry.untrack(this);
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public Panel getPanel() { return panel; }
    public SlotGroupRegion getRegion() { return region; }
    public int getPadding() { return padding; }

    /** Returns declared targets; null before {@link #on} is called. */
    public @Nullable List<SlotGroupCategory> getTargets() { return targets; }

    public boolean isTargetingDeclared() { return targets != null; }

    /** True iff {@code category} is one of this adapter's declared targets. */
    public boolean matches(SlotGroupCategory category) {
        if (targets == null) return false;
        return targets.contains(category);
    }

    /** GUI-scaled window width (Pass 3 screen-edge reference); large fallback
     *  when the window is unavailable so no spurious wrap fires. */
    private static int guiScaledWidth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return Integer.MAX_VALUE / 4;
        return mc.getWindow().getGuiScaledWidth();
    }

    private static int guiScaledHeight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return Integer.MAX_VALUE / 4;
        return mc.getWindow().getGuiScaledHeight();
    }

    /**
     * Returns the panel's screen-space origin for the given slot-group
     * bounds anchored in {@code category}, or empty when the panel is
     * invisible or the region overflows the slot group's extent.
     */
    public Optional<ScreenOrigin> getOrigin(SlotGroupBounds bounds,
                                             SlotGroupCategory category,
                                             AbstractContainerScreen<?> screen) {
        if (!ClientWindowVisibility.panelShown(panel)) return Optional.empty();
        // Pass 3 — feed the screen-edge content-width budget BEFORE measuring,
        // so a slot-group-anchored panel wraps rather than sailing off-screen.
        // Single chokepoint: both render() and the input path call getOrigin.
        int availOuter = SlotGroupRegionMath.availableSlotGroupWidth(
                region, bounds, guiScaledWidth(), RegionConstants.SCREEN_EDGE_MARGIN);
        panel.setAvailableContentWidth(availOuter - 2 * padding);
        int pw = panel.getWidth() + 2 * padding;
        int ph = panel.getHeight() + 2 * padding;
        int prefix = SlotGroupRegionRegistry.axialPrefix(panel, category, region);
        // Stale reference after unregister() — skip this panel this frame.
        if (prefix == RegionRegistry.NOT_REGISTERED) return Optional.empty();
        Optional<ScreenOrigin> result =
                SlotGroupRegionMath.resolveSlotGroup(region, bounds, pw, ph, prefix,
                        guiScaledWidth(), guiScaledHeight());
        if (result.isEmpty()) {
            SlotGroupRegionRegistry.warnSlotGroupOverflowOnce(panel, category, region,
                    pw, ph, prefix, bounds);
        }
        return result;
    }

    // ── Render + input ─────────────────────────────────────────────────

    /**
     * Renders the panel against the given slot-group bounds in
     * {@code category}. Called from
     * {@link SlotGroupPanelRegistry}'s dispatch — once per matching (adapter,
     * category) pair per frame.
     */
    public void render(GuiGraphics graphics, SlotGroupBounds bounds,
                       SlotGroupCategory category,
                       int mouseX, int mouseY,
                       AbstractContainerScreen<?> screen) {
        Optional<ScreenOrigin> originOpt = getOrigin(bounds, category, screen);
        if (originOpt.isEmpty()) return;
        ScreenOrigin origin = originOpt.get();

        int panelWidth = panel.getWidth() + 2 * padding;
        int panelHeight = panel.getHeight() + 2 * padding;

        if (panel.getStyle() != PanelStyle.NONE) {
            PanelRendering.renderPanel(graphics,
                    origin.x(), origin.y(),
                    panelWidth, panelHeight,
                    panel.getStyle());
        }

        RenderContext ctx = new RenderContext(
                graphics, origin.x() + padding, origin.y() + padding,
                mouseX, mouseY);

        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;
            element.render(ctx);
        }

        // Panel-level tooltip — fires over the slot-group panel's outer
        // bounds. Phase 17 addition; matches the ScreenPanelAdapter and
        // MKScreen tooltip wiring.
        panel.maybeQueueTooltip(graphics,
                origin.x(), origin.y(), panelWidth, panelHeight,
                mouseX, mouseY, ctx.hasMouseInput());
    }

    /**
     * Dispatches a mouse click to visible elements. Same padding-inclusive
     * hit-test logic as {@link ScreenPanelAdapter#mouseClicked}. Returns
     * whether any element consumed the click.
     */
    public boolean mouseClicked(SlotGroupBounds bounds, SlotGroupCategory category,
                                double mouseX, double mouseY, int button,
                                AbstractContainerScreen<?> screen) {
        Optional<ScreenOrigin> originOpt = getOrigin(bounds, category, screen);
        if (originOpt.isEmpty()) return false;
        ScreenOrigin origin = originOpt.get();

        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;

        for (PanelElement element : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, element)) continue;

            int sx = contentX + element.getChildX();
            int sy = contentY + element.getChildY();
            if (mouseX < sx || mouseX >= sx + element.getWidth()) continue;
            if (mouseY < sy || mouseY >= sy + element.getHeight()) continue;

            if (element.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}
