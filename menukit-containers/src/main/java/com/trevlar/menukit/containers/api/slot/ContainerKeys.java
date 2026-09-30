package com.trevlar.menukit.containers.api.slot;

import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.KindTag;
import com.trevlar.menukit.api.window.Tier;
import com.trevlar.menukit.api.window.TriBool;

import net.minecraft.resources.Identifier;

/**
 * The server-tier key whose seam is Containers' own. The slot author's other rules
 * are MenuKit's since 6.0.0 ({@code BehaviorKeys.GATING}, {@code BINDING}), enforced
 * by MenuKit at vanilla's seams for every slot kind (§0064). Mending stays here
 * because its seam, the XP orb, is Containers' ({@code ExperienceOrbMendMixin}).
 */
public final class ContainerKeys {

    private ContainerKeys() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("menukit", path);
    }

    /**
     * Whether items in this slot take part in XP-orb Mending: an opted-in slot joins
     * the unified, fairly weighted repair pool (§0055) when a mending orb is
     * absorbed. Created and vanilla slots on the player's inventory menu. Default
     * {@link TriBool#FALSE}.
     */
    public static final BehaviorKey<TriBool> MENDING = BehaviorKey.of(
            id("mending"), TriBool.class, TriBool.FALSE, Tier.SERVER,
            KindTag.CREATED_SLOT, KindTag.VANILLA_SLOT);
}
