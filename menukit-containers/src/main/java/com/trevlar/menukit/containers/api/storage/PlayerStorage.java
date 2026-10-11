package com.trevlar.menukit.containers.api.storage;

import com.trevlar.menukit.api.slot.Storage;

/**
 * Marks a {@link Storage} as the player inventory side of a menu, as opposed to the
 * container side.
 *
 * <p>{@link com.trevlar.menukit.containers.api.menu.CustomContainerMenu}'s shift-click
 * routing uses it to send items from the player side to the container, and from the
 * container side to the player.
 *
 * <p>Storage from {@link StorageAttachment#playerAttached} implements this marker.
 * Consumers with their own player-backed Storage (through
 * {@code StorageAttachment.custom(...)} or a direct Storage implementation) should
 * implement it too if that Storage stands for the player's inventory when
 * shift-clicking.
 *
 * <p>This was once a concrete class with its own item list and save and load
 * methods. It remains as a marker because shift-click routing needs to tell player
 * storage from container storage, and the narrow {@link Storage} interface doesn't
 * say which is which.
 */
public interface PlayerStorage extends Storage {
}
