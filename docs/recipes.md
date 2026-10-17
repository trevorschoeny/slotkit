# Recipes

One task per section. Each says what it needs: the client tier works with SlotKit on the client alone, and building slots needs SlotKit on both sides with `bothSides: required` ([concepts](concepts.md#two-tiers)). The build compiles every Java block on this page (the `validator-slotkit` snippets), so each one matches the API as it ships. Imports are the `api` packages of SlotKit and MenuKit, and your IDE finds them by class name.

## Put a panel next to the player inventory on every screen

Needs: SlotKit, client tier. Call from the client entry point.

```java
Panel panel = Panel.builder("mymod:toolbar")
        .style(PanelStyle.NONE)
        .elements(toolbarButtons)
        .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
        .visibleWhen(() -> config.toolbar)
        .build();
new SlotGroupPanelAdapter(panel)
        .on(SlotGroupCategory.PLAYER_INVENTORY);
```

Result: the panel renders above the right edge of the player inventory grid on every screen that shows that grid, while `config.toolbar` is on. Pass several categories to `.on(...)` to render the panel once per matching group:

```java
new SlotGroupPanelAdapter(panel)
        .on(SlotGroupCategory.CHEST_STORAGE,
            SlotGroupCategory.SHULKER_STORAGE,
            SlotGroupCategory.DISPENSER_STORAGE,
            SlotGroupCategory.HOPPER_STORAGE);
```

`SlotGroupPanelAdapter` is a MenuKit anchor whose box is each slot group's rectangle, so its panels stack, claim and hide the way MenuKit's do. A `SlotGroupPanelAdapter` with no `.on(...)` fails at the first screen open, with the panel id in the message.

## Stack switches beside the inventory, wrapping into columns

Needs: SlotKit, client tier. Call from the client entry point.

```java
Panel switches = Panel.builder("mymod:switches")
        .style(PanelStyle.NONE)
        .add(Flow.builder().vertical().gap(1).addAll(featureSwitches).build())
        .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
        .build();
new SlotGroupPanelAdapter(switches)
        .on(SlotGroupCategory.PLAYER_INVENTORY);
```

Result: the switches stack down the right of the player inventory grid, 1 pixel apart. The column runs from the grid's top to the bottom of the screen's frame, which on the survival inventory is below the hotbar, not where the grid ends. The switches then continue in a second column to the right, and a third. The panel widens with each column. A switch with a `visibleWhen` that is off takes no room, and the ones below it move up. The `Flow` places its children, so their `at(...)` does nothing.

## Add synced slots to the player, shown on every container screen

Needs: SlotKit on both sides, and `"custom": { "slotkit": { "bothSides": "required" } }` in your `fabric.mod.json`. Declare the storage at common init, and register the group from the common entry point.

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);
```

```java
POCKET_SLOTS = ParityGroup.register("mymod:pockets",
        SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)));
```

```java
// Client initializer: your panel, the slots under a button.
Panel pockets = Panel.builder("mymod:pockets")
        .add(Button.builder().label(Component.literal("Sort")).size(60, 14).build())
        .add(SlotFlowElement.builder().addGroup(POCKET_SLOTS).at(0, 18).build())
        .style(PanelStyle.RAISED)
        .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP).priority(20))
        .build()
        .visibleWhen(() -> ClientSlots.isShown(POCKET_SLOTS.id()));
new ScreenPanelAdapter(pockets).onMatching(ScreenMatcher.all());
```

Result: nine real slots under a Sort button, in the top left gutter of the survival inventory, the creative inventory, and every container screen. Their contents persist on the player and sync through vanilla's slot protocol. The group is data, registered once in common code, and is on every menu. The panel is yours: its place, its look, what sits beside the slots and which screens show it, all MenuKit's. `SlotFlowElement.of(group)` (or `builder().addGroup(group)` for several groups in one flow) lays the slots out at vanilla's 18 px pitch, wrapping to the panel's width; `SlotElement.builder().slot(group.id(), i).at(x, y)` places one by hand. `ScreenMatcher.allExcept(Class...)` keeps the panel off named screens; the slots stay on those menus, parked where nothing draws or clicks them. `ClientSlots.isShown(id)` is false where the group's slots aren't live, so a styled panel hides with them.

The owner id's namespace is your mod id. `define` throws at startup if it is not, or if your mod does not declare `required` ([the definition check](concepts.md#declaring-sides)). A player whose copy of your mod registers different slot groups is refused by a server running yours, with a message naming the mod.

The category is required. It tells other mods what the group is. `SlotGroupCategories.of(menu)` lists the group under it next to the vanilla categories, on every menu the group sits on. A search that walks the player's inventory menu can then treat pockets as inventory and leave an elytra slot alone. Use a vanilla category when the group is one of those things, and declare your own (`new SlotGroupCategory("mymod", "equipment")`) when it is not. The name is a contract once another mod depends on it.

`SlotSpec.shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)` and `dragFill(false)` keep a group out of the bulk shortcuts (shift-click, double-click collect, drag-fill). The slot stays storage, and the shortcuts skip it.

`SlotSpec.accepts(Predicate<ItemStack>)` limits what a slot takes. `SlotSpec.revealWhen(BooleanSupplier)` hides the slots on the client until the supplier returns true. Death, keepInventory, Curse of Vanishing and Curse of Binding behave as they do for vanilla slots. `POCKETS.dropsOnDeath(DropRule.KEEP)` overrides the death rule.

`SlotSpec.shiftClickIn(true)`, said explicitly, makes the group a shift-click target (since 1.1.0): in the player's inventory a shift-clicked stack tries its partial stacks, then its empty slots, before vanilla's routing, and each slot's gate decides. On other menus it is tried after the menu's own routing moved nothing, so a chest still takes a stack first. The menu's own routing never lands in these slots by accident (since 2.0.0). Shift-click out of one goes to the player on every menu: on the inventory screen the main inventory, then the hotbar; elsewhere the hotbar end first, as vanilla moves a container's stack. To send shift-clicks somewhere else, register a [route](#decide-where-a-shift-click-goes).

To tell your slots apart on any screen, creative's included:

```java
CreatedSlot created = CreatedSlot.of(slot);
return created != null && created.panelId().equals("mymod:pockets");
```

## Put a slot above the offhand

`SlotGroupPanelAdapter` places your panel around a slot group's box instead of the menu frame. Here the panel sits on the offhand, and the slot takes elytras and catches them on shift-click:

```java
ELYTRA_SLOT = ParityGroup.register("mymod:elytra",
        SlotSpec.at("slot", new SlotGroupCategory("mymod", "elytra"))
                .storage(player -> ELYTRA.bind(player))
                .accepts(stack -> stack.is(Items.ELYTRA))
                .emptyIcon(Identifier.fromNamespaceAndPath("mymod", "container/slot/elytra"))
                .shiftClickIn(true));

// Client initializer:
Panel elytra = Panel.builder("mymod:elytra")
        .add(SlotFlowElement.of(ELYTRA_SLOT))
        .position(PanelPosition.region(OutsideRegion.TOP_CENTER).priority(20).gap(0))
        .build()
        .opaque(false);
new SlotGroupPanelAdapter(elytra, 0).on(SlotGroupCategory.PLAYER_OFFHAND);
```

Result: one slot right above the offhand on the survival and creative inventories, and no slot on screens without an offhand. Panels on the same group share it and stack by region and priority. `TOP_CENTER` stacks upward and a lower priority sits nearer the group, so another mod's slot on `PLAYER_OFFHAND` at priority 10 goes between this one and the offhand, and a panel that isn't shown leaves no gap. (`TOP_ALIGN_LEFT` would stack them in a row to the right.) Mods building one column agree on priorities, nothing else. `onGroup(...)` takes specific groups, created ones included (`ParityGroup.idOf(owner, group)`).

`gap(0)` keeps stacked slot panels on vanilla's 18 px pitch: the group's box is its slots' frames, and the host's default 2 px gap would set your slots off the grid. `SlotGroupPanelAdapter` logs a warning for a panel holding slots without it.

`emptyIcon` is the outline an empty slot shows, drawn by vanilla the way it draws the armour outlines. It takes a GUI sprite id; a mod's own sprite lives at `assets/<ns>/textures/gui/sprites/<path>.png`.

On a slot group, the panel's position is a `region`, `center()` or `pixel(...)`. Give the slot a category of your own, not `PLAYER_OFFHAND` or `PLAYER_ARMOR`, which other mods read as vanilla's.

## Open and size a group from the server

Since 2.0.0. `activeCount(start)` lets the server decide how many of a group's slots are live for each player. Declare the most you need; slots past the count are inert on both sides, sent to the client as empty and refusing clicks.

```java
BAG_SLOTS = ParityGroup.register("mymod:bag",
        SlotSpec.at("slots", MY_CATEGORY).count(27)
                .storage(p -> BAG.bind(p))
                .activeCount(0));

// Server, when the bag opens, and when it closes:
BAG_SLOTS.setActiveCount(player, 27);
BAG_SLOTS.setActiveCount(player, 0);
```

Result: the group is closed until the server opens it, and then shows exactly the live slots: `SlotFlowElement` lays out only those, and `ClientSlots.isShown` is false at 0. The count is the player's, so it holds on every menu, a chest opened meanwhile included. It goes back to the declared start on respawn and on a new login, and isn't saved. The client hears a change before the slots' contents sync, so a closed group's items never reach it.

## Decide where a shift-click goes

Since 2.0.0. A route answers, for the clicked slot, which destinations a shift-clicked stack tries, in order. Register it in common code: the client runs it too, to predict the move, so answer from state both sides have.

```java
QuickMoveRoutes.register((menu, player, source) -> {
    if (!bagOpen(player) || CreatedSlot.of(source) != null) return null;   // no opinion
    return List.of(Dest.group(BAG_SLOTS.id()), Dest.VANILLA);
});
```

Result: while the bag is open, a shift-click from the inventory or an open chest fills the bag first, then goes where the menu sends it. The first route with an answer wins; `null` leaves the click to the next route, then to SlotKit's default. `Dest.group(id)` names a created group or a vanilla one (`SlotGroupId.category(...)`), `Dest.groupOf(menuSlot)` whatever group holds that slot, `Dest.PLAYER` the player's inventory and hotbar, and `Dest.VANILLA` the menu's own routing, which only comes last. A route that ends without it stops there and leaves the rest in the source. Every slot still judges the move: gates, vetoes, `shiftClickIn`, and slots past an active count.

## Show a vanilla slot in your own panel

Since 2.0.0. A `SlotElement` can present a slot the menu built itself. Here a furnace's fuel slot moves into a labelled panel beside the furnace:

```java
Panel fuel = Panel.builder("mymod:fuel")
        .add(TextLabel.builder().text(Component.literal("Fuel")).build())
        .add(SlotElement.builder().slot(Address.menuSlot(MenuType.FURNACE, 1)).at(0, 12).build())
        .style(PanelStyle.RAISED)
        .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
        .build();
new ScreenPanelAdapter(fuel).on(FurnaceScreen.class);
```

Result: the fuel slot sits under the label, drawn above the panel, and keeps everything vanilla gives it: sync, shift-click, its category. `Address.menuSlot(type, index)` names the same slot on every menu of a type; `SlotRef.address()` names one particular container's. Only screens the panel is on change. There the slot sits where the panel puts it. Scrolled out of a `ScrollContainer` it is parked (not drawn, not clickable); while the panel is hidden, and once the screen closes, it is back where the menu put it.

The screen's background still draws the slot's old frame at its old place, so a panel taking over a vanilla grid covers it. Present a slot in one panel only: a second element for a slot another one already presents steps aside and logs a warning.

## Attach a flag to a slot

Needs: SlotKit. No `slotkit` block: a client skips a channel its server does not have. Register the channel at common init, before any container menu opens.

```java
public static SlotStateChannel<Boolean> CHANNEL;

public static void register() {
    CHANNEL = SlotState.register(
            Identifier.fromNamespaceAndPath("mymod", "container_lock"),
            Codec.BOOL,
            StreamCodec.<RegistryFriendlyByteBuf, Boolean>of(
                    (buf, v) -> buf.writeBoolean(v),
                    buf -> buf.readBoolean()),
            false,
            SlotStateChannel.Visibility.SHARED,
            (player, slot) -> canLock(slot.container()));   // who may write it
}
```

Result: every slot in every menu has a boolean that defaults to false. `CHANNEL.get(address)` and `CHANNEL.set(address, true)` read and write it for the viewer's open menu, and when you hold the live slot:

```java
boolean locked = CHANNEL.get(slot);
CHANNEL.set(slot, !locked);
```

`SHARED` stores one value per slot for all viewers. Omit the visibility for `PRIVATE`, one value per viewer. The value persists as NBT on the slot's owner. Writing the default removes it.

The last argument is the channel's writer rule. The server judges every write a client sends: the channel exists, the value parses with the channel's `StreamCodec`, the write names the player's open menu and a live slot on it, the rule allows this player at this slot, and the player is within a write rate. A refused write changes nothing, and the server sends the writer its own value back. A `SHARED` channel with no rule refuses every client write, since one player's write is another player's state. A `PRIVATE` one lets a player write their own value. Server code writing a channel is not judged.

On a server without SlotKit, or without this channel, the client neither sends the write nor shows it. To grey a control the server cannot hold, read `Presence.serverHasChannel(id)`.

With no open menu, use `CHANNEL.get(player, key, index)` with a `PersistentContainerKey`, or `CHANNEL.get(container, index)` for a shared value at a placed container.

## Open a custom menu with its own slots

Needs: SlotKit on both sides, and `bothSides: required`. Define at common init.

```java
public static final CustomMenu CUSTOM = CustomMenu
        .define(Identifier.fromNamespaceAndPath(MOD_ID, "custom_menu"), MyMod::buildHandler)
        .title(Component.literal("My Custom Menu"))
        .validWhen(player -> player.isAlive())   // who may open it, and for how long
        .register();

public static CustomContainerMenu buildHandler(
        MenuType<CustomContainerMenu> type, int syncId, Inventory inv) {
    return CustomContainerMenu.builder(type)
            .panel("mymod:menu:main", p -> p.group("items", MY_CATEGORY, EphemeralStorage.of(9)))
            .panel("mymod:menu:extra", p -> p.group("extra", MY_CATEGORY, EphemeralStorage.of(3))
                    .hidden().toggleable())
            .build(syncId);
}
```

```java
// Client initializer: each panel, its slots included, on MenuKit's Panel.Builder.
ClientMenu.of(CUSTOM)
        .panel("mymod:menu:main", p -> p.position(PanelPosition.main())
                .add(TextLabel.builder().text(Component.literal("Items")).at(0, -12).build())
                .add(SlotFlowElement.of(SlotGroupId.created("mymod:menu:main", "items"), 9)))
        .panel("mymod:menu:extra", p -> p.position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
                .toggleKey(InputConstants.KEY_E)
                .add(SlotFlowElement.of(SlotGroupId.created("mymod:menu:extra", "extra"), 3)));

// Client:
ClientMenu.of(CUSTOM).requestOpen();
// Server:
CUSTOM.open(serverPlayer);
```

Result: `requestOpen()` sends one payload. The server opens the menu because `validWhen` holds for the player, and the client shows a screen with a nine-slot panel. E shows and hides the extra panel. The handler factory runs on both sides and must build the same storages in the same order. Pass the `type` argument straight to `CustomContainerMenu.builder(type)`. `MOD_ID` is your mod id: the menu id's namespace goes through the same definition check as a container panel's.

The handler is structure only: panels, their groups, `hidden()`, `toggleable()`, `pairsWith`, `rightClick`. A `rightClick` handler runs instead of vanilla's click, as a click, on both sides. The client's run is its prediction and the server's run is the one that counts, so put a one-sided effect behind `player.level().isClientSide()`. `p.group(id, category, storage, priority)` sets shift-click priority. The panels are the client's. `ClientMenu.of(menu).panel(id, p -> ...)` takes MenuKit's `Panel.Builder` (elements, style, placement, toggle key), raised unless you say otherwise, and the panel's slots are elements you add to it, as in any panel: a group's id is `SlotGroupId.created(panelId, groupId)`. Every panel the handler declares needs one, or the screen refuses to open and names it. `.screen(MyScreen::new)` sets a `CustomContainerScreen` subclass. A panel that declares no placement takes the standalone default, where the first is the main panel and later ones stack below it.

The server decides whether a panel is shown, with `setPanelVisible(id, visible)`, and syncs it to the client. A player's toggle key sends a request to the server, which honours it only for a panel declared `toggleable()`. Without `validWhen` a client cannot open the menu at all. The server can, and the menu stays open while the player is alive.

## Gate a slot by its address

Needs: SlotKit. A gate on a vanilla slot is client tier. A created slot's address needs the mod that builds the slot. Call at common init.

```java
Address a = Address.createdSlot(CreatedSlots.groupId("mymod:ring", "ring"), 0);
Window.slot(a).gate(new SlotGate() {
    @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) { return stack.is(Items.GOLD_INGOT); }
    @Override public boolean mayPickup(Player player, GatingContext ctx) { return true; }
    @Override public int maxStackSize(ItemStack stack, int vanillaMax) { return Math.min(1, vanillaMax); }
});
```

Result: slot 0 of the `ring` group accepts gold ingots only, one per slot, whenever a slot with that address exists in an open menu. `SlotGate`, `GatingContext`, `BehaviorKeys.GATING` and `BINDING` are client tier and enforced for every slot kind, so a mod that builds no slots gates a vanilla slot the same way. `BehaviorKeys.MENDING` needs SlotKit on the server. `SlotSpec.gate(SlotGate)` and `SlotSpec.accepts(Predicate)` set the same gate at declaration time for container-parity slots.

Inside a gate, `ctx.actingPlayer()` is the player making the move, or null for a hopper or dispenser. To relax a rule for a player without SlotKit, check `Presence.clientHasSlotKit(ctx.actingPlayer())` after the null check.

## Block operations on a locked slot

Needs: SlotKit, client tier. Call at common init.

```java
SlotOperations.veto((ref, op) ->
        ref.container() instanceof Inventory inv
                && ref.player() != null && ref.player().getUUID().equals(inv.player.getUUID())
                && MyLocks.isLocked(ref.containerSlot())
                && MyLocks.blocks(op));
```

Result: every operation in the game, vanilla's and any other mod's, calls `SlotOperations.allows` before it acts. This rule refuses the operations the player chose on the slots the player locked. A veto can only say no, so it never overwrites what the slot's author declared and there is nothing to restore on unlock. `SlotOperations.all()` is the list a settings screen shows, with `SlotOperations.name(op)` and `description(op)` for the words. The player check keeps a per-player lock from answering for another player on the same integrated server. Declare nothing on the slots themselves. A rule that needs the slot's group registers a `SlotOperations.GroupVeto` and is handed it.

On a vanilla server the veto still holds for the client's own clicks: the client does not send a click the veto refuses.

## Add an operation of your own

Needs: SlotKit, client tier. Define at common init, and check before acting.

```java
public static final BehaviorKey<TriBool> RESTOCK_TAKE = BehaviorKey.of(
        Identifier.fromNamespaceAndPath("mymod", "restock_take"), TriBool.class, TriBool.TRUE,
        Tier.SERVER, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
```

```java
// init
SlotOperations.define(RESTOCK_TAKE, SlotOperations.Role.TAKE);
```

```java
// wherever the operation picks a slot
if (!SlotOperations.allows(menu, slot, player, RESTOCK_TAKE)) continue;
```

With two lang lines:

```json
"slot_operation.mymod.restock_take": "Restock takes from",
"slot_operation.mymod.restock_take.description": "Auto-restock may pull a refill out of this slot."
```

If the operation moves items by sending clicks, send them under its name:

```java
SlotOperations.as(RESTOCK_TAKE, RESTOCK_PUT, () -> {
    gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.QUICK_MOVE, player);
});
```

Result: the operation appears in `SlotOperations.all()` for every mod to list. A slot's author can turn it off per slot, group or category (`SlotOperations.inherent`), and a locking mod's veto reaches it. An operation that moves items between two slots is two keys, one for the slot being emptied and one for the slot being filled. A lock can then answer each side on its own. Its clicks are judged as the operation, not as the shift-click or plain click they look like, so a lock that blocks shift-click does not block it.
