package com.trevlar.slotkit.api.slot;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets a consumer add extra items to the XP repair pool, beyond the registered slots
 * opted in by address through {@code ContainerKeys.MENDING}.
 *
 * <p>A consumer like Inventory Max may want to mend any item in the inventory.
 * Without this hook it would need its own {@code ExperienceOrb} mixin, and the two
 * would compete for the same per-orb repair, spending the XP twice. A {@link Provider}
 * registered here puts those items in the same pool Containers already builds, so one
 * orb still repairs one item.
 *
 * <p>Containers never decides to mend the vanilla inventory on its own; it only
 * mends what a consumer hands it. Which items (gated behind a broad-mending toggle,
 * say) is decided entirely in the consumer's {@link Provider}.
 *
 * <p>Containers applies vanilla's check itself (damaged and carrying the
 * {@code REPAIR_WITH_XP} effect), so a provider may hand over a whole inventory
 * unfiltered. If a candidate is chosen, its {@link Candidate#stack()} is repaired in
 * place with vanilla's math, and then {@link Candidate#onRepaired()} is called. That
 * callback must save and sync the source (write the stack back or mark its storage
 * dirty), since an in-place durability change alone may not reach disk.
 */
public final class MendingCandidates {

    /** One mendable item plus how to persist its repair. */
    public interface Candidate {
        /** The stack repaired in place if this candidate is chosen. */
        ItemStack stack();

        /**
         * Called after {@link #stack()} is repaired; save and sync the source (write
         * it back or mark it dirty). Runs once per orb, and only if this candidate was
         * the one chosen and repaired.
         */
        void onRepaired();
    }

    /** Supplies a player's extra mend candidates when an XP orb is collected. */
    @FunctionalInterface
    public interface Provider {
        /** Extra candidates for this player (server-side). Return empty for none. */
        List<Candidate> candidatesFor(ServerPlayer player);
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    /** Registers a candidate provider. Call once at mod init. */
    public static void register(Provider provider) {
        com.trevlar.menukit.api.Declarations.requireOpen("MendingCandidates.register");
        PROVIDERS.add(provider);
    }

    /** Gathers all registered providers' candidates for the player (empty if none). */
    public static List<Candidate> gather(ServerPlayer player) {
        if (PROVIDERS.isEmpty()) return List.of();
        List<Candidate> all = new ArrayList<>();
        for (Provider provider : PROVIDERS) {
            List<Candidate> c = provider.candidatesFor(player);
            if (c != null && !c.isEmpty()) all.addAll(c);
        }
        return all;
    }

    private MendingCandidates() {}
}
