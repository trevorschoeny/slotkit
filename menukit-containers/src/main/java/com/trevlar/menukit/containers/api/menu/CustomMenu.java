package com.trevlar.menukit.containers.api.menu;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * The turnkey custom-menu primitive, one {@code define(...).register()} chain
 * replaces the ~4 plumbing pieces a consumer used to hand-write for every custom
 * {@link CustomContainerMenu} menu (its own {@code MenuType} registration, its own
 * open C2S payload + receiver, its own {@code MenuScreens.register}, its own open
 * call). Modeled on {@code ContainerPanel.define(...).register()}.
 *
 * <h3>Recipe</h3>
 *
 * <pre>{@code
 * // Consumer COMMON initializer (runs both sides):
 * public static final CustomMenu CUSTOM = CustomMenu
 *     .define(Identifier.fromNamespaceAndPath(MOD_ID, "custom_menu"), MyMenu::buildHandler)
 *     .title(Component.literal("My Custom Menu"))   // optional; default = "menu.<ns>.<path>"
 *     .validWhen(player -> player.isAlive())        // needed for a client to ask to open it
 *     .arm(MyMenu::armBehaviors)                     // optional; arm slot behavior by Address, same chain
 *     .register();
 *
 * // Consumer CLIENT initializer: how each panel looks, and the screen class if the
 * // menu has its own (default CustomContainerScreen::new). Never in common code: naming a
 * // client class there crashes a dedicated server.
 * ClientMenu.of(MyMod.CUSTOM)
 *     .screen(MyScreen::new)
 *     .panel("mymod:menu:main", p -> p.position(PanelPosition.main()).add(label));
 *
 * // The handler factory receives the menu's own MenuType (no reach-back to the
 * // CUSTOM handle being assigned above, see HandlerFactory):
 * public static CustomContainerMenu buildHandler(
 *         MenuType<CustomContainerMenu> type, int syncId, Inventory inv) {
 *     return CustomContainerMenu.builder(type)        // type handed in, never CUSTOM.getType()
 *             .panel("mymod:menu:main", p -> p.group("items", storage))
 *             .build(syncId);
 * }
 *
 * // Open it:
 * ClientMenu.of(CUSTOM).requestOpen();   // client: asks the server, which checks validWhen
 * CUSTOM.open(serverPlayer);             // server-side direct (no networking)
 * }</pre>
 *
 * <h3>Why the factory takes the MenuType</h3>
 *
 * A handler must call {@link CustomContainerMenu#builder(MenuType)} with this menu's
 * {@code MenuType}, but that type is built <em>inside</em> {@code register()}. If
 * the factory had to reach back to the static handle the {@code define(...).register()}
 * chain is still mid-assigning (e.g. {@code CUSTOM.type()}), it would work only
 * because the factory runs lazily, a consumer who evaluates the type eagerly or
 * inlines the define call hits a forward-reference / null trap. So the factory is a
 * {@link HandlerFactory} that is <em>handed</em> the freshly-built type: both the
 * {@link MenuType}'s own client-side factory lambda and the server-side
 * {@link SimpleMenuProvider} in {@link #open(ServerPlayer)} pass {@code type} in. The
 * consumer never names the handle.
 *
 * <h3>The client/server split</h3>
 *
 * {@code register()} is side-neutral (common init): it builds and registers the
 * {@link MenuType}, and records the handle by id so the server can resolve an open
 * request. Everything the client adds (the screen class, how each panel looks, the
 * open request) is {@code ClientMenu}'s, in Containers' client source set, so this
 * class names no client type (§0067).
 *
 * <h3>Who may open it, and for how long</h3>
 *
 * {@link Builder#validWhen} is the menu's context predicate. A client's request to
 * open the menu is honoured only when the menu declares one and it holds for that
 * player; the open menu closes when it stops holding (vanilla's {@code stillValid},
 * checked on the server every tick). Without one, the menu opens only from the
 * server ({@link #open}) and stays open while the player is alive.
 *
 * <h3>Namespace your panel ids</h3>
 *
 * This menu's {@link #id()} {@code Identifier} is globally unique, but it does NOT
 * scope the {@link com.trevlar.menukit.api.window.Address}es of the slots the menu's
 * handler creates, those are keyed globally by {@code (panelId, groupId, localIndex)}
 * (see {@code CreatedSlotAdapter.addressOf}). So the panel ids the handler declares
 * (via {@link CustomContainerMenu#builder}) must be namespaced per-mod, e.g.
 * {@code "mymod:menu:main"}, exactly as container-parity panel ids already are, or two
 * mods that both name a panel {@code "main"} will collide and arm each other's slots.
 * That id is the same one used for layout references and for addressing in
 * {@link Builder#arm}.
 */
public final class CustomMenu {

    // ── Handler factory ─────────────────────────────────────────────────

    /**
     * Builds the menu's {@link CustomContainerMenu}, handed the menu's own
     * {@link MenuType} so the consumer never reaches back to the static handle
     * the {@code define(...).register()} chain is still mid-assigning. Runs
     * identically on both sides, server via the {@link SimpleMenuProvider} in
     * {@link #open(ServerPlayer)}, client via the {@link MenuType} factory, so
     * its storages must be same-size for sync. The {@code type} passed in is the
     * exact type to feed {@link CustomContainerMenu#builder(MenuType)}.
     */
    @FunctionalInterface
    public interface HandlerFactory {
        /**
         * @param type    this menu's freshly-built {@link MenuType}, pass it
         *                straight to {@link CustomContainerMenu#builder(MenuType)}
         * @param syncId  the menu's sync id (server-assigned)
         * @param inv     the opening player's inventory
         */
        CustomContainerMenu build(MenuType<CustomContainerMenu> type, int syncId, Inventory inv);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit");

    // ── Registered definitions ──────────────────────────────────────────
    // DEFINITIONS: every menu, in registration order (the client registers their
    // screens). BY_ID: resolved server-side by the generic open receiver.
    private static final List<CustomMenu> DEFINITIONS = new CopyOnWriteArrayList<>();
    private static final Map<Identifier, CustomMenu> BY_ID = new ConcurrentHashMap<>();

    // ── Handle state (frozen after register()) ──────────────────────────
    private final Identifier id;
    private final HandlerFactory handlerFactory;
    private final Component title;
    private final MenuType<CustomContainerMenu> type;
    private final @Nullable Predicate<Player> validWhen;

    private CustomMenu(Identifier id,
                    HandlerFactory handlerFactory,
                    Component title,
                    MenuType<CustomContainerMenu> type,
                    @Nullable Predicate<Player> validWhen) {
        this.id = id;
        this.handlerFactory = handlerFactory;
        this.title = title;
        this.type = type;
        this.validWhen = validWhen;
    }

    // ── Definition entry point ──────────────────────────────────────────

    /**
     * Begins a custom-menu definition.
     *
     * @param id             the registry id for the menu's {@link MenuType} (also
     *                       the key the generic open payload carries)
     * @param handlerFactory builds the {@link CustomContainerMenu} from
     *                       {@code (type, syncId, playerInventory)}, handed the
     *                       menu's own {@link MenuType} so it never reaches back to
     *                       the handle being assigned; runs identically on both
     *                       sides (server via the menu provider, client via the
     *                       MenuType factory), so storages must be same-size for sync
     */
    public static Builder define(Identifier id, HandlerFactory handlerFactory) {
        return new Builder(id, handlerFactory);
    }

    /** Fluent configuration; terminates in {@code register()}. */
    public static final class Builder {
        private final Identifier id;
        private final HandlerFactory handlerFactory;
        private Component title;                                        // null => default translation key
        private @Nullable Runnable arm = null;                          // behavior-arming; run by register()
        private @Nullable Predicate<Player> validWhen = null;           // null => server opens only

        Builder(Identifier id, HandlerFactory handlerFactory) {
            this.id = id;
            this.handlerFactory = handlerFactory;
        }

        /** The menu's display title. Optional, defaults to {@code "menu.<namespace>.<path>"}. */
        public Builder title(Component title) {
            this.title = title;
            return this;
        }


        /**
         * Who may open this menu, and for how long (§0067). A client's open request
         * ({@code ClientMenu.of(menu).requestOpen()}) is honoured only when this is
         * declared and holds for the requesting player, checked on the server; the
         * open menu closes when it stops holding (vanilla's {@code stillValid}, every
         * tick). A menu tied to a block checks the player's distance to it here.
         * Without it the menu opens only from the server ({@link CustomMenu#open}) and
         * stays open while the player is alive.
         */
        public Builder validWhen(Predicate<Player> context) {
            this.validWhen = java.util.Objects.requireNonNull(context, "context");
            return this;
        }

        /**
         * A behavior-arming hook, invoked by {@code register()} <b>after</b> the
         * {@link MenuType} + id are live, so a custom menu's slot behavior is armed in
         * the same define chain as the menu it belongs to and cannot be forgotten in a
         * separate pass.
         *
         * <p>Arm slot behavior by {@link com.trevlar.menukit.api.window.Address} here
         * the menu's created slots are addressable as soon as it is registered
         * ({@code Window.slot(Address.createdSlot(SlotGroupId.created(panelId, groupId), i)).set(...)}).
         * Side-neutral: it runs at register() time on whichever side called it (the
         * engine declarations are pure data), exactly like arming behavior by hand in
         * common init.
         *
         * <pre>{@code
         * CustomMenu.define(id, MyMenu::buildHandler)
         *     .arm(() -> {
         *         for (int i = 0; i < 2; i++) {
         *             Window.slot(Address.createdSlot(SlotGroupId.created("side", "filtered"), i))
         *                   .gate(diamondsOnly);
         *         }
         *     })
         *     .register();
         * }</pre>
         */
        public Builder arm(Runnable arm) {
            this.arm = arm;
            return this;
        }

        /**
         * Finalises the registration. Side-neutral (common init): builds + registers
         * the {@link MenuType}, resolves the default title if none was given, stores
         * the handle in {@link #DEFINITIONS} + {@link #BY_ID}, and returns it. Call
         * once at mod init.
         */
        public CustomMenu register() {
    com.trevlar.menukit.api.window.Declarations.requireOpen("CustomMenu " + id + " register()");
            // The MenuType factory: (syncId, inv) -> handler. Used by the client to
            // reconstruct the menu from the server's open packet. The consumer's
            // factory is handed the menu's own MenuType, but the type isn't built
            // until the line below, and the factory lambda needs it. A one-element
            // holder breaks the self-reference: the lambda captures the (effectively
            // final) holder and reads holder[0] at invocation time, after assignment.
            @SuppressWarnings("unchecked")
            final MenuType<CustomContainerMenu>[] holder = new MenuType[1];
            final Predicate<Player> validity = validWhen;
            MenuType<CustomContainerMenu> type = new MenuType<>(
                    (syncId, inv) -> withValidity(handlerFactory.build(holder[0], syncId, inv), validity),
                    FeatureFlagSet.of());
            holder[0] = type;
            Registry.register(BuiltInRegistries.MENU, id, type);

            Component resolvedTitle = (title != null)
                    ? title
                    : Component.translatable("menu." + id.getNamespace() + "." + id.getPath());

            CustomMenu handle = new CustomMenu(id, handlerFactory, resolvedTitle, type, validWhen);
            DEFINITIONS.add(handle);
            BY_ID.put(id, handle);

            // Behavior-arming runs last, now that the MenuType + id are live and the
            // menu's created slots are addressable (see Builder.arm). Keeping it on
            // the define chain means it can't be forgotten in a separate pass.
            if (arm != null) {
                arm.run();
            }
            return handle;
        }
    }

    // ── Handle accessors ────────────────────────────────────────────────

    /** This menu's registered {@link MenuType}. */
    public MenuType<CustomContainerMenu> type() { return type; }

    /** This menu's registry id (also the open-payload key). */
    public Identifier id() { return id; }

    /** Every registered menu, in registration order. The client registers their screens. */
    @ApiStatus.Internal
    public static List<CustomMenu> all() { return List.copyOf(DEFINITIONS); }

    /** A handler built by this menu's factory carries the menu's validity. */
    private static CustomContainerMenu withValidity(CustomContainerMenu handler, @Nullable Predicate<Player> validity) {
        if (validity != null) handler.validWhen(validity);
        return handler;
    }

    // ── Open ────────────────────────────────────────────────────────────

    /**
     * Opens this menu for the given server player, server-side direct, no
     * networking. Vanilla syncs the open to the client, which rebuilds the menu
     * through this menu's {@link MenuType} and shows the registered screen.
     */
    public void open(ServerPlayer player) {
        // The handle's `type` is fully assigned by register() before open() can run,
        // so the server-side provider hands the consumer's factory the same MenuType
        // the client-side factory gets, no reach-back to the static handle.
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> withValidity(handlerFactory.build(type, syncId, inv), validWhen),
                title));
    }

    // ── Generic server receiver (called from MenuKitContainers.init()) ────────────────

    /**
     * Resolves a menu by the id carried in an open payload, or {@code null} if no
     * menu was registered under that id.
     */
    @ApiStatus.Internal
    public static @Nullable CustomMenu byId(Identifier id) {
        return BY_ID.get(id);
    }

    /**
     * A client's request to open menu {@code id} (the generic open payload), judged on
     * the server (§0067): an unknown menu, a menu without {@link Builder#validWhen}, or
     * one whose predicate does not hold for {@code player} is refused and logged.
     * Returns whether it opened. Main thread.
     */
    @ApiStatus.Internal
    public static boolean handleOpenRequest(ServerPlayer player, Identifier id) {
        CustomMenu handle = BY_ID.get(id);
        if (handle == null) {
            LOGGER.warn("[MenuKit-Containers] refused an open request from {}: no menu '{}'",
                    player.getName().getString(), id);
            return false;
        }
        if (handle.validWhen == null) {
            LOGGER.warn("[MenuKit-Containers] refused an open request from {}: menu '{}' declares no "
                    + "validWhen, so it opens only from the server", player.getName().getString(), id);
            return false;
        }
        if (!handle.validWhen.test(player)) {
            LOGGER.debug("[MenuKit-Containers] refused an open request from {}: menu '{}' is not valid for them",
                    player.getName().getString(), id);
            return false;
        }
        handle.open(player);
        return true;
    }
}
