package com.trevlar.slotkit.api.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Builds the client {@link CustomContainerScreen} for a registered {@code CustomMenu}.
 * Its shape matches both {@code MenuScreens.ScreenConstructor.create} and the
 * three-argument {@link CustomContainerScreen} constructor, so the default factory is
 * just {@code CustomContainerScreen::new}, and a consumer subclass (overriding
 * {@code init()} for its own keys, listeners or drag) fits as {@code MySubclass::new}.
 *
 * <p>Client only. It builds a GUI object and is called only from
 * {@code ClientMenu.registerScreens()} on the client (run from
 * {@code SlotKitClient.onInitializeClient}), never on a dedicated server. It
 * lives in SlotKit's client source set, so common code cannot name it.
 */
@FunctionalInterface
public interface MenuScreenFactory {

    /** Builds the screen for the handler, player inventory and title. */
    CustomContainerScreen create(CustomContainerMenu handler, Inventory inventory, Component title);
}
