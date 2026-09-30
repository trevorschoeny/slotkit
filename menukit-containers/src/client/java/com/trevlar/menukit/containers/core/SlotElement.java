package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.inject.SlotGroupId;

import com.trevlar.menukit.core.AbstractPanelElement;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.RenderContext;
import com.trevlar.menukit.core.SlotRendering;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotPositionAccessor;
import com.trevlar.menukit.window.Address;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A registered MKC slot presented as a {@link PanelElement} — the keystone of
 * the everything-is-a-panel model. A slot is just another element you drop into a
 * panel, alongside buttons and labels.
 *
 * <h3>What this element does, and what it deliberately does not</h3>
 *
 * A slot has two separable layers:
 * <ul>
 *   <li><b>Registration</b> — a real, server-synced {@link MKCSlot} in
 *       {@code menu.slots}. Built at menu-construction time via {@link MKCSlots};
 *       <em>untouched by this class</em>.</li>
 *   <li><b>Placement</b> — where it sits on screen. <em>This is what
 *       {@code SlotElement} owns.</em> Each frame, in layer 2 of
 *       {@code ContainerScreenLayers}, it resolves
 *       its panel-relative position to screen space and writes it into the live
 *       slot's own {@code Slot.x/y}. (Layer 2: after vanilla has drawn the
 *       vanilla slots, before it draws the created ones.) It also draws the recessed 18×18 frame,
 *       which is panel chrome.</li>
 * </ul>
 *
 * It does <b>not</b> draw the item, the count, the durability bar, the hover
 * highlight, or a ghost icon. <b>Vanilla does</b> — its {@code extractSlots} walks
 * {@code menu.slots} and draws this slot at the {@code x/y} written here, through
 * the same {@code extractSlot} call it uses for a vanilla slot; its
 * {@code getHoveredSlot} finds it the same way. That is the presentation half of
 * {@link MKCSlot}'s substitutability contract: a third party's slot hook, or any
 * mod reading {@code slot.x/y}, sees a created slot and a vanilla slot as the same
 * thing because there is no MenuKit slot pass for them to differ in. (This is the
 * model {@code MKCHandledScreen} has always used on MenuKit's own screens.)
 *
 * <h3>Located, not held — the menu-attach lifecycle</h3>
 *
 * A {@code SlotElement} does <em>not</em> hold a slot reference; it holds the
 * slot's stable {@link Address} and resolves the live in-menu slot from the
 * current screen's menu each frame (through {@link CreatedSlotAdapter}'s
 * session-cached binding). This is what lets one statically-registered panel work
 * on every screen: the survival inventory and the creative screen carry
 * <em>different</em> slot instances for the same logical slot (creative wraps it in
 * a {@code SlotWrapper} and projects it onto a throwaway menu), and a held
 * reference would bind to one and be wrong on the other. The position is written
 * to whichever instance is in {@code menu.slots} — the wrapper on creative —
 * because that is the one vanilla draws.
 *
 * <h3>Parked when not presented</h3>
 *
 * At the end of each frame ({@link SlotElementRegistry#parkUnpresented}) every
 * attached element whose panel did not render it this frame moves its slot
 * off-screen. So a slot whose panel is hidden, out of region, or hidden by the
 * window is at a position vanilla neither draws nor hit-tests next frame, rather
 * than wherever it was last drawn; one that was presented keeps its position, so
 * hover between frames agrees with the picture. ({@link MKCSlot#isActive}
 * covers the panel-hidden case on its own; parking covers the rest.)
 *
 * <p>An element in an <b>overlay</b>-positioned panel (layer 4) writes its
 * position after vanilla's slot pass, so vanilla draws it one frame late and
 * under the overlay's chrome. Created slots belong in flow panels; nothing
 * places them in overlays today.
 *
 * <h3>Why a slot stays live under its own panel's claim</h3>
 *
 * A slot's item interaction is owned by vanilla: a click flows through
 * {@code AbstractContainerScreen.mouseClicked} → {@code slotClicked(getHoveredSlot())},
 * and MenuKit's library-owned {@code getHoveredSlot} interception
 * ({@link MKCSlotInput}) makes the registered slot win over a vanilla slot it
 * covers. An opaque panel claims its whole rectangle (§0065: no holes), so the
 * claim routes a point over this element's slot back to vanilla
 * ({@link #presentsSlotAt}): the panel's own slot hovers and clicks, while
 * anything else under the panel stays inert. On a transparent panel the slot is
 * simply not solid ({@link #isElementOpaque()} is {@code false}), so it claims
 * nothing itself.
 * {@link SlotElementRegistry} tells the library's screen hook which panels
 * currently host a {@code SlotElement}.
 */
public final class SlotElement extends AbstractPanelElement {

    /** Off-screen: where a slot sits when no panel is presenting it this frame. */
    private static final int PARKED = MKCSlots.OFFSCREEN;

    private final String panelId;
    private final Address address;

    /** Whether a panel presented (placed) this element's slot this frame. Render thread only. */
    private boolean presented = false;

    private SlotElement(Builder b) {
        super(b);
        this.panelId = b.group.panelId();
        this.address = Address.createdSlot(b.group, b.index);
        this.width = SlotRendering.DEFAULT_SIZE;
        this.authoredWidth = SlotRendering.DEFAULT_SIZE;
        this.height = SlotRendering.DEFAULT_SIZE;
    }

    /**
     * A slot element: {@code SlotElement.builder().slot(MKCSlots.groupId(panel, group), i).at(x, y).build()}.
     * The shared vocabulary applies: {@code at}, {@code visibleWhen} (on top of the
     * slot's own reveal), {@code tooltip} (shown over an empty slot; a slot with an item
     * shows the item's). A slot is never solid, so {@code opaque} has no effect.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** The registered slot's panel id this element presents (its registry key). */
    public String panelId() { return panelId; }

    /** The element's size is the slot's frame and never shrinks. */
    @Override public void layoutWithin(int budget) {}

    /** The element's size is the slot's frame. */
    @Override public void fillWidth(int width) {}

    /**
     * Not solid: on a transparent panel a slot claims nothing itself; vanilla's
     * slot machinery owns its interaction. (On an opaque panel the panel claims its
     * whole rectangle, and {@link #presentsSlotAt} keeps this slot live under it.)
     */
    @Override public boolean isElementOpaque() { return false; }

    /**
     * Whether this element's live slot is under the point: vanilla's hover cell (the
     * 18x18 frame around the item box) at the slot's current position. The claiming
     * panel routes such a point to the slot (§0065), so a panel's own slots hover,
     * click and tooltip while everything else it covers is inert.
     */
    @Override
    public boolean presentsSlotAt(com.trevlar.menukit.core.InputContext in) {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs)) return false;
        Slot slot = presented(acs.getMenu());
        if (slot == null) return false;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        double relX = in.mouseX() - acc.mk$getLeftPos();
        double relY = in.mouseY() - acc.mk$getTopPos();
        return relX >= slot.x - 1 && relX < slot.x + 17 && relY >= slot.y - 1 && relY < slot.y + 17;
    }

    /** Hidden by its own {@code visibleWhen}, when the slot can't be resolved on this screen, or when its panel is hidden. */
    @Override
    public boolean isVisible() {
        return super.isVisible() && presented(currentMenu()) != null;
    }

    @Override
    public void render(RenderContext ctx) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
        Slot slot = presented(acs.getMenu());
        if (slot == null) return;

        int screenX = ctx.originX() + getChildX();
        int screenY = ctx.originY() + getChildY();

        // Place the live slot where the panel put this element, in vanilla's
        // slot coordinate space (frame-relative; x/y name the 16×16 item box,
        // which sits ITEM_INSET inside the 18×18 frame). Vanilla's hover
        // resolution and slot pass, which follow layer 1 in the same
        // extractContents call, then find and draw it here.
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        place(slot,
                screenX + SlotRendering.ITEM_INSET - acc.mk$getLeftPos(),
                screenY + SlotRendering.ITEM_INSET - acc.mk$getTopPos());
        presented = true;

        // The frame is panel chrome — the only thing drawn here.
        SlotRendering.drawSlotBackground(ctx.graphics(), screenX, screenY,
                SlotRendering.DEFAULT_SIZE, false);

        // Plain-English hover tooltip (tester-aid / consumer disclosure), routed
        // through the shared queueTooltip so it inherits the library max-width.
        // Only queued when the slot is EMPTY — when it holds an item, vanilla's
        // own item-stack tooltip is the useful one and should win the frame.
        if (slot.getItem().isEmpty()) {
            queueTooltip(ctx);
        }
    }

    /**
     * End of frame: parks this element's slot off-screen unless {@link #render}
     * placed it this frame; clears the mark either way. No-op when the slot isn't
     * on {@code menu}.
     */
    void endFrame(AbstractContainerMenu menu) {
        boolean wasPresented = presented;
        presented = false;
        if (wasPresented) return;
        Slot slot = CreatedSlotAdapter.INSTANCE.resolve(menu, address);
        if (slot != null) place(slot, PARKED, PARKED);
    }

    /**
     * The live in-menu slot this element presents on {@code menu} (the creative
     * wrapper on creative), or {@code null} when it isn't on this menu or its
     * panel is hidden (inert — indistinguishable from absent, §0058).
     */
    private @Nullable Slot presented(@Nullable AbstractContainerMenu menu) {
        if (menu == null) return null;
        Slot slot = CreatedSlotAdapter.INSTANCE.resolve(menu, address);
        if (slot == null) return null;
        MKCSlot mk = MKCSlotAccess.asMKCSlot(slot);
        return (mk == null || mk.isInert()) ? null : slot;
    }

    private static @Nullable AbstractContainerMenu currentMenu() {
        return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs
                ? acs.getMenu() : null;
    }

    /** Writes a slot's presentation position — vanilla's own {@code x/y}. */
    private static void place(Slot slot, int x, int y) {
        SlotPositionAccessor pos = (SlotPositionAccessor) slot;
        pos.mk$setX(x);
        pos.mk$setY(y);
    }

    // ── Lifecycle: join/leave the active-slot registry so the screen hook can
    //    park and resolve hover/click for this panel-hosted slot ─────────────

    @Override
    public void onAttach(Screen screen) {
        SlotElementRegistry.add(this);
    }

    @Override
    public void onDetach(Screen screen) {
        SlotElementRegistry.remove(this);
    }

    /** Builds a {@link SlotElement}. {@link #slot} is required. */
    public static final class Builder extends AbstractPanelElement.Builder<SlotElement, Builder> {
        private SlotGroupId.@org.jspecify.annotations.Nullable Created group;
        private int index = -1;

        private Builder() {}

        @Override protected Builder self() { return this; }

        /**
         * The created slot to present: its group ({@code MKCSlots.groupId(panelId, groupId)}
         * or {@code MKCContainerPanel.groupId(...)}) and its index in the group.
         */
        public Builder slot(SlotGroupId.Created group, int index) {
            this.group = Objects.requireNonNull(group, "group");
            if (index < 0) throw new IllegalArgumentException("index must not be negative, got " + index);
            this.index = index;
            return this;
        }

        @Override
        public SlotElement build() {
            require(group != null, "slot(group, index) is required");
            return new SlotElement(this);
        }
    }
}
