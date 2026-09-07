package com.trevlar.menukit.core;

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

import java.util.function.Supplier;

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
 * <h3>Why a slot is a click-through HOLE in its panel</h3>
 *
 * A slot's item interaction is owned by vanilla: a click flows through
 * {@code AbstractContainerScreen.mouseClicked} → {@code slotClicked(getHoveredSlot())},
 * and MenuKit's library-owned {@code getHoveredSlot} interception
 * ({@link MKCSlotInput}) makes the registered slot win over a vanilla slot it
 * covers. So the panel must <em>not</em> eat the click; {@link #isElementOpaque()}
 * returns {@code false} (a hole) so the click reaches vanilla.
 * {@link SlotElementRegistry} tells the library's screen hook which panels
 * currently host a {@code SlotElement}.
 */
public final class SlotElement implements PanelElement {

    /** Off-screen — where a slot sits when no panel is presenting it this frame. */
    private static final int PARKED = MKCSlots.OFFSCREEN;

    private final String panelId;
    private final Address address;
    private final int childX;
    private final int childY;

    /** Whether a panel presented (placed) this element's slot this frame. Render thread only. */
    private boolean presented = false;

    /** Optional hover tooltip — slots are direct PanelElement implementors,
     *  so they carry their own supplier rather than inheriting one. */
    private @Nullable Supplier<Component> tooltipSupplier;

    /**
     * @param panelId    the registered slot's panel id (as given to
     *                   {@link MKCSlots.Builder#panel})
     * @param groupId    the registered slot's group id ({@link MKCSlots.Builder#group})
     * @param localIndex the slot's index within its group (0-based)
     * @param childX     panel-local X (within the panel's content area, after padding)
     * @param childY     panel-local Y
     */
    public SlotElement(String panelId, String groupId, int localIndex,
                       int childX, int childY) {
        this.panelId = panelId;
        this.address = CreatedSlotAdapter.addressOf(panelId, groupId, localIndex);
        this.childX = childX;
        this.childY = childY;
    }

    /** The registered slot's panel id this element presents (its registry key). */
    public String panelId() { return panelId; }

    /** Attaches a hover tooltip with fixed text to this slot. Chainable. */
    public SlotElement tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Attaches a hover tooltip with supplier-driven text to this slot. Chainable. */
    public SlotElement tooltip(@Nullable Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    /** The universal {@link PanelElement} tooltip contract — slots are first-class
     *  tooltip citizens via their own supplier field. */
    @Override
    public @Nullable Supplier<Component> tooltipSupplier() {
        return tooltipSupplier;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return SlotRendering.DEFAULT_SIZE; }
    @Override public int getHeight() { return SlotRendering.DEFAULT_SIZE; }

    /**
     * A slot is a click-through hole (see class javadoc): vanilla's slot-click
     * machinery owns the interaction, so the panel must let the click pass
     * rather than eat it. The covered vanilla slot is made inert by the slot
     * {@code getHoveredSlot} resolution, not by panel opacity.
     */
    @Override public boolean isElementOpaque() { return false; }

    /** Hidden when the slot can't be resolved on this screen, or its panel is hidden. */
    @Override
    public boolean isVisible() {
        return presented(currentMenu()) != null;
    }

    @Override
    public void render(RenderContext ctx) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
        Slot slot = presented(acs.getMenu());
        if (slot == null) return;

        int screenX = ctx.originX() + childX;
        int screenY = ctx.originY() + childY;

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
}
