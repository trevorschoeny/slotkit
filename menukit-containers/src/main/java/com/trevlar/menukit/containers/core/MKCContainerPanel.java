package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.core.OutsideRegion;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;

import com.trevlar.menukit.inject.ScreenOrigin;
import com.trevlar.menukit.inject.SlotGroupId;
import com.trevlar.menukit.window.Address;
import com.trevlar.menukit.window.WindowEngine;
import com.trevlar.menukit.window.GroupKey;
import com.trevlar.menukit.window.GroupIds;
import com.trevlar.menukit.window.Decl;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotGate;
import com.trevlar.menukit.window.TriBool;
import com.trevlar.menukit.window.Window;

import net.minecraft.world.inventory.InventoryMenu;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The one-call, no-mixin registration for a panel whose slots have <b>container
 * parity</b> — they appear, click, and sync on <em>every</em> screen that shows
 * the player inventory (survival, creative, and every chest/furnace/modded
 * container), default-on, opt-out per screen.
 *
 * <h3>What one registration drives</h3>
 *
 * <pre>{@code
 * // Consumer COMMON initializer (runs both sides): the slots, and where the panel sits.
 * MKCContainerPanel.define("inventory-plus:pockets")
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
 *   <li>builds the real, synced {@link MKCSlot}s on the player's own
 *       {@code InventoryMenu} (the library-owned {@code MKCInventoryMenuMixin},
 *       both sides), with no consumer mixin;</li>
 *   <li>projects the same slots onto every foreign container menu
 *       ({@link MKCSlotProjection}, both sides);</li>
 *   <li>gets the creative item picker for free (the creative wrapper wraps whatever
 *       {@link MKCSlot}s sit on {@code player.inventoryMenu});</li>
 *   <li>on the client, renders the panel's chrome and slot presentation on every
 *       container screen its parity scope accepts (default: all).</li>
 * </ol>
 *
 * <h3>The client/server split</h3>
 *
 * The server builds a menu's slots at construction, before any client exists, so the
 * slot data is declared here, in common code, and travels onto every container menu
 * unconditionally: the only sync-correct default. What only the client has (the chrome
 * elements and the screen scope, which name client types) is declared with
 * {@code ClientContainerPanel} from the client initializer (§0067; the compiler refuses
 * it here). The scope gates presentation, not whether the slot data exists: a slot a
 * screen opts out of is still on that menu, just not shown there. The client wires every
 * registered panel at client start, after every client initializer has run.
 */
public final class MKCContainerPanel {

    private MKCContainerPanel() {}

    // ── Registered definitions (read on the client to wire chrome) ──────
    static final List<Definition> DEFINITIONS = new CopyOnWriteArrayList<>();

    // The single foreign-menu projection source is registered lazily on the
    // first define().register() — one source covers every parity panel because
    // its factory applies the whole ParitySlotRegistry.
    private static volatile boolean projectionSourceRegistered = false;

    /** Immutable snapshot of one registered container panel. {@code position} is
     *  the panel's one placement (§0065): a region around the menu frame, a screen
     *  spot, an overlay, or a per-frame pixel supplier (§0057 Revision). */
    record Definition(String panelId,
                              PanelPosition position,
                              int padding,
                              PanelStyle style,
                              boolean opaque,
                              @Nullable BooleanSupplier visibleWhen,
                              int pinnedHeight,
                              int pinnedWidth,
                              List<SlotSpec> slots) {}

    /** Begins a container-parity panel registration with the given panel id. */
    public static Builder define(String panelId) {
        return new Builder(panelId);
    }

    /** Fluent configuration; terminates in {@code register()}. */
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

        /** Region placement around the menu frame + explicit content padding (default priority). */
        public Builder at(OutsideRegion region, int padding) {
            return at(PanelPosition.region(region), padding);
        }

        /**
         * Any container-screen placement + explicit content padding: a
         * {@code region(...)} with {@code .priority(n)} or {@code .offset(dx, dy)}, a
         * {@code screenAnchor(...)}, a {@code center()} overlay, or a
         * {@code pixel(...)}. (Replaces the {@code RegionAnchor} overload:
         * {@code at(OutsideRegion.X.priority(20), 7)} is now
         * {@code at(PanelPosition.region(OutsideRegion.X).priority(20), 7)}.)
         */
        public Builder at(PanelPosition position, int padding) {
            this.position = position;
            this.padding = padding;
            return this;
        }

        /**
         * Pixel-precision placement (§0057 Revision — Trev's call, 2026-07-01):
         * the panel's <b>outer</b> top-left comes from {@code origin}, re-evaluated
         * <b>every frame</b>, in <b>absolute screen pixels</b>. The precision
         * escape for the positions regions cannot express — a point <em>inside</em>
         * the content frame (e.g. a panel directly above the offhand slot, via
         * {@link com.trevlar.menukit.inject.VanillaSlotResolver#resolve}) or
         * an origin that moves per frame (e.g. a row centred over the hovered
         * hotbar column). Returning {@code null} skips the panel that frame — the
         * natural "this screen doesn't surface my anchor" escape.
         *
         * <p>Everything else about the parity panel is unchanged: the slots are
         * real synced {@link MKCSlot}s on every container menu, the chrome and
         * {@code SlotElement}s ride the panel (their intra-panel offsets are fixed;
         * only the panel origin moves), and the client's parity scope and {@link #showWhen}
         * compose as usual. No reactive wrap/scroll budgets are fed — pixel
         * placement means the consumer owns the exact geometry, on-screen included.
         *
         * <p>Sugar for {@code at(PanelPosition.pixel(origin), padding)}. Declare
         * exactly one placement; a later {@code .at(...)} replaces an earlier one.
         *
         * @param origin  per-frame supplier of the panel's outer top-left in
         *                absolute screen pixels; {@code null} return = skip frame
         * @param padding content padding inside the panel edge (often {@code 0}
         *                for slot-tight precision panels)
         */
        public Builder at(Supplier<ScreenOrigin> origin, int padding) {
            return at(PanelPosition.pixel(origin), padding);
        }

        /** Panel background style. Default {@link PanelStyle#NONE} (flush — the slots draw their own frames). */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /** Interaction opacity (default {@code true} — the panel eats clicks over its bounds). */
        public Builder opaque(boolean opaque) {
            this.opaque = opaque;
            return this;
        }

        /**
         * Gates the WHOLE panel's visibility (chrome + slot presentation) on a
         * client-side predicate — the entire parity panel appears/disappears as
         * one. Default (unset): always visible. When the supplier returns false,
         * the chrome, background, and slot elements all stop rendering together;
         * per-group {@code revealWhen} still applies on top when the panel is
         * shown. Client-side presentation gate (the panel's {@code showWhen}); does not
         * by itself drive server slot-sync, so
         * pair it with per-group {@code revealWhen} when the slots are
         * sync-critical (as the validator pool does).
         */
        public Builder showWhen(BooleanSupplier visibleWhen) {
            this.visibleWhen = visibleWhen;
            return this;
        }

        /** Adds a slot group to this panel (its recipe + its on-screen presentation). */
        public Builder addSlot(SlotSpec spec) {
            this.slots.add(spec);
            return this;
        }

        /**
         * Pins the panel's height to a fixed pixel extent (default: auto-size to
         * visible content). Use when the panel is bottom/right-anchored AND its
         * visible content fluctuates between frames — e.g. a group revealed by a
         * per-frame {@code revealWhen} predicate. Without a pin, an anchored
         * panel's auto-size changes when a group reveals, which moves the
         * already-visible slots; if a reveal predicate reads cursor-over-slot
         * state, that motion can un-trigger the reveal and oscillate (a flash).
         * Pinning the height reserves the panel's full extent so reveals never
         * shift existing slots. Mirrors {@code Panel.pinnedHeight(int)}; width
         * stays adaptive.
         *
         * @param h pinned content height in pixels
         */
        public Builder pinnedHeight(int h) {
            this.pinnedHeight = h;
            return this;
        }

        /**
         * Pins the panel's width to a fixed pixel extent (default: auto-size to
         * visible content). The width twin of {@link #pinnedHeight(int)} — and
         * the one that matters for a RIGHT-anchored, hover-reveal panel: when a
         * revealed group is WIDER than the already-visible ones, an adaptive
         * width grows the panel, and a right anchor turns that growth into a
         * LEFTWARD shift of the existing slots. If the reveal predicate reads
         * cursor-over-slot state, that shift slides the hover target out from
         * under the cursor and oscillates (the flash). Pinning the width
         * reserves the panel's full horizontal extent so reveals never shift
         * existing slots sideways. Mirrors {@code Panel.pinnedWidth(int)}.
         *
         * @param w pinned content width in pixels
         */
        public Builder pinnedWidth(int w) {
            this.pinnedWidth = w;
            return this;
        }

        /** Pins BOTH axes — convenience for {@link #pinnedWidth(int)} +
         *  {@link #pinnedHeight(int)}, the bulletproof "footprint never changes
         *  on reveal" form. */
        public Builder size(int w, int h) {
            this.pinnedWidth = w;
            this.pinnedHeight = h;
            return this;
        }

        /**
         * Finalises the registration. Side-neutral: registers each slot recipe,
         * ensures the foreign-menu projection source exists, and stores the
         * definition for client chrome wiring. Call once at mod init (common
         * initializer).
         */
        public void register() {
    com.trevlar.menukit.window.Declarations.requireOpen("MKCContainerPanel " + panelId + " register()");
            // A placement is required, and it must be one a container screen can
            // place (checked here, on both sides, since the client adapter that
            // would otherwise reject it only exists on the client).
            PanelPosition.Mode mode = position.mode();
            if (mode == PanelPosition.Mode.UNPLACED || mode == PanelPosition.Mode.MAIN) {
                throw new IllegalStateException(
                        "MKCContainerPanel '" + panelId + "': needs a placement before register(): "
                        + ".at(OutsideRegion, padding), .at(PanelPosition, padding) or "
                        + ".at(pixelOriginSupplier, padding). A container screen has no main() panel.");
            }

            // 1. Register each slot's recipe under a derived, collision-free panel
            //    id (container panel id + group). Both build seams read these.
            //    Then ARM any inline behavior the spec declared, by Address — this
            //    is sugar over Window.slot(addr).set: behavior still lives in the
            //    engine (keyed by Address, resolved when the slot appears), it's
            //    just declared where the group is declared instead of in a separate,
            //    forgettable pass. Side-neutral (the engine declarations are pure
            //    data), matching the consumer's own common-init arming.
            for (SlotSpec spec : slots) {
                if (spec.storageFactory() == null) {
                    throw new IllegalStateException(
                            "MKCContainerPanel '" + panelId + "': slot group '"
                            + spec.groupId() + "' needs .storage(...) before register().");
                }
                ParitySlotRegistry.register(slotPanelId(panelId, spec.groupId()), spec);
                armInlineBehavior(panelId, spec);
            }

            // 2. Ensure the one foreign-menu projection source exists. Its factory
            //    applies the whole registry, so a single source covers every parity
            //    panel; appliesTo excludes the player's own InventoryMenu (served by
            //    the library inventory mixin, so it must not be double-appended).
            ensureProjectionSource();

            // 3. Stash for the client's wiring (ClientContainerPanel, at client start).
            DEFINITIONS.add(new Definition(panelId, position, padding,
                    style, opaque, visibleWhen, pinnedHeight,
                    pinnedWidth, List.copyOf(slots)));
        }
    }

    /**
     * Publishes a {@link SlotSpec}'s category for every local index in the group, and
     * arms whatever behavior the spec declared at the group rung of the cascade. A
     * null inline value leaves the engine default for that key (so an un-declared
     * slot stays exactly vanilla).
     *
     * <p>Group rung, not per address: a {@code SlotSpec} <em>is</em> a group, so a
     * consumer that overrides one slot through
     * {@code Window.slot(address(panelId, groupId, i)).set(KEY, value)} outranks it
     * by specificity rather than by who declared last.
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
        // Declared whether or not the spec declares behavior: the group's category,
        // recorded once on the group, is what lets the category's inherent values
        // reach its slots. This path registers at init, so the group is listed from
        // the title screen.
        com.trevlar.menukit.inject.SlotGroups.declare(groupId(containerPanelId, spec.groupId()), spec.category());
        if (gate == null && binding == null && mending == null && shiftClickOut == null
                && shiftClickIn == null && collect == null && dragFill == null) return;

        // Declared at the GROUP rung, not per address. A SlotSpec IS a group, so its
        // values belong one level above a per-slot declaration: a consumer overriding
        // one slot with Window.slot(addr).set(...) then wins outright, instead of the
        // two colliding on the per-address level and being settled by whichever mod's
        // init ran last. Above the group sits the category (PRECEDENCE_CATEGORY).
        GroupKey group = GroupKey.of(groupId(containerPanelId, spec.groupId()));   // the group's own identity
        // The slot author's rules and the operations are MenuKit keys, enforced by
        // MenuKit at vanilla's seams for every slot kind; mending's seam is Containers'.
        if (gate != null)          WindowEngine.setGroup(group, BehaviorKeys.GATING, Decl.set(gate));
        if (binding != null)       WindowEngine.setGroup(group, BehaviorKeys.BINDING, Decl.set(binding));
        if (mending != null)       WindowEngine.setGroup(group, MKCBehaviorKeys.MENDING, Decl.set(mending));
        if (shiftClickOut != null) WindowEngine.setGroup(group, BehaviorKeys.SHIFT_CLICK_OUT, Decl.set(shiftClickOut));
        if (shiftClickIn != null)  WindowEngine.setGroup(group, BehaviorKeys.SHIFT_CLICK_IN, Decl.set(shiftClickIn));
        if (collect != null)       WindowEngine.setGroup(group, BehaviorKeys.COLLECT, Decl.set(collect));
        if (dragFill != null)      WindowEngine.setGroup(group, BehaviorKeys.DRAG_FILL, Decl.set(dragFill));
    }

    private static synchronized void ensureProjectionSource() {
        if (projectionSourceRegistered) return;
        projectionSourceRegistered = true;
        MKCSlotProjection.register(
                menu -> !(menu instanceof InventoryMenu),
                ParitySlotRegistry::applyTo);
    }

    /** The id the built {@link MKCSlot}s carry and the {@code SlotElement}s resolve against. */
    static String slotPanelId(String containerPanelId, String groupId) {
        return containerPanelId + ":" + groupId;
    }

    // ── The public way to name a parity group ──

    /**
     * The {@link SlotGroupId} naming one of this panel's slot groups — what a
     * {@code SlotGroupPanelAdapter.onGroup(...)} anchors to, so another mod can
     * place a panel against these slots.
     *
     * <p>Minted here rather than by hand: the container-parity path derives its own
     * slot panel id ({@code containerPanelId:groupId}), so building the identity from
     * the raw ids would silently name a group that never resolves. A slot's address is
     * {@code Address.createdSlot(groupId(panel, group), index)}.
     */
    public static SlotGroupId.Created groupId(String containerPanelId, String groupId) {
        return SlotGroupId.created(slotPanelId(containerPanelId, groupId), groupId);
    }
}
