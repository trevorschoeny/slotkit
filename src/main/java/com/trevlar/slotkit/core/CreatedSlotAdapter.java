package com.trevlar.slotkit.core;

import com.trevlar.slotkit.api.slot.ContainerPanel;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.group.SlotGroupId;

import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.KindTag;
import com.trevlar.slotkit.window.OwnerRef;
import com.trevlar.slotkit.window.OwnerScope;
import com.trevlar.slotkit.window.PanelAddressing;
import com.trevlar.slotkit.window.Token;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resolves a {@code CREATED_SLOT} {@link Address} to its live in-menu slot. Position
 * is read off that slot's own {@code x/y}, as for a vanilla slot. Also home to the
 * canonical created-slot {@link #addressOf} encoding that minting and resolving share.
 *
 * <h2>Created-slot identity does not depend on the menu</h2>
 *
 * A created slot (a pocket, say) is the same logical slot wherever it is shown: on the
 * survival {@code InventoryMenu}, wrapped onto the creative item-picker menu, or
 * projected onto a foreign container. Its address must not depend on the rendering
 * menu's family, or the same pocket would get different addresses in survival and
 * creative. So it roots at a constant family (the panel family) and is identified
 * only by {@code panelId}, {@code groupId} and {@code localIndex}.
 * Resolution therefore skips the menu-family check (that is for vanilla slots, which
 * belong to their menu), and the identity scan below is the presence check.
 *
 * <h2>Cache instead of a per-frame scan</h2>
 *
 * The old {@code SlotElement.resolve()} scanned {@code menu.slots} every frame for
 * every element. This keeps the same identity match but caches
 * {@code Address -> live menu index} per live menu instance. A reopen creates a new
 * menu instance, so the cache starts fresh, and a collected menu drops its entry
 * through the {@link WeakHashMap}. The cache holds only {@code int} indices, never a
 * {@code Slot} or container reference. Client thread only.
 */
public final class CreatedSlotAdapter {

    /** The adapter is stateless; the cache and encoding are static. */
    public static final CreatedSlotAdapter INSTANCE = new CreatedSlotAdapter();

    private CreatedSlotAdapter() {}


    // menu instance -> (address -> matching slot's menu index). Holds only indices.
    private static final Map<AbstractContainerMenu, Map<Address, Integer>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public @Nullable Slot resolve(AbstractContainerMenu menu, Address address) {
        Map<Address, Integer> bindings = CACHE.computeIfAbsent(menu, m -> new HashMap<>());

        // Fast path: a cached index, re-validated against the live slot's identity.
        Integer cachedIndex = bindings.get(address);
        if (cachedIndex != null) {
            CreatedSlot mk = mkcAt(menu, cachedIndex);
            if (mk != null && addressOf(mk).equals(address)) {
                return menu.slots.get(cachedIndex);
            }
            bindings.remove(address); // stale, so rescan
        }

        // Scan once, filling the binding map for every created slot, and return the
        // requested match if present. The hit is the in-menu slot (the creative
        // wrapper on creative), which is the one vanilla draws, hit-tests and routes
        // a click through.
        Slot hit = null;
        for (int i = 0; i < menu.slots.size(); i++) {
            CreatedSlot mk = mkcAt(menu, i);
            if (mk == null) continue;
            Address a = addressOf(mk);
            bindings.put(a, i);
            if (a.equals(address)) {
                hit = menu.slots.get(i);
            }
        }
        return hit;
    }

    /**
     * The canonical {@link Address} of a created slot, shared by resolution (here) and
     * minting. It depends only on the slot's identity, not on any rendering menu. It
     * nests under its panel in {@link PanelAddressing#PANEL_FAMILY}, with the same
     * {@link PanelAddressing#regKey} encoding a panel element uses, so a created slot,
     * a panel element and the panel share one owner chain and a panel-level default
     * reaches the created slot just as it reaches an element. The slot's
     * {@code groupId} and {@code localIndex} are its durable declaration token.
     */
    public static Address addressOf(CreatedSlot mk) {
        return Address.createdSlot(SlotGroupId.created(mk.panelId(), mk.groupId()), mk.getLocalIndex());
    }

    @ApiStatus.Internal

    private static @Nullable CreatedSlot mkcAt(AbstractContainerMenu menu, int index) {
        if (index < 0 || index >= menu.slots.size()) return null;
        return CreatedSlotAccess.asMKCSlot(menu.slots.get(index));
    }
}
