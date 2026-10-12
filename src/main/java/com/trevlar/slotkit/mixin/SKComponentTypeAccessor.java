package com.trevlar.slotkit.mixin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A component type's wire codec, readable and replaceable, so SlotKit can keep its
 * portable-state key off the network ({@code PortableSlotState.installWireStrip}).
 * custom_data has no stream codec of its own: vanilla derives one from its codec when
 * the type is built, so the type's field is the only place to wrap it.
 */
@ApiStatus.Internal
@Mixin(targets = "net.minecraft.core.component.DataComponentType$Builder$SimpleType")
public interface SKComponentTypeAccessor {

    @Accessor("streamCodec")
    StreamCodec<? super RegistryFriendlyByteBuf, ?> sk$streamCodec();

    @Accessor("streamCodec")
    @Mutable
    void sk$setStreamCodec(StreamCodec<? super RegistryFriendlyByteBuf, ?> codec);
}
