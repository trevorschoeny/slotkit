package com.trevlar.slotkit.api.window;

import net.minecraft.resources.Identifier;

/**
 * The library's built-in {@link BehaviorKey}s, one constant per behavior. The
 * engine is generic, so a mod adds a behavior by declaring a key of its own.
 *
 * <ul>
 *   <li>Client tier: the observed reactions.</li>
 *   <li>Server tier, the slot author's rules: {@link #GATING} (what a slot accepts
 *       and releases, and how many), {@link #BINDING} (Curse of Binding on any
 *       slot), the eleven {@linkplain SlotOperations operations}, and
 *       {@link #MENDING}.</li>
 * </ul>
 */
public final class BehaviorKeys {

    private BehaviorKeys() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    // The slot author's rules: server tier, every slot kind.

    /**
     * What a slot accepts and releases, and its per-item stack cap: a
     * {@link SlotGate}. Default {@link SlotGate#OPEN}, exactly vanilla. SlotKit
     * enforces it once at each vanilla method that moves items.
     */
    public static final BehaviorKey<SlotGate> GATING = BehaviorKey.of(
            id("gating"), SlotGate.class, SlotGate.OPEN, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Whether Curse of Binding is enforced on this slot: a bound item cannot be
     * taken out while the player is alive, survival only, the rule vanilla gives
     * armor slots. Default {@link TriBool#FALSE}.
     */
    public static final BehaviorKey<TriBool> BINDING = BehaviorKey.of(
            id("binding"), TriBool.class, TriBool.FALSE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    // Reactions: slot kinds only, defaulting to a no-op hook. Client-observed only:
    // the client diffs a slot's synced contents each tick. There is no server-side
    // pair; 6.0.0 removed the declared one, which nothing fired.

    /**
     * Fires on the client when synced slot contents grow. UI feedback only (a flash,
     * a sound, a badge), with no authority. Client tier, so it works on any server,
     * and it fires for created slots too, whose contents sync the same way.
     * Default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_INSERT_OBSERVED = BehaviorKey.of(
            id("on_insert_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Fires on the client when synced slot contents shrink. UI feedback only, with
     * no authority. Client tier; default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_TAKE_OBSERVED = BehaviorKey.of(
            id("on_take_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    // Operations: every slot kind, defaulting to vanilla. An operation is something
    // done to a slot; a category says what the slot is. SlotOperations covers the
    // split, how a mod adds an operation of its own, and how one is blocked.
    // Vanilla's operations are split as far as they go, one key per thing a player
    // can do to a slot, so a mod can block each on its own; a settings screen groups
    // them however it likes. All eleven are TriBool, default TRUE, server tier,
    // every slot kind, and enforced at vanilla's own seams by SKOperationsMixin and
    // SKInventoryInsertMixin.

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

    /**
     * Whether items in this slot take part in XP-orb Mending. An opted-in slot joins
     * the single, fairly weighted repair pool when an orb is absorbed. Applies to
     * created and vanilla slots on the player's inventory menu. Server tier: needs
     * SlotKit on the server, whose orb seam runs it. Default {@link TriBool#FALSE}.
     */
    public static final BehaviorKey<TriBool> MENDING = BehaviorKey.of(
            id("mending"), TriBool.class, TriBool.FALSE, Tier.SERVER,
            KindTag.CREATED_SLOT, KindTag.VANILLA_SLOT);
}
