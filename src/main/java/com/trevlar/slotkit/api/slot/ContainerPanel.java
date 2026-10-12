package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.core.CreatedSlotProjection;
import com.trevlar.slotkit.core.ParitySlotRegistry;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;

import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.WindowEngine;
import com.trevlar.slotkit.api.window.GroupKey;
import com.trevlar.slotkit.window.GroupIds;
import com.trevlar.slotkit.api.window.Decl;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.TriBool;
import com.trevlar.slotkit.api.window.Window;

import net.minecraft.world.inventory.InventoryMenu;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Registers, in one call and with no mixin, a panel whose slots have container
 * parity: they appear, take clicks and sync on every screen that shows the player
 * inventory (survival, creative, and every chest, furnace or modded container). On
 * by default, with opt-out per screen.
 *
 * <h3>What one registration does</h3>
 *
 * <pre>{@code
 * // Consumer COMMON initializer (runs both sides): the slots, and where the panel sits.
 * ContainerPanel.define("inventory-plus:pockets")
 *     .at(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP).priority(20), 7)
 *     .style(PanelStyle.RAISED)
 *     .addSlot(SlotSpec.at("pockets", MY_CATEGORY).count(9)   // slots flow and wrap to width
 *             .storage(player -> POCKETS.bind(player)))
 *     .register();
 *
 * // Consumer CLIENT initializer, only for chrome or a narrower screen scope:
 * ClientContainerPanel.of("inventory-plus:pockets")
 *     .chrome(() -> List.of(Button.builder()...build()))
 *     .parity(ScreenMatcher.allExcept(...));
 * }</pre>
 *
 * From that declaration the library:
 * <ol>
 *   <li>builds the real, synced {@link CreatedSlot}s on the player's own
 *       {@code InventoryMenu} (the library-owned {@code SKInventoryMenuMixin},
 *       both sides), with no consumer mixin;</li>
 *   <li>projects the same slots onto every foreign container menu
 *       ({@link CreatedSlotProjection}, both sides);</li>
 *   <li>covers the creative item picker with no extra work (the creative wrapper wraps
 *       whatever {@link CreatedSlot}s sit on {@code player.inventoryMenu});</li>
 *   <li>on the client, renders the panel's chrome and slot presentation on every
 *       container screen its parity scope accepts (default: all).</li>
 * </ol>
 *
 * <h3>The client/server split</h3>
 *
 * The server builds a menu's slots when the menu is created, before any client is
 * involved, so the slot data is declared here in common code and goes onto every
 * container menu unconditionally. Nothing else would stay in sync. What only the
 * client has (the chrome elements and the screen scope, which name client types) is
 * declared with {@code ClientContainerPanel} from the client initializer; the compiler
 * refuses it here. The scope controls presentation, not whether the slot data exists:
 * a slot a screen opts out of is still on that menu, just not shown there. The client
 * wires every registered panel at client start, after every client initializer has
 * run.
 */
public final class ContainerPanel {

    private ContainerPanel() {}

    // Registered definitions, read on the client to wire chrome
    static final List<Definition> DEFINITIONS = new CopyOnWriteArrayList<>();

    // The one foreign-menu projection source is registered on the first
    // define().register(). One source covers every parity panel because its factory
    // applies the whole ParitySlotRegistry.
    private static volatile boolean projectionSourceRegistered = false;

    /** One registered container panel. {@code position} is its one placement: a
     *  region around the menu frame, a screen spot, an overlay, or a per-frame pixel
     *  supplier. */
    record Definition(String panelId,
                              PanelPosition position,
                              int padding,
                              PanelStyle style,
                              boolean opaque,
                              @Nullable BooleanSupplier visibleWhen,
                              int pinnedHeight,
                              int pinnedWidth,
                              List<SlotSpec> slots) {}

    /** Starts registering a container-parity panel with the given id. */
    public static Builder define(String panelId) {
        com.trevlar.slotkit.network.ModSides.requireBuildsSlots("ContainerPanel", panelId);
        return new Builder(panelId);
    }

    /** Configures the panel. Ends with {@code register()}. */
    public static final class Builder {
        private final String panelId;
        private PanelPosition position = PanelPosition.UNPLACED;      // required: one .at(...)
        private int padding = 7;                                       // ScreenPanelAdapter.DEFAULT_PADDING
        private PanelStyle style = PanelStyle.NONE;
        private boolean opaque = true;
        private @Nullable BooleanSupplier visibleWhen = null;          // null = always visible
        private int pinnedHeight = -1;                                 // -1 = auto-size to visible content
        private int pinnedWidth = -1;                                  // -1 = auto-size to visible content
        private final List<SlotSpec> slots = new ArrayList<>();

        Builder(String panelId) {
            this.panelId = panelId;
        }

        /** Places the panel in a region around the menu frame, with content padding (default priority). */
        public Builder at(OutsideRegion region, int padding) {
            return at(PanelPosition.region(region), padding);
        }

        /**
         * Any container-screen placement, with content padding: a {@code region(...)}
         * with {@code .priority(n)} or {@code .offset(dx, dy)}, a
         * {@code screenAnchor(...)}, a {@code center()} overlay, or a {@code pixel(...)}.
         * This replaces the {@code RegionAnchor} overload:
         * {@code at(OutsideRegion.X.priority(20), 7)} is now
         * {@code at(PanelPosition.region(OutsideRegion.X).priority(20), 7)}.
         */
        public Builder at(PanelPosition position, int padding) {
            this.position = position;
            this.padding = padding;
            return this;
        }

        /**
         * Pixel placement: the panel's outer top-left comes from {@code origin},
         * evaluated every frame, in absolute screen pixels. For positions regions
         * can't express: a point inside the content frame (a panel right above the
         * offhand slot, found with
         * {@link com.trevlar.slotkit.api.group.VanillaSlotResolver#resolve}), or an
         * origin that moves each frame (a row centred over the hovered hotbar column).
         * Returning {@code null} skips the panel for that frame, for screens that
         * don't have the anchor.
         *
         * <p>Everything else about the parity panel stays the same. The slots are real
         * synced {@link CreatedSlot}s on every container menu, the chrome and
         * {@code SlotElement}s move with the panel (their offsets inside it are fixed;
         * only the panel origin moves), and the client's parity scope and
         * {@link #visibleWhen} combine as usual. No wrap or scroll budgets are
         * computed: with pixel placement the consumer owns the exact geometry,
         * including keeping it on screen.
         *
         * <p>Shorthand for {@code at(PanelPosition.pixel(origin), padding)}. Declare
         * one placement; a later {@code .at(...)} replaces an earlier one.
         *
         * @param origin  per-frame supplier of the panel's outer top-left in absolute
         *                screen pixels; returning {@code null} skips the frame
         * @param padding content padding inside the panel edge (often {@code 0}
         *                for slot-tight precision panels)
         */
        public Builder at(Supplier<ScreenOrigin> origin, int padding) {
            return at(PanelPosition.pixel(origin), padding);
        }

        /** Panel background style. Default {@link PanelStyle#NONE}: flush, and the slots draw their own frames. */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /** Whether the panel takes clicks over its bounds (default {@code true}). */
        public Builder opaque(boolean opaque) {
            this.opaque = opaque;
            return this;
        }

        /**
         * Shows or hides the whole panel (chrome and slots) on a client-side
         * predicate, so it appears and disappears as one. Unset, it is always
         * visible. When the supplier returns false, the chrome, background and slot
         * elements all stop rendering together; per-group {@code revealWhen} still
         * applies on top while the panel is shown. This only affects the client's
         * presentation, not server slot sync, so pair it with per-group
         * {@code revealWhen} when the slots must stay in sync (as the validator pool
         * does).
         */
        public Builder visibleWhen(BooleanSupplier visibleWhen) {
            this.visibleWhen = visibleWhen;
            return this;
        }

        /** Adds a slot group to this panel (its recipe and its on-screen presentation). */
        public Builder addSlot(SlotSpec spec) {
            this.slots.add(spec);
            return this;
        }

        /**
         * Fixes the panel's height in pixels (by default it sizes to visible content).
         * Use it when the panel is anchored bottom or right and its visible content
         * changes between frames, as with a group shown by a per-frame
         * {@code revealWhen} predicate. Without it, an anchored panel resizes when a
         * group appears, which moves the slots already shown. If the reveal predicate
         * reads whether the cursor is over a slot, that movement can undo the reveal
         * and make it flicker. A fixed height reserves the full extent so reveals
         * never move existing slots. Same as {@code Panel.pinnedHeight(int)}; width
         * still adapts.
         *
         * @param h pinned content height in pixels
         */
        public Builder pinnedHeight(int h) {
            this.pinnedHeight = h;
            return this;
        }

        /**
         * Fixes the panel's width in pixels (by default it sizes to visible content).
         * The width version of {@link #pinnedHeight(int)}, and the one that matters
         * for a right-anchored panel that reveals on hover. When a revealed group is
         * wider than the ones already shown, an adaptive width grows the panel, and a
         * right anchor turns that growth into a shift to the left of the existing
         * slots. If the reveal predicate reads whether the cursor is over a slot, the
         * shift slides the hover target out from under the cursor and it flickers. A
         * fixed width reserves the full horizontal extent so reveals never move
         * existing slots sideways. Same as {@code Panel.pinnedWidth(int)}.
         *
         * @param w pinned content width in pixels
         */
        public Builder pinnedWidth(int w) {
            this.pinnedWidth = w;
            return this;
        }

        /** Fixes both width and height ({@link #pinnedWidth(int)} and
         *  {@link #pinnedHeight(int)}), so the panel's size never changes on reveal. */
        public Builder size(int w, int h) {
            this.pinnedWidth = w;
            this.pinnedHeight = h;
            return this;
        }

        /**
         * Completes the registration on either side: registers each slot recipe, makes
         * sure the foreign-menu projection source exists, and stores the definition for
         * the client to wire chrome. Call once at mod init, from the common
         * initializer.
         */
        public void register() {
    com.trevlar.menukit.api.Declarations.requireOpen("ContainerPanel " + panelId + " register()");
            // A placement is required, and it must be one a container screen can
            // place (checked here, on both sides, since the client adapter that
            // would otherwise reject it only exists on the client).
            PanelPosition.Mode mode = position.mode();
            if (mode == PanelPosition.Mode.UNPLACED || mode == PanelPosition.Mode.MAIN) {
                throw new IllegalStateException(
                        "ContainerPanel '" + panelId + "': needs a placement before register(): "
                        + ".at(OutsideRegion, padding), .at(PanelPosition, padding) or "
                        + ".at(pixelOriginSupplier, padding). A container screen has no main() panel.");
            }

            // 1. Register each slot's recipe under a derived panel id (container
            //    panel id plus group) that can't collide. Both build hooks read these.
            //    Then set any behaviour the spec declared. Behaviour still lives in
            //    the engine, keyed by Address and resolved when the slot appears; it
            //    is only declared next to the group. Runs on either side, since engine
            //    declarations are plain data, like a consumer's own common init.
            for (SlotSpec spec : slots) {
                if (spec.storageFactory() == null) {
                    throw new IllegalStateException(
                            "ContainerPanel '" + panelId + "': slot group '"
                            + spec.groupId() + "' needs .storage(...) before register().");
                }
                ParitySlotRegistry.register(slotPanelId(panelId, spec.groupId()), spec);
                armInlineBehavior(panelId, spec);
            }

            // 2. Make sure the one foreign-menu projection source exists. Its factory
            //    applies the whole registry, so a single source covers every parity
            //    panel. appliesTo excludes the player's own InventoryMenu, which the
            //    library's inventory mixin already serves.
            ensureProjectionSource();

            // 3. Keep it for the client's wiring (ClientContainerPanel, at client start).
            DEFINITIONS.add(new Definition(panelId, position, padding,
                    style, opaque, visibleWhen, pinnedHeight,
                    pinnedWidth, List.copyOf(slots)));
        }
    }

    /**
     * Publishes a {@link SlotSpec}'s category for the group and sets whatever behaviour
     * the spec declared at the group level. A null value leaves the engine default for
     * that key, so an undeclared slot stays plain vanilla.
     *
     * <p>Set for the group, not per address: a {@code SlotSpec} is a group, so a
     * consumer that overrides one slot with
     * {@code Window.slot(address(panelId, groupId, i)).set(KEY, value)} wins because
     * it is more specific, not because it was declared last.
     */
    @SuppressWarnings("removal") // QUICK_MOVE stays declared for 5.0.0 readers until 6.0.0
    private static void armInlineBehavior(String containerPanelId, SlotSpec spec) {
        SlotGate gate = spec.gateValue();
        TriBool binding = spec.bindingValue();
        TriBool mending = spec.mendingValue();
        TriBool shiftClickOut = spec.shiftClickOutValue();
        TriBool shiftClickIn = spec.shiftClickInValue();
        TriBool collect = spec.collectValue();
        TriBool dragFill = spec.dragFillValue();
        // Declared whether or not the spec declares behaviour: the group's category,
        // recorded once on the group, is what lets the category's own values reach
        // its slots. This path registers at init, so the group is listed from the
        // title screen.
        com.trevlar.slotkit.api.group.SlotGroups.declare(groupId(containerPanelId, spec.groupId()), spec.category());
        if (gate == null && binding == null && mending == null && shiftClickOut == null
                && shiftClickIn == null && collect == null && dragFill == null) return;

        // Declared for the group, not per address. A SlotSpec is a group, so its
        // values sit one level above a per-slot declaration. A consumer overriding one
        // slot with Window.slot(addr).set(...) then wins outright, instead of the two
        // colliding at the per-address level and whichever mod's init ran last
        // winning. Above the group sits the category (PRECEDENCE_CATEGORY).
        GroupKey group = GroupKey.of(groupId(containerPanelId, spec.groupId()));   // the group's own identity
        // The slot rules and operations are MenuKit keys, which MenuKit enforces in
        // vanilla's code for every kind of slot. Mending is enforced by Containers.
        if (gate != null)          WindowEngine.setGroup(group, BehaviorKeys.GATING, Decl.set(gate));
        if (binding != null)       WindowEngine.setGroup(group, BehaviorKeys.BINDING, Decl.set(binding));
        if (mending != null)       WindowEngine.setGroup(group, BehaviorKeys.MENDING, Decl.set(mending));
        if (shiftClickOut != null) WindowEngine.setGroup(group, BehaviorKeys.SHIFT_CLICK_OUT, Decl.set(shiftClickOut));
        if (shiftClickIn != null)  WindowEngine.setGroup(group, BehaviorKeys.SHIFT_CLICK_IN, Decl.set(shiftClickIn));
        if (collect != null)       WindowEngine.setGroup(group, BehaviorKeys.COLLECT, Decl.set(collect));
        if (dragFill != null)      WindowEngine.setGroup(group, BehaviorKeys.DRAG_FILL, Decl.set(dragFill));
    }

    private static synchronized void ensureProjectionSource() {
        if (projectionSourceRegistered) return;
        projectionSourceRegistered = true;
        CreatedSlotProjection.register(
                menu -> !(menu instanceof InventoryMenu),
                ParitySlotRegistry::applyTo);
    }

    /** The id the built {@link CreatedSlot}s carry and the {@code SlotElement}s resolve against. */
    static String slotPanelId(String containerPanelId, String groupId) {
        return containerPanelId + ":" + groupId;
    }

    // The public way to name a parity group

    /**
     * The {@link SlotGroupId} of one of this panel's slot groups. It is what
     * {@code SlotGroupPanelAdapter.onGroup(...)} anchors to, so another mod can place
     * a panel against these slots.
     *
     * <p>Build it here rather than by hand: the container-parity path derives its own
     * slot panel id ({@code containerPanelId:groupId}), so an identity built from the
     * raw ids would silently name a group that never resolves. A slot's address is
     * {@code Address.createdSlot(groupId(panel, group), index)}.
     */
    public static SlotGroupId.Created groupId(String containerPanelId, String groupId) {
        return SlotGroupId.created(slotPanelId(containerPanelId, groupId), groupId);
    }
}
