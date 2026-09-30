package com.trevlar.menukit.api.window;

import com.trevlar.menukit.window.ClickTags;
import com.trevlar.menukit.window.PlayerActions;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The probe a test mod checks the click-tag hand-off and the in-world actions
 * through (§0038, §0068): which operation a sent click carried, and whether an
 * action would be refused. MenuKit's validators use it. A shipping mod tags its
 * clicks with {@link SlotOperations#as} and never records or claims a tag itself.
 */
public final class OperationProbe {

    private OperationProbe() {}

    /** A click tag as the probe sees it: the operation pair it carries. */
    public record Tag(BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        ClickTags.Tag inner() { return new ClickTags.Tag(take, put); }
        static @Nullable Tag of(ClickTags.@Nullable Tag inner) {
            return inner == null ? null : new Tag(inner.take(), inner.put());
        }
    }

    /** What the client does as it sends a tagged click to the integrated server. */
    public static void recordClick(UUID player, int containerId, int slotId, int button, ContainerInput input, Tag tag) {
        ClickTags.record(player, containerId, slotId, button, input, tag.inner());
    }

    /** What the server does as that click arrives: the tag, once, or {@code null}. */
    public static @Nullable Tag claimClick(UUID player, int containerId, int slotId, int button, ContainerInput input) {
        return Tag.of(ClickTags.claim(player, containerId, slotId, button, input));
    }

    /** The same for an in-world action (Q, Ctrl-Q, F with no screen open). */
    public static void recordAction(UUID player, ServerboundPlayerActionPacket.Action action, Tag tag) {
        ClickTags.recordAction(player, action, tag.inner());
    }

    public static @Nullable Tag claimAction(UUID player, ServerboundPlayerActionPacket.Action action) {
        return Tag.of(ClickTags.claimAction(player, action));
    }

    /** Whether {@code player} doing {@code action} would be refused, as both the client and server seams judge it. */
    public static boolean refusesAction(Player player, ServerboundPlayerActionPacket.Action action) {
        return PlayerActions.refuses(player, action);
    }
}
