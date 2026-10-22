package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.core.attachment.BlockScopedDropHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the contents of {@code blockScoped} storage attachments as item entities
 * when a block entity is removed, before it is destroyed. Consumers using
 * {@code StorageAttachment.blockScoped(...)} get the same drop-on-break behavior as
 * a vanilla container with no extra work.
 *
 * <p>Targets {@link BlockEntity#preRemoveSideEffects(BlockPos, BlockState)} at
 * HEAD. In 26.2 this is the pre-removal hook where vanilla drops container contents
 * ({@code BaseContainerBlockEntity.preRemoveSideEffects} calls
 * {@code Containers.dropContents}). Dropping at HEAD means attachment contents drop
 * alongside the vanilla block entity's inventory, in the same frame and with the
 * same physics.
 *
 * <p>The injection only reads the attachment. It doesn't cancel or change vanilla's
 * removal, so other mods can inject at the same point. Drops happen on the server
 * only, because {@link BlockScopedDropHandler#dropAllAt(Level, BlockPos, BlockEntity)}
 * checks {@code level.isClientSide()}.
 *
 * <p>Consumers who want their attachment's contents lost instead declare it with
 * {@code StorageAttachment.custom(spec)} rather than {@code blockScoped(...)}. Only
 * blockScoped attachments are in the registry this mixin reads.
 */
@ApiStatus.Internal
@Mixin(BlockEntity.class)
public abstract class SKBlockDropMixin {

    @Inject(method = "preRemoveSideEffects", at = @At("HEAD"))
    private void sk$dropBlockScopedContents(BlockPos pos, BlockState state,
                                                   CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) return;
        BlockScopedDropHandler.dropAllAt(level, pos, self);
    }
}
