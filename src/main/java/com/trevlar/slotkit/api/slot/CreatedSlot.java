package com.trevlar.slotkit.api.slot;

import com.trevlar.slotkit.core.CreatedSlotAccess;
import com.trevlar.slotkit.core.CreatedSlotAdapter;
import com.trevlar.slotkit.api.menu.MenuPanel;
import com.trevlar.slotkit.api.storage.Storage;

import com.trevlar.slotkit.api.window.Address;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A slot that is a vanilla {@link Slot} to everything outside it. It keeps its place
 * in the hierarchy (panel, group, index) in final fields and belongs to a
 * {@link CreatedSlotGroup}.
 *
 * <p>It behaves like a vanilla slot. Every override calls {@code super}, so
 * vanilla's behaviour and other mods' mixins still apply. It is also presented like
 * one: its position is vanilla's own {@code Slot.x/y}, vanilla's slot pass draws it,
 * and vanilla's {@code getHoveredSlot} finds it (see {@code ContainerScreenLayers} in
 * MenuKit). A third party's {@code extractSlot} mixin, or any code that walks
 * {@code menu.slots} and reads {@code x/y}, treats a created slot and a vanilla slot
 * the same because they go through the same calls. The overrides:
 * <ul>
 *   <li>{@code mayPlace}: false when inert, otherwise super</li>
 *   <li>{@code mayPickup}: false when inert, otherwise super</li>
 *   <li>{@code getItem}: EMPTY when inert, otherwise super</li>
 *   <li>{@code isActive}: false when the panel is hidden, otherwise super</li>
 * </ul>
 *
 * <p>A mod that mixes into {@code Slot.mayPlace} to add a global filter sees its
 * filter run on MenuKit slots through the {@code super.mayPlace} call, and the most
 * restrictive answer wins. A mod iterating slots sees inert MenuKit slots as empty,
 * which is a valid state for any slot.
 *
 * <p>Hierarchy: Screen, then Panel, then CreatedSlotGroup, then CreatedSlot.
 */
public class CreatedSlot extends Slot {

    private final String panelId;
    private final String groupId;
    private final int localIndex;

    private final CreatedSlotGroup group;

    // Its visibility decides whether the slot is inert.
    private final MenuPanel panel;

    // Position is vanilla's own Slot.x/y and nothing else. The panel that shows
    // this slot (its SlotElement) writes the resolved position there every frame,
    // before vanilla's slot pass, so vanilla and every other mod's slot hook draw
    // and hit-test a created slot exactly as they do a vanilla slot. Position can
    // change but identity cannot: x/y are client-only and never synced. A new slot
    // sits off-screen (CreatedSlots.OFFSCREEN) until a panel shows it.

    /**
     * @param container      vanilla Container adapter (from handler construction)
     * @param containerIndex index within the container
     * @param x              screen x position
     * @param y              screen y position
     * @param group          owning CreatedSlotGroup
     * @param panel          owning panel as the menu knows it (its visibility decides inertness)
     * @param groupId        group identifier within the panel
     * @param localIndex     slot index within the group (0-based)
     */
    public CreatedSlot(Container container, int containerIndex, int x, int y,
                       CreatedSlotGroup group, MenuPanel panel, String groupId,
                       int localIndex) {
        super(container, containerIndex, x, y);
        this.group = group;
        this.panel = panel;
        this.panelId = panel.id();
        this.groupId = groupId;
        this.localIndex = localIndex;
    }

    /**
     * The created slot {@code slot} is, or {@code null} for any other slot. Sees
     * through creative's slot wrapper, so it works for a slot taken from any
     * screen's menu.
     */
    public static @Nullable CreatedSlot of(Slot slot) {
        return CreatedSlotAccess.asMKCSlot(slot);
    }

    /** Panel this slot belongs to. */
    public String panelId() { return panelId; }

    /** Group this slot belongs to within its panel. */
    public String groupId() { return groupId; }

    /** Index within the group (0-based). */
    public int getLocalIndex() { return localIndex; }

    /** The CreatedSlotGroup this slot belongs to. */
    public CreatedSlotGroup getGroup() { return group; }

    // All of this slot's behaviour lives in the engine, keyed by this address, never
    // on the slot. The identity fields are final, so the address never changes and is
    // cached lazily. Computing it twice is harmless; the value is equal either way.
    private Address cachedAddress;

    /** This slot's created {@link Address}, the same in every menu. Behaviour is resolved by it. */
    public Address address() {
        Address a = cachedAddress;
        if (a == null) {
            a = CreatedSlotAdapter.addressOf(this);
            cachedAddress = a;
        }
        return a;
    }

    /**
     * True when this slot's panel is hidden. To the outside world an inert slot looks
     * like no slot at all: getItem returns EMPTY, mayPlace returns false, and
     * shift-click skips it.
     *
     * <p>Public for third parties that want to check directly rather than rely on
     * those methods.
     */
    public boolean isInert() {
        return !panel.isShown();
    }

    // Each override calls super so other mods' mixins into Slot still run. MenuKit
    // adds to vanilla behaviour and never replaces it.

    /**
     * Whether this stack can be placed here. An inert slot refuses. Otherwise this is
     * vanilla's own answer, which MenuKit's slot gate ({@code SKSlotGateMixin}) has
     * already checked against this slot's address. Calls super, so a mixin on
     * {@code Slot.mayPlace} still runs.
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        if (isInert()) return false;
        return super.mayPlace(stack);
    }

    /** Whether items can be taken. Inert refuses; the slot gate and Curse of Binding are checked in MenuKit's mixin. */
    @Override
    public boolean mayPickup(Player player) {
        if (isInert()) return false;
        return super.mayPickup(player);
    }

    // No override for the no-argument getMaxStackSize(). A stack-size policy is
    // per item, and calling it with ItemStack.EMPTY returns 1, which would break
    // everything. Vanilla's Slot.getMaxStackSize() returns
    // container.getMaxStackSize() (99), which is the right slot-level limit.

    /**
     * Returns the item in this slot, or {@link ItemStack#EMPTY} when the panel is
     * inert, so a hidden panel's storage is never exposed.
     *
     * <p>{@link #isActive}, {@link #mayPlace} and {@link #mayPickup} returning false
     * block interaction but not reading. Without this override, anything that reads
     * {@code slot.getItem()} on a hidden slot (vanilla's {@code broadcastChanges}
     * syncing to the client, other mods' mixins on {@code Slot.getItem}, consumer code
     * walking {@code menu.slots}) would see the real contents. That would leak
     * per-player state kept in hidden-panel storage (lock state, private
     * inventories, sort state) to the client and to any mod hook.
     *
     * <p>Returning EMPTY while inert prevents that. When the panel becomes visible,
     * {@code broadcastChanges} sees the change and sends the real contents to the
     * client. The backing {@link com.trevlar.slotkit.api.storage.Storage} still holds
     * the real items, and consumer code that owns the panel can read it directly.
     *
     * <p>Inert means hidden from the world for both interaction and reading. Both
     * are needed for the library's inertness guarantee (see the Inertness and
     * SyncSafety probes in {@code /mkverify all}).
     */
    @Override
    public ItemStack getItem() {
        if (isInert()) return ItemStack.EMPTY;
        return super.getItem();
    }

    /**
     * Whether this slot is active (visible and usable).
     *
     * <p>When false, vanilla skips the slot for rendering and hover detection.
     * Together with {@link #getItem} returning EMPTY while inert, this hides the slot
     * from the world entirely.
     */
    @Override
    public boolean isActive() {
        if (isInert()) return false;
        return super.isActive();
    }
}
