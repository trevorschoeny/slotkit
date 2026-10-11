package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.inject.Slots;

import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * Mints the {@link Address} of a vanilla slot. It is the one place that decides a
 * vanilla slot's identity, shared by the client resolver (render, decorate) and
 * the server gating seam, so both agree.
 *
 * <h2>Container-based when possible, menu-based as a fallback</h2>
 *
 * When {@link ContainerIdentity} resolves the slot's container, the address is
 * menu-independent: {@link #CONTAINER_FAMILY}, {@code sub(scopeId)} and the
 * container-relative index. The same physical slot then has one address whether
 * reached through its menu, by a hopper or after a reopen, with or without
 * Containers loaded. An unidentifiable container (crafting grid, horse
 * storage) falls back to a menu-based address: the menu's family and the flat menu
 * index.
 */
public final class VanillaAddressing {

    private VanillaAddressing() {}

    /** The constant root family of container-identified vanilla slots (menu-independent). */
    public static final ScreenFamilyKey CONTAINER_FAMILY =
            ScreenFamilyKey.of(Identifier.fromNamespaceAndPath("menukit", "container"));

    /**
     * The {@link Address} of {@code inMenuSlot} on {@code menu}. Library use only:
     * consumers hold addresses minted by identity, not raw slots.
     */
    @ApiStatus.Internal
    public static Address addressOf(AbstractContainerMenu menu, Slot inMenuSlot) {
        Slot target = Slots.target(inMenuSlot); // identity comes from the unwrapped creative target
        var id = ContainerIdentity.scope(target.container, target.getContainerSlot());
        if (id.isPresent()) {
            return Address.vanillaSlot(CONTAINER_FAMILY, OwnerScope.sub(id.get().scopeId()), id.get().localIndex());
        }
        return Address.vanillaSlot(WindowMint.familyOf(menu), WindowMint.scopeOf(menu), inMenuSlot.index);
    }

    /**
     * The container-based {@link Address} of a slot reached without a menu, from an
     * automation seam (hopper, dispenser) that has only {@code (container, index)}.
     * Empty when the container has no identity, and then no gating applies and the
     * interaction stays vanilla. There is no menu fallback, since automation is not
     * a menu interaction.
     */
    @ApiStatus.Internal
    public static java.util.Optional<Address> addressOf(net.minecraft.world.Container container, int containerSlotIndex) {
        return ContainerIdentity.scope(container, containerSlotIndex)
                .map(r -> Address.vanillaSlot(CONTAINER_FAMILY, OwnerScope.sub(r.scopeId()), r.localIndex()));
    }

    /** Whether {@code address} is a container-identified (menu-independent) vanilla address. */
    public static boolean isContainerAddressed(Address address) {
        return address.owner() instanceof OwnerRef.RootOwner root
                && root.family().equals(CONTAINER_FAMILY);
    }
}
