package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.api.slot.SlotGroupResolver;
import com.trevlar.menukit.api.slot.SlotGroupCategory;

import com.trevlar.menukit.api.slot.SlotGroupCategories;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.inject.CreatedGroupResolver;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes every created slot group into MenuKit core's slot-group registry —
 * the Containers half of "other mods find created slots the same way they find
 * vanilla ones."
 *
 * <p>One universal {@link SlotGroupResolver}, installed once at client init
 * through {@link SlotGroupCategories#extendEvery}: for whatever menu is being
 * resolved, walk {@code menu.slots}, and report each {@link CreatedSlot} under its
 * group's {@link com.trevlar.menukit.api.slot.SlotGroupCategory} (the creative
 * wrapper is unwrapped by {@link CreatedSlotAccess}, so the reported index is the
 * in-menu one). Groups on the player's inventory menu, projected onto a chest,
 * or wrapped on the creative tab all resolve wherever they sit; an inert group's
 * slots are skipped (§0065: a hidden thing is invisible to every observer).
 *
 * <p>Consumers never call this. They declare the category on the spec and read
 * the registry with MK types only.
 */
@ApiStatus.Internal
public final class CreatedSlotCategories {

    private CreatedSlotCategories() {}

    /** Installs the universal resolver. Called once from {@code MenuKitContainersClient}. */
    public static void install() {
        SlotGroupCategories.extendEvery(CreatedSlotCategories::resolve);
    }

    /**
     * Every created group with a live slot on {@code menu}, each reported as its
     * own contribution. Bucketed by {@code (panelId, groupId)}, NOT by category:
     * several created groups can declare the same category, and folding them
     * together would hand the anchor layer one bounding box spanning all of them —
     * the 2026-09-09 regression this split fixes. Inert groups are absent (§0065:
     * a hidden thing is invisible on every surface).
     */
    private static List<CreatedGroupResolver.Contribution> resolve(AbstractContainerMenu menu) {
        Map<String, GroupAccumulator> byGroup = new LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            CreatedSlot mk = CreatedSlotAccess.asMKCSlot(slot);
            if (mk == null || mk.isInert()) continue;
            // NUL separator — never appears in a normal id, so the composite key is
            // injective over distinct (panelId, groupId) pairs.
            String key = mk.panelId() + '\0' + mk.groupId();
            byGroup.computeIfAbsent(key, k -> new GroupAccumulator(
                            mk.panelId(), mk.groupId(), mk.getGroup().getCategory()))
                    .indices.add(i);
        }
        if (byGroup.isEmpty()) return List.of();
        List<CreatedGroupResolver.Contribution> out = new ArrayList<>(byGroup.size());
        for (GroupAccumulator g : byGroup.values()) {
            out.add(new CreatedGroupResolver.Contribution(g.panelId, g.groupId, g.category,
                    g.indices.stream().mapToInt(Integer::intValue).toArray()));
        }
        return out;
    }

    /** One group's slots, gathered as the menu is walked. */
    private static final class GroupAccumulator {
        final String panelId;
        final String groupId;
        final SlotGroupCategory category;
        final List<Integer> indices = new ArrayList<>();

        GroupAccumulator(String panelId, String groupId, SlotGroupCategory category) {
            this.panelId = panelId;
            this.groupId = groupId;
            this.category = category;
        }
    }
}
