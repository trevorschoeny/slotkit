# SlotKit

SlotKit is a slot library for Fabric mods, built on [MenuKit](https://modrinth.com/mod/menukit). A mod uses it to find and act on the slots in any menu, vanilla or modded, or to build real slots and custom menus that persist and sync. I built it for my own mods and opened it up so other modders can use it too.

If you're a player and another mod asked for SlotKit, install it alongside that mod and you're done.

SlotKit was called MenuKit: Containers. Mods built for Containers need an update, and the [upgrading guide](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md) has the steps. MenuKit: Containers 6.2.0 is still in this project's version list.

## Features

SlotKit has two tiers. A mod needs SlotKit on the other side only for the second.

| Tier | Needs SlotKit on | Adds |
| --- | --- | --- |
| Client | The client only. It works on any server, vanilla included. | Slot groups and categories in every menu, slot operations, vetoes and gates, and panels placed against a slot group |
| Client and server | Both sides | Created slots, custom container menus, storage attachments, per-slot state, and items found inside shulker boxes and bundles |

- Every slot group, vanilla or created, is published under a category any mod can read, with or without a menu open.
- Each thing that can be done to a slot is named as an operation. Vanilla has eleven, one per gesture, and a mod can add its own. A slot's own setting is checked first, then its group's, then its category's, and a locking mod can veto any operation.
- A MenuKit panel can be placed against a slot group, so it shows on every screen with that group. That includes the survival inventory, every chest, and every other container.
- Slots in a MenuKit scroll area clip to it, so a long grid of slots can scroll.
- Created slots are UI components. A pocket, an extra equipment slot, or a satchel is each a real `Slot`, synced by vanilla's protocol. A mod registers its slot groups once and places them in its own MenuKit panels, next to its buttons and labels, on every screen it chooses.
- The server can open, close, and resize a slot group for each player, and a mod can decide where a shift-click goes.
- A slot's contents can be shown to other players, for gear drawn on the player model.
- A mod can find and use items anywhere a player carries them: the inventory, the ender chest, and shulker boxes and bundles nested to any depth. A change writes back through every container.
- Custom container menus are built from MenuKit panels.
- Created slots behave like vanilla ones in creative and survival, on death, and with Curse of Binding and Mending.
- Per-slot state can be attached to any slot, either private to each player or shared by everyone viewing it. The server checks every write a client sends. The state is stored on the slot's owner (a player, block, entity, or item), where `/data get` can read it.
- Each side checks which mods the other has before the world loads. A server refuses only players missing a mod that builds slots, and names the mods to install, with links. Everyone else joins.
- Worlds and player files saved with MenuKit: Containers load with no conversion, and shulker boxes keep their contents and slot state.

## Example

A panel above the player inventory, registered once from the client entry point. It uses the client tier, so it works on any server.

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

Nine synced pocket slots on every container screen, registered once from the common entry point. These need SlotKit on both sides.

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);
```

```java
POCKET_SLOTS = ParityGroup.register("mymod:pockets",
        SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)));
```

The slots go in a panel of the mod's own, built from the client entry point.

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

## Getting started

Minecraft 26.3 is unobfuscated, so SlotKit goes on the plain `implementation` configuration, beside MenuKit.

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.3.0+26.3'
    implementation 'maven.modrinth:xQUBXV8I:2.0.0+26.3'   // SlotKit
}
```

`xQUBXV8I` is SlotKit's Modrinth project id. It points to the project no matter what the slug is.

Declare both in `fabric.mod.json`, each against its own version, with both bounds ([why](https://github.com/trevorschoeny/slotkit/blob/main/docs/versioning.md)). Separate the bounds with a space, since Fabric doesn't read a comma there.

```json
"depends": { "menukit": ">=7.3.0 <8.0.0", "slotkit": ">=2.0.0 <3.0.0" }
```

Players install SlotKit as its own mod, next to the mods that use it. List it as a required dependency on the mod's Modrinth page so it's installed with them.

## Requirements

- [MenuKit](https://modrinth.com/mod/menukit) 7.3.0 or newer, below 8.0.0. SlotKit builds its panels and slot drawing on MenuKit.
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Compatibility

Minecraft 26.3 with Fabric. Install SlotKit on the client. It's optional on the server, and needed there only when a mod that builds slots is installed. SlotKit replaces MenuKit: Containers, so the two can't be installed together, and it can't be installed with Inventory Max either.

## Used by

- [Inventory Plus](https://modrinth.com/mod/inventory-plus-trevlar)
- [Hotbar Pockets](https://modrinth.com/mod/hotbar-pockets)

## How to use

This guide follows the order a mod usually meets SlotKit, from finding slots on the client to building slots that need both sides. Each part links to the full developer docs. [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md) defines every term, and [Recipes](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md) has a worked example for each common task.

### The public API

The API is every type in a package named `api`, which is `com.trevlar.slotkit.api.*`. The version contract covers those. Every other package is internal and can change in any release, a patch included. A task that seems to need an internal type is a gap in the API, so [open an issue](https://github.com/trevorschoeny/slotkit/issues) for it. [Versioning](https://github.com/trevorschoeny/slotkit/blob/main/docs/versioning.md) has what each part of a version number promises.

### Declare which sides need SlotKit

A mod states in its `fabric.mod.json` whether it needs SlotKit on both sides.

```json
"custom": { "slotkit": { "bothSides": "required" } }
```

- `required` is for a mod that builds slots or custom menus. A server with the mod admits only clients that have SlotKit and the mod.
- `preferred` is for a mod that works on one side and does more with both, such as shared slot state. Nobody is refused for it.
- A mod with no `slotkit` block only finds and acts on slots. It works on any server and never causes a refusal.

Any other value throws when the mod loads. [Declaring sides](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#declaring-sides) covers the handshake and every message a refused player sees.

### Find slot groups

A slot group category is a name for a group of slots, such as `PLAYER_INVENTORY`, `PLAYER_HOTBAR`, `CHEST_STORAGE`, or `FURNACE_INPUT`. `SlotGroupCategories.all()` lists every category with no menu open, `SlotGroupCategories.of(menu)` lists the categories on a menu with their slots, and `SlotGroupCategories.categoriesBySlot(menu)` gives the category of each slot. `SlotGroups` lists individual groups, which can tell one mod's pockets apart from the main inventory when both declare `PLAYER_INVENTORY`. This is client tier. [Slot group category](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#slot-group-category) has the full rules for naming and minting categories.

### Place a panel against a slot group

`SlotGroupPanelAdapter` places a MenuKit panel against a slot group instead of a screen's frame, as in the first example above. Pass several categories to `.on(...)` to show the panel once per matching group. An adapter with no `.on(...)` fails at the first screen open, with the panel id in the message. This is client tier. See [Recipes](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#put-a-panel-next-to-the-player-inventory-on-every-screen).

### Allow, gate, and veto operations

An operation is something done to a slot. Vanilla's eleven are `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT`, `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `INVENTORY_INSERT`, and every one is on by default.

- What a slot allows is decided by a cascade. A per-slot declaration wins over the slot's group, the group wins over its category, and the category wins over the key's default. `SlotOperations.inherent(category, operation, value)` sets the category level.
- `SlotOperations.veto(rule)` registers a rule that can only say no, for a mod that is not the slot's author, such as a lock.
- `SlotOperations.allows(menu, slot, player, operation)` is the one check, and it passes when the cascade allows the operation and no veto refuses it.
- A gate sets what a slot accepts and releases, and how many of an item it holds. It is set with `SlotSpec.gate(...)` or `Window.slot(address).gate(...)`.
- A mod adds its own operation by declaring a `BehaviorKey`, publishing it with `SlotOperations.define`, and shipping a name and description in its lang file.

[Operation](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#operation) and [Gate](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#gate) cover roles, `AppliesTo`, and click tagging with `SlotOperations.as`. See also [Block operations on a locked slot](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#block-operations-on-a-locked-slot) and [Add an operation of your own](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#add-an-operation-of-your-own).

### Build created slots

A created slot is a real `Slot` a mod adds to a menu. `ParityGroup.register(owner, spec)` registers a group of them once, in common code, on the player's inventory menu and every container menu, as in the second example above. SlotKit builds no panel for it. The mod shows the group in its own MenuKit panel with `SlotFlowElement`, which lays the slots out in rows, or `SlotElement`, which places one slot. The contents persist through a `StorageAttachment` on the slot's owner, built with `StorageAttachment.playerAttached(namespace, key, size)` for a player or `EphemeralStorage.of(size)` for one menu session.

- The owner id's namespace is the mod's id. `register` throws at startup when it isn't, or when the mod doesn't declare `required`. The message names the group and the line to add.
- `SlotSpec.shiftClickIn(true)` makes a group a shift-click target, and `SlotSpec.emptyIcon(...)` draws an outline in its empty slots.
- `SlotSpec.activeCount(start)` lets the server set how many of the group's slots are live for each player, and `QuickMoveRoutes` decides where a shift-click goes.
- `StorageAttachment.playerAttachedVisible(...)` also sends the contents to other players' clients, for gear drawn on the player.
- A `SlotGroupPanelAdapter` places the panel against a slot group, such as above the offhand.

This needs SlotKit on both sides. See [Parity group](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#parity-group), [Storage](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#storage), and [Put a slot above the offhand](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#put-a-slot-above-the-offhand).

### Find items inside shulker boxes and bundles

`ItemSources` walks the items a player carries, in the inventory, the ender chest, and shulker boxes and bundles to any depth, nearest first. An `ItemScope` says which of those to search. On the server, `take` and `walk` remove or change an item and write the change back through every container. `find` lists matches on either side with an `ItemPath` for each, so a client can choose an item and the server can act on it with `at`. See [Item sources](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#item-sources) and [Find and use items inside shulker boxes and bundles](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#find-and-use-items-inside-shulker-boxes-and-bundles).

### Scroll slots in a scroll area

A `SlotElement` inside a MenuKit `ScrollContainer` scrolls and clips like any other element. A slot across the edge of the scroll area draws, hovers, and takes clicks only where it shows. A mod that uses `SlotElement` needs no extra code for this.

### Open a custom menu

`CustomMenu.define(id, ...)` builds a container menu with its own slots, laid out with MenuKit panels. The server decides which panels are shown, a player can toggle only a panel declared `toggleable()`, and a client can open the menu only while its `validWhen` holds. The same definition check as `ParityGroup` applies. See [Open a custom menu with its own slots](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#open-a-custom-menu-with-its-own-slots).

### Attach state to a slot

`SlotState.register(id, codec, streamCodec, defaultValue, visibility, canWrite)` creates a channel that stores one typed value per slot, separate from the slot's item. `PRIVATE` stores one value per viewer, and `SHARED` stores one value for everyone. A slot is named by an `Address`, which resolves after a menu reopens and on both sides. The server checks every write a client sends, and a `SHARED` channel refuses every client unless its `canWrite` rule allows them. See [Slot state channel](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#slot-state-channel) and [Attach a flag to a slot](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#attach-a-flag-to-a-slot).

### Ask what the other side runs

`Presence` reports what the other side had when the player joined. On the client, `Presence.serverHasSlotKit()`, `serverHas(modId)`, and `serverHasChannel(channelId)` answer about the server. On the server, `Presence.clientHasSlotKit(player)` and `clientHas(player, modId)` answer about a player. See [Presence](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#presence).

### Know the limits

[Limits](https://github.com/trevorschoeny/slotkit/blob/main/docs/limits.md) lists what SlotKit doesn't do and the open gaps in this release, such as created slots in overlay panels.

### Upgrade

[Upgrading](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md) moves a mod from SlotKit 1.x to 2.0, with a before and after for each change, and from MenuKit: Containers or MenuKit 6's slot code, with an import map and the two renames.

## License

MIT.
