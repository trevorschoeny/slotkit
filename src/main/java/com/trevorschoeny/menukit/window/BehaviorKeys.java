package com.trevorschoeny.menukit.window;

import net.minecraft.resources.Identifier;

/**
 * The library's built-in {@link BehaviorKey}s. The engine is generic, so this set
 * grows one constant at a time as each behavior's phase lands — adding a behavior
 * is adding a key here (or, for a server-tier behavior whose value type is an MKC
 * type, an equivalent constant MKC-side).
 *
 * <p><b>Phase 3a</b> defines the client-tier keys whose value types are simple
 * and known now — enough to exercise the engine MK-alone. Still owed (added with
 * their phases, by value type):
 * <ul>
 *   <li>client-tier, complex value: {@code DECORATION}, {@code HOVER},
 *       {@code ON_CLICK}, {@code PARITY} (Phase 5/6).</li>
 *   <li>server-tier (MKC-side, MKC value types): {@code GATING}, {@code QUICK_MOVE},
 *       {@code DROP_RULE}, {@code BINDING}, {@code MENDING}, {@code ON_INSERT},
 *       {@code ON_TAKE} (Phase 3c/4).</li>
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
}
