package com.trevlar.slotkit.api.slot;

import org.jetbrains.annotations.ApiStatus;

import com.trevlar.slotkit.core.CreatedSlotAccess;
import com.trevlar.slotkit.core.CreatedSlotAdapter;
import com.trevlar.slotkit.core.CreatedSlotInput;
import com.trevlar.slotkit.core.SlotElementRegistry;
import com.trevlar.slotkit.api.group.SlotGroupId;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.host.SlotClip;

import com.trevlar.slotkit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.slotkit.mixin.SlotPositionAccessor;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.KindTag;
import com.trevlar.slotkit.window.VanillaAddressing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A registered slot shown as a {@link PanelElement}. A slot is just another element
 * you put in a panel, next to buttons and labels.
 *
 * <h3>What this element does, and what it doesn't</h3>
 *
 * A slot has two separate parts:
 * <ul>
 *   <li>Registration: a real, server-synced {@link CreatedSlot} in
 *       {@code menu.slots}, built when the menu is constructed through
 *       {@link CreatedSlots}. This class doesn't touch it.</li>
 *   <li>Placement: where it sits on screen, which is what {@code SlotElement} owns.
 *       Each frame, in layer 2 of {@code ContainerScreenLayers} (after vanilla has
 *       drawn the vanilla slots and before it draws the created ones), it converts
 *       its panel-relative position to screen space and writes it into the live
 *       slot's own {@code Slot.x/y}. It also draws the recessed 18x18 frame, which
 *       is panel chrome.</li>
 * </ul>
 *
 * It does not draw the item, count, durability bar, hover highlight or ghost icon.
 * Vanilla does: its {@code extractSlots} walks {@code menu.slots} and draws this slot
 * at the {@code x/y} written here, through the same {@code extractSlot} call it uses
 * for a vanilla slot, and its {@code getHoveredSlot} finds it the same way. So a
 * third party's slot hook, or any mod reading {@code slot.x/y}, treats a created slot
 * and a vanilla slot the same, because SlotKit has no separate slot pass for them
 * to differ in. {@code CustomContainerScreen} has always worked this way on its own
 * screens.
 *
 * <h3>In a clipping container</h3>
 *
 * In a {@code ScrollContainer} (or anything else that clips what it draws) the slot
 * scrolls and clips like any element. Each frame it places its slot, it reports it to
 * MenuKit ({@code SlotClip.shown}) with the clip it draws under, and MenuKit holds
 * vanilla's draw of the slot and its hover to that clip: a slot across the viewport's
 * edge keeps its position and shows, hovers and takes clicks only inside. A slot
 * wholly out of view is not rendered, so it is parked (below).
 *
 * <h3>Vanilla slots</h3>
 *
 * {@link Builder#slot(Address)} presents a vanilla slot instead: one the menu built
 * itself, named by {@link Address#menuSlot} (the same slot on every menu of a type,
 * which is what a panel registered once wants) or by the container-based address
 * {@code SlotRef.address()} gives (one particular box). The slot keeps everything
 * vanilla gives it (sync, shift-click, its category); the panel only moves it. Its
 * position changes only on screens where this element is attached: there it sits
 * where the panel puts it, or parked when the panel doesn't show it, and every
 * other screen's layout is untouched. A screen's background still draws the slot's
 * old frame at its old spot, so a panel taking over a vanilla grid hides that.
 * Present each vanilla slot at most once per screen, and never as a created slot
 * over the same container as well, which would put two views of one container in
 * {@code menu.slots}.
 *
 * <h3>Looked up each frame, not held</h3>
 *
 * A {@code SlotElement} holds no slot reference. It holds the slot's stable
 * {@link Address} and looks up the live in-menu slot from the current screen's menu
 * each frame, through {@link CreatedSlotAdapter}'s cache. That is what lets one
 * panel registered once work on every screen. The survival inventory and the
 * creative screen carry different slot instances for the same logical slot (creative
 * wraps it in a {@code SlotWrapper} on a throwaway menu), and a held reference would
 * be wrong on one of them. The position is written to whichever instance is in
 * {@code menu.slots}, the wrapper on creative, because that is the one vanilla draws.
 *
 * <h3>Parked when not shown</h3>
 *
 * At the end of each frame ({@link SlotElementRegistry#parkUnpresented}), every
 * attached element whose panel did not render it that frame moves its slot
 * off-screen. So a slot whose panel is hidden, out of its region, or hidden by the
 * window sits where vanilla neither draws nor hit-tests it next frame, instead of
 * where it was last drawn. A slot that was shown keeps its position, so hover between
 * frames matches what is on screen. ({@link CreatedSlot#isActive} handles a hidden
 * panel by itself; parking handles the other cases.)
 *
 * <p>An element in an overlay panel (layer 4) writes its position after vanilla's
 * slot pass, so vanilla draws it a frame late and under the overlay's chrome.
 * Created slots belong in flow panels; nothing puts them in overlays today.
 *
 * <h3>Why a slot stays live under its own panel</h3>
 *
 * Vanilla owns a slot's item interaction: {@code AbstractContainerScreen.mouseClicked}
 * calls {@code slotClicked(getHoveredSlot())}, and MenuKit's {@code getHoveredSlot}
 * interception ({@link CreatedSlotInput}) makes the registered slot win over a vanilla
 * slot it covers. An opaque panel claims its whole rectangle with no holes, so the
 * claim sends a point over this element's slot back to vanilla
 * ({@link #presentsSlotAt}). The panel's own slot hovers and takes clicks, while
 * anything else under the panel stays inert. On a transparent panel the slot is not
 * solid ({@link #isElementOpaque()} is {@code false}), so it claims nothing itself.
 * {@link SlotElementRegistry} tells the library's screen hook which panels currently
 * host a {@code SlotElement}.
 */
public final class SlotElement extends AbstractPanelElement {

    /** Off-screen: where a slot sits when no panel is showing it this frame. */
    private static final int PARKED = CreatedSlots.OFFSCREEN;

    /** The created slot's panel, or {@code null} for a vanilla slot. */
    private final @Nullable String panelId;
    private final Address address;

    /** Whether a panel placed this element's slot this frame. Render thread only. */
    private boolean presented = false;

    /**
     * Whether another element on this screen already presents this element's slot.
     * Such an element stays out of the way (no placement, no claim) so the first one
     * keeps the slot: two panels writing one slot's position would fight every frame.
     */
    private boolean duplicate = false;

    private SlotElement(Builder b) {
        super(b);
        this.panelId = b.group != null ? b.group.panelId() : null;
        this.address = b.group != null ? Address.createdSlot(b.group, b.index) : b.address;
        this.width = SlotRendering.DEFAULT_SIZE;
        this.authoredWidth = SlotRendering.DEFAULT_SIZE;
        this.height = SlotRendering.DEFAULT_SIZE;
    }

    /**
     * A slot element: {@code SlotElement.builder().slot(CreatedSlots.groupId(panel, group), i).at(x, y).build()}.
     * The usual element methods apply: {@code at}, {@code visibleWhen} (on top of the
     * slot's own reveal), and {@code tooltip} (shown over an empty slot; a slot with an
     * item shows the item's). A slot is never solid, so {@code opaque} has no effect.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** The panel id of the created slot this element shows (its registry key), or {@code null} for a vanilla slot. */
    public @Nullable String panelId() { return panelId; }

    /**
     * The vanilla slot this element hosts on {@code menu} when a panel has it on
     * screen (not parked), else {@code null}. Always {@code null} for a created slot.
     */
    @ApiStatus.Internal
    public @Nullable Slot hostedVanillaSlot(AbstractContainerMenu menu) {
        if (panelId != null) return null;
        Slot slot = VanillaAddressing.resolve(menu, address);
        return slot != null && slot.x != PARKED ? slot : null;
    }

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
     * 18x18 frame around the item box) at the slot's current position. The panel
     * claiming the point sends it to the slot, so a panel's own slots hover, take
     * clicks and show tooltips while everything else it covers is inert.
     */
    @Override
    public boolean presentsSlotAt(com.trevlar.menukit.api.element.InputContext in) {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs)) return false;
        Slot slot = presented(acs.getMenu());
        if (slot == null) return false;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        double relX = in.mouseX() - acc.sk$getLeftPos();
        double relY = in.mouseY() - acc.sk$getTopPos();
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

        // Place the live slot where the panel put this element, in vanilla's slot
        // coordinates (relative to the frame; x/y are the 16x16 item box, which sits
        // ITEM_INSET inside the 18x18 frame). Vanilla's hover resolution and slot
        // pass, which follow layer 1 in the same extractContents call, then find and
        // draw it here.
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) acs;
        place(slot,
                screenX + SlotRendering.ITEM_INSET - acc.sk$getLeftPos(),
                screenY + SlotRendering.ITEM_INSET - acc.sk$getTopPos());
        presented = true;
        // Tell MenuKit the clip this element draws under, so a slot across a
        // ScrollContainer's edge draws, hovers and clicks only where it is shown.
        SlotClip.shown(ctx, slot);

        // The frame is panel chrome, and the only thing drawn here.
        SlotRendering.drawSlotBackground(ctx.graphics(), screenX, screenY,
                SlotRendering.DEFAULT_SIZE, false);

        // The hover tooltip goes through the shared queueTooltip, so it gets the
        // library's max width. Only queued when the slot is empty: when it holds an
        // item, vanilla's own item tooltip is the useful one.
        if (slot.getItem().isEmpty()) {
            queueTooltip(ctx);
        }
    }

    /**
     * End of frame: parks this element's slot off-screen unless {@link #render} placed
     * it this frame, and clears the mark either way. Does nothing when the slot isn't
     * on {@code menu}.
     */
    @ApiStatus.Internal
    public void endFrame(AbstractContainerMenu menu) {
        boolean wasPresented = presented;
        presented = false;
        if (wasPresented || duplicate) return;   // a duplicate never parks the first element's slot
        Slot slot = resolve(menu);
        if (slot != null) place(slot, PARKED, PARKED);
    }

    /**
     * The live in-menu slot this element shows on {@code menu} (the creative wrapper
     * on creative), or {@code null} when it isn't on this menu or its panel is hidden
     * (inert, which looks the same as absent).
     */
    private @Nullable Slot presented(@Nullable AbstractContainerMenu menu) {
        if (menu == null || duplicate) return null;
        Slot slot = resolve(menu);
        if (slot == null || panelId == null) return slot; // a vanilla slot is never inert
        CreatedSlot mk = CreatedSlotAccess.asMKCSlot(slot);
        return (mk == null || mk.isInert()) ? null : slot;
    }

    /** The live in-menu slot the address names on {@code menu}, whatever its state. */
    @ApiStatus.Internal
    public @Nullable Slot resolve(AbstractContainerMenu menu) {
        return panelId != null
                ? CreatedSlotAdapter.INSTANCE.resolve(menu, address)
                : VanillaAddressing.resolve(menu, address);
    }

    private static @Nullable AbstractContainerMenu currentMenu() {
        return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs
                ? acs.getMenu() : null;
    }

    // Writes a slot's on-screen position into vanilla's own x/y.
    private static void place(Slot slot, int x, int y) {
        SlotPositionAccessor pos = (SlotPositionAccessor) slot;
        pos.sk$setX(x);
        pos.sk$setY(y);
    }

    // Join and leave the registry, so the screen hook can park this slot and resolve
    // hover and click for it.

    @Override
    public void onAttach(Screen screen) {
        duplicate = false;
        if (screen instanceof AbstractContainerScreen<?> acs) {
            Slot mine = resolve(acs.getMenu());
            if (mine != null && SlotElementRegistry.presents(acs.getMenu(), mine)) {
                duplicate = true;
                org.slf4j.LoggerFactory.getLogger("SlotKit").warn(
                        "Two slot elements on {} present the same slot ({}); the first keeps it. "
                        + "Present each slot in one panel only.", screen.getClass().getSimpleName(), address);
            }
        }
        SlotElementRegistry.add(this);
    }

    @Override
    public void onDetach(Screen screen) {
        SlotElementRegistry.remove(this);
    }

    /** Builds a {@link SlotElement}. One of the {@code slot} methods is required. */
    public static final class Builder extends AbstractPanelElement.Builder<SlotElement, Builder> {
        private SlotGroupId.@org.jspecify.annotations.Nullable Created group;
        private int index = -1;
        private @Nullable Address address;

        private Builder() {}

        @Override protected Builder self() { return this; }

        /**
         * The created slot to present: its group ({@code CreatedSlots.groupId(panelId, groupId)}
         * or {@code ParityGroup.idOf(...)}) and its index in the group.
         */
        public Builder slot(SlotGroupId.Created group, int index) {
            this.group = Objects.requireNonNull(group, "group");
            if (index < 0) throw new IllegalArgumentException("index must not be negative, got " + index);
            this.index = index;
            this.address = null;
            return this;
        }

        /**
         * The vanilla slot to present: {@link Address#menuSlot(net.minecraft.world.inventory.MenuType, int)}
         * for the same slot on every menu of a type, or a slot's container-based
         * address from {@code SlotRef.address()} for one particular container. A
         * created slot goes through {@link #slot(SlotGroupId.Created, int)}.
         */
        public Builder slot(Address address) {
            Objects.requireNonNull(address, "address");
            if (address.kind() != KindTag.VANILLA_SLOT) {
                throw new IllegalArgumentException("slot(Address) takes a vanilla slot's address, got " + address.kind()
                        + "; present a created slot with slot(group, index)");
            }
            this.address = address;
            this.group = null;
            return this;
        }

        @Override
        public SlotElement build() {
            require(group != null || address != null, "slot(group, index) or slot(address) is required");
            return new SlotElement(this);
        }
    }
}
