package com.trevlar.slotkit.api.slot;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.core.SlotElementRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One slot group as one element, found by its id on whatever menu is open, and shown
 * in rows. {@code SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE))}
 * shows a single chest's 27 slots, a double chest's 54, or a barrel's, with no
 * menu-type indices. A created group's id ({@code group.id()}, or
 * {@code SlotGroupId.parse("created|othermod:panel|group")} for another mod's group,
 * with no compile dependency on that mod) shows that group.
 *
 * <p>The group is resolved through {@code SlotGroupCategories.groups(menu)}, so the
 * element sees exactly what any mod sees: vanilla's groups, groups another mod's
 * resolver adds, and every created group. It moves slots on screen only: no slot is
 * added and {@code menu.slots} is unchanged, so the group stays its own container to
 * vanilla and to other mods.
 *
 * <p>The element is shown only while at least one of the group's slots is: hidden on a
 * menu without the group, while a created group isn't revealed or its panel is hidden,
 * and when every slot is past the group's active count. It packs the shown slots, so a
 * created group past its active count draws only its open slots.
 *
 * <p>A panel may hold several of these, for one mod's groups or several mods'; each
 * stays its own container. One group is shown by one element at a time on a screen: a
 * second element showing the same group draws nothing and logs an error once.
 *
 * <p>Since 2.1.0.
 */
public final class SlotGroupElement extends AbstractPanelElement {

    private static final int PITCH = CreatedSlots.SLOT_PITCH;

    private final SlotGroupId group;
    private final int columns;

    // One slot element per index seen so far: grown to the largest count the group has
    // had on any menu (27 on a single chest, 54 on a double), the first count of them shown.
    private final List<SlotElement> slots = new ArrayList<>();
    // The screen the element is attached to, so a slot element added later attaches too.
    private @Nullable Screen attached;
    // How wide the rows may get: the panel's budget, at most the declared columns.
    private int budget = Integer.MAX_VALUE;
    // Whether this element has logged that another element already shows its group.
    private boolean warnedTwice = false;

    private SlotGroupElement(Builder b) {
        super(b);
        this.group = b.group;
        this.columns = b.columns;
    }

    /** The group with {@code id}, in rows of nine (a vanilla container's width). */
    public static SlotGroupElement of(SlotGroupId id) {
        return builder(id).build();
    }

    /** A builder, for {@code columns(n)} and the usual element methods ({@code at}, {@code visibleWhen}). */
    public static Builder builder(SlotGroupId id) {
        return new Builder(id);
    }

    /** The group this element shows. */
    public SlotGroupId group() { return group; }

    // The group's slot count on the open menu, growing the slot elements to match.
    private int count() {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs)) return 0;
        ResolvedSlotGroup resolved = SlotElementRegistry.group(acs.getMenu(), group);
        int n = resolved == null ? 0 : resolved.slots().size();
        while (slots.size() < n) {
            SlotElement slot = SlotElement.builder().slot(group, slots.size()).build();
            if (attached != null) slot.onAttach(attached);
            slots.add(slot);
        }
        return n;
    }

    // The shown slots, in group order: the first count() elements that are visible.
    private List<SlotElement> shown() {
        int n = count();
        List<SlotElement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) if (slots.get(i).isVisible()) out.add(slots.get(i));
        return out;
    }

    private int cols() {
        return Math.max(1, Math.min(columns, budget / PITCH));
    }

    @Override public void layoutWithin(int budget) { this.budget = Math.max(PITCH, budget); }

    /** Column fill does not apply: the rows hug the shown slots. */
    @Override public void fillWidth(int width) {}

    /** A full row's width, the same whichever slots are shown, so the panel's budget stays stable. */
    @Override public int naturalWidth() { return columns * PITCH; }

    @Override
    public int getWidth() {
        int n = shown().size();
        return n == 0 ? 0 : Math.min(n, cols()) * PITCH;
    }

    @Override
    public int getHeight() {
        int n = shown().size();
        int c = cols();
        return n == 0 ? 0 : ((n + c - 1) / c) * PITCH;
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && !shown().isEmpty();
    }

    /** Not solid, like the slots it shows (see {@link SlotElement}). */
    @Override public boolean isElementOpaque() { return false; }

    @Override
    public boolean presentsSlotAt(com.trevlar.menukit.api.element.InputContext in) {
        for (SlotElement slot : shown()) if (slot.presentsSlotAt(in)) return true;
        return false;
    }

    @Override
    public void render(RenderContext ctx) {
        // One group, one element per screen: a second one draws nothing, and says so once.
        Object holder = SlotElementRegistry.claimGroup(group, this);
        if (holder != null) {
            if (!warnedTwice) {
                warnedTwice = true;
                org.slf4j.LoggerFactory.getLogger("SlotKit").error(
                        "Slot group {} is shown by two elements on one screen; only the first draws it. "
                        + "Show a group in one panel at a time (gate the others with visibleWhen).", group);
            }
            return;
        }
        int c = cols();
        int originX = ctx.originX() + getChildX();
        int originY = ctx.originY() + getChildY();
        int k = 0;
        for (SlotElement slot : shown()) {
            // Through MenuKit's dispatch, as SlotFlowElement does: a cell wholly outside
            // the clip isn't placed, so its slot parks instead of taking the hover below.
            ChildDispatch.render(List.of(slot), ctx.at(originX + (k % c) * PITCH, originY + (k / c) * PITCH));
            k++;
        }
    }

    /** Culled with its section: every shown slot is held, so it parks. */
    @Override
    public void renderCulled(RenderContext ctx) {
        if (SlotElementRegistry.claimGroup(group, this) == null) ChildDispatch.renderCulled(shown(), ctx);
    }

    @Override
    public void onAttach(Screen screen) {
        attached = screen;
        warnedTwice = false;
        for (SlotElement s : slots) s.onAttach(screen);
    }

    @Override
    public void onDetach(Screen screen) {
        for (SlotElement s : slots) s.onDetach(screen);
        attached = null;
    }

    /** Builds a {@link SlotGroupElement}. */
    public static final class Builder extends AbstractPanelElement.Builder<SlotGroupElement, Builder> {
        private final SlotGroupId group;
        private int columns = 9;

        private Builder(SlotGroupId group) {
            this.group = Objects.requireNonNull(group, "group");
        }

        @Override protected Builder self() { return this; }

        /** Slots per row, 9 by default. Fewer show when the panel is narrower. */
        public Builder columns(int columns) {
            if (columns < 1) throw new IllegalArgumentException("columns must be at least 1, got " + columns);
            this.columns = columns;
            return this;
        }

        @Override
        public SlotGroupElement build() {
            return new SlotGroupElement(this);
        }
    }
}
