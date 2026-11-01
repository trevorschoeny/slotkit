package com.trevlar.slotkit.api.item;

import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/**
 * A container that shows one item's contents, such as a shulker box opened from the
 * player's inventory, and says where that item is. Container slot {@code i} is index
 * {@code i} inside the item, so {@link ItemSources#pathOf} names the slot as
 * {@code itemPath(player).child(i)}: "slot 3 of the box at inventory slot 14".
 *
 * <pre>{@code
 * class OpenedBox extends StorageContainerAdapter implements ItemContents {
 *     public @Nullable ItemPath itemPath(Player p) { return p == opener && !live.isStale() ? live.path() : null; }
 * }
 * }</pre>
 *
 * Implement it on the container behind the screen's slots, on whichever side knows the
 * path (usually only the server's). Since 2.3.0.
 */
public interface ItemContents {

    /**
     * Where the item is, as {@code player} names it, or {@code null} when that player
     * can't name it (another player's inventory) or the item is gone. Read on each call,
     * so a container that follows its item to a new place answers with the new path.
     */
    @Nullable ItemPath itemPath(Player player);
}
