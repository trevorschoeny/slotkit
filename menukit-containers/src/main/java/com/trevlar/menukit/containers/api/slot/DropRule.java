package com.trevlar.menukit.containers.api.slot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * What happens to a player-scoped slot's content on death, set per attachment. Follows
 * the convention Trinkets (Fabric) and Curios (NeoForge) share: a small enum with a
 * {@code DEFAULT} value that resolves to exactly what vanilla does.
 *
 * <ul>
 *   <li>DEFAULT behaves like vanilla with no setup. Each stack resolves in vanilla's
 *       order: {@code keepInventory} gives KEEP, otherwise Curse of Vanishing gives
 *       DESTROY, otherwise DROP. A consumer who declares nothing gets vanilla
 *       behaviour under both gamerule settings.</li>
 *   <li>KEEP (soulbound): the item survives death whatever {@code keepInventory}
 *       says, carried across respawn by the attachment's {@code copyOnDeath}.</li>
 *   <li>DROP: always drops at the death location, even with {@code keepInventory}
 *       on.</li>
 *   <li>DESTROY: always vanishes (a cursed or bound slot).</li>
 * </ul>
 *
 * <p>Explicit rules override {@code keepInventory}, as in Trinkets and Curios: a
 * {@code KEEP} slot stays kept even with the gamerule off, which is the point of
 * soulbound. Only {@code DEFAULT} reads the gamerule.
 */
public enum DropRule {
    KEEP, DROP, DESTROY, DEFAULT;

    /**
     * Resolves this rule to a concrete action for one stack. {@code KEEP},
     * {@code DROP} and {@code DESTROY} return unchanged, overriding
     * {@code keepInventory}; {@code DEFAULT} follows vanilla's order. Never returns
     * {@code DEFAULT}.
     */
    public DropRule resolve(ItemStack stack, boolean keepInventory) {
        if (this != DEFAULT) return this;
        if (keepInventory) return KEEP;
        // Curse of Vanishing: the item destroys itself on death. Vanilla's own
        // destroyVanishingCursedItems() uses this same PREVENT_EQUIPMENT_DROP check.
        if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            return DESTROY;
        }
        return DROP;
    }
}
