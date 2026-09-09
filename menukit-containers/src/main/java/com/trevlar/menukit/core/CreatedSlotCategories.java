package com.trevlar.menukit.core;

import com.trevlar.menukit.inject.SlotGroupCategories;
import com.trevlar.menukit.inject.SlotGroupResolver;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes every created slot group into MenuKit core's slot-group registry —
 * the Containers half of "other mods find created slots the same way they find
 * vanilla ones."
 *
 * <p>One universal {@link SlotGroupResolver}, installed once at client init
 * through {@link SlotGroupCategories#extendEvery}: for whatever menu is being
 * resolved, walk {@code menu.slots}, and report each {@link MKCSlot} under its
 * group's {@link com.trevlar.menukit.core.SlotGroupCategory} (the creative
 * wrapper is unwrapped by {@link MKCSlotAccess}, so the reported index is the
 * in-menu one). Groups on the player's inventory menu, projected onto a chest,
 * or wrapped on the creative tab all resolve wherever they sit; an inert group's
 * slots are skipped (§0058: a hidden thing is invisible to every observer).
 *
 * <p>Consumers never call this. They declare the category on the spec and read
 * the registry with MK types only.
 */
@ApiStatus.Internal
public final class CreatedSlotCategories {

    private CreatedSlotCategories() {}

    /** Installs the universal resolver. Called once from {@code MKCClient}. */
    public static void install() {
        SlotGroupCategories.extendEvery(CreatedSlotCategories::resolve);
    }

    private static Map<SlotGroupCategory, int[]> resolve(AbstractContainerMenu menu) {
        Map<SlotGroupCategory, List<Integer>> byCategory = new HashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            MKCSlot mk = MKCSlotAccess.asMKCSlot(slot);
            if (mk == null || mk.isInert()) continue;
            byCategory.computeIfAbsent(mk.getGroup().getCategory(), k -> new ArrayList<>()).add(i);
        }
        if (byCategory.isEmpty()) return Map.of();
        Map<SlotGroupCategory, int[]> out = new HashMap<>();
        for (Map.Entry<SlotGroupCategory, List<Integer>> e : byCategory.entrySet()) {
            out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }
}
