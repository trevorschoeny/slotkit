package com.trevlar.slotkit.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creative's own click path, for a click a mod sends while the creative screen is open. */
@ApiStatus.Internal
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenInvoker {

    @Invoker("slotClicked")
    void sk$slotClicked(@Nullable Slot slot, int slotId, int button, ContainerInput input);
}
