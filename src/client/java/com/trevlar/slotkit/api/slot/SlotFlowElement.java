package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.group.SlotGroupId;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lays out a panel's registered slots to fit its width, the way text and elements
 * size to fit. One {@link PanelElement} owns the panel's slots and flows the visible
 * ones into the panel's available content width, wrapping onto new rows below.
 *
 * <h3>Why a flow and not a fixed grid</h3>
 *
 * With slots at fixed {@code (childX, childY)} positions on a declared column count,
 * a panel whose groups reveal separately has to pin a wide fixed width to reserve the
 * largest extent. That leaves empty space when only a narrow group is shown, and
 * slides the panel sideways (a flicker) when a wider group reveals over a narrow one
 * near the screen edge. This element has no fixed grid and no pinned width: the visible slots flow into whatever
 * width the panel has this frame, and the panel fits around the result.
 *
 * <h3>Stable budget, fitted size</h3>
 *
 * {@link #naturalWidth()} reports the width of all slots in one row, which doesn't
 * depend on which groups are revealed. The panel computes
 * {@code contentWidth = min(naturalWidth, ceiling)}. The natural width is almost
 * always larger than the screen-edge ceiling the placement layer sets, so the budget
 * passed to {@link #layoutWithin} is that ceiling, which depends on screen geometry
 * and not on slot visibility, so it stays the same across reveals. The panel
 * therefore never re-runs its cached configuration pass when a group toggles:
 * {@link #getWidth()}, {@link #getHeight()} and {@link #render} recompute the fitted
 * layout from the current visibility every frame against that budget. The panel
 * reports the fitted width, so it stays narrow and never triggers the screen-edge
 * slide, while the flow knows it may grow up to the budget before wrapping. Reveals
 * need no extra handling, and there is no empty space and no flicker.
 *
 * <h3>Client rendering only</h3>
 *
 * The flow positions are presentation only. Each child {@link SlotElement} resolves
 * its live slot by identity and writes its {@code Slot.x/y} every frame, so the real
 * synced slots' identity and sync are untouched; only where vanilla draws and
 * hit-tests them changes. Its slots stay live under the panel through
 * {@link #presentsSlotAt}. The empty cells of a partial last row belong to the panel
 * and are inert.
 */
public final class SlotFlowElement extends AbstractPanelElement {

    private static final int PITCH = CreatedSlots.SLOT_PITCH;

    // A small default budget, so the first frame (before layoutWithin) shows a row of
    // about 9 rather than a single column. The first configuration pass replaces it
    // with the real screen-edge ceiling.
    private static final int DEFAULT_BUDGET = 9 * PITCH;

    // All slots this panel owns, in declared order. A group's slots are contiguous,
    // and the flow runs on across groups (a slot's name and tooltip show its group,
    // not a row break), so a revealed group's slots join the flow and wrap with the
    // rest.
    private final List<SlotElement> slots;

    // How wide the flow may get before wrapping: the panel's screen-edge ceiling,
    // which stays the same across reveals. See the class doc.
    private int budget = DEFAULT_BUDGET;

    private SlotFlowElement(Builder b) {
        super(b);
        this.slots = List.copyOf(b.slots);
    }

    /** A flow of slot elements: {@code SlotFlowElement.builder().addAll(slots).at(x, y).build()}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Every slot of a parity group, in order, flowing to the panel's width: the
     * one-liner for putting a group in a panel. Each slot carries the spec's tooltip.
     */
    public static SlotFlowElement of(ParityGroup group) {
        return builder().addGroup(group).build();
    }

    /** Slots {@code 0} to {@code count - 1} of a group, for an {@code onto} group or one named by id. */
    public static SlotFlowElement of(SlotGroupId.Created group, int count) {
        Builder b = builder();
        for (int i = 0; i < count; i++) b.add(SlotElement.builder().slot(group, i).build());
        return b.build();
    }

    /** Column fill does not apply: the flow hugs its visible slots. */
    @Override public void fillWidth(int width) {}

    /**
     * Stores the panel's content-width budget as the wrap limit. Because
     * {@link #naturalWidth()} reports the larger all-slots width, this budget is the
     * screen-edge ceiling, which stays the same across reveals.
     */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(PITCH, budget);
    }

    /**
     * The width of all slots in one row, deliberately independent of visibility, so
     * the panel's {@code min(naturalWidth, ceiling)} comes out as the stable ceiling
     * (see the class doc). The per-frame fitted width is {@link #getWidth()}.
     */
    @Override
    public int naturalWidth() {
        return Math.max(PITCH, slots.size() * PITCH);
    }

    // Columns that fit in the current budget (at least 1).
    private int columns() {
        return Math.max(1, budget / PITCH);
    }

    // A slot is visible when its group is revealed and it resolves on this screen.
    private int visibleCount() {
        int n = 0;
        for (SlotElement s : slots) if (s.isVisible()) n++;
        return n;
    }

    /** The fitted width: at most {@code columns()} slots per row, narrower when fewer than a full row are shown. */
    @Override
    public int getWidth() {
        int visible = visibleCount();
        if (visible == 0) return 0;
        int cols = Math.min(visible, columns());
        return cols * PITCH;
    }

    /** The fitted height: one row per {@code columns()} visible slots, growing downward. */
    @Override
    public int getHeight() {
        int visible = visibleCount();
        if (visible == 0) return 0;
        int cols = columns();
        int rows = (visible + cols - 1) / cols;
        return rows * PITCH;
    }

    /** Visible only while at least one child slot is, so the panel reserves no space
     *  and draws no frame when every group is hidden. */
    @Override
    public boolean isVisible() {
        if (!super.isVisible()) return false;
        for (SlotElement s : slots) if (s.isVisible()) return true;
        return false;
    }

    /**
     * Not solid, like the slots it hosts (see {@link SlotElement}): on a transparent
     * panel the flow claims nothing, and vanilla resolves the slot under the cursor.
     */
    @Override public boolean isElementOpaque() { return false; }

    /** Whether one of the flow's shown slots is under the point (see {@link SlotElement#presentsSlotAt}). */
    @Override
    public boolean presentsSlotAt(com.trevlar.menukit.api.element.InputContext in) {
        for (SlotElement slot : slots) {
            if (slot.isVisible() && slot.presentsSlotAt(in)) return true;
        }
        return false;
    }

    @Override
    public void render(RenderContext ctx) {
        int cols = columns();
        int originX = ctx.originX() + getChildX();
        int originY = ctx.originY() + getChildY();
        int k = 0;
        for (SlotElement slot : slots) {
            if (!slot.isVisible()) continue;
            int col = k % cols;
            int row = k / cols;
            // Each child renders itself (writes its real slot's x/y, draws the frame,
            // sets its tooltip) at an origin shifted to its cell. The child's own
            // childX/childY are 0, so that origin is its cell on screen; the flow
            // does the positioning. Through MenuKit's dispatch, so a cell wholly outside
            // the clip (a row scrolled out of a ScrollContainer) isn't placed and its
            // slot parks: placed there, it would sit over whatever is below the clip and
            // take its hover.
            ChildDispatch.render(List.of(slot), ctx.at(originX + col * PITCH, originY + row * PITCH));
            k++;
        }
    }

    // Forwarded to the children, so each slot joins the registry the screen hook
    // uses to resolve hover and click.

    @Override
    public void onAttach(Screen screen) {
        for (SlotElement s : slots) s.onAttach(screen);
    }

    @Override
    public void onDetach(Screen screen) {
        for (SlotElement s : slots) s.onDetach(screen);
    }

    /** Builds a {@link SlotFlowElement} over slot elements, in flow order. */
    public static final class Builder extends AbstractPanelElement.Builder<SlotFlowElement, Builder> {
        private final List<SlotElement> slots = new ArrayList<>();

        private Builder() {}

        @Override protected Builder self() { return this; }

        /** Adds one slot element to the flow. */
        public Builder add(SlotElement slot) {
            slots.add(Objects.requireNonNull(slot, "slot"));
            return this;
        }

        /**
         * Adds every slot of a parity group, in order, each with the spec's tooltip.
         * Several groups in one flow run on into each other and wrap together.
         */
        public Builder addGroup(ParityGroup group) {
            var tooltip = group.spec().tooltip();
            for (int i = 0; i < group.count(); i++) {
                SlotElement.Builder slot = SlotElement.builder().slot(group.id(), i);
                if (tooltip != null) slot.tooltip(tooltip);
                add(slot.build());
            }
            return this;
        }

        /** Adds slot elements to the flow, in order. */
        public Builder addAll(List<SlotElement> slots) {
            slots.forEach(this::add);
            return this;
        }

        @Override
        public SlotFlowElement build() {
            return new SlotFlowElement(this);
        }
    }
}
