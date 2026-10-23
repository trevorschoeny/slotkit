package com.trevlar.slotkit.api.group;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.slotkit.core.SlotAnchors;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One panel on every group of a category that shows on the open menu, vanilla or created,
 * on-demand instances included. Client only.
 * {@link com.trevlar.slotkit.api.slot.SlotPlacement#group(SlotGroupCategory, PanelPosition)}
 * reaches only the category's vanilla group; this reaches a chest and the shulker box and
 * ender chest a mod opens beside it, each with its own panel.
 *
 * <pre>{@code
 * CategoryPanels.declare(SlotGroupCategory.SHULKER_STORAGE, on -> Panel.builder("mymod:sort/shulker/" + on.index())
 *         .add(sortButton(() -> on.group()))     // the group this panel sits on, this frame
 *         .style(PanelStyle.NONE)
 *         .in(ModeScope.BOTH, on.around(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT)))
 *         .build());
 * }</pre>
 *
 * <h3>Which group a panel is on</h3>
 * The groups of the category with slots on screen this frame are counted in
 * {@link SlotGroupCategories#groups} order: the vanilla group first, then created groups in
 * menu order. Panel {@code i} sits on the {@code i}-th, and {@link On#group()} names it. A
 * group whose slots are all hidden (a tab not selected, parked, inert) isn't counted, so it
 * gets no panel, and when a tab switches the panel moves to the group now shown. Read
 * {@code group()} when acting (a click), not once: which group it is can change while the
 * screen is up.
 *
 * <h3>Declare at init</h3>
 * {@link #declare} builds and declares {@link #MAX_SHOWN} panels, before MenuKit's
 * declarations freeze, so its factory runs at init and each panel needs its own id
 * ({@link On#index()} in it).
 */
public final class CategoryPanels {

    private CategoryPanels() {}

    /**
     * How many groups of one category get a panel at once on one screen.
     */
    // ponytail: a fixed pool, since MenuKit builds a screen's anchor hosts when it opens and
    // groups come later; a fifth shown group of one category goes without. Upgrade path: an
    // anchor with any number of boxes in MenuKit.
    public static final int MAX_SHOWN = 4;

    /** Where one of a category's panels goes, and which group it is on. */
    public static final class On {
        private final SlotGroupCategory category;
        private final int index;

        private On(SlotGroupCategory category, int index) {
            this.category = category;
            this.index = index;
        }

        /** The category. */
        public SlotGroupCategory category() { return category; }

        /** This panel's number, {@code 0} to {@code MAX_SHOWN - 1}: the count of shown groups before its own. */
        public int index() { return index; }

        /** Around the box of the group this panel is on, as {@code SlotPlacement.group} places around one group. */
        public Placement around(PanelPosition position) {
            return Placement.around(SlotAnchors.shownGroup(category, index), Objects.requireNonNull(position, "position"));
        }

        /** The group this panel is on, on the open screen this frame, or {@code null} where it isn't showing. */
        public @Nullable ResolvedSlotGroup group() {
            SlotAnchors.Shown shown = SlotAnchors.shown(Minecraft.getInstance().gui.screen(), category, index);
            return shown == null ? null : shown.group();
        }
    }

    /**
     * Builds {@link #MAX_SHOWN} panels with {@code panel}, one per {@link On}, and declares
     * each with a {@link SlotGroupPanelAdapter}. A panel shows where its own {@code On}'s
     * placement puts it; one that never uses {@code on.around} isn't on the category at all.
     *
     * @return the adapters, in index order
     * @throws IllegalStateException after MenuKit's declarations froze
     */
    public static List<SlotGroupPanelAdapter> declare(SlotGroupCategory category, Function<On, Panel> panel) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(panel, "panel");
        List<SlotGroupPanelAdapter> out = new ArrayList<>(MAX_SHOWN);
        for (int i = 0; i < MAX_SHOWN; i++) out.add(new SlotGroupPanelAdapter(panel.apply(new On(category, i))));
        return List.copyOf(out);
    }
}
