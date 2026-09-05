package com.trevlar.menukit.window;

import net.minecraft.world.item.ItemStack;

/**
 * The fire-entry for reactive verbs — the one place a contents change is turned
 * into a {@link ReactiveHook} invocation, going through the same {@link WindowEngine}
 * cascade as every behavior and bounded by the same {@link ReactionGuard}.
 *
 * <h2>Explicit verbs, not a guess</h2>
 *
 * Insert and take are separate calls rather than one auto-classified entry,
 * because the caller — a firing seam — knows the exact semantics the boundary
 * can't recover: a hopper insert is unambiguously an insert; a click-swap is
 * <em>both</em> a take of the old stack and an insert of the new (architecture
 * Part 2 §6), so a swap-capable seam calls {@link #fireTake} then {@link #fireInsert}.
 * No lossy net-count heuristic is baked in here.
 *
 * <h2>Two tiers</h2>
 *
 * <ul>
 *   <li><b>Server-authoritative</b> ({@link #fireInsert}/{@link #fireTake},
 *       {@code server=true}) — resolves {@code ON_INSERT}/{@code ON_TAKE} (SERVER
 *       tier) and routes invocation through the {@link ReactiveDispatch} port, so
 *       MK-alone no-ops and MKC owns server-thread/transaction semantics.</li>
 *   <li><b>Client-observed</b> ({@code server=false}) — resolves the observed
 *       variants (CLIENT tier) and invokes directly; MK-alone capable, pure UI
 *       feedback, no authority, fires for created slots too.</li>
 * </ul>
 *
 * <h2>"A rejected insert must NOT fire onInsert"</h2>
 *
 * Upheld at the call site: a seam fires only after a mutation has committed
 * (gating runs <em>before</em> and vetoes; reactions run <em>after</em>). A
 * blocked interaction never reaches here.
 *
 * <h2>The owed gap</h2>
 *
 * The firing <em>seams</em> (which vanilla menu methods to intercept per menu
 * type; the client synced-change detector) are the architecture's named owed
 * implementation, verified in-game. This class is the boundary they call; it is
 * complete and bounded now, so wiring a seam later is a single call.
 */
public final class WindowReactions {

    private WindowReactions() {}

    /**
     * Fire the insert reaction for a committed gain of content at {@code address}.
     * No-op when no hook is bound or (server tier) when MKC is absent. Bounded by
     * the {@link ReactionGuard}.
     *
     * @param server {@code true} = server-authoritative ({@code ON_INSERT}, routed
     *               through the dispatch port); {@code false} = client-observed.
     */
    public static void fireInsert(Address address, ItemStack before, ItemStack after, ReactCause cause, boolean server) {
        fire(address, server ? BehaviorKeys.ON_INSERT : BehaviorKeys.ON_INSERT_OBSERVED,
                before, after, cause, server);
    }

    /** Fire the take reaction for a committed loss of content at {@code address}. */
    public static void fireTake(Address address, ItemStack before, ItemStack after, ReactCause cause, boolean server) {
        fire(address, server ? BehaviorKeys.ON_TAKE : BehaviorKeys.ON_TAKE_OBSERVED,
                before, after, cause, server);
    }

    // ── internal: resolve, guard, invoke ────────────────────────────────────

    private static void fire(Address address, BehaviorKey<ReactiveHook> key,
                             ItemStack before, ItemStack after, ReactCause cause, boolean server) {
        ReactiveHook hook = WindowEngine.resolve(address, key);
        if (hook == ReactiveHook.NONE) return; // nobody reacts here — zero cost

        ReactEvent event = ReactEvent.snapshot(address, before, after, cause);
        ReactionGuard.run(address, key, () -> {
            if (server) {
                ServerTier.dispatch().fire(hook, event); // server-tier: route through MKC
            } else {
                hook.react(event);                       // client-tier: fire directly
            }
        });
    }
}
