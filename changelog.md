## 2.2.2

#### Fixed

- `MenuSwitch.back` brings a trading screen back with its trades, the trader's xp and level, instead of an empty trade list.
- A mount's inventory (a horse, donkey, mule, llama, camel or nautilus) can be set aside and brought back by `MenuSwitch`, instead of closing on the first switch.
- A click sent just before the server switched menus with `MenuSwitch` landed nowhere; on one of the player's inventory slots it now lands on the same slot in the new menu.
- The world pickup slot operation's name read "Inventory insert"; it's "Item pickup" now.
- A menu set aside by `MenuSwitch` now closes when the player dies, respawns, leaves the End or disconnects, so a crafting grid behind it hands its items back.
- Breaking a block emptied its `blockScoped` storage only onto the ground; a menu still open on it could hand every item out a second time.
- Restoring from a grave overwrote whatever the player had put in the slot since; the grave's stack now goes elsewhere and the slot's item stays.
- A grave took every stack from a player attachment; it now leaves `KEEP` stacks with the player and destroys `DESTROY` ones, as a death without a grave does.
- An `EphemeralStorage` group in a custom menu lost its items when the menu closed; they go back to the player, as a crafting grid's do.
- A panel the server shows arrived with empty slots until something changed them.
- A death kept a player's active slot counts instead of starting them over, and leaving the End could lose them.
- A slot state value set by server code with `set(player, key, index, value)`, or on a placed container with no player, never reached the clients viewing it; the second wasn't stored either.
- A player's slot state values went missing on the client after a dimension change.
- A dispenser or dropper whose random pick landed on a gated slot fired nothing, even with other slots ready.
- `MenuSwitch` set aside a menu opened through Fabric's `ExtendedMenuProvider`, and `back` then sent an open packet its client can't build it from; such a menu now closes normally on a switch.
- A `MenuSwitch.open` whose provider threw lost the player's cursor stack.
- A menu set aside by a switch made from inside a click (a right-click handler) sent no updates after `back` until its next click.
- A `CLICK_TAKE` veto didn't hold on an output slot (crafting, furnace) when the cursor held the same item.
- A gate on a player inventory slot didn't hold against a number-key or offhand swap, the drop key, or an item picked up into the inventory.
- An open request for an unknown custom menu logged a warning on every packet; it's a debug line now.
- A `LiveItem` stayed live after a hopper (or any `removeItem`) took its whole stack, so a menu open on that box kept taking items that went nowhere.
- A created slot group declared as `SHULKER_STORAGE` took a shulker box by click, number key, shift-click or drag; like vanilla's shulker box slots, it now refuses anything that can't go inside a container item.

## 2.2.1

#### Fixed

- A slot hidden only on the client (not the server) now keeps a server write instead of dropping it, fixing pocket items that showed as present and clickable but invisible in the inventory and HUD.

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
