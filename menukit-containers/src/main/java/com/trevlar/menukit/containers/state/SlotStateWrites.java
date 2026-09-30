package com.trevlar.menukit.containers.state;

import com.trevlar.menukit.containers.api.state.SlotStateChannel;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The server's judgement of a slot-state write a client sent (§0067), as one pure
 * function so its order is provable without a server ({@code :validator-mkc:trustCheck}).
 *
 * <p>In order, each step refusing what fails it:
 * <ol>
 *   <li>the channel is registered;</li>
 *   <li>its {@code StreamCodec} parses the value (the canonical re-encoding is what
 *       gets stored);</li>
 *   <li>the write names the player's open menu ({@code containerId});</li>
 *   <li>the slot is on it and active, and the menu is still valid for the player;</li>
 *   <li>the channel's {@link SlotStateChannel.CanWrite} allows this player at this slot
 *       (default deny for SHARED);</li>
 *   <li>the player is within the write rate ({@link Rate});</li>
 *   <li>a value equal to the channel's default removes the entry instead of storing it.</li>
 * </ol>
 * The record lists {@code canWrite} before the menu and slot checks; it is asked after
 * them here because it is handed the slot, and a slot exists only once the menu
 * matches. Every check must pass either way.
 */
@ApiStatus.Internal
public final class SlotStateWrites {

    private SlotStateWrites() {}

    /** What the server does with a write. */
    public enum Verdict {
        STORE, REMOVE,
        UNKNOWN_CHANNEL, MALFORMED, WRONG_MENU, INACTIVE_SLOT, DENIED, RATE_LIMITED;

        /** Whether the write changes stored state. */
        public boolean accepted() {
            return this == STORE || this == REMOVE;
        }
    }

    /** A judged write: the verdict, and the parsed value when it got that far. */
    public record Judged<T>(Verdict verdict, @Nullable T value) {}

    /**
     * Judges one write. Each input is asked only when the steps before it passed, so a
     * refused write never reaches the consumer's {@code canWrite} or spends the rate.
     *
     * @param channel     the registered channel, or {@code null} for an unknown id
     * @param decode      parses the sent bytes with the channel's codec
     * @param menuMatches whether the write names the player's open menu
     * @param slotLive    whether the slot is on that menu, active, and the menu still valid
     * @param canWrite    the channel's writer rule, for this player at this slot
     * @param withinRate  takes one write from the player's allowance
     */
    public static <T> Judged<T> judge(@Nullable SlotStateChannel<T> channel,
                                      Function<SlotStateChannel<T>, Optional<T>> decode,
                                      BooleanSupplier menuMatches, BooleanSupplier slotLive,
                                      Predicate<SlotStateChannel<T>> canWrite, BooleanSupplier withinRate) {
        if (channel == null) return new Judged<>(Verdict.UNKNOWN_CHANNEL, null);
        Optional<T> value = decode.apply(channel);
        if (value.isEmpty()) return new Judged<>(Verdict.MALFORMED, null);
        T v = value.get();
        if (!menuMatches.getAsBoolean()) return new Judged<>(Verdict.WRONG_MENU, v);
        if (!slotLive.getAsBoolean()) return new Judged<>(Verdict.INACTIVE_SLOT, v);
        if (!canWrite.test(channel)) return new Judged<>(Verdict.DENIED, v);
        if (!withinRate.getAsBoolean()) return new Judged<>(Verdict.RATE_LIMITED, v);
        return new Judged<>(Objects.equals(v, channel.defaultValue()) ? Verdict.REMOVE : Verdict.STORE, v);
    }

    /**
     * A per-player write allowance: a bucket of {@code burst} writes refilled at
     * {@code perSecond}. A lock drag across a double chest is 54 writes, inside the
     * default burst; a client that keeps sending faster than the refill is refused
     * until it slows. Bounded: one entry per online player, dropped on disconnect.
     */
    public static final class Rate {
        private final double burst;
        private final double perMilli;
        private final ConcurrentHashMap<UUID, double[]> buckets = new ConcurrentHashMap<>(); // {tokens, lastMillis}

        public Rate(int burst, int perSecond) {
            this.burst = burst;
            this.perMilli = perSecond / 1000.0;
        }

        /** Takes one write from {@code player}'s allowance at {@code nowMillis}; false when it is spent. */
        public boolean tryWrite(UUID player, long nowMillis) {
            double[] b = buckets.computeIfAbsent(player, p -> new double[]{burst, nowMillis});
            synchronized (b) {
                b[0] = Math.min(burst, b[0] + (nowMillis - b[1]) * perMilli);
                b[1] = nowMillis;
                if (b[0] < 1) return false;
                b[0] -= 1;
                return true;
            }
        }

        /** Forgets a player (on disconnect). */
        public void forget(UUID player) {
            buckets.remove(player);
        }
    }

    /** The server's one allowance: 64 writes at once, 32 a second after. */
    public static final Rate RATE = new Rate(64, 32);
}
