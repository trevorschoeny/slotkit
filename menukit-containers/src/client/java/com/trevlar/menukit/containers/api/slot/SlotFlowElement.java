package com.trevlar.menukit.containers.api.slot;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The reactive slot-wrapping primitive, the slot analogue of element/text
 * reactive sizing (Movement ④). One {@link PanelElement} that owns a panel's
 * registered slots and FLOWS the currently-visible ones into the panel's
 * available content width, wrapping to new rows that grow downward.
 *
 * <h3>Why a flow, not a fixed grid</h3>
 *
 * Historically a {@link ContainerPanel}'s slots were baked at fixed
 * {@code (childX, childY)} positions on a consumer-declared column count, and a
 * panel whose groups reveal independently had to pin a wide fixed width to
 * reserve the maximal extent, leaving empty space when only a narrow group was
 * up, and sliding sideways (the "flash") when a wider group revealed over a
 * narrow one near the screen edge. This element replaces that: there is no fixed
 * grid and no pinned width. The visible slots simply flow into whatever width
 * the panel has this frame and the panel hugs the result.
 *
 * <h3>Stable budget, reactive hug</h3>
 *
 * The flow's {@link #naturalWidth()} reports the ALL-slots single-row width, a
 * value that does not depend on which groups are revealed. The owning panel
 * computes {@code contentWidth = min(naturalWidth, ceiling)}; because the natural
 * width is (almost always) larger than the screen-edge ceiling the placement
 * layer imposes, the budget handed to {@link #layoutWithin} is that ceiling, a
 * value that is stable across reveals (it depends on screen geometry, not slot
 * visibility). So the panel never has to re-run its (cached) configuration pass
 * when a group toggles: {@link #getWidth()}, {@link #getHeight()} and
 * {@link #render} recompute the hug layout from the CURRENT visibility every
 * frame against that stable budget. The panel then reports the hug width (so it
 * stays narrow → never trips the screen-edge slide), while the flow internally
 * knows it MAY grow up to the budget before wrapping. Reactive to reveals for
 * free, no empty space, no flash.
 *
 * <h3>Client-render-only (§0055)</h3>
 *
 * The flow positions are pure presentation: each child {@link SlotElement}
 * resolves its live slot by identity and writes its {@code Slot.x/y} every frame,
 * so the real synced slots' identity/sync are untouched, only where vanilla
 * draws and hit-tests them reflows. Its slots stay live under the panel's own
 * claim through {@link #presentsSlotAt}; the empty cells of a partial last row are
 * the panel's, and inert.
 */
public final class SlotFlowElement extends AbstractPanelElement {

    private static final int PITCH = CreatedSlots.SLOT_PITCH;

    /** A small default budget so frame 0 (before the first layoutWithin) reads
     *  as a sane ~9-wide row rather than a single column. Overwritten by the
     *  real screen-edge ceiling on the first configuration pass. */
    private static final int DEFAULT_BUDGET = 9 * PITCH;

    /** All slots this panel owns, in declared order. A group's slots are a
     *  contiguous run; the flow is continuous across groups (group identity is
     *  carried by each slot's name/tooltip, not by a row break), so a revealed
     *  group's slots simply join the stream and wrap with everything else. */
    private final List<SlotElement> slots;

    /** The width the flow may occupy before wrapping, the panel's stable
     *  screen-edge ceiling. See the class doc. */
    private int budget = DEFAULT_BUDGET;

    private SlotFlowElement(Builder b) {
        super(b);
        this.slots = List.copyOf(b.slots);
    }

    /** A flow of slot elements: {@code SlotFlowElement.builder().addAll(slots).at(x, y).build()}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Column fill does not apply: the flow hugs its visible slots. */
    @Override public void fillWidth(int width) {}

    /**
     * The panel hands this the content-width budget; we store it as the wrap
     * ceiling. Because {@link #naturalWidth()} reports the (larger) all-slots
     * width, this budget is the screen-edge ceiling, stable across reveals.
     */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(PITCH, budget);
    }

    /**
     * All-slots single-row width, intentionally visibility-INDEPENDENT, so the
     * panel's {@code min(naturalWidth, ceiling)} resolves to the stable ceiling
     * (see class doc). Never the per-frame hug width, that is {@link #getWidth()}.
     */
    @Override
    public int naturalWidth() {
        return Math.max(PITCH, slots.size() * PITCH);
    }

    /** Columns that fit in the current budget (≥ 1). */
    private int columns() {
        return Math.max(1, budget / PITCH);
    }

    /** Visible slot count this frame (a slot is visible when its group is
     *  revealed and it resolves on the current screen). */
    private int visibleCount() {
        int n = 0;
        for (SlotElement s : slots) if (s.isVisible()) n++;
        return n;
    }

    /** Hug width: the visible slots laid into at most {@code columns()} per row,
     *  hugging the content when fewer than a full row are shown. */
    @Override
    public int getWidth() {
        int visible = visibleCount();
        if (visible == 0) return 0;
        int cols = Math.min(visible, columns());
        return cols * PITCH;
    }

    /** Hug height: one row per {@code columns()} visible slots, growing downward. */
    @Override
    public int getHeight() {
        int visible = visibleCount();
        if (visible == 0) return 0;
        int cols = columns();
        int rows = (visible + cols - 1) / cols;
        return rows * PITCH;
    }

    /** Visible only while at least one child slot is visible, so the owning
     *  panel reserves no space (and draws no frame) when every group is hidden. */
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
            // Each child renders itself (writes its real slot's x/y, draws the
            // frame, sets its tooltip) at a per-cell shifted
            // origin. The child's own childX/childY are 0, so the shifted
            // origin IS its on-screen cell, the flow owns positioning.
            slot.render(ctx.at(originX + col * PITCH, originY + row * PITCH));
            k++;
        }
    }

    // ── Registry lifecycle, forward to children so each registered slot joins
    //    the active-slot registry the screen hook resolves hover/click through ──

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
