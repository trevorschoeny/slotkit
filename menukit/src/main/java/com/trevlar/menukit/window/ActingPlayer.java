package com.trevlar.menukit.window;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Who is clicking, for the duration of one menu click transaction.
 *
 * <p>Vanilla's deep seams have no player in scope: {@code moveItemStackTo} (the
 * destination side of a shift-click) takes a stack and a slot range and nothing
 * else. A decision made there that depends on the player (a per-player lock, a
 * capability check) needs the player captured at the click boundary. The library
 * captures it in {@code AbstractContainerMenu.clicked} and clears it in a
 * {@code finally}, so it never outlives the click. Automation (a hopper) and any
 * path that is not a click leave it {@code null}.
 *
 * <p>Was Containers' {@code GatingContext} thread-local (5.0.0); moved here so the
 * MenuKit operation seams can see the player without Containers installed.
 */
@ApiStatus.Internal
public final class ActingPlayer {

    private ActingPlayer() {}

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    /** Capture the acting player for the duration of a click transaction. */
    public static void set(@Nullable Player player) {
        if (player != null) CURRENT.set(player);
        else CURRENT.remove();
    }

    /** Always called in a {@code finally}; never relied on a return path. */
    public static void clear() {
        CURRENT.remove();
    }

    /** The player acting on the current thread, or {@code null} outside a click. */
    public static @Nullable Player current() {
        return CURRENT.get();
    }
}
