package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.group.SlotGroupResolver;
import com.trevlar.slotkit.api.group.SlotGroupCategory;

import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.core.CreatedGroupResolver;

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
 * Publishes every created slot group into MenuKit core's slot-group registry, so other
 * mods find created slots the same way they find vanilla ones.
 *
 * <p>One universal {@link SlotGroupResolver}, installed once at init through
 * {@link SlotGroupCategories#extendEvery}. For whatever menu is being resolved, it
 * walks {@code menu.slots} and reports each {@link CreatedSlot} under its group's
 * {@link com.trevlar.slotkit.api.group.SlotGroupCategory}. {@link CreatedSlotAccess}
 * unwraps the creative wrapper, so the reported index is the in-menu one. Groups on
 * the player's inventory menu, projected onto a chest or wrapped on the creative tab
 * all resolve wherever they sit. An inert group's slots are skipped, since a hidden
 * thing is invisible to every observer.
 *
 * <p>Consumers never call this. They declare the category on the spec and read the
 * registry with MenuKit types only.
 */
@ApiStatus.Internal
public final class CreatedSlotCategories {

    private CreatedSlotCategories() {}

    /**
     * Every created group with a live slot on {@code menu}, each reported as its
     * own contribution. Bucketed by {@code (panelId, groupId)}, not by category:
     * several created groups can declare the same category, and folding them
     * together would hand the anchor layer one bounding box spanning all of them
     * (a past regression). Inert groups are left out.
     */
    public static List<CreatedGroupResolver.Contribution> resolve(AbstractContainerMenu menu) {
        Map<String, GroupAccumulator> byGroup = new LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            CreatedSlot mk = CreatedSlotAccess.asMKCSlot(slot);
            if (mk == null || mk.isInert()) continue;
            // NUL never appears in a normal id, so distinct (panelId, groupId) pairs
            // always give distinct keys.
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
