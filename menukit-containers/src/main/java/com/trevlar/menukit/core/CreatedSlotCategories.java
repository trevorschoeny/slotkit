package com.trevlar.menukit.core;

import com.trevlar.menukit.inject.SlotGroupCategories;
import com.trevlar.menukit.window.Address;
import com.trevlar.menukit.window.SlotOperations;
import com.trevlar.menukit.inject.SlotGroupResolver;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
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
