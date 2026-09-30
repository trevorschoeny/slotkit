package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.core.SlotGroupCategory;
import com.trevlar.menukit.window.Declarations;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Anchors a {@link Panel} to a slot group's box on container screens: a toolbar
 * above the player inventory, a readout beside the hotbar. Each targeted group on
 * the open screen gets its own slot-group host (§0065), whose reference rectangle is
 * that group's box this frame.
 *
 * <pre>{@code
 * Panel toolbar = Panel.builder("mymod:toolbar")
 *         .add(...)
 *         .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
 *         .build();
 * new SlotGroupPanelAdapter(toolbar).on(SlotGroupCategory.PLAYER_INVENTORY);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * The panel declares {@code region(OutsideRegion)} (around the group's box),
 * {@code center()} or {@code pixel(...)}. A group has no screen spots, so
 * {@code screenAnchor}, {@code main()} and unplaced panels are rejected at
 * construction.
 *
 * <h3>Targeting is required</h3>
 * {@link #on} (a category's vanilla group) or {@link #onGroup} (specific groups,
 * created ones included), exactly once. Exact match: categories are flat tags. An
 * adapter with no targeting fails loudly at the first screen open. The panel appears
 * once per targeted group present on the screen.
 *
 * <h3>Claims</h3>
 * A slot-group panel claims by the one rule like every other: an opaque one over a
 * slot makes that slot inert (its hover, click and tooltip).
 *
 * <h3>Frozen after init</h3>
 * Construction, targeting and {@link #unregister()} throw once MenuKit's
 * declarations freeze at client start. Gate at runtime with {@link Panel#showWhen}.
 */
public final class SlotGroupPanelAdapter {

    /** Default content padding for styled panels. */
    public static final int DEFAULT_PADDING = ScreenPanelAdapter.DEFAULT_PADDING;

    private final Panel panel;
    private final int padding;
    private final String modId;
    private final int seq;

    /** Declared target groups; null until targeting is declared. */
    private @Nullable List<SlotGroupId> targets = null;

    /**
     * Declares the panel with its own interior padding ({@code 0} for
     * {@link PanelStyle#NONE}, {@link #DEFAULT_PADDING} otherwise).
     *
     * @throws IllegalArgumentException if a slot group cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public SlotGroupPanelAdapter(Panel panel) {
        this(panel, panel.interiorPadding());
    }

    /**
     * Declares the panel with explicit content padding.
     *
     * @throws IllegalArgumentException if a slot group cannot place the panel's position
     * @throws IllegalStateException    after MenuKit's declarations froze
     */
    public SlotGroupPanelAdapter(Panel panel, int padding) {
        Declarations.requireOpen("SlotGroupPanelAdapter(" + panel.getId() + ")");
        PanelHost.requireSupported(PanelHost.Kind.SLOT_GROUP, panel, panel.getPosition());
        this.panel = panel;
        this.padding = padding;
        this.modId = PanelHost.captureCallerModId();
        this.seq = PanelHost.nextSeq();
        ScreenPanelRegistry.declare(this);
    }

    // ── Targeting ───────────────────────────────────────────────────────

    /** Anchors to each category's vanilla group. Call exactly once. */
    public SlotGroupPanelAdapter on(SlotGroupCategory... categories) {
        if (categories.length == 0) {
            throw new IllegalArgumentException("SlotGroupPanelAdapter for panel '" + panel.getId()
                    + "': .on() requires at least one category.");
        }
        SlotGroupId[] ids = new SlotGroupId[categories.length];
        for (int i = 0; i < categories.length; i++) ids[i] = SlotGroupId.category(categories[i]);
        return onGroup(ids);
    }

    /**
     * Anchors to specific slot groups, including ones MenuKit: Containers created
     * (whose identity is their {@code (panelId, groupId)}). Call exactly once.
     */
    public SlotGroupPanelAdapter onGroup(SlotGroupId... groups) {
        Declarations.requireOpen("SlotGroupPanelAdapter(" + panel.getId() + ") targeting");
        if (targets != null) {
            throw new IllegalStateException("SlotGroupPanelAdapter for panel '" + panel.getId()
                    + "' already declared targeting. Call .on(...) exactly once.");
        }
        if (groups.length == 0) {
            throw new IllegalArgumentException("SlotGroupPanelAdapter for panel '" + panel.getId()
                    + "': .onGroup() requires at least one group.");
        }
        this.targets = List.of(groups);
        return this;
    }

    // ── Teardown ────────────────────────────────────────────────────────

    /**
     * Withdraws this adapter from screens opened from now on. Idempotent. Throws after
     * MenuKit's declarations froze; gate at runtime with {@link Panel#showWhen}.
     */
    public void unregister() {
        Declarations.requireOpen("SlotGroupPanelAdapter(" + panel.getId() + ").unregister()");
        ScreenPanelRegistry.withdraw(this);
    }

    // ── Accessors ──────────────────────────────────────────────────────

    /** The panel this adapter declares. */
    public Panel getPanel() { return panel; }

    /** The content padding inside the panel's edge. */
    public int getPadding() { return padding; }

    /** The declared target groups, or {@code null} before targeting. */
    public @Nullable List<SlotGroupId> getTargets() { return targets; }

    /** Whether targeting was declared. */
    public boolean isTargetingDeclared() { return targets != null; }

    /** Whether {@code group} is one of this adapter's targets. */
    public boolean matches(SlotGroupId group) {
        return targets != null && targets.contains(group);
    }

    String modId() { return modId; }

    int seq() { return seq; }
}
