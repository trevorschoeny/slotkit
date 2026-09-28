package com.trevlar.menukit.window;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The single "live slot → {@link Address}" mapping, on both sides: the client's
 * slot observers (observed reactions, {@link ObservedReactions}; interaction
 * signals, {@link WindowSignals}) and the operation seams and vetoes, which run on
 * the server too ({@link SlotOperations}, {@link SlotRef}). The name predates that;
 * 6.0.0's naming pass renames it. MK-alone the default addresses
 * vanilla slots only ({@link VanillaAddressing}); MKC installs the kind-aware
 * {@code SlotAddresses.of} so a created slot resolves to its created address. One
 * install point, one resolution rule, so two observers can never disagree on which
 * address a slot has.
 *
 * <p><b>Internal plumbing.</b> Both ends are library-owned: MKC {@link #install}s
 * its kind-aware rule and MK's client observers {@link #addressOf} live slots
 * through it. Consumers address slots by {@link Address}; they neither install nor
 * call this directly.
 */
@ApiStatus.Internal
public final class ClientSlotAddressing {

    private ClientSlotAddressing() {}

    /** Maps a live slot to its {@link Address}; swapped for the kind-aware MKC one when present. */
    @FunctionalInterface
    public interface SlotAddressFn {
        Address addressOf(AbstractContainerMenu menu, Slot slot);
    }

    private static volatile SlotAddressFn fn = VanillaAddressing::addressOf;

    /** MKC installs its kind-aware {@code SlotAddresses.of} here from common init, on both sides. */
    public static void install(SlotAddressFn impl) {
        com.trevlar.menukit.window.Declarations.requireOpen("ClientSlotAddressing.install");
        fn = Objects.requireNonNull(impl, "impl");
    }

    /** The {@link Address} of {@code slot} in {@code menu} under the installed rule. */
    public static Address addressOf(AbstractContainerMenu menu, Slot slot) {
        return fn.addressOf(menu, slot);
    }
}
