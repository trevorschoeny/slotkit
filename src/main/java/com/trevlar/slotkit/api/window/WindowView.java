package com.trevlar.slotkit.api.window;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.window.PanelAddressing;
import com.trevlar.slotkit.window.VanillaAddressing;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The window's view of one open menu, which mints typed {@link WindowHandle}s. A
 * handle is a value addressing one thing; the view is how you get one for "this
 * slot on the screen in front of me".
 *
 * <h2>What it does</h2>
 *
 * Mints handles by address. {@link #slot(int)} turns a vanilla menu index into a
 * slot handle (through {@link VanillaAddressing}); {@link #slot(ModeScope, Address)} wraps one
 * a consumer already holds, checking the {@link KindTag} so a panel address (still
 * parsed from text 6.x saved) can't pass as a slot.
 *
 * <p>Setting and reading behavior on a handle goes straight to the engine
 * ({@link WindowEngine}, by address) and needs no live resolution. So it works with
 * the client tier on any server, and with SlotKit on the server for server-tier
 * behavior too.
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
     * A handle on the vanilla slot at flat menu {@code index}. For the library's
     * own resolution only. Consumers name a vanilla slot with {@link #slot(ModeScope, Address)}
     * and an address minted by identity, since a flat menu index ties the slot to
     * one menu, which is what addressing exists to avoid.
     */
    @ApiStatus.Internal
    public SlotHandle slot(int index) {
        return new SlotHandle(ModeScope.BOTH, VanillaAddressing.addressOf(menu, menu.getSlot(index)));
    }

    /** A handle on a slot named by {@code address} (vanilla or created), declaring for {@code scope}. */
    public SlotHandle slot(ModeScope scope, Address address) {
        requireKind(address, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
        return new SlotHandle(scope, address);
    }

    /**
     * {@link #slot(ModeScope, Address)} in both modes.
     *
     * @deprecated since 2.1.0, name the mode: {@code slot(ModeScope.BOTH, address)}.
     */
    @Deprecated(since = "2.1.0")
    public SlotHandle slot(Address address) {
        return slot(ModeScope.BOTH, address);
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
