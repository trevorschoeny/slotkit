package com.trevlar.menukit.containers.core;

import com.trevlar.menukit.core.SlotGroupCategory;

import com.trevlar.menukit.inject.SlotGroupCategories;
import com.trevlar.menukit.window.Address;
import com.trevlar.menukit.window.SlotOperations;
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

    // ── Address → category, for the operations cascade (both sides) ─────────
    //
    // A category's INHERENT operations (SlotOperations.inherent) resolve through a
    // GroupKey whose membership asks "is this address in that category?". The window
    // engine is menu-free, so it cannot look a slot up on a menu; this index answers
    // from the declaration instead. Filled when a group registers, on whichever side
    // registered it, and read on the server where the operation seams run.

    private static final Map<Address, SlotGroupCategory> BY_ADDRESS = new ConcurrentHashMap<>();

    /** Installs the lookup MenuKit's operation cascade resolves categories through. */
    public static void installLookup() {
        SlotOperations.installCategoryLookup(BY_ADDRESS::get);
    }

    /**
     * Records the category of one created slot, so its group's category can supply
     * operation defaults, and publishes the category to the registry. Idempotent: a
     * group re-registers on every menu construction and writes the same values.
     *
     * <p>Takes the {@link Address} rather than the identity triple on purpose. Each
     * creation path mints addresses its own way ({@code MKCContainerPanel} nests the
     * group id into the panel id; {@code MKCSlots} does not), so the caller passes
     * the address <em>it</em> minted and the index cannot drift from the address the
     * window resolves.
     */
    public static void index(Address address, SlotGroupCategory category) {
        BY_ADDRESS.put(address, category);
        SlotGroupCategories.declare(category);
    }

    /** The declared category of a created slot address, or {@code null}. */
    public static @Nullable SlotGroupCategory of(Address address) {
        return BY_ADDRESS.get(address);
    }

    /**
     * Every created group with a live slot on {@code menu}, each reported as its
     * own contribution. Bucketed by {@code (panelId, groupId)}, NOT by category:
     * several created groups can declare the same category, and folding them
     * together would hand the anchor layer one bounding box spanning all of them —
     * the 2026-09-09 regression this split fixes. Inert groups are absent (§0058:
     * a hidden thing is invisible on every surface).
     */
    private static List<CreatedGroupResolver.Contribution> resolve(AbstractContainerMenu menu) {
        Map<String, GroupAccumulator> byGroup = new LinkedHashMap<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            MKCSlot mk = MKCSlotAccess.asMKCSlot(slot);
            if (mk == null || mk.isInert()) continue;
            // NUL separator — never appears in a normal id, so the composite key is
            // injective over distinct (panelId, groupId) pairs.
            String key = mk.getPanelId() + '\0' + mk.getGroupId();
            byGroup.computeIfAbsent(key, k -> new GroupAccumulator(
                            mk.getPanelId(), mk.getGroupId(), mk.getGroup().getCategory()))
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
