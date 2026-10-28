## 2.2.0

#### Added

- `ItemSources.pathOf(player, slot)` names the item in an inventory slot, an ender chest slot, or a carried attachment's slot, with the same answer on the client and the server, except an ender chest's own menu on the client. [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#item-sources).
- `playerAttached(...).carried()` marks a player attachment as carried, `ItemScope.carried(true)` walks it, and `ItemSources.containerOf(player, path)` returns its contents.
- `ItemState` is a value that rides on an item, saved with it or not as the mod chooses. [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#item-state-and-slot-looks).
- `SlotLooks` ties an item state or a client-side check to an animation, another stack drawn in the item's place, or a mark drawn under or over the item. [Recipe](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#show-an-item-as-open).
- `SlotDoubleClick` gives a plain left double-click on a slot to a mod's action, with the lifted stack drawn back in its slot until the second click. [Recipe](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#open-an-item-on-a-double-click).

#### Changed

- Every binding of a player, block, or entity attachment (`playerAttached`, `playerAttachedVisible`, `blockScoped`, `entityAttached`) shares one live list of stacks per owner, so a stack keeps its identity across the menu's slots, shift-clicks, and `ItemSources`.
- Requires MenuKit 7.5.0 or newer, for the slot decorations `SlotLooks` draws through.
