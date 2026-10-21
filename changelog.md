## 2.0.0

Mods now place SlotKit's slots in their own MenuKit panels, and can reach items inside shulker boxes and bundles. Mods built on 1.x need an update, and the [upgrading guide](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md#from-slotkit-1x-to-20) has a before and after for each change. Saved slot contents carry over with no conversion. Requires MenuKit 7.3.0 or newer.

### Changed

- `ParityGroup` replaces `ContainerPanel`. A mod registers its slot groups once in common code and places them in its own MenuKit panel with `SlotFlowElement` or `SlotElement`.
- Every shift-click goes through SlotKit's routing. A shift-click out of a created slot goes to the player on every menu, and a menu's own routing no longer puts items into created slots.
- The project has a new icon.

### Added

- `ItemSources` finds and uses items in the inventory, the ender chest, and shulker boxes and bundles nested to any depth. A take or a change writes back through every container. A client can choose an item by its `ItemPath`, and the server acts on it.
- `SlotSpec.activeCount` lets the server open, close, and resize a slot group for each player.
- `QuickMoveRoutes` lets a mod decide where a shift-click goes.
- `StorageAttachment.playerAttachedVisible` sends a slot's contents to other players' clients, for gear drawn on the player.
- `SlotElement` can show a slot the menu built itself, such as a furnace's fuel slot, in a mod's own panel.

### Fixed

- In item-backed storage, a stack that grew in place, as when vanilla merges two stacks, is no longer lost.
- A vanilla slot shown in a hidden panel goes back to where the menu put it.
