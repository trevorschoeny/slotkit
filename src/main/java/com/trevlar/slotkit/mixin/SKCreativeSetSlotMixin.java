package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes registered slots round-trip in creative. Without it, items placed in a
 * registered slot in creative silently vanish, and the consumer has no way to fix
 * that (this first showed up with Inventory Max's equipment slots).
 *
 * <h3>Why the packet handler</h3>
 *
 * Survival slot edits go through {@code ServerboundContainerClickPacket} to
 * {@code menu.clicked}, which reaches a registered {@link CreatedSlot} like any other
 * slot. Creative edits take a different server path,
 * {@code ServerboundSetCreativeModeSlotPacket} to {@code handleSetCreativeModeSlot},
 * which only handles vanilla slot indices: it writes
 * {@code player.inventoryMenu.getSlot(n)} only for {@code n} in {@code [1, 45]},
 * drops {@code n < 0}, and silently discards everything else. A registered slot sits
 * past index 45 (it is appended after the offhand), so its creative placement hits
 * the discard path and the item vanishes on the server. The fix belongs in the
 * packet handler, before survival and creative diverge, not in a per-screen mixin
 * that creative would bypass.
 *
 * <h3>What it does</h3>
 *
 * At HEAD, for a creative player, if the incoming slot index resolves to a registered
 * {@link CreatedSlot} on the inventory menu, it repeats vanilla's own in-bounds write
 * for that index: {@code setByPlayer} (which goes through the slot's
 * {@code StorageContainerAdapter} to the backing {@code Storage}, and skips
 * {@code mayPlace} just as vanilla creative does), then {@code setRemoteSlot} and
 * {@code broadcastChanges} to keep the client in sync. Then it cancels, so vanilla's
 * discard never runs. Every other index (vanilla slot, drop sentinel, out of range)
 * is left to vanilla.
 *
 * <h3>Generic</h3>
 *
 * Registered slots are detected by type ({@code instanceof CreatedSlot}), not through
 * a registry. Every created slot is a {@link CreatedSlot}, so this covers all of them
 * with no per-feature knowledge. Only the library knows a slot is registered, so the
 * library has to own this. It routes the consumer's own slot and decides no consumer
 * behavior.
 */
@ApiStatus.Internal
@Mixin(ServerGamePacketListenerImpl.class)
public class SKCreativeSetSlotMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void sk$routeSlotCreativeSet(
            ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        // Run only on the server thread. Vanilla's first action (just after this
        // HEAD inject) is PacketUtils.ensureRunningOnSameThread, which reschedules
        // the initial network-thread call onto the main thread and aborts it. This
        // inject runs before that, so on the off-thread call it must return without
        // canceling, letting vanilla reschedule, and act only on the main-thread
        // call. That keeps the storage write and broadcastChanges off the network
        // thread.
        MinecraftServer server = this.player.level().getServer();
        if (server == null || !server.isSameThread()) return;

        // Only creative edits use this packet. Same check as vanilla, so a
        // non-creative player (a spoofed packet) is left to vanilla's own checks.
        if (!this.player.hasInfiniteMaterials()) return;

        int slotNum = packet.slotNum();
        AbstractContainerMenu menu = this.player.inventoryMenu;

        // Leave everything that isn't a registered slot to vanilla: the drop
        // sentinel (n < 0), out-of-range indices and real vanilla slots. Only a
        // CreatedSlot past vanilla's range is handled here, which is exactly the
        // index vanilla would silently discard.
        if (slotNum < 0 || slotNum >= menu.slots.size()) return;
        Slot slot = menu.getSlot(slotNum);
        if (!(slot instanceof CreatedSlot)) return;

        // Repeat vanilla's in-bounds write for valid stacks, then cancel either way
        // so vanilla doesn't also run its discard.
        ItemStack stack = packet.itemStack();
        boolean validStack = stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize();
        if (stack.isItemEnabled(this.player.level().enabledFeatures()) && validStack) {
            slot.setByPlayer(stack);                 // through StorageContainerAdapter to the backing Storage
            menu.setRemoteSlot(slotNum, stack);      // keep remote tracking in step, so nothing is re-sent
            menu.broadcastChanges();
        }
        ci.cancel();
    }
}
