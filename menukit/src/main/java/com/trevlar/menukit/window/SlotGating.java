package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.GatingContext;
import com.trevlar.menukit.api.window.SlotGate;
import com.trevlar.menukit.api.window.SlotRef;
import com.trevlar.menukit.api.window.WindowEngine;
import com.trevlar.menukit.inject.Slots;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The gate decision every seam asks: resolve {@link BehaviorKeys#GATING} (and
 * {@link BehaviorKeys#BINDING}) for a slot by its {@link Address} and apply it.
 * One question per slot per vanilla method (§0064):
 *
 * <ul>
 *   <li>{@code Slot.mayPlace}, {@code mayPickup}, {@code getMaxStackSize}: the
 *       slot-level seam, which every path consults (a click's {@code safeInsert}
 *       and {@code tryRemove}, the empty pass of a shift-click, pick-all, the
 *       creative bridge). A created slot resolves here too, through the installed
 *       addressing rule, so it no longer gates itself a second time.</li>
 *   <li>The merge pass of {@code moveItemStackTo} never asks {@code mayPlace}, so
 *       it asks {@link #mayPlaceOnMove} once per slot instead.</li>
 *   <li>Hoppers and dispensers, which reach a slot by container and index.</li>
 * </ul>
 *
 * <p>When nobody has declared any server behaviour, every method returns at once and
 * vanilla interaction pays nothing: the slots nobody touches stay exactly vanilla.
 *
 * <p>Internal plumbing: every caller is a MenuKit seam. Consumers declare a gate with
 * {@code Window.slot(address).gate(...)} and never call these.
 */
@ApiStatus.Internal
public final class SlotGating {

    private SlotGating() {}

    // ── Slot-level: no menu in hand, the slot's own address ────────────────

    /** Whether {@code stack} may be placed into {@code slot}. */
    public static boolean mayPlaceAt(Slot slot, ItemStack stack) {
        if (!WindowEngine.hasServerDeclarations()) return true;
        SlotGate gate = gateOf(slot);
        return gate == SlotGate.OPEN || gate.mayPlace(stack, GatingContext.current());
    }

    /** Whether {@code player} may take from {@code slot}: Curse of Binding first, then the gate. */
    public static boolean mayPickupAt(Slot slot, Player player) {
        if (!WindowEngine.hasServerDeclarations()) return true;
        Address address = ClientSlotAddressing.addressOf(slot);
        if (address == null) return true;
        if (bindingDeniesPickup(address, slot, player)) return false;
        SlotGate gate = WindowEngine.resolve(address, BehaviorKeys.GATING);
        return gate == SlotGate.OPEN || gate.mayPickup(player, GatingContext.current());
    }

    /** The per-item stack cap for {@code slot}, clamped to {@code vanillaMax}. */
    public static int maxStackAt(Slot slot, ItemStack stack, int vanillaMax) {
        if (!WindowEngine.hasServerDeclarations()) return vanillaMax;
        SlotGate gate = gateOf(slot);
        return gate == SlotGate.OPEN ? vanillaMax : Math.min(gate.maxStackSize(stack, vanillaMax), vanillaMax);
    }

    // ── The merge pass of a shift-click: the menu is in hand ───────────────

    /**
     * Whether {@code stack} may merge into {@code slot} during {@code moveItemStackTo}.
     * The merge pass folds into non-empty slots without asking {@code mayPlace}, so
     * without this a shift-click would top up a stack in a slot the gate forbids.
     */
    public static boolean mayPlaceOnMove(AbstractContainerMenu menu, Slot slot, ItemStack stack) {
        if (!WindowEngine.hasServerDeclarations()) return true;
        SlotGate gate = WindowEngine.resolve(SlotRef.addressOf(menu, slot), BehaviorKeys.GATING);
        return gate == SlotGate.OPEN || gate.mayPlace(stack, GatingContext.current());
    }

    // ── Automation: container and index, no menu, no player ────────────────

    /** Whether automation may insert {@code stack} into a placed container's slot. */
    public static boolean mayPlaceInto(Container container, int slotIndex, ItemStack stack) {
        if (!WindowEngine.hasServerDeclarations()) return true;
        SlotGate gate = automationGate(container, slotIndex);
        return gate == SlotGate.OPEN || gate.mayPlace(stack, GatingContext.current());
    }

    /** Whether automation may extract from a placed container's slot. Nobody is acting, so nobody is exempt. */
    public static boolean mayExtractFrom(Container container, int slotIndex) {
        if (!WindowEngine.hasServerDeclarations()) return true;
        SlotGate gate = automationGate(container, slotIndex);
        return gate == SlotGate.OPEN || gate.mayPickup(null, GatingContext.current());
    }

    // ── internals ──────────────────────────────────────────────────────────

    private static SlotGate gateOf(Slot slot) {
        Address address = ClientSlotAddressing.addressOf(slot);
        return address == null ? SlotGate.OPEN : WindowEngine.resolve(address, BehaviorKeys.GATING);
    }

    private static SlotGate automationGate(Container container, int slotIndex) {
        return VanillaAddressing.addressOf(container, slotIndex)
                .map(address -> WindowEngine.resolve(address, BehaviorKeys.GATING))
                .orElse(SlotGate.OPEN);
    }

    /**
     * Curse of Binding: with {@link BehaviorKeys#BINDING} set, a bound item
     * ({@code PREVENT_ARMOR_CHANGE}) cannot be taken out while the player is alive,
     * survival only. The rule vanilla gives armour slots, on any slot that asks.
     */
    private static boolean bindingDeniesPickup(Address address, Slot slot, @Nullable Player player) {
        if (!WindowEngine.resolve(address, BehaviorKeys.BINDING).asBoolean()) return false;
        if (player == null || player.hasInfiniteMaterials()) return false;
        return EnchantmentHelper.has(Slots.target(slot).getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }
}
