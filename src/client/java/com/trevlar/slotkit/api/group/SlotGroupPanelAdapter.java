package com.trevlar.slotkit.api.group;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.WrapsPanelAdapter;
import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anchors a {@link Panel} to a slot group's box on container screens: a toolbar
 * above the player inventory, a readout beside the hotbar. Built on MenuKit's anchor
 * context: each targeted group is one {@link Anchor} whose box is that group's
 * slots on the open screen this frame, so panels on one group share a host and stack.
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

    // One anchor per group id: MenuKit refuses a second Anchor.of with the same id,
    // and panels on one anchor share its host, which is what stacks them. Declared
    // on first use, always before the freeze (adapters are declarations too).
    private static final Map<SlotGroupId, Anchor> ANCHORS = new ConcurrentHashMap<>();

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
    }

    /** Anchors to each category's vanilla group. Call exactly once. */
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
     * their {@code (panelId, groupId)}). Call exactly once.
     */
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
        for (int i = 0; i < groups.length; i++) anchors[i] = anchorFor(groups[i]);
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

    /** The anchor for a group: one per id, declared on first use. */
    private static Anchor anchorFor(SlotGroupId group) {
        return ANCHORS.computeIfAbsent(group, g -> Anchor.of(anchorId(g), screen -> box(screen, g)));
    }

    /** An anchor id per group, readable in a log: {@code slotkit:group/<sanitised group id>}. */
    private static Identifier anchorId(SlotGroupId group) {
        String path = group.asString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return Identifier.fromNamespaceAndPath("slotkit", "group/" + path);
    }

    /**
     * One slot group's box: the rectangle around its slots' 18x18 frames this frame
     * (re-resolved every time, since creative tab switches and dynamic menus change
     * {@code menu.slots}), or {@code null} when the group is not on this screen right
     * now. Each group gets its own box: a created group declaring a vanilla category
     * never stretches that category's box.
     *
     * <p>The frame, not the 16x16 item box: vanilla lays slots out on an 18 px pitch,
     * so a created slot (itself an 18x18 frame) stacked flush on this box with
     * {@code gap(0)} lands exactly one pitch away, on the grid. Measuring from the item
     * box, with MenuKit's 2 px gap, put it 1 to 3 px off.
     */
    private static @Nullable Reference box(Screen screen, SlotGroupId id) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        for (ResolvedSlotGroup group : SlotGroupCategories.groups(acs.getMenu())) {
            if (!group.id().equals(id) || group.slots().isEmpty()) continue;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Slot slot : group.slots()) {
                // A slot's frame is its item box grown by 1 px on every side.
                minX = Math.min(minX, slot.x - 1);
                minY = Math.min(minY, slot.y - 1);
                maxX = Math.max(maxX, slot.x + 17);
                maxY = Math.max(maxY, slot.y + 17);
            }
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
            return new Reference(acc.sk$getLeftPos() + minX, acc.sk$getTopPos() + minY,
                    maxX - minX, maxY - minY);
        }
        return null;
    }
}
