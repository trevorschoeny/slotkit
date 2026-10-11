package com.trevlar.menukit.window;

import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotRef;
import com.trevlar.menukit.api.window.WindowSignals;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import com.trevlar.menukit.inject.Slots;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * The one mapping from a live slot to its {@link Address}, on both sides. The
 * client's slot observers use it ({@link ObservedReactions}, {@link WindowSignals}),
 * and so do the operation seams and vetoes, which run on the server too
 * ({@link SlotOperations}, {@link SlotRef}); the class name is older than that.
 * With MenuKit alone it addresses vanilla slots only ({@link VanillaAddressing}).
 * MenuKit: Containers installs the kind-aware {@code SlotAddresses.of}, so a created
 * slot resolves to its created address. With one rule installed in one place, two
 * observers can never disagree on a slot's address.
 *
 * <p>Both ends are inside the library: Containers {@link #install}s its rule and
 * MenuKit's observers call {@link #addressOf}. Consumers address slots by
 * {@link Address} and never call this.
 */
@ApiStatus.Internal
public final class ClientSlotAddressing {

    private ClientSlotAddressing() {}

    /** Maps a live slot to its {@link Address}. Replaced by Containers' kind-aware rule when it is present. */
    @FunctionalInterface
    public interface SlotAddressFn {
        Address addressOf(AbstractContainerMenu menu, Slot slot);

        /**
         * The address of a slot reached with no menu in hand (the slot-level gate
         * seam), or {@code null} when its container has no identity. The default
         * knows vanilla slots; Containers' rule answers a created slot's own address.
         */
        default @Nullable Address addressOf(Slot slot) {
            Slot target = Slots.target(slot);
            return VanillaAddressing.addressOf(target.container, target.getContainerSlot()).orElse(null);
        }
    }

    private static volatile SlotAddressFn fn = VanillaAddressing::addressOf;

    /** Containers installs its kind-aware {@code SlotAddresses.of} here from common init, on both sides. */
    public static void install(SlotAddressFn impl) {
        com.trevlar.menukit.api.window.Declarations.requireOpen("ClientSlotAddressing.install");
        fn = Objects.requireNonNull(impl, "impl");
    }

    /** The {@link Address} of {@code slot} in {@code menu} under the installed rule. */
    public static Address addressOf(AbstractContainerMenu menu, Slot slot) {
        return fn.addressOf(menu, slot);
    }

    /** The {@link Address} of {@code slot} with no menu in hand, or {@code null}. */
    public static @Nullable Address addressOf(Slot slot) {
        return fn.addressOf(slot);
    }
}
