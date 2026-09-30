package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.ActingPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The context a {@link SlotGate} decision runs in: who is acting, and whether that
 * player's client can see slot state.
 *
 * <h2>Acting player</h2>
 *
 * A deep seam (a hopper transfer, vanilla's {@code moveItemStackTo}) has no player
 * in scope. MenuKit captures the acting player at the click boundary
 * ({@link ActingPlayer}), on both sides, so a nested decision can see it.
 * Automation leaves it {@code null}.
 *
 * <h2>The capability port</h2>
 *
 * Whether a server player's client can see slot state is a fact only Containers
 * knows (it owns the slot-state channel). Containers installs the answer at init;
 * MenuKit alone answers {@code true}, the enforcing direction, because a gate that
 * cannot tell must not open.
 */
public final class GatingContext {

    /** Whether {@code player}'s client can receive slot state. Containers installs this. */
    @FunctionalInterface
    public interface Capability {
        boolean canSeeSlotState(ServerPlayer player);
    }

    private static volatile Capability capability = player -> true;

    /** Containers installs its slot-state capability check here, from common init. */
    @ApiStatus.Internal
    public static void installCapability(Capability impl) {
        Declarations.requireOpen("GatingContext.installCapability");
        capability = Objects.requireNonNull(impl, "impl");
    }

    /** The context for the current thread: the acting player, if any. */
    public static GatingContext current() {
        return new GatingContext(ActingPlayer.current());
    }

    private final @Nullable Player actingPlayer;

    private GatingContext(@Nullable Player actingPlayer) {
        this.actingPlayer = actingPlayer;
    }

    /** Who is acting (a {@link ServerPlayer} for a real interaction; null for automation). */
    public @Nullable Player actingPlayer() {
        return actingPlayer;
    }

    /**
     * Whether the acting player can see slot state. A gate whose effect is invisible
     * to a client without Containers may choose to open for that player, since it
     * cannot show them why it refused (§0055). {@code true} for automation, for the
     * client's own prediction, and for a capable server player: the enforcing
     * direction. Only a server player whose client lacks Containers is {@code false}.
     */
    public boolean actingPlayerCapable() {
        return !(actingPlayer instanceof ServerPlayer sp) || capability.canSeeSlotState(sp);
    }
}
