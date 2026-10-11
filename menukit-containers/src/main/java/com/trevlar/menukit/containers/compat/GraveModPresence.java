package com.trevlar.menukit.containers.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Set;

/**
 * Detects whether any known grave-container mod is installed, meaning a mod that
 * holds a dead player's items in a persistent grave, chest or bag.
 *
 * <p>{@link com.trevlar.menukit.containers.core.attachment.PlayerDeathDropHandler}
 * uses it. When such a mod is present, the fallback death drops are flagged never to
 * despawn, so they stay beside the grave for as long as the grave does instead of
 * expiring after vanilla's 5 minutes. With no grave mod, drops keep vanilla despawn,
 * so registered slots behave exactly like a normal death drop and never outlive
 * vanilla items.
 *
 * <p>Only grave-container mods count. Keep-inventory and void-rescue mods ("Your
 * Items Are Safe", "Lenient Death") are left out on purpose: they spawn no grave, so
 * never-despawning items beside a grave that doesn't exist would bring back the same
 * mismatch this check prevents.
 *
 * <p>Detection is by mod id (the string {@link FabricLoader#isModLoaded(String)}
 * checks), computed once because mod presence is fixed for the run. The list is
 * maintained by hand. Every id was read from the mod's own {@code fabric.mod.json},
 * because slugs often differ from ids: {@code pneumono_gravestones} ships the id
 * {@code gravestones}.
 *
 * <p>A wrong or missing id only means that grave mod's players get vanilla despawn
 * (the old behaviour), never a crash. So the list errs toward more ids: a false
 * positive only makes dropped items last longer, while a false negative lets them
 * expire beside a real grave.
 */
public final class GraveModPresence {

    /**
     * Known grave-container mod ids. Forge and NeoForge-only graves can never load on
     * Fabric ({@code isModLoaded} returns false) but are listed for completeness.
     * Several entries stop below 1.21.11. They stay so the check keeps working if
     * those mods ship a newer build, or on other Minecraft versions.
     */
    private static final Set<String> GRAVE_MOD_IDS = Set.of(
            // Grave mods live on 1.21.x Fabric
            "universal-graves",    // Universal Graves (Patbox)
            "gravestones",         // Pneumono's Gravestones  (slug pneumono_gravestones)
            "gravestone",          // "Gravestone" (n8-M4x and HKS-HNS both ship this id)
            "deathchest",          // Simple Death Chest      (slug simple-death-chest)
            "deadsimplebags",      // Dead Simple Bags (death backpack)
            // The mr_* datapack-mod grave family (ids never match their slugs)
            "mr_ly_graves",        // Graves (lullaby6)
            "mr_auto_graveslite",  // Auto Graves Lite
            "mr_vanilla_graves",   // Vanilla Graves
            "mr_simple_grave",     // Simple Graves
            "mr_conures_graves",   // Very Simple Gravestones (Conure)
            // Verified Fabric grave ids that stop below 1.21.11
            "yigd",                // You're in Grave Danger
            "workinggraves",       // Working Graves (StoneLabs)
            "forgottengraves",     // Forgotten Graves (Ginsm)
            // Legacy Fabric, before 1.21 (harmless to list)
            "playergraves",        // Player Graves (OnyxStudios)
            "bettergraves",        // Better Graves (CerulanLumina)
            // Forge and NeoForge only (never load on Fabric, listed for completeness)
            "corpse",              // Corpse (henkelmax)
            "tombstone"            // Corail Tombstone (Corail31)
    );

    /** Mod presence cannot change after launch, so this is computed once. */
    private static final boolean ANY_PRESENT = compute();

    private static boolean compute() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : GRAVE_MOD_IDS) {
            if (loader.isModLoaded(id)) return true;
        }
        return false;
    }

    /** True if any known grave-container mod is installed. */
    public static boolean anyGraveModPresent() {
        return ANY_PRESENT;
    }

    private GraveModPresence() {}
}
