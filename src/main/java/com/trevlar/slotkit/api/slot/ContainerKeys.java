package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.KindTag;
import com.trevlar.slotkit.api.window.Tier;
import com.trevlar.slotkit.api.window.TriBool;

import net.minecraft.resources.Identifier;

/**
 * The server-tier behaviour key that Containers itself enforces. The other slot
 * rules have belonged to MenuKit since 6.0.0 ({@code BehaviorKeys.GATING},
 * {@code BINDING}), which enforces them in vanilla's code for every kind of slot.
 * Mending stays here because the code it hooks, the XP orb, is Containers'
 * ({@code SKExperienceOrbMendMixin}).
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
