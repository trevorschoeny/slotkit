## 3.0.0

#### Added

- Automation operations `AUTOMATION_PUT` and `AUTOMATION_TAKE`, with `Automation.room` and `Automation.mayTake`: what hoppers, droppers, dispensers and copper golems ask before moving items, and what a mod's own automation can ask, also for a slot inside a shulker box in a chest. A put veto on `AUTOMATION_PUT` (a mod's lock) holds for all of them.
- `SlotOperations.Limit`, registered with `limit`: a cap on how many of an item a slot holds, below its stack size. Every limit is asked and the lowest wins, at every hand that fills a slot: clicks, shift-clicks, drags, pickups, automation, and slots inside a box item. `SlotOperations.limitOf` reads it.
- `SlotOperations.Preference`, registered with `prefer`: a slot a mod prefers for an item is tried first when a pickup or a shift-click picks where to land, partial stacks before empty slots as before. Vetoes and limits still judge every slot; with nothing preferred the order is vanilla's.
- `SlotStateChannel.get(SlotRef)`, and `get`/`set` on an item: a SHARED channel's value wherever a veto's slot lives, including slots inside a box item.

#### Fixed

- A shulker box broken in survival dropped its slot state; the loot table copied only its name and contents.
- A copper golem filling a chest now follows slot rules, whichever mod chose its slots.
- Slot state holding a registry-backed value (an enchanted item) failed to save on the server; it now encodes with the world's registries.

#### Removed (breaking)

API no mod used is gone. [Upgrading](docs/upgrading.md#from-slotkit-2x-to-30) lists what to use instead.

- The custom-menu framework: `CustomMenu`, `CustomContainerMenu`, `SlotLayout`, `QuickMoveRouter`, and the client's `ClientMenu`, `CustomContainerScreen` and `MenuScreenFactory`.
- The extra storage kinds: `PlayerStorage`, `VirtualStorage`, `KeyedStorage`, `KeyedStorages`, `EphemeralStorage`, and `CustomAttachmentSpec` with `StorageAttachment.custom` and `customPlayerAttached`. `StorageAttachment.ephemeral` stays.
- `DropRule` and `PlayerStorageAttachment.dropsOnDeath`. Every player attachment dies the vanilla way, as it did by default: `keepInventory` keeps it, Curse of Vanishing destroys it, otherwise it drops.

## 2.3.0

#### Added

- `SlotOperations.PutVeto`, registered with `vetoPut`: a veto handed the stack about to land, so a rule can refuse a put by item ("only into a container that already holds it"). Every put seam asks it: click place, shift-click in, drag-fill, number-key and offhand swaps (both slots), world pickup, and clicks a mod sends under `as(...)`.
- `SlotOperations.allowsPut(ref, operation, incoming)`: `allows`, then every put veto with the incoming stack.
- `SlotGroups.holds(ref, incoming)`: whether any slot in that slot's group already holds the same item, on the open menu or, with none, in the player's inventory.
- `ItemContents`: a container showing one item's contents (a shulker box opened from the inventory) says where that item is, and `ItemSources.pathOf` then names its slots as indexes inside the item.
- `ItemPath.child(index)` and `ItemPath.within(outer)`: the path one level in, and whether a path is an item or anything inside it.

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
- A created slot group declared as `SHULKER_STORAGE` took a shulker box by click, number key, shift-click or drag; like vanilla's shulker box slots, it now refuses anything that can't go inside a container item.
- A gate on a player inventory slot didn't hold against a number-key or offhand swap, the drop key, or an item picked up into the inventory.
- The F key's offhand swap with no screen open didn't hand a put veto the real incoming stack, so a plain swap could put through an item a veto would have refused.
- A `LiveItem` stayed live after a hopper (or any `removeItem`) took its whole stack, so a menu open on that box kept taking items that went nowhere.

### 2.2.1

#### Fixed

- A slot hidden only by the client's reveal check now keeps a write the server syncs to it, instead of dropping it. This fixed pocket items that stayed invisible on the client after a pickup, Restock, or a cycle.

### 2.2.0

#### Added

- `ItemSources.pathOf(player, slot)` names the item in an inventory slot, an ender chest slot, or a carried attachment's slot, with the same answer on the client and the server, except an ender chest's own menu on the client. [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#item-sources).
- `playerAttached(...).carried()` marks a player attachment as carried, `ItemScope.carried(true)` walks it, and `ItemSources.containerOf(player, path)` returns its contents.
- `ItemState` is a value that rides on an item, saved with it or not as the mod chooses. [Concepts](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md#item-state-and-slot-looks).
- `SlotLooks` ties an item state or a client-side check to an animation, another stack drawn in the item's place, or a mark drawn under or over the item. [Recipe](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#show-an-item-as-open).
- `SlotDoubleClick` gives a plain left double-click on a slot to a mod's action, with the lifted stack drawn back in its slot until the second click. [Recipe](https://github.com/trevorschoeny/slotkit/blob/main/docs/recipes.md#open-an-item-on-a-double-click).

#### Changed

- Every binding of a player, block, or entity attachment (`playerAttached`, `playerAttachedVisible`, `blockScoped`, `entityAttached`) shares one live list of stacks per owner, so a stack keeps its identity across the menu's slots, shift-clicks, and `ItemSources`.
- Requires MenuKit 7.5.0 or newer, for the slot decorations `SlotLooks` draws through.
