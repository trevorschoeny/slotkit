# MenuKit: Containers

MenuKit: Containers is the slot extension for MenuKit. It adds slots that persist and sync, in custom container menus or on screens where vanilla has no slots.

What it does:
- Creates slots as UI components. A pocket, an extra equipment slot or a satchel is each a real `Slot`, synced by vanilla's protocol.
- Adds custom container menus built from MenuKit panels.
- Attaches per-slot state to any slot, either private to each player or shared by everyone viewing it. The server checks every write a client sends.
- Makes created slots behave like vanilla ones in creative and survival, on death, and with Curse of Binding and Mending.
- Shows a registered slot on every container screen, with no per-screen setup.
- Stores each slot's state on its owner (a player, block, entity or item), where `/data get` can read it.

Runs on client and server. A server that has it requires it on every player's game. A player without it is told to install MenuKit and MenuKit: Containers before the world loads. Depends on MenuKit, and requires Fabric and Fabric API.

## Install

Minecraft 26.2 is unobfuscated, so both artifacts go on the plain `implementation` configuration:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:6.0.0+26.2'
    implementation 'maven.modrinth:menukit-containers:6.0.0+26.2'
}
```

Declare both in `fabric.mod.json`, with both bounds ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)):

```json
"depends": { "menukit": ">=6.0.0 <7.0.0", "menukit-containers": ">=6.0.0 <7.0.0" }
```

## Example

Nine synced pocket slots on every container screen, registered once from the common entry point:

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

## Docs

MenuKit and MenuKit: Containers share one repository and one set of docs: [github.com/trevorschoeny/menukit](https://github.com/trevorschoeny/menukit). Start with [getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md), then the [recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) for slots, slot flags, and custom menus. The [API reference](https://trevorschoeny.github.io/menukit/) is the generated javadoc. The public API is the `api` packages (`com.trevlar.menukit.containers.api.*`). Everything else is internal and can change in any release.

## License

MIT.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
