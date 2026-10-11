package com.trevlar.menukit.containers.mixin;

import com.trevlar.menukit.api.window.WindowEngine;
import com.trevlar.menukit.containers.api.slot.MendingCandidates;
import com.trevlar.menukit.containers.api.slot.ContainerKeys;
import com.trevlar.menukit.containers.api.slot.CreatedSlot;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.window.VanillaAddressing;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Widens vanilla's XP-orb repair pool, so a damaged Mending item repairs from XP just
 * like worn armour does when it sits in a registered slot opted in by address through
 * the {@code ContainerKeys.MENDING} key, or comes from a consumer source
 * ({@link MendingCandidates}). Like death drops and binding, this is a vanilla
 * mechanic the library extends to registered slots.
 *
 * <h3>One pool</h3>
 * Vanilla's {@code repairPlayerItems} picks a single damaged Mending item with
 * {@code EnchantmentHelper.getRandomItemWith(REPAIR_WITH_XP, ...)}, repairs it,
 * consumes the matching XP and recurses with the leftover. This {@link Redirect}s that
 * one pick so the pool is built in a single place:
 * <pre>  vanilla equipped (kept) + opted-in registered slots + consumer candidates</pre>
 * and the repair then runs with vanilla's own math. Because the pool is built at the
 * pick, one orb repairs one item across all sources, nothing is repaired twice, and
 * consumers need no {@code ExperienceOrb} mixin of their own.
 *
 * <h3>Vanilla is untouched when nobody opts in</h3>
 * If the entity isn't a server player, or no registered slot opts in and no consumer
 * registers a candidate, the redirect returns vanilla's result unchanged. Vanilla
 * equipped items keep their exact selection and break behaviour through the original
 * {@code getRandomItemWith} call.
 *
 * <h3>Fair selection</h3>
 * A uniform random pick across the pool, like vanilla's per-orb randomness. Each
 * equipped mendable item becomes its own pool entry, instead of vanilla's single
 * collapsed {@code getRandomItemWith} pick. So every mendable item is weighted
 * equally, and registered and consumer candidates are not favoured over equipped
 * items as a group.
 *
 * <h3>Sync</h3>
 * Vanilla repairs the chosen stack in place after this redirect returns, and
 * {@code repairPlayerItems} recurses, so nothing can be persisted at pick time. For a
 * non-vanilla candidate a commit is queued on the orb instance (a registered slot:
 * {@code slot.set}, which marks the storage dirty; a consumer candidate: its
 * {@code onRepaired}). It runs before the next pick, or at {@code playerTouch} TAIL
 * for the last one, once vanilla has repaired the stack. An in-place durability change alone may not
 * reach disk for an attachment-backed slot, so the commit marks it dirty. Vanilla's
 * {@code broadcastChanges} syncs the in-place change to the client.
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbMendMixin {

    // The persist actions (write back and mark dirty) for the non-vanilla candidate
    // picked on the previous recursion level, not yet flushed to live storage. Kept on
    // the orb instance, which is stable across the playerTouch to repairPlayerItems
    // recursion, and created at playerTouch HEAD so it cannot leak across orbs.
    //
    // Vanilla repairs the picked stack in place only after this redirect returns, then
    // recurses. So at the next redirect call the previous pick is freshly repaired, and
    // it is flushed (the repaired copy written back to the live attachment) before the
    // rescan. Without the flush, an attachment-backed slot's getStack builds a new,
    // still-damaged copy on every call because the live attachment was never updated.
    // The same item is offered again on every recursion, vanilla's per-step XP use
    // eventually rounds down to zero, and the recursion never ends (StackOverflowError).
    // With the flush, a repaired created or consumer item leaves the pool just as
    // vanilla's live equipment does once mended.
    @Unique
    private List<Runnable> mk$pendingCommits;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void mk$mendBegin(Player player, CallbackInfo ci) {
        this.mk$pendingCommits = new ArrayList<>();
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void mk$mendDrain(Player player, CallbackInfo ci) {
        mk$flushPending();          // persist the last pick
        this.mk$pendingCommits = null;
    }

    // Runs and clears the queued persist actions, writing picked repairs to live storage.
    @Unique
    private void mk$flushPending() {
        if (this.mk$pendingCommits != null && !this.mk$pendingCommits.isEmpty()) {
            for (Runnable commit : this.mk$pendingCommits) commit.run();
            this.mk$pendingCommits.clear();
        }
    }

    @Redirect(
            method = "repairPlayerItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
                            + "getRandomItemWith(Lnet/minecraft/core/component/DataComponentType;"
                            + "Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)"
                            + "Ljava/util/Optional;"))
    private Optional<EnchantedItemInUse> mk$widenMendPool(
            DataComponentType<?> effect, LivingEntity entity, Predicate<ItemStack> isDamaged) {
        // Persist the previous recursion level's pick before rescanning, so a repaired
        // created or consumer item is no longer offered. This is what ends the
        // recursion (see the field comment).
        mk$flushPending();

        Optional<EnchantedItemInUse> vanilla =
                EnchantmentHelper.getRandomItemWith(effect, entity, isDamaged);
        if (!(entity instanceof ServerPlayer player)) return vanilla;

        // Gather the extra (non-vanilla) candidates. Parallel lists: pool[i] is the
        // repair target and commits[i] its mark-dirty action (null for vanilla items).
        List<EnchantedItemInUse> pool = new ArrayList<>();
        List<Runnable> commits = new ArrayList<>();
        int extras = 0;

        // Opted-in slots on the player's own inventory menu, with MENDING resolved from
        // the engine by the slot's address. Two kinds:
        //  - a created slot (CreatedSlot), addressed by id. Its storage returns a copy,
        //    so a write-back commit is queued (flushed before the next scan or at TAIL).
        //  - a vanilla inventory slot, opted in by its container address. The item is the
        //    real inventory stack, so vanilla's in-place repair persists and syncs on its
        //    own (commit is null, as for equipped gear).
        // The scan is skipped when no behaviour is set anywhere, so normal XP pickup,
        // by far the common case, costs nothing extra.
        for (Slot slot : !WindowEngine.hasServerDeclarations() ? List.<Slot>of() : player.inventoryMenu.slots) {
            if (slot instanceof CreatedSlot mk) {
                if (WindowEngine.resolve(mk.address(), ContainerKeys.MENDING).asBoolean()) {
                    ItemStack stack = mk.getItem();
                    if (mk$mendable(stack)) {
                        pool.add(new EnchantedItemInUse(stack, null, player, item -> {}));
                        commits.add(() -> mk.set(stack));   // write back and markDirty
                        extras++;
                    }
                }
            } else {
                Address a = VanillaAddressing.addressOf(slot.container, slot.getContainerSlot())
                        .orElse(null);
                if (a != null && WindowEngine.resolve(a, ContainerKeys.MENDING).asBoolean()) {
                    ItemStack stack = slot.getItem();
                    if (mk$mendable(stack)) {
                        pool.add(new EnchantedItemInUse(stack, null, player, item -> {}));
                        commits.add(null);   // real inventory stack, repairs in place
                        extras++;
                    }
                }
            }
        }
        // Consumer candidates. The consumer picks which; this applies the mendable check.
        for (MendingCandidates.Candidate candidate : MendingCandidates.gather(player)) {
            ItemStack stack = candidate.stack();
            if (mk$mendable(stack)) {
                pool.add(new EnchantedItemInUse(stack, null, player, item -> {}));
                commits.add(candidate::onRepaired);
                extras++;
            }
        }

        // Nothing opted in, so vanilla behaviour is left untouched.
        if (extras == 0) return vanilla;

        // Add every equipped mendable item as its own pool entry. Vanilla's
        // getRandomItemWith collapses all equipped matches into one pick; expanding them
        // weights every mendable item equally, so registered and consumer candidates
        // aren't favoured over equipped items as a group. Equipped items repair in place
        // on the live equipment stack, which syncs through vanilla's equipment broadcast,
        // so their commit is null, as for vanilla's own pick.
        for (EquipmentSlot eq : EquipmentSlot.values()) {
            ItemStack equipped = entity.getItemBySlot(eq);
            if (mk$mendable(equipped)) {
                pool.add(new EnchantedItemInUse(equipped, null, player, item -> {}));
                commits.add(null);
            }
        }
        if (pool.isEmpty()) return Optional.empty();

        int index = player.getRandom().nextInt(pool.size());
        Runnable commit = commits.get(index);
        if (commit != null && this.mk$pendingCommits != null) {
            // Queue it rather than run it: vanilla repairs this pick in place right after
            // the return, and the next redirect call (or the TAIL drain) flushes it.
            this.mk$pendingCommits.add(commit);
        }
        return Optional.of(pool.get(index));
    }

    // Damaged and carries the vanilla REPAIR_WITH_XP (Mending) effect.
    @Unique
    private static boolean mk$mendable(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamaged()
                && EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP);
    }
}
