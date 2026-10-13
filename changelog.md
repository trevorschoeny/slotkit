## 1.1.0

Additions for mods that build slots. Nothing in 1.0.0 changes. Needs MenuKit 7.1.0 or newer.

### Added

- `ContainerPanel.Builder.on(SlotGroupCategory...)` and `onGroup(SlotGroupId...)` place a slot panel around a slot group instead of the menu frame. Panels on one group stack by priority, so several mods can share a column above the offhand.
- `SlotSpec.shiftClickIn(true)`, said explicitly, makes a group a shift-click target in the player's inventory. Items fill partial stacks first, then empty slots, and anything the slot's filter refuses goes where vanilla sends it. A slot mod no longer needs its own shift-click mixin.
- `AppliesTo.only(...)` takes a mod's slot group only when its category is one the operation named.

### Fixed

- Mending declared on a category, as with `SlotOperations.inherent(PLAYER_INVENTORY, MENDING, TRUE)`, now reaches the vanilla inventory slots and not only created ones.
- An XP orb no longer counts the held item twice when its hotbar slot is opted in to Mending.

## 1.0.0

SlotKit is MenuKit: Containers renamed, plus the slot code MenuKit 6 carried. It has its own version number and needs MenuKit 7.0.0 or newer, below 8.0.0. Mods built for Containers or for MenuKit 6's slot code need an update. The [upgrading guide](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md) has the steps and an import map that rewrites the moved names.

### Renamed

- The mod id `menukit-containers` is `slotkit`, and `com.trevlar.menukit.containers.api.*` is `com.trevlar.slotkit.api.*`.
- `SlotGroup`, the created kind, is `CreatedSlotGroup`. `ContainerKeys.MENDING` is `BehaviorKeys.MENDING`, and `ContainerKeys` is gone.

### Moved in from MenuKit

- Slot groups, categories and sets (`api.group`), `Storage` and `VirtualStorage` (`api.storage`), the window, addresses, slot operations, gates and vetoes (`api.window`), and `SlotGroupPanelAdapter`.

### Changed

- A server no longer refuses every player without SlotKit. It refuses only players missing SlotKit or a mod that declares `"bothSides": "required"`, and names the mods with links. Everyone else joins.
- A mod that builds slots or custom menus declares `required`, and namespaces its panel and menu ids with its own mod id. `ContainerPanel.define` and `CustomMenu.define` throw at startup otherwise, with a message naming the fix.
- The join check also compares each required mod's `ContainerPanel` slot groups and slot counts, and refuses a player whose copy differs, naming the mod.
- Slot state carried on a shulker box is kept in `minecraft:custom_data` and stays off the network. Shulkers saved with Containers keep their contents and state.

### Added

- `Presence` answers what the other side runs: `serverHasSlotKit`, `serverHas` and `serverHasChannel` on the client, `clientHasSlotKit` and `clientHas` on the server.

### Removed

- `Address.panel` and `Address.panelElement`, the window's panel and element handles, and the `VISIBILITY` and `OPACITY` keys. A panel's own `visibleWhen` and `opaque` do the job.

### Unchanged

- Every saved id keeps its `menukit:` namespace. Worlds, player files and a mod's saved settings load with no conversion.
