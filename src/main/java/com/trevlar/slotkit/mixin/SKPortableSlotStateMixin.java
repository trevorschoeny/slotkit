package com.trevlar.slotkit.mixin;

import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.state.PerPlayerSlotStateBag;
import com.trevlar.slotkit.state.PortableSlotState;
import com.trevlar.slotkit.state.SlotStateAttachments;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries per-slot state between a block and its item.
 *
 * <p>Applies to the shulker box by default, and to any container block entity
 * registered with {@code SlotState.registerPortable}. A shulker's contents travel
 * between block and item in vanilla's {@code minecraft:container} component, but slot
 * state lives in a Fabric attachment, which does not travel. So the whole bag rides
 * on the item under one key in {@code minecraft:custom_data}
 * ({@link PortableSlotState}), beside any other mod's custom data.
 *
 * <h3>Place: the block entity takes the key</h3>
 * At the end of {@code applyComponents} the bag moves into the attachment, and the key
 * leaves the leftover components (the ones the block entity keeps and re-emits on
 * break), so the attachment is the only copy while the box is placed. It reads the
 * item's patch directly rather than through {@code applyImplicitComponents}' getter:
 * asking that getter for custom_data would mark the whole component consumed and
 * drop every other mod's custom data from the leftovers.
 *
 * <h3>Break: the item gets the key back, only when there is state</h3>
 * {@code collectComponents} (the drop, pick-block, any copy to an item) merges a copy of
 * the bag into the custom_data it returns when the bag holds something. An emptied bag
 * writes nothing, so marks cleared while the box was placed do not come back.
 */
@ApiStatus.Internal
@Mixin(BlockEntity.class)
public abstract class SKPortableSlotStateMixin {

    @Shadow
    public abstract DataComponentMap components();

    @Shadow
    public abstract void setComponents(DataComponentMap components);

    // Item to block entity: the bag into the attachment, the key out of the leftovers.
    @Inject(method = "applyComponents", at = @At("TAIL"))
    private void sk$restorePortableSlotState(DataComponentMap prototype, DataComponentPatch patch, CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (!SlotState.isPortable(be)) return;
        PerPlayerSlotStateBag bag = PortableSlotState.read(patch.get(prototype, DataComponents.CUSTOM_DATA));
        if (bag == null) return;
        be.setAttached(SlotStateAttachments.BLOCK_ENTITY, new PerPlayerSlotStateBag(bag.backing().copy()));
        CustomData leftover = components().get(DataComponents.CUSTOM_DATA);
        if (leftover != null) {
            setComponents(PortableSlotState.withCustomData(components(), PortableSlotState.without(leftover)));
        }
        be.setChanged();
    }

    // Block entity to item: a copy of a non-empty bag, merged into the item's custom data.
    @ModifyReturnValue(method = "collectComponents", at = @At("RETURN"))
    private DataComponentMap sk$emitPortableSlotState(DataComponentMap collected) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (!SlotState.isPortable(be) || !be.hasAttached(SlotStateAttachments.BLOCK_ENTITY)) return collected;
        PerPlayerSlotStateBag bag = be.getAttached(SlotStateAttachments.BLOCK_ENTITY);
        if (bag == null || bag.isEmpty()) return collected;
        return PortableSlotState.withCustomData(collected,
                PortableSlotState.with(collected.get(DataComponents.CUSTOM_DATA), bag));
    }
}
