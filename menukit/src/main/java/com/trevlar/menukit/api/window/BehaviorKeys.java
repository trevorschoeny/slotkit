package com.trevlar.menukit.api.window;

import net.minecraft.resources.Identifier;

/**
 * The library's built-in {@link BehaviorKey}s, one constant per behaviour. The
 * engine is generic, so a mod adds a behaviour by declaring a key of its own.
 *
 * <ul>
 *   <li>Client tier: {@link #VISIBILITY}, {@link #OPACITY}, and the observed
 *       reactions.</li>
 *   <li>Server tier, the slot author's rules: {@link #GATING} (what a slot accepts
 *       and releases, and how many), {@link #BINDING} (Curse of Binding on any
 *       slot), and the eleven {@linkplain SlotOperations operations}. Containers keeps {@code ContainerKeys.MENDING}, whose seam
 *       (the XP orb) is its own.</li>
 * </ul>
 */
public final class BehaviorKeys {

    private BehaviorKeys() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    /**
     * Whether an addressable thing is shown, a client-side {@link VisibilityRule}
     * predicate (re-evaluated each frame), so one key spans static hide and dynamic
     * reveal (hover). Client-tier, every kind. Default {@link VisibilityRule#VISIBLE}.
     * Resolved + evaluated on the client only (never server-side; see VisibilityRule).
     */
    public static final BehaviorKey<VisibilityRule> VISIBILITY = BehaviorKey.of(
            id("visibility"), VisibilityRule.class, VisibilityRule.VISIBLE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT, KindTag.PANEL_ELEMENT, KindTag.PANEL);

    /**
     * Whether a panel is interaction-opaque (eats clicks over its bounds). Client-
     * tier, panels only. Default {@link TriBool#TRUE} (M9: panels opaque by default).
     */
    public static final BehaviorKey<TriBool> OPACITY = BehaviorKey.of(
            id("opacity"), TriBool.class, TriBool.TRUE, Tier.CLIENT, KindTag.PANEL);

    // ── The slot author's rules (server tier, every slot kind) ─────────────────

    /**
     * What a slot accepts and releases, and its per-item stack cap: a
     * {@link SlotGate}. Default {@link SlotGate#OPEN}, exactly vanilla. MenuKit
     * enforces it once at each vanilla method that moves items (§0064).
     */
    public static final BehaviorKey<SlotGate> GATING = BehaviorKey.of(
            id("gating"), SlotGate.class, SlotGate.OPEN, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Whether Curse of Binding is enforced on this slot: a bound item cannot be
     * taken out while the player is alive, survival only, the rule vanilla gives
     * armour slots. Default {@link TriBool#FALSE}.
     */
    public static final BehaviorKey<TriBool> BINDING = BehaviorKey.of(
            id("binding"), TriBool.class, TriBool.FALSE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    // ── Reactions, slot kinds only; default = no-op hook ─────────────────────
    //
    // Client-observed only: the client diffs a slot's synced contents each tick.
    // There is no server-side pair: 6.0.0 removed the declared one, which
    // nothing fired.

    /**
     * Fires on the client when synced slot contents grow, pure UI feedback (flash,
     * sound, badge), no authority. CLIENT tier → MK-alone capable, and fires for
     * created slots too (their contents sync identically). Default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_INSERT_OBSERVED = BehaviorKey.of(
            id("on_insert_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Fires on the client when synced slot contents shrink, pure UI feedback, no
     * authority. CLIENT tier; default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_TAKE_OBSERVED = BehaviorKey.of(
            id("on_take_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    // ── Operations, every slot kind; default = vanilla (participates) ────────
    //
    // An OPERATION is something done TO a slot, as opposed to a CATEGORY, which
    // says what the slot IS. See {@link SlotOperations} for the split, for how a
    // mod adds an operation of its own, and for how one is blocked. Vanilla's
    // operations are split as far as they go, one key per thing a player can do
    // to a slot, so a mod that blocks them can block each on its own; a settings
    // screen groups them however it likes. All eleven are TriBool, default TRUE
    // (vanilla), SERVER tier, every slot kind, and enforced at vanilla's own seams
    // by MenuKit's MKOperationsMixin and MKInventoryInsertMixin.

    /**
     * Operation: whether vanilla's double-click collect may take from this slot
     * ({@code canTakeItemForPickAll}). Default {@link TriBool#TRUE}: vanilla sweeps it.
     */
    public static final BehaviorKey<TriBool> COLLECT = BehaviorKey.of(
            id("collect"), TriBool.class, TriBool.TRUE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Operation: whether vanilla's drag-fill (spreading a carried stack across
     * slots) may place into this slot ({@code canDragTo}). Default {@link TriBool#TRUE}.
     */
    public static final BehaviorKey<TriBool> DRAG_FILL = BehaviorKey.of(
            id("drag_fill"), TriBool.class, TriBool.TRUE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    private static BehaviorKey<TriBool> operation(String path) {
        return BehaviorKey.of(id(path), TriBool.class, TriBool.TRUE, Tier.SERVER,
                KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
    }

    /**
     * Operation: whether a plain click may pick up this slot's items. A click that
     * swaps the cursor's items for different ones in the slot takes and puts, so
     * it needs this and {@link #CLICK_PUT}.
     */
    public static final BehaviorKey<TriBool> CLICK_TAKE = operation("click_take");

    /** Operation: whether a plain click may put the cursor's items into this slot, all or one. */
    public static final BehaviorKey<TriBool> CLICK_PUT = operation("click_put");

    /** Operation: whether shift-clicking this slot may send its stack elsewhere. */
    public static final BehaviorKey<TriBool> SHIFT_CLICK_OUT = operation("shift_click_out");

    /** Operation: whether a shift-click elsewhere may land items in this slot. */
    public static final BehaviorKey<TriBool> SHIFT_CLICK_IN = operation("shift_click_in");

    /**
     * Operation: whether a number key may swap this slot with a hotbar slot. Both
     * slots of a swap must allow it.
     */
    public static final BehaviorKey<TriBool> HOTBAR_SWAP = operation("hotbar_swap");

    /** Operation: whether the offhand key may swap this slot with the offhand. Both slots must allow it. */
    public static final BehaviorKey<TriBool> OFFHAND_SWAP = operation("offhand_swap");

    /** Operation: whether Q may drop one item out of this slot. */
    public static final BehaviorKey<TriBool> DROP = operation("drop");

    /** Operation: whether Ctrl-Q may drop this slot's whole stack. */
    public static final BehaviorKey<TriBool> DROP_STACK = operation("drop_stack");

    /**
     * Operation: whether an item given to the inventory may land in this slot,
     * including topping up a partial stack already there. Every insertion through
     * {@code Inventory.getFreeSlot} and {@code getSlotWithRemainingSpace}: a pickup
     * from the ground, {@code /give}, creative pick-block, a crafting grid's
     * returns, recipe placement. The id stays {@code menukit:world_pickup}, since
     * consumers persist ids. Off-menu seam: resolves from the slot's own
     * declaration or the default, then the vetoes.
     */
    public static final BehaviorKey<TriBool> INVENTORY_INSERT = operation("world_pickup");

    /** Every operation vanilla ships, in the order a settings list would show them. */
    public static final java.util.List<BehaviorKey<TriBool>> VANILLA_OPERATIONS = java.util.List.of(
            CLICK_TAKE, CLICK_PUT, SHIFT_CLICK_OUT, SHIFT_CLICK_IN, COLLECT, DRAG_FILL,
            HOTBAR_SWAP, OFFHAND_SWAP, DROP, DROP_STACK, INVENTORY_INSERT);
}
