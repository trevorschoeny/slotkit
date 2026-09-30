package com.trevlar.menukit.containers.api.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Builds the client {@link CustomContainerScreen} for a registered {@code CustomMenu}.
 * Shape matches both {@code MenuScreens.ScreenConstructor.create} and the
 * {@link CustomContainerScreen} 3-arg constructor, so the default factory is simply
 * {@code CustomContainerScreen::new} and a consumer subclass (overriding {@code init()}
 * for custom keys/listeners/drag) drops in as {@code MySubclass::new}.
 *
 * <p><b>Client-only.</b> This factory constructs a GUI object; it is invoked
 * exclusively from {@code ClientMenu.registerScreens()} on the client (drained from
 * {@code MenuKitContainersClient.onInitializeClient}) and is <em>never</em> invoked on a
 * dedicated server. It lives in Containers' client source set, so common code
 * cannot name it (§0067).
 */
@FunctionalInterface
public interface MenuScreenFactory {

    /** Constructs the screen for the given handler / player inventory / title. */
    CustomContainerScreen create(CustomContainerMenu handler, Inventory inventory, Component title);
}
