package com.trevlar.menukit.window;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.ContainerInput;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Which operation a click is being sent <em>for</em>, when a mod simulates it.
 *
 * <h2>The problem this solves</h2>
 *
 * A client-side inventory mod performs its operations by sending vanilla clicks:
 * a restock equips armour with a shift-click, a sort shuffles with plain clicks.
 * Each of those clicks lands on a vanilla seam that judges it as the gesture it
 * looks like. If a player's lock refuses shift-click but allows restock, the
 * restock's shift-click is refused as a shift-click, and the mod breaks for
 * obeying the rules. A click therefore carries the operation it serves, and the
 * seams judge the vetoes by that operation instead of the gesture
 * ({@link SlotOperations#allowsGesture}).
 *
 * <h2>Roles</h2>
 *
 * An operation that moves items has two sides, the slot being emptied and the
 * slot being filled, so a tag is a pair: the operation for taking and the one for
 * putting. Every vanilla gesture is defined with its {@link SlotOperations.Role},
 * and a tagged gesture is judged by the tag's operation for that role; a swap
 * takes and puts, so it is judged by both.
 *
 * <h2>Two threads</h2>
 *
 * The tag is a thread-local on the thread that sends the click. The client runs
 * the click once as a prediction on that thread and sees the tag directly. The
 * integrated server (singleplayer, or a LAN host's own clicks) runs it again on
 * the server thread, which cannot see the client's thread-local, so the client
 * {@link #record records} the tag as it sends the packet and the server
 * {@link #claim claims} it when that click arrives, matched by player, menu, slot,
 * button and click type. Same JVM, same player: no trust boundary is crossed. A
 * LAN guest's clicks come from another JVM, never carry a tag, and are judged as
 * the gestures they are.
 */
@ApiStatus.Internal
public final class ClickTags {

    private ClickTags() {}

    /** The operations a simulated click serves: one for the slot it takes from, one for the slot it puts into. */
    public record Tag(BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        public Tag {
            Objects.requireNonNull(take, "take");
            Objects.requireNonNull(put, "put");
        }
    }

    private static final ThreadLocal<Tag> CURRENT = new ThreadLocal<>();

    /** The tag on this thread, or {@code null} when the click is not a simulated one. */
    public static @Nullable Tag current() {
        return CURRENT.get();
    }

    /** Runs {@code clicks} with {@code tag} on this thread, restoring whatever was there. */
    static void run(Tag tag, Runnable clicks) {
        Tag outer = CURRENT.get();
        CURRENT.set(tag);
        try {
            clicks.run();
        } finally {
            restore(outer);
        }
    }

    /**
     * Puts {@code tag} on this thread for one click transaction, if there is one,
     * and returns what was there so {@link #exit} can put it back. A {@code null}
     * tag leaves the thread as it was: on the client that keeps the sender's tag.
     */
    public static @Nullable Tag enter(@Nullable Tag tag) {
        Tag outer = CURRENT.get();
        if (tag != null) CURRENT.set(tag);
        return outer;
    }

    /** Restores what {@link #enter} returned. Always called in a {@code finally}. */
    public static void exit(@Nullable Tag outer) {
        restore(outer);
    }

    private static void restore(@Nullable Tag outer) {
        if (outer == null) CURRENT.remove();
        else CURRENT.set(outer);
    }

    /**
     * The operations a tagged {@code gesture} is judged by for the vetoes, or
     * {@code null} when {@code gesture} is not a vanilla gesture (then it is asked
     * about plainly, tag or no tag).
     */
    static @Nullable List<BehaviorKey<TriBool>> carriedFor(Tag tag, BehaviorKey<TriBool> gesture) {
        if (!BehaviorKeys.VANILLA_OPERATIONS.contains(gesture)) return null;
        SlotOperations.Role role = SlotOperations.role(gesture); // one source for roles: the definition
        if (role == null) return null;
        return switch (role) {
            case TAKE -> List.of(tag.take());
            case PUT -> List.of(tag.put());
            case BOTH -> tag.take().equals(tag.put()) ? List.of(tag.take()) : List.of(tag.take(), tag.put());
        };
    }

    // ── Client → integrated server ─────────────────────────────────────────

    // A tag is recorded under the key the server will know the action by: a click
    // by (player, menu, slot, button, input), a player action (Q, Ctrl-Q, F with no
    // screen open) by (player, action).
    private record ClickKey(UUID player, int containerId, int slotId, int button, ContainerInput input) {}
    private record ActionKey(UUID player, ServerboundPlayerActionPacket.Action action) {}
    private record Pending(Object key, Tag tag, long atNanos) {}

    // The server handles a click packet within a tick or two. A tag older than this
    // belongs to a packet the server dropped (its menu closed in between) and must
    // not be claimed by a later, untagged click that happens to match.
    // ponytail: a fixed window; if a click can legitimately wait longer (a server
    // stalled past it), the click is judged as its gesture, which is the strict side.
    private static final long EXPIRY_NANOS = 5_000_000_000L;

    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    /** Client: a tagged click is about to be sent to the integrated server. */
    public static void record(UUID player, int containerId, int slotId, int button, ContainerInput input, Tag tag) {
        PENDING.add(new Pending(new ClickKey(player, containerId, slotId, button, input), tag, System.nanoTime()));
    }

    /** Client: a tagged player action (Q, Ctrl-Q, F with no screen open) is about to be sent. */
    public static void recordAction(UUID player, ServerboundPlayerActionPacket.Action action, Tag tag) {
        PENDING.add(new Pending(new ActionKey(player, action), tag, System.nanoTime()));
    }

    /**
     * Server: the tag the client recorded for this click, removed so it is used
     * once, or {@code null}. Clicks arrive in the order they were sent, so the
     * first match is this click's. Expired entries are dropped on the way.
     */
    public static @Nullable Tag claim(UUID player, int containerId, int slotId, int button, ContainerInput input) {
        return claimKey(new ClickKey(player, containerId, slotId, button, input));
    }

    /** Server: the tag the client recorded for this player action, once, or {@code null}. */
    public static @Nullable Tag claimAction(UUID player, ServerboundPlayerActionPacket.Action action) {
        return claimKey(new ActionKey(player, action));
    }

    private static synchronized @Nullable Tag claimKey(Object key) {
        long now = System.nanoTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now - p.atNanos() > EXPIRY_NANOS) {
                it.remove();
                continue;
            }
            if (p.key().equals(key)) {
                it.remove();
                return p.tag();
            }
        }
        return null;
    }

    /** Tags recorded and not yet claimed, for the probe. */
    public static List<Tag> pendingForTest() {
        List<Tag> out = new ArrayList<>();
        for (Pending p : PENDING) out.add(p.tag());
        return out;
    }
}
