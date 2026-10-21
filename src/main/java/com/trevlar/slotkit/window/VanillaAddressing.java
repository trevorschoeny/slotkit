package com.trevlar.slotkit.window;

import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.core.Slots;

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
 * reached through its menu, by a hopper or after a reopen. An unidentifiable
 * container (crafting grid, horse storage) falls back to a menu-based address: the menu's family and the flat menu
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
        // A creative wrapper stands for a slot of the player's inventory menu (its crafting
        // grid, say): name that slot, as survival's inventory does, not the wrapper, whose
        // own index is 0 for every wrapper.
        if (target != inMenuSlot) {
            return Address.vanillaSlot(WindowMint.inventoryFamily(), WindowMint.scopeOf(menu), target.index);
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

    /**
     * The live slot on {@code menu} that a {@code VANILLA_SLOT} address names (the
     * creative wrapper where the menu holds one), or {@code null} when it isn't on
     * this menu. A menu-based address matches by the menu's family and the flat
     * index; a container-based one by the address each slot on the menu mints.
     */
    @ApiStatus.Internal
    public static net.minecraft.world.inventory.@org.jspecify.annotations.Nullable Slot resolve(
            AbstractContainerMenu menu, Address address) {
        if (address.kind() != com.trevlar.slotkit.api.window.KindTag.VANILLA_SLOT) return null;
        if (!isContainerAddressed(address)) {
            if (!(address.owner() instanceof OwnerRef.RootOwner root)
                    || !(address.token() instanceof Token.IndexToken(int index))) return null;
            if (root.family().equals(WindowMint.familyOf(menu))) {
                return index < menu.slots.size() ? menu.slots.get(index) : null;
            }
            // On creative, an inventory-menu address (as addressOf mints for a wrapper, the
            // crafting grid say) names the wrapper standing for that inventory-menu slot.
            if (root.family().equals(WindowMint.inventoryFamily())) {
                for (Slot slot : menu.slots) {
                    Slot target = Slots.target(slot);
                    if (target != slot && target.index == index
                            && ContainerIdentity.scope(target.container, target.getContainerSlot()).isEmpty()) return slot;
                }
            }
            return null;
        }
        // Scans menu.slots (each slot's address is cached per menu);
        // index it per menu if a panel ever hosts hundreds of container-addressed slots.
        for (Slot slot : menu.slots) {
            if (address.equals(com.trevlar.slotkit.api.window.SlotRef.addressOf(menu, slot))) return slot;
        }
        return null;
    }

    /** Whether {@code address} is a container-identified (menu-independent) vanilla address. */
    public static boolean isContainerAddressed(Address address) {
        return address.owner() instanceof OwnerRef.RootOwner root
                && root.family().equals(CONTAINER_FAMILY);
    }
}
