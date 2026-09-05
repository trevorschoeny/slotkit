# MenuKit: Containers

MenuKit: Containers is the slot extension for MenuKit. It adds slots that persist and sync, in custom container menus or on screens where vanilla has none.

What it does:
- Creates slots as UI components: a pocket, an extra equipment slot, a satchel, each a real `Slot` synced by vanilla's protocol.
- Adds custom container menus that use MenuKit panels.
- Attaches per-slot state to any slot, private per player, or shared across viewers.
- Makes created slots behave like vanilla ones in creative and survival, on death, and with Curse of Binding and Mending.
- Shows a registered slot on every container screen without per-screen setup.
- Stores each slot's state on its owner (player, block, entity, or item), readable with `/data get`.

Runs on client and server. Depends on MenuKit, which it pulls in automatically. Requires Fabric.

## Install

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit-containers:3.0.0+26.2'
}
```

```json
"depends": { "menukit": ">=3.0.0", "menukit-containers": ">=3.0.0" }
```

## Docs

MenuKit and MenuKit: Containers share one repository and one set of docs: [github.com/trevorschoeny/menukit](https://github.com/trevorschoeny/menukit). Start with [getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md), then the [recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) for slots, slot flags, and custom menus. The [API reference](https://trevorschoeny.github.io/menukit/) is the generated javadoc.

## License

MIT.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
