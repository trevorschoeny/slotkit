package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.trevlar.menukit.window.ActingPlayer;
import com.trevlar.menukit.window.ClickTags;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Sets up what a click transaction's deep seams need to know, for its duration,
 * and clears it in {@code finally}:
 *
 * <ul>
 *   <li><b>Who is clicking</b> ({@link ActingPlayer}): vanilla's
 *       {@code moveItemStackTo} has no player in scope.</li>
 *   <li><b>What the click is for</b> ({@link ClickTags}): on the server thread,
 *       the operation a simulated click carries, claimed from what the client
 *       recorded as it sent the packet. On the client the sender's own tag is
 *       already on the thread and is left alone.</li>
 * </ul>
 */
@Mixin(AbstractContainerMenu.class)
public class MKClickContextMixin {

    @WrapMethod(method = "clicked")
    private void mk$clickContext(int slotId, int button, ContainerInput clickType, Player player,
                                 Operation<Void> original) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        ClickTags.Tag claimed = player instanceof ServerPlayer
                ? ClickTags.claim(player.getUUID(), self.containerId, slotId, button, clickType)
                : null;
        ClickTags.Tag outer = ClickTags.enter(claimed);
        ActingPlayer.set(player);
        try {
            original.call(slotId, button, clickType, player);
        } finally {
            ActingPlayer.clear();
            ClickTags.exit(outer);
        }
    }
}
