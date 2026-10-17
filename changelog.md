## 1.1.1

Fixes for slots in MenuKit scroll areas. Nothing changes for mods built on 1.1.0. Requires MenuKit 7.2.0 or newer.

### Changed

- Slots clip to the MenuKit `ScrollContainer` they sit in. A slot across the edge of the scroll area draws, hovers, and takes clicks only where it shows. Mods that use `SlotElement` don't need to change anything.
- In the shulker box menu, the box's storage slots are now found by the box's container instead of by their position. A vanilla shulker box groups the same as before, and players see no difference.

### Fixed

- A slot scrolled out of view no longer blocks clicks above and to the left of its panel.
