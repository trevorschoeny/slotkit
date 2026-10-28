## 2.2.0

A mod can name the item in any slot, give an item a state, and change how a slot draws an item while a state or a check holds. A mod can also take a double-click on a slot for its own action. Requires MenuKit 7.5.0 or newer.

#### Added

- `ItemSources.pathOf(player, slot)` names the item in an inventory slot, an ender chest slot or a carried attachment's slot, with the same answer on the client and the server. Creative's tabs name the slot they show.
- A player attachment can be declared `carried()`: it holds the player's own things, such as a pocket. `ItemScope.carried(true)` walks it, its paths have source `CARRIED`, and `ItemSources.containerOf(player, path)` returns its contents. Its stacks keep their identity across every binding, so a mod can track one from tick to tick.
- `ItemState` is a value that rides on an item, such as "open" or "favorite", saved with the item or not as the mod chooses. Vanilla syncs it with the stack, so every client drawing the item reads it.
- `SlotLooks` changes how a slot draws an item while an `ItemState` or a client-side check holds: an animation that plays forward and back, another model, or a mark under or over the item. MenuKit does the drawing, so the mod writes no mixin.
- `SlotDoubleClick` takes a plain left double-click on a slot for a mod's action, in place of vanilla's "gather all". The item never seems to move, and it works on creative's inventory tab. A registration can also hear when a first click ends without a second.

#### Changed

- A player attachment's stacks are shared by every binding of it (the menu's slots, a shift-click, `ItemSources`), so a stack is the same object wherever it's read.

## 2.1.0

Rules about slots now hold in creative, and a mod says which game mode each one is for. Panels can sit on a single slot, and each container on a menu stays its own. Calls replaced in this release are deprecated, and the [upgrading guide](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md#from-slotkit-20-to-21) shows the new form of each deprecated call. Requires MenuKit 7.4.0 or newer.

#### Added

- Every declaration about a slot takes a mode scope: `ModeScope.SURVIVAL`, `CREATIVE`, or `BOTH`. Vetoes, category rules, per-slot rules and gates, and slot-state channels read the acting player's mode on each check.
- Slot anchors let a panel sit on one slot or one slot group. `SlotPlacement.slot(address, dx, dy)` places a panel at an offset from one slot, and `SlotPlacement.group(group, position)` places it around a slot group. A panel can have a different placement in each mode and shows only where its slot or group is on the open menu.
- `Address.playerInventory(n)` names a player inventory slot, the same address on every screen and for every player, so a rule can name it at startup.
- `SlotGroupElement` shows any slot group by its id, such as a chest's storage or another mod's created group, in a mod's own panel.
- With Inventory Profiles Next installed, its sort and move-all act on one container at a time.
- `OnDemandGroup` lets the server add slot groups to the menu a player has open and remove them, such as a shulker box opened from a chest's screen. Slot indices never shift while the menu is open.
- `CategoryPanels` puts a panel on every shown group of a category, created and on-demand groups included, and `SlotGroupCategory.BUNDLE_STORAGE` names an opened bundle.
- `MenuSwitch` switches a player between their inventory, other menus, and back, while the chest they opened in the world stays open behind them. The cursor stack moves with each switch.
- `ItemSources` also searches a container the caller supplies, such as the open chest, and `ItemSources.live` keeps a menu open on a nested item across many writes.

#### Changed

- A menu's own shift-click stops before every slot SlotKit added, so a chest's item goes to the player's inventory and never into another mod's slots.
- A player inventory slot's address no longer names the player. A 2.0 address saved with a player's UUID reads as `Address.playerInventory(n)`.
- Creative's item tabs no longer carry SlotKit slots, as vanilla shows only the hotbar there.
- The forms without a mode scope, and `SlotGroupPanelAdapter.on(...)`, are deprecated, and mean both modes.

#### Fixed

- Vetoes and the operation cascade hold in creative, on both tabs. A locked slot stays locked there.
- A click a mod sends in creative goes through creative's own click path and lands on the right slot, so buttons that move items work in creative as in survival.
- A mod looking up an inventory slot on creative's inventory tab finds it.
- A row of slots scrolled out of view no longer takes clicks meant for what's below it.
- A vanilla slot the menu turned off stays hidden in a mod's panel.
- A custom menu panel placed on a slot anchor is logged instead of crashing the client.
- An anchored panel no longer draws once its slot or slot group has scrolled out of view.
