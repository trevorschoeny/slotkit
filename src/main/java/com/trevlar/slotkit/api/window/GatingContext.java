package com.trevlar.slotkit.api.window;

import com.trevlar.slotkit.window.ActingPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * The context a {@link SlotGate} decision runs in: who is acting, and on which slot.
 *
 * <p>A deep seam (a hopper transfer, vanilla's {@code moveItemStackTo}) has no player
 * in scope. SlotKit captures the acting player at the click boundary
 * ({@link ActingPlayer}), on both sides, so a nested decision can see it.
 * Automation leaves it {@code null}.
 */
public final class GatingContext {

    // The slot a gate is judging on this thread, set around each slot-level decision.
    private static final ThreadLocal<Slot> JUDGING = new ThreadLocal<>();

    /** The context for the current thread: the acting player, and the slot judged, if any. */
    public static GatingContext current() {
        return new GatingContext(ActingPlayer.current(), JUDGING.get());
    }

    private final @Nullable Player actingPlayer;
    private final @Nullable Slot slot;

    private GatingContext(@Nullable Player actingPlayer, @Nullable Slot slot) {
        this.actingPlayer = actingPlayer;
        this.slot = slot;
    }

    /**
     * The slot the gate is judging, as the menu holds it (creative's wrapper on creative),
     * or {@code null} for automation, which judges a container index with no slot. What a
     * gate on an on-demand group reads its instance from ({@code OnDemandGroup.instanceOf}).
     * Since 2.1.0.
     */
    public @Nullable Slot slot() {
        return slot;
    }

    /** Library use: runs one gate decision on {@code slot}, so {@link #current()} names it. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static <T> T judging(Slot slot, java.util.function.Supplier<T> decision) {
        Slot before = JUDGING.get();
        JUDGING.set(slot);
        try {
            return decision.get();
        } finally {
            if (before == null) JUDGING.remove(); else JUDGING.set(before);
        }
    }

    /** Who is acting (a {@link net.minecraft.server.level.ServerPlayer} for a real interaction; null for automation). */
    public @Nullable Player actingPlayer() {
        return actingPlayer;
    }
}
