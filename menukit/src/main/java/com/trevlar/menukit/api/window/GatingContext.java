package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.ActingPlayer;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * The context a {@link SlotGate} decision runs in: who is acting.
 *
 * <p>A deep seam (a hopper transfer, vanilla's {@code moveItemStackTo}) has no player
 * in scope. MenuKit captures the acting player at the click boundary
 * ({@link ActingPlayer}), on both sides, so a nested decision can see it.
 * Automation leaves it {@code null}.
 */
public final class GatingContext {

    /** The context for the current thread: the acting player, if any. */
    public static GatingContext current() {
        return new GatingContext(ActingPlayer.current());
    }

    private final @Nullable Player actingPlayer;

    private GatingContext(@Nullable Player actingPlayer) {
        this.actingPlayer = actingPlayer;
    }

    /** Who is acting (a {@link net.minecraft.server.level.ServerPlayer} for a real interaction; null for automation). */
    public @Nullable Player actingPlayer() {
        return actingPlayer;
    }
}
