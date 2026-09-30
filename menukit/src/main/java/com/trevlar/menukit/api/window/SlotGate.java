package com.trevlar.menukit.api.window;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a slot accepts and releases, and how much of an item it holds: the value of
 * {@link BehaviorKeys#GATING}, the slot author's rule. MenuKit resolves a gate for
 * any slot, vanilla or created, by its {@link Address} and applies it once at each
 * vanilla method that moves items: the slot's own {@code mayPlace}, {@code mayPickup}
 * and {@code getMaxStackSize} (which every click, shift-click and automation path
 * consults), the merge pass of {@code moveItemStackTo}, and the hopper and dispenser
 * seams. A denied placement or pickup is refused before the mutation commits.
 *
 * <h2>A gate is not a lock</h2>
 *
 * A gate says what the slot <em>is for</em>: an elytra slot that takes only an
 * elytra, a coin slot that holds one item. It belongs to whoever declared the slot.
 * A lock is somebody else's policy laid over any slot ("nothing touches this one")
 * and is a {@linkplain SlotOperations.Veto veto}, never a gate. A veto can only say
 * no and leaves the author's gate alone, so lifting the lock restores nothing.
 *
 * <h2>The {@link GatingContext}</h2>
 *
 * Each decision receives who is acting, so a gate can choose its own policy for a
 * player whose client cannot see slot state ({@link GatingContext#actingPlayerCapable}).
 */
public interface SlotGate {

    /** Whether {@code stack} may be placed into the gated slot in this context. */
    boolean mayPlace(ItemStack stack, GatingContext context);

    /** Whether {@code player} may take from the gated slot in this context. */
    boolean mayPickup(Player player, GatingContext context);

    /**
     * The per-item stack cap for this slot, given vanilla's own cap. The default
     * imposes no extra limit; a gate that wants single-item slots returns
     * {@code Math.min(1, vanillaMax)}. The caller re-clamps to vanilla, so a gate
     * can never raise the cap above what vanilla allows.
     */
    default int maxStackSize(ItemStack stack, int vanillaMax) {
        return vanillaMax;
    }

    /** The permissive default, vanilla behaviour: the library default for {@link BehaviorKeys#GATING}. */
    SlotGate OPEN = new SlotGate() {
        @Override public boolean mayPlace(ItemStack stack, GatingContext context) { return true; }
        @Override public boolean mayPickup(Player player, GatingContext context) { return true; }
    };
}
