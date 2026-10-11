package com.trevlar.menukit.containers.api.slot;

import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.KindTag;
import com.trevlar.menukit.api.window.Tier;
import com.trevlar.menukit.api.window.TriBool;

import net.minecraft.resources.Identifier;

/**
 * The server-tier behaviour key that Containers itself enforces. The other slot
 * rules have belonged to MenuKit since 6.0.0 ({@code BehaviorKeys.GATING},
 * {@code BINDING}), which enforces them in vanilla's code for every kind of slot.
 * Mending stays here because the code it hooks, the XP orb, is Containers'
 * ({@code ExperienceOrbMendMixin}).
 */
public final class ContainerKeys {

    private ContainerKeys() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    /**
     * Whether items in this slot take part in XP-orb Mending. An opted-in slot joins
     * the single, fairly weighted repair pool when an orb is absorbed. Applies to
     * created and vanilla slots on the player's inventory menu. Default
     * {@link TriBool#FALSE}.
     */
    public static final BehaviorKey<TriBool> MENDING = BehaviorKey.of(
            id("mending"), TriBool.class, TriBool.FALSE, Tier.SERVER,
            KindTag.CREATED_SLOT, KindTag.VANILLA_SLOT);
}
