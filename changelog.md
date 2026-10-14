## 1.1.0

Additions for mods that build slots. Nothing in 1.0.0 changes. Needs MenuKit 7.1.0 or newer.

### Added

- `ContainerPanel.Builder.on(SlotGroupCategory...)` and `onGroup(SlotGroupId...)` place a slot panel around a slot group instead of the menu frame. Panels on one group stack by priority, so several mods can share a column above the offhand.
- `SlotSpec.shiftClickIn(true)`, set explicitly, makes a group a shift-click target in the player's inventory. Items fill partial stacks first, then empty slots, and anything the slot's filter refuses goes where vanilla sends it. A slot mod no longer needs its own shift-click mixin.
- `SlotSpec.emptyIcon(Identifier)` gives a group's empty slots an outline, drawn the way vanilla draws the armour outlines.
- `AppliesTo.only(...)` takes a mod's slot group only when its category is one the operation named.

### Changed

- A panel on a slot group is now placed around the group's 18x18 slot frames rather than the 16x16 item boxes, so it sits 1 pixel further out than before. Slot panels on a group sit flush, with no gap, so a column of created slots keeps vanilla's 18 pixel slot pitch.

### Fixed

- Mending declared on a category, as with `SlotOperations.inherent(PLAYER_INVENTORY, MENDING, TRUE)`, now reaches the vanilla inventory slots and not only created ones.
- An XP orb no longer counts the held item twice when its hotbar slot is opted in to Mending.
- On chests, barrels and horse inventories with mods' slots added, the chest's or horse's storage no longer takes in part of the player's inventory, and panels placed on the player's inventory stay beside it instead of moving to the top of the screen.
