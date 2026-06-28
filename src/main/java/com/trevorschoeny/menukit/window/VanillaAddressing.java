package com.trevorschoeny.menukit.window;

import com.trevorschoeny.menukit.inject.Slots;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Mints the {@link Address} of a vanilla slot — the single place that decides a
 * vanilla slot's identity, shared by the client resolver (render/decorate) and
 * the server gating seam, so both agree.
 *
 * <h2>Container-based when possible, menu-based as a fallback</h2>
 *
 * When the {@link VanillaSlotIdentity} port resolves the slot's container (MKC
 * present, a §0050-identifiable container), the address is MENU-INDEPENDENT:
 * {@link #CONTAINER_FAMILY} + {@code sub(scopeId)} + the container-relative index.
 * The same physical slot then has one address whether reached via its menu, a
 * hopper, or after a reopen. When the port returns empty (MK alone, or an
 * unidentifiable container), it falls back to a menu-based address (the menu's
 * family + the flat menu index) — fine, because vanilla-slot gating only exists
 * when MKC is present, so a menu-based decoration address has nothing to be
 * inconsistent with.
 */
public final class VanillaAddressing {

    private VanillaAddressing() {}

    /** The constant root family of container-identified vanilla slots (menu-independent). */
    public static final ScreenFamilyKey CONTAINER_FAMILY =
            ScreenFamilyKey.of(Identifier.fromNamespaceAndPath("menukit", "container"));

    /** The {@link Address} of {@code inMenuSlot} on {@code menu}. */
    public static Address addressOf(AbstractContainerMenu menu, Slot inMenuSlot) {
        Slot target = Slots.target(inMenuSlot); // identity off the unwrapped (creative) target
        var id = ServerTier.identity().identify(target.container, target.getContainerSlot());
        if (id.isPresent()) {
            return Address.vanillaSlot(CONTAINER_FAMILY, OwnerScope.sub(id.get().scopeId()), id.get().localIndex());
        }
        // Menu-based fallback: the menu's family + the flat menu index.
        return Address.vanillaSlot(WindowMint.familyOf(menu), WindowMint.scopeOf(menu), inMenuSlot.index);
    }

    /** Whether {@code address} is a container-identified (menu-independent) vanilla address. */
    public static boolean isContainerAddressed(Address address) {
        return address.owner() instanceof OwnerRef.RootOwner root
                && root.family().equals(CONTAINER_FAMILY);
    }
}
