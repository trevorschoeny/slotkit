package com.trevlar.slotkit.core.attachment;

import com.trevlar.slotkit.api.storage.Storage;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Storage over one player's {@code playerAttached} attachment, saying whose and which,
 * so {@code ItemSources.pathOf} can name a menu slot over it. Not public API.
 */
public interface AttachedPlayerStorage extends Storage {
    Player owner();
    Identifier attachment();
}
