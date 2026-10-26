package com.trevlar.slotkit.api.item;

/**
 * Where {@link ItemSources} looks: the player's 36 main slots always, and optionally their
 * carried attachments, the ender chest, the inside of shulker boxes and the inside of
 * bundles, nested up to a depth limit. Immutable; each setter returns a new scope.
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
 * @param carried    whether the player attachments declared {@code carried()} are searched,
 *                   after the inventory and before the ender chest. Since 2.2.0.
 */
public record ItemScope(boolean enderChest, boolean shulkers, boolean bundles, int maxDepth, boolean carried) {

    public ItemScope {
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be 0 or more, was " + maxDepth);
    }

    /** The 2.1 constructor: carried attachments off. */
    public ItemScope(boolean enderChest, boolean shulkers, boolean bundles, int maxDepth) {
        this(enderChest, shulkers, bundles, maxDepth, false);
    }

    /** The inventory's 36 main slots only: no carried attachments, no ender chest, no containers. */
    public static ItemScope inventory() {
        return new ItemScope(false, false, false, 16, false);
    }

    public ItemScope enderChest(boolean on) { return new ItemScope(on, shulkers, bundles, maxDepth, carried); }
    public ItemScope shulkers(boolean on)   { return new ItemScope(enderChest, on, bundles, maxDepth, carried); }
    public ItemScope bundles(boolean on)    { return new ItemScope(enderChest, shulkers, on, maxDepth, carried); }
    public ItemScope maxDepth(int depth)    { return new ItemScope(enderChest, shulkers, bundles, depth, carried); }
    public ItemScope carried(boolean on)    { return new ItemScope(enderChest, shulkers, bundles, maxDepth, on); }
}
