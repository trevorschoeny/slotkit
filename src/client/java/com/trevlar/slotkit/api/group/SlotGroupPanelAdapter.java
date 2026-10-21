package com.trevlar.slotkit.api.group;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.WrapsPanelAdapter;
import com.trevlar.slotkit.core.SlotAnchors;


import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Anchors a {@link Panel} to slot groups and slots on container screens: a toolbar
 * above the player inventory, a readout beside the hotbar, a slot above the offhand.
 * Built on MenuKit's anchors: each group or slot is one {@link Anchor} whose box is on
 * the open screen this frame, so panels on one group share a host and stack.
 *
 * <pre>{@code
 * Panel toolbar = Panel.builder("mymod:toolbar")
 *         .add(...)
 *         .in(ModeScope.BOTH, SlotPlacement.group(SlotGroupCategory.PLAYER_INVENTORY,
 *                 PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT)))
 *         .build();
 * new SlotGroupPanelAdapter(toolbar);
 * }</pre>
 *
 * <h3>Placement is the panel's</h3>
 * Since 2.1.0 the panel's placements say where it goes, one per mode scope:
 * {@link com.trevlar.slotkit.api.slot.SlotPlacement#group} around a group's box,
 * {@link com.trevlar.slotkit.api.slot.SlotPlacement#slot} at an offset from one slot.
 * The two modes can differ entirely. The panel shows where its current mode's group or
 * slot is on the open screen, and nowhere else. A group or slot has no screen spots, so
 * {@code screenAnchor}, {@code main()} and unplaced panels are rejected at construction.
 *
 * <h3>Targeting</h3>
 * Nothing besides the placements. The deprecated {@link #on} and {@link #onGroup} put
 * the panel's screen placements around each named group instead, in both modes, and
 * show it once per targeted group present on the screen.
 *
 * <h3>Claims</h3>
 * A slot-group panel claims points by the same rule as every other panel: an opaque
 * one over a slot makes that slot inert (its hover, click and tooltip).
 *
 * <h3>Frozen after init</h3>
 * Construction, targeting and {@link #unregister()} throw once MenuKit's
 * declarations freeze at client start. Gate at runtime with {@link Panel#visibleWhen}.
 */
@WrapsPanelAdapter
public final class SlotGroupPanelAdapter {

    /** Default content padding for styled panels. */
    public static final int DEFAULT_PADDING = AnchorPanelAdapter.DEFAULT_PADDING;

    private final AnchorPanelAdapter anchored;

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
        this.anchored = new AnchorPanelAdapter(panel, padding);
        warnIfSlotPanelKeepsHostGap(panel);
    }

    /**
     * A slot panel on a group keeps vanilla's 18 px pitch only at {@code gap(0)}: the
     * group's box is its slots' frames, and the host's default gap would set the
     * panel's slots 2 px off the group's. The gap is the panel's to declare, so this
     * only warns, once per panel; a panel without slots (a toolbar) keeps the host gap.
     */
    private static void warnIfSlotPanelKeepsHostGap(Panel panel) {
        // Only a region stacks against the box with a gap; an at(...) placement sits at its offset.
        boolean keepsGap = panel.placements().values().stream().anyMatch(p ->
                p.position().mode() == com.trevlar.menukit.api.panel.PanelPosition.Mode.REGION
                        && p.position().gap() == com.trevlar.menukit.api.panel.PanelPosition.HOST_GAP);
        if (!keepsGap) return;
        for (var element : panel.getRawElements()) {
            if (element instanceof com.trevlar.slotkit.api.slot.SlotElement
                    || element instanceof com.trevlar.slotkit.api.slot.SlotFlowElement) {
                org.slf4j.LoggerFactory.getLogger("SlotKit").warn(
                        "Panel '{}' shows slots on a slot group but leaves its gap unset, so its slots sit "
                        + "2 px off the group's 18 px pitch. Declare .gap(0) on its PanelPosition.", panel.id());
                return;
            }
        }
    }

    /**
     * Anchors to each category's vanilla group, in both modes. Call at most once.
     *
     * @deprecated since 2.1.0, the group is part of the panel's placement:
     *             {@code in(ModeScope.BOTH, SlotPlacement.group(category, position))}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public SlotGroupPanelAdapter on(SlotGroupCategory... categories) {
        if (categories.length == 0) {
            throw new IllegalArgumentException("SlotGroupPanelAdapter for panel '" + getPanel().id()
                    + "': .on() requires at least one category.");
        }
        SlotGroupId[] ids = new SlotGroupId[categories.length];
        for (int i = 0; i < categories.length; i++) ids[i] = SlotGroupId.category(categories[i]);
        return onGroup(ids);
    }

    /**
     * Anchors to specific slot groups, including created ones (whose identity is
     * their {@code (panelId, groupId)}), in both modes. Call at most once.
     *
     * @deprecated since 2.1.0, the group is part of the panel's placement:
     *             {@code in(ModeScope.BOTH, SlotPlacement.group(group, position))}.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    @SuppressWarnings("removal")   // MenuKit's AnchorPanelAdapter.on, which this keeps working
    public SlotGroupPanelAdapter onGroup(SlotGroupId... groups) {
        Declarations.requireOpen("SlotGroupPanelAdapter(" + getPanel().id() + ") targeting");
        if (targets != null) {
            throw new IllegalStateException("SlotGroupPanelAdapter for panel '" + getPanel().id()
                    + "' already declared targeting. Call .on(...) exactly once.");
        }
        if (groups.length == 0) {
            throw new IllegalArgumentException("SlotGroupPanelAdapter for panel '" + getPanel().id()
                    + "': .onGroup() requires at least one group.");
        }
        this.targets = List.of(groups);
        Anchor[] anchors = new Anchor[groups.length];
        for (int i = 0; i < groups.length; i++) anchors[i] = SlotAnchors.group(groups[i]);
        anchored.on(anchors);
        return this;
    }

    /**
     * Withdraws this adapter from screens opened from now on. Idempotent. Throws after
     * MenuKit's declarations froze; gate at runtime with {@link Panel#visibleWhen}.
     */
    public void unregister() {
        anchored.unregister();
    }

    /** The panel this adapter declares. */
    public Panel getPanel() { return anchored.getPanel(); }

    /** The content padding inside the panel's edge. */
    public int getPadding() { return anchored.getPadding(); }

    /** The declared target groups, or {@code null} before targeting. */
    public @Nullable List<SlotGroupId> getTargets() { return targets; }

    /** Whether targeting was declared. */
    public boolean isTargetingDeclared() { return targets != null; }

    /** Whether {@code group} is one of this adapter's targets. */
    public boolean matches(SlotGroupId group) {
        return targets != null && targets.contains(group);
    }
}
