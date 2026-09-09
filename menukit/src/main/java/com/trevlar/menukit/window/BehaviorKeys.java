package com.trevlar.menukit.window;

import net.minecraft.resources.Identifier;

/**
 * The library's built-in {@link BehaviorKey}s. The engine is generic, so this set
 * grows one constant at a time as each behavior's phase lands — adding a behavior
 * is adding a key here (or, for a server-tier behavior whose value type is an MKC
 * type, an equivalent constant MKC-side).
 *
 * <p><b>Phase 3a</b> defines the client-tier keys whose value types are simple
 * and known now — enough to exercise the engine MK-alone. <b>Phase 4</b> adds the
 * reactive verbs: their value type ({@link ReactiveHook}) is an MK type, so all
 * four reaction keys live here — the SERVER-tier {@code ON_INSERT}/{@code ON_TAKE}
 * and the CLIENT-tier observed variants — letting an MK-alone consumer name even
 * the server verbs (only the firing of the server ones requires MKC). Still owed
 * (added with their phases, by value type):
 * <ul>
 *   <li>client-tier, complex value: {@code HOVER}, {@code ON_CLICK},
 *       {@code PARITY} (Phase 5/6). ({@code DECORATION} is no longer owed: since
 *       4.0.0 vanilla draws every slot, so vanilla's own decoration path reaches
 *       created slots.)</li>
 *   <li>server-tier whose value type is an MKC type (so MKC-side in
 *       {@code MKCBehaviorKeys}): {@code GATING}, {@code QUICK_MOVE},
 *       {@code DROP_RULE}, {@code BINDING}, {@code MENDING}.</li>
 * </ul>
 */
public final class BehaviorKeys {

    private BehaviorKeys() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    /**
     * Whether an addressable thing is shown — a client-side {@link VisibilityRule}
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

    /**
     * Whether a panel makes what it covers inert. Client-tier, panels only.
     * Default {@link TriBool#FALSE} (a panel is not inert-making unless declared).
     */
    public static final BehaviorKey<TriBool> INERTNESS = BehaviorKey.of(
            id("inertness"), TriBool.class, TriBool.FALSE, Tier.CLIENT, KindTag.PANEL);

    // ── Reactive verbs (Phase 4) — slot kinds only; default = no-op hook ──────

    /**
     * Fires when a slot gains content, on the server inside the menu transaction
     * (authoritative; may have game-state effects). SERVER tier → fires only with
     * MKC present (the firing seams are the architecture's named owed gap). Default
     * {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_INSERT = BehaviorKey.of(
            id("on_insert"), ReactiveHook.class, ReactiveHook.NONE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Fires when a slot loses content, on the server inside the menu transaction.
     * SERVER tier; default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_TAKE = BehaviorKey.of(
            id("on_take"), ReactiveHook.class, ReactiveHook.NONE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Fires on the client when synced slot contents grow — pure UI feedback (flash,
     * sound, badge), no authority. CLIENT tier → MK-alone capable, and fires for
     * created slots too (their contents sync identically). Default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_INSERT_OBSERVED = BehaviorKey.of(
            id("on_insert_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Fires on the client when synced slot contents shrink — pure UI feedback, no
     * authority. CLIENT tier; default {@link ReactiveHook#NONE}.
     */
    public static final BehaviorKey<ReactiveHook> ON_TAKE_OBSERVED = BehaviorKey.of(
            id("on_take_observed"), ReactiveHook.class, ReactiveHook.NONE, Tier.CLIENT,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    // ── Operations — every slot kind; default = vanilla (participates) ────────
    //
    // An OPERATION is something done TO a slot in bulk, as opposed to a CATEGORY,
    // which says what the slot IS. See {@link SlotOperations} for the split and for
    // how a mod adds an operation of its own. Vanilla ships three: shift-click
    // (MKC's QUICK_MOVE key), and the two below, each with a menu-level seam —
    // double-click COLLECT (PICKUP_ALL sweeps every slot holding the carried type;
    // AbstractContainerMenu.canTakeItemForPickAll) and DRAG FILL (QUICK_CRAFT
    // spreads the carried stack across dragged-over slots; canDragTo).
    //
    // These two are declared here, MK-side, so an MK-only consumer can name them on
    // a vanilla slot (a locked slot that must not be swept, say); only the
    // ENFORCEMENT needs MKC, which injects at those two vanilla seams.

    /**
     * Operation: whether vanilla's double-click collect may take from this slot.
     * SERVER tier (the seam runs in {@code doClick}, both sides; MKC enforces).
     * Default {@link TriBool#TRUE}: vanilla sweeps it.
     */
    public static final BehaviorKey<TriBool> COLLECT = BehaviorKey.of(
            id("collect"), TriBool.class, TriBool.TRUE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

    /**
     * Operation: whether vanilla's drag-fill (spreading a carried stack across
     * slots) may place into this slot. SERVER tier; MKC enforces. Default
     * {@link TriBool#TRUE}.
     */
    public static final BehaviorKey<TriBool> DRAG_FILL = BehaviorKey.of(
            id("drag_fill"), TriBool.class, TriBool.TRUE, Tier.SERVER,
            KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
}
