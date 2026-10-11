package com.trevlar.menukit.containers.mixin;

import com.trevlar.menukit.containers.state.PerPlayerSlotStateBag;
import com.trevlar.menukit.containers.state.SlotStateAttachments;
import com.trevlar.menukit.containers.state.SlotStateComponents;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries per-slot state between a block and its item.
 *
 * <p>Applies to the shulker box by default, and to any container block entity
 * registered with {@code SlotState.registerPortable}. A shulker's contents travel
 * between block and item in vanilla's {@code minecraft:container} component, but
 * slot state lives in a Fabric attachment, which does not travel (since 1.20.5 only
 * declared components do). This mixin uses the same vanilla component path as the
 * contents to carry the whole per-block-entity bag inside one generic component,
 * {@link SlotStateComponents#PORTABLE_SLOT_STATE}.
 *
 * <p>{@code ShulkerBoxBlockEntity} does not declare
 * {@code collectImplicitComponents} or {@code applyImplicitComponents} itself; it
 * inherits them from {@link RandomizableContainerBlockEntity}. Mixin can only target
 * declared methods, so this targets the parent and checks {@code SlotState.isPortable}
 * in each inject: it fires for every randomizable container but acts only for
 * registered portable types. The component is not network-synchronised, because it
 * holds every player's private marks and no client reads it.
 *
 * <p>The attachment ({@link SlotStateAttachments#BLOCK_ENTITY}) stays the source of
 * truth; the component is only for travel. It is copied in both directions so it
 * never shares the live bag. It covers any registered channel, with no per-feature
 * schema.
 */
@ApiStatus.Internal
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class ShulkerBoxPortableStateMixin {

    // Block entity to item: write a copy of the bag as the travel component.
    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void mk$emitPortableSlotState(DataComponentMap.Builder components, CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        if (!com.trevlar.menukit.containers.api.state.SlotState.isPortable(be)) return;
        if (!be.hasAttached(SlotStateAttachments.BLOCK_ENTITY)) return;
        PerPlayerSlotStateBag bag = be.getAttached(SlotStateAttachments.BLOCK_ENTITY);
        if (bag != null && !bag.isEmpty()) {
            components.set(SlotStateComponents.PORTABLE_SLOT_STATE,
                    new PerPlayerSlotStateBag(bag.backing().copy()));
        }
    }

    // Item to block entity: restore a copy of the bag from the travel component.
    @Inject(method = "applyImplicitComponents", at = @At("TAIL"))
    private void mk$restorePortableSlotState(DataComponentGetter input, CallbackInfo ci) {
        if (!com.trevlar.menukit.containers.api.state.SlotState.isPortable((BlockEntity) (Object) this)) return;
        PerPlayerSlotStateBag bag = input.get(SlotStateComponents.PORTABLE_SLOT_STATE);
        if (bag != null) {
            BlockEntity be = (BlockEntity) (Object) this;
            be.setAttached(SlotStateAttachments.BLOCK_ENTITY,
                    new PerPlayerSlotStateBag(bag.backing().copy()));
            be.setChanged();
        }
    }
}
