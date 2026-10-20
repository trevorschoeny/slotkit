package com.trevlar.slotkit.api.item;

/**
 * Where {@link ItemSources} looks: the player's 36 main slots always, and optionally the
 * ender chest, the inside of shulker boxes and the inside of bundles, nested up to a
 * depth limit. Immutable; each setter returns a new scope.
 *
 * <pre>{@code
 * ItemScope scope = ItemScope.inventory().enderChest(true).shulkers(true).bundles(true);
 * }</pre>
 *
 * @param enderChest whether the ender chest's own slots (and, with the flags below, the
 *                   containers in them) are searched after the inventory
 * @param shulkers   whether the contents of shulker box items are searched
 * @param bundles    whether the contents of bundles are searched
 * @param maxDepth   how many containers deep the walk goes; 0 means only the top-level
 *                   slots. The default, 16, is a guard against a pathological item,
 *                   not a limit a player meets.
 */
public record ItemScope(boolean enderChest, boolean shulkers, boolean bundles, int maxDepth) {

    public ItemScope {
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be 0 or more, was " + maxDepth);
    }

    /** The inventory's 36 main slots only: no ender chest, no containers. */
    public static ItemScope inventory() {
        return new ItemScope(false, false, false, 16);
    }

    public ItemScope enderChest(boolean on) { return new ItemScope(on, shulkers, bundles, maxDepth); }
    public ItemScope shulkers(boolean on)   { return new ItemScope(enderChest, on, bundles, maxDepth); }
    public ItemScope bundles(boolean on)    { return new ItemScope(enderChest, shulkers, on, maxDepth); }
    public ItemScope maxDepth(int depth)    { return new ItemScope(enderChest, shulkers, bundles, depth); }
}
