package com.trevorschoeny.menukit.window;

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
 *   <li>client-tier, complex value: {@code DECORATION}, {@code HOVER},
 *       {@code ON_CLICK}, {@code PARITY} (Phase 5/6).</li>
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
     * Whether an addressable thing is shown. Client-tier, every kind. Default
     * {@link TriBool#TRUE} — visible unless a declaration hides it.
     */
    public static final BehaviorKey<TriBool> VISIBILITY = BehaviorKey.of(
            id("visibility"), TriBool.class, TriBool.TRUE, Tier.CLIENT,
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
}
