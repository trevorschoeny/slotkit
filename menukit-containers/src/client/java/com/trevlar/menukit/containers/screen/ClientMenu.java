package com.trevlar.menukit.containers.screen;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.containers.network.MKCOpenMenuC2SPayload;
import com.trevlar.menukit.containers.network.Presence;
import com.trevlar.menukit.window.Declarations;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The client half of an {@link MKCMenu} (§0067): its screen class, how each of its
 * panels looks, and the request to open it. Declared from the client initializer;
 * the menu itself (its slots and panels as the server knows them) is common.
 *
 * <pre>{@code
 * ClientMenu.of(MyMod.CUSTOM)
 *     .screen(MyScreen::new)                                // optional; default MKCHandledScreen
 *     .panel("mymod:menu:main", p -> p
 *             .position(PanelPosition.main())
 *             .add(TextLabel.builder().text(title).at(64, 0).build()))
 *     .panel("mymod:menu:side", p -> p
 *             .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
 *             .toggleKey(GLFW.GLFW_KEY_T));                 // works when the side panel is toggleable()
 *
 * ClientMenu.of(MyMod.CUSTOM).requestOpen();                // later: ask the server to open it
 * }</pre>
 *
 * A panel's look is MenuKit's {@link Panel.Builder}: elements, style, placement, toggle
 * key. Whether it is shown is the server's (the screen shows it while the server says
 * so); a {@code visible(...)} here is ignored. A panel with no declaration here is a
 * plain raised panel of its slots, placed by the standalone default (the first is the
 * main frame, later ones stack below it).
 */
public final class ClientMenu {

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    private static final Map<MenuType<?>, ClientMenu> BY_TYPE = new ConcurrentHashMap<>();
    private static volatile boolean screensRegistered = false;

    private final MKCMenu menu;
    private volatile @Nullable MKCMenuScreenFactory screen;
    private final Map<String, Consumer<Panel.Builder>> panels = new LinkedHashMap<>();

    private ClientMenu(MKCMenu menu) {
        this.menu = menu;
    }

    /** The client half of {@code menu}. */
    public static ClientMenu of(MKCMenu menu) {
        Objects.requireNonNull(menu, "menu");
        return BY_TYPE.computeIfAbsent(menu.getType(), t -> new ClientMenu(menu));
    }

    /**
     * This menu's screen, when it has its own class ({@code MyScreen::new}, a subclass of
     * {@link MKCHandledScreen}). Without it the menu uses {@code MKCHandledScreen}.
     *
     * @throws IllegalStateException after client start (screens are registered then)
     */
    public ClientMenu screen(MKCMenuScreenFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (screensRegistered) {
            throw new IllegalStateException("ClientMenu " + menu.getId() + ": screen(...) after client start "
                    + "never takes effect; call it from your client initializer");
        }
        this.screen = factory;
        return this;
    }

    /**
     * How panel {@code panelId} looks, on MenuKit's {@link Panel.Builder}. Applied each
     * time a screen for this menu opens.
     */
    public ClientMenu panel(String panelId, Consumer<Panel.Builder> look) {
        Declarations.requireOpen("ClientMenu " + menu.getId() + " panel " + panelId);
        synchronized (panels) {
            if (panels.putIfAbsent(Objects.requireNonNull(panelId, "panelId"), Objects.requireNonNull(look, "look")) != null) {
                throw new IllegalStateException("ClientMenu " + menu.getId() + ": panel '" + panelId + "' is declared twice");
            }
        }
        return this;
    }

    /**
     * Asks the server to open this menu for the local player. The server opens it only
     * when the menu declares {@code validWhen} and it holds for this player (§0067).
     * Nothing is sent to a server without Containers (§0069).
     */
    public void requestOpen() {
        if (!Presence.serverHasContainers()) {
            LOGGER.debug("[MenuKit-Containers] not asking to open {}: the server does not run Containers", menu.getId());
            return;
        }
        ClientPlayNetworking.send(new MKCOpenMenuC2SPayload(menu.getId()));
    }

    /** The look declared for {@code panelId} on the menu of {@code type}, or {@code null}. */
    static @Nullable Consumer<Panel.Builder> lookFor(@Nullable MenuType<?> type, String panelId) {
        ClientMenu client = type == null ? null : BY_TYPE.get(type);
        if (client == null) return null;
        synchronized (client.panels) {
            return client.panels.get(panelId);
        }
    }

    /**
     * Registers every menu's screen with vanilla, at client start, after every client
     * initializer has had its chance to call {@link #screen}.
     */
    @ApiStatus.Internal
    public static void registerScreens() {
        screensRegistered = true;
        for (MKCMenu menu : MKCMenu.all()) {
            ClientMenu client = BY_TYPE.get(menu.getType());
            MKCMenuScreenFactory factory = client != null && client.screen != null ? client.screen : MKCHandledScreen::new;
            MenuScreens.register(menu.getType(), factory::create);
        }
    }
}
