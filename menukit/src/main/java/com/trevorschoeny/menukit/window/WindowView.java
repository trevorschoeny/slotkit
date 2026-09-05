package com.trevorschoeny.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The per-session facade of THE ONE WINDOW — bound to the current concrete menu,
 * it mints typed {@link WindowHandle}s (architecture Part 2 §2). A handle is a
 * value addressing one thing; the view is how you obtain one for "this slot on the
 * screen in front of me."
 *
 * <h2>What it does today</h2>
 *
 * Mints handles by address. {@link #slot(int)} turns a vanilla menu index into a
 * slot handle (via {@link VanillaAddressing}); the address-taking minters wrap an
 * address a consumer already holds (e.g. a created slot's address, or a panel's),
 * checking the {@link KindTag} so a panel address can't masquerade as a slot.
 *
 * <p>Behavior set/get on a handle is pure engine ({@link WindowEngine}, by
 * address) and needs no live resolution — so the SET API is fully usable now,
 * MK-alone, for client-tier behavior and (with MKC present) server-tier behavior.
 * Live resolution (reading a slot's contents, decoration draw) and the
 * created-slot/panel/element <em>minting by identity</em> arrive with the
 * panel/element resolver wiring; this view grows there, additively.
 */
public final class WindowView {

    private final AbstractContainerMenu menu;

    private WindowView(AbstractContainerMenu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    /** A view over {@code menu}. */
    public static WindowView of(AbstractContainerMenu menu) {
        return new WindowView(menu);
    }

    /**
     * A handle on the vanilla slot at flat menu {@code index}.
     *
     * <p><b>Internal plumbing.</b> The address-only public way to name a vanilla
     * slot is {@link #slot(Address)} (with an address minted by identity). This
     * int-index minter exists for the library's own resolution path and is not a
     * consumer surface — a flat menu index is exactly the menu-coupling THE ONE
     * WINDOW's addressing replaces.
     */
    @ApiStatus.Internal
    public SlotHandle slot(int index) {
        return new SlotHandle(VanillaAddressing.addressOf(menu, menu.getSlot(index)));
    }

    /** A handle on a slot named by {@code address} (vanilla or created). */
    public SlotHandle slot(Address address) {
        requireKind(address, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
        return new SlotHandle(address);
    }

    /** A handle on a panel element named by {@code address}. */
    public ElementHandle element(Address address) {
        requireKind(address, KindTag.PANEL_ELEMENT);
        return new ElementHandle(address);
    }

    /** A handle on a panel named by {@code address}. */
    public PanelHandle panel(Address address) {
        requireKind(address, KindTag.PANEL);
        return new PanelHandle(address);
    }

    // ── Identity minters (menu-independent: the panel subtree roots at a constant
    //    family, so these need no live screen — one address everywhere) ──────────

    /** A handle on the panel with id {@code panelId} (its own visibility/opacity/inertness). */
    public PanelHandle panel(String panelId) {
        return new PanelHandle(PanelAddressing.ofPanel(panelId));
    }

    /** A handle on element {@code elementDeclId} within panel {@code panelId}. */
    public ElementHandle element(String panelId, String elementDeclId) {
        return new ElementHandle(PanelAddressing.ofElement(panelId, elementDeclId));
    }

    private static void requireKind(Address address, KindTag... allowed) {
        Objects.requireNonNull(address, "address");
        for (KindTag k : allowed) {
            if (address.kind() == k) return;
        }
        throw new IllegalArgumentException(
                "Address kind " + address.kind() + " is not one of " + java.util.Arrays.toString(allowed));
    }
}
