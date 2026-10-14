# SlotKit

Formerly MenuKit: Containers. Mods built for Containers need an update, and the [upgrading guide](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md) has the steps. MenuKit: Containers 6.2.0 is still in this project's version list.

SlotKit is a slot library for Fabric mods, built on [MenuKit](https://modrinth.com/mod/menukit). A mod uses it to find and act on the slots in any menu, or to build real slots and custom menus that persist and sync.

It has two tiers, and a mod needs SlotKit on the other side only for the second:

| Tier | Needs SlotKit on | Adds |
|---|---|---|
| Client | The client only. Works on any server, vanilla included. | Slot groups and categories in every menu, slot operations, vetoes and gates, panels placed against a slot group |
| Client and server | Both sides | Created slots, custom container menus, storage attachments, per-slot state |

What it does:

- Publishes every slot group, vanilla or created, under a category any mod can read, with or without a menu open.
- Names each thing that can be done to a slot as an operation. Vanilla has eleven, one per gesture, and a mod can add its own. A slot answers for itself first, then its group, then its category, and a locking mod can veto any operation.
- Places a MenuKit panel against a slot group, so it shows on every screen with that group: the survival inventory, every chest, and every other container.
- Creates slots as UI components. A pocket, an extra equipment slot or a satchel is each a real `Slot`, synced by vanilla's protocol, and shows on every container screen with no per-screen setup.
- Adds custom container menus built from MenuKit panels.
- Makes created slots behave like vanilla ones in creative and survival, on death, and with Curse of Binding and Mending.
- Attaches per-slot state to any slot, either private to each player or shared by everyone viewing it. The server checks every write a client sends. The state is stored on the slot's owner (a player, block, entity or item), where `/data get` can read it.
- Checks which mods each side has before the world loads. A server refuses only players missing a mod that builds slots, and names the mods to install, with links. Everyone else joins.

Requires MenuKit 7.1.0 or newer and Fabric API. Worlds and player files saved with MenuKit: Containers load with no conversion, and shulker boxes keep their contents and slot state.

## Install

Minecraft 26.3 is unobfuscated, so SlotKit goes on the plain `implementation` configuration, beside MenuKit:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.1.0+26.3'
    implementation 'maven.modrinth:xQUBXV8I:1.1.0+26.3'   // SlotKit
}
```

`xQUBXV8I` is SlotKit's Modrinth project id. It resolves whatever the project's slug is.

Declare both in `fabric.mod.json`, each against its own version, with both bounds ([why](https://github.com/trevorschoeny/slotkit/blob/main/docs/versioning.md)):

```json
"depends": { "menukit": ">=7.1.0 <8.0.0", "slotkit": ">=1.1.0 <2.0.0" }
```

A mod that builds slots or custom menus also declares that it needs SlotKit on both sides:

```json
"custom": { "slotkit": { "bothSides": "required" } }
```

## Example

A panel above the player inventory, registered once from the client entry point. It uses the client tier, so it works on any server:

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

Nine synced pocket slots on every container screen, registered once from the common entry point. These need SlotKit on both sides:

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);
```

```java
ContainerPanel.define("mymod:pockets")
        .at(OutsideRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

The panel id's namespace is the mod's id. `define` throws at startup when it is not, or when the mod does not declare `required`.

## The public API

The API is every type in a package named `api`: `com.trevlar.slotkit.api.*`. The version contract covers those. Every other package is internal and can change in any release. A task that seems to need an internal type is a gap in the API. [Open an issue](https://github.com/trevorschoeny/slotkit/issues) for it.

## Docs

- [Getting started](https://github.com/trevorschoeny/slotkit/blob/main/docs/getting-started.md): the dependency, a panel beside the player inventory, nine real slots.
- [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md): the two tiers, declaring sides, slot groups, operations, created slots, addresses, slot state.
- [Recipes](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md): the common tasks.
- [Limits](https://github.com/trevorschoeny/slotkit/blob/main/docs/limits.md): what SlotKit does not do, and the open gaps.
- [Versioning](https://github.com/trevorschoeny/slotkit/blob/main/docs/versioning.md): what a version number promises.
- [Upgrading](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md): moving a mod from MenuKit: Containers or from MenuKit 6's slot code.

## Compatibility

Minecraft 26.3, Fabric. SlotKit replaces MenuKit: Containers, so the two can't be installed together.

## License

MIT.

## Issues

[github.com/trevorschoeny/slotkit/issues](https://github.com/trevorschoeny/slotkit/issues).
