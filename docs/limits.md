# Limits

What SlotKit does not do, and the open gaps a consumer can hit. MenuKit lists its own on [MenuKit's limits page](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md). [concepts.md](concepts.md) defines the terms.

## Open gaps

Behavior that is incomplete in the current release.

| Area | Current behavior |
|---|---|
| Created slot in an overlay panel | Not supported. Overlay panels draw after vanilla's slot pass, so a slot they host is placed one frame late and drawn under the panel. Put created slots in flow panels. |
| Reactions | `ON_INSERT_OBSERVED` and `ON_TAKE_OBSERVED` fire on the client when a slot's synced contents change. They are UI feedback. No server-side reaction key exists. Server code that must act on a change hooks the slot or menu method that makes it. |
| Window scope | Every address resolves in the primary scope. Per-tab and per-sub-window scopes are not active. |
| The window | Gates, operations, reactions and behavior keys apply to slots only. A panel's visibility and opacity are MenuKit's, set on the panel with `visibleWhen` and `opaque`. |
| A slot group built with a menu, before any menu | `CreatedSlots.onto(menu, player)` registers a group when that menu is built, so `SlotGroups` lists it only after the first one. Declare the group at init with `SlotGroups.declare` to list it from the title screen. |
| Operations on a slot reached with no menu open | An inventory insert (`Inventory.getFreeSlot`) has no menu, so a vanilla slot's category is unknown there. `INVENTORY_INSERT` on such a slot resolves from the slot's own declaration or the key's default, then the vetoes. The group and category rungs need a menu. |
| Operations the server works out for itself, on a server without SlotKit | The client does not send a click that its own slot does not allow (plain click, shift-click out, drop, swap, a slot joining a drag). Where a shift-click lands, what double-click collect sweeps, and where a picked-up item goes are decided by the server. Those hold in singleplayer and on a LAN host, where SlotKit runs the server side, and not on a server that does not run it. The same goes for a Q action a mod sends to the connection itself instead of through `LocalPlayer.drop`. |
| Slot layouts the handshake does not compare | The join check compares each required mod's `ParityGroup` slot groups. Custom menus and groups built with `CreatedSlots.onto` are not compared, so two versions with different layouts there desync. Change the menu or group id when its layout changes. |
| Drop rule key | `dropsOnDeath(DropRule)` on a player storage attachment covers death. No window key covers drop rules. |
| Advancements | Created slots use a separate container and do not fire vanilla's inventory-change trigger. The consumer fires it. |
| Item-attached storage | Uses vanilla's container component only. |
| Block-portable content | Slot state travels with a carried shulker box. General content travel for other blocks does not. |
| Item sources reach | `ItemSources` walks the inventory's 36 main slots, the ender chest, a container the caller supplies, shulker boxes and bundles. Armor, the offhand, the cursor and created slots are not walked, except a player attachment declared `carried()` when the scope asks for it. Inside items, it opens shulker boxes and any item with bundle contents, and no other container. A stack of more than one shulker box or bundle is not opened, since its boxes share one set of contents. |
| Item sources on the client | `find` reads only what the client has. Vanilla sends a client its ender chest's contents only inside the chest's own menu, so a client finds nothing there. Every write happens on the server. |
| Active counts | Held per player for the session. They reset to the declared start after death and on login, and are not saved. |

## Deliberate limits

Behavior that is intended, not an open gap.

| Area | Behavior |
|---|---|
| Mode scope with no player | A rule's mode scope (`SURVIVAL`, `CREATIVE` or `BOTH`, the game mode it is declared for) is checked against the acting player's mode ([mode scope](concepts.md#mode-scope)). Where no player exists, only `BOTH` declarations apply. That covers a gate asked by a hopper or a dispenser, the observed reactions (`ON_INSERT_OBSERVED` and `ON_TAKE_OBSERVED`), and `WindowHandle.behavior(key)`. A gate on a slot of a player's inventory with no acting player is judged in that player's mode. `WindowHandle.behavior(key, player)` resolves for a player's mode. |
| A `SlotSpec`'s own rules | The rules a `SlotSpec` declares for its group (its gate from `gate` or `accepts`, `binding`, `mending`, `shiftClickOut`, `shiftClickIn`, `collect` and `dragFill`) have no mode scope and hold in both modes. A slot that needs a rule per mode declares it on its address with `Window.slot(scope, address)`, which outranks the group. A category rule from `inherent` does not, since a group's declaration outranks its category's. |
| Creative is judged on the client | In creative, vanilla applies a click on the client and sends the server only the slots' new contents. SlotKit judges creative's clicks on the client, and the server cannot judge them, as on a server without SlotKit. |
| Other sorting mods | Mods that tell containers apart by `slot.container` treat each slot group as its own container ([separate containers](concepts.md#separate-containers)). A sorting mod that treats every slot that is not the player's as one container merges them. SlotKit covers Inventory Profiles Next ([below](#inventory-profiles-next)) and no other such mod. |

## Inventory Profiles Next

Inventory Profiles Next (IPN) treats a screen as the player's slots plus one container of everything else, so its sort and move-all would pull a created group's items into the chest beside it. SlotKit carries a fix for it, checked against IPN 2.3.8 for Minecraft 26.3, and applied only when IPN is loaded. With it, IPN acts on one container at a time:

- With the mouse over a SlotKit slot group, IPN leaves out every slot that is neither the player's nor that group's, so its sort key sorts that group alone.
- Anywhere else, IPN leaves out every SlotKit slot group, so the sort button on a chest sorts the chest alone, and move-all moves into the chest and never into a group.
- The player's own slots are left to IPN, so restocking still works.

The fix hooks one method inside IPN. If a later IPN version changes that method, the fix does nothing and the game does not crash.

## Runtime constraints

- Only a mod that declares `"custom": { "slotkit": { "bothSides": "required" } }` builds slots. `ParityGroup.register` and `CustomMenu.define` throw for any other, and for an id whose namespace is not a loaded mod's id.
- A server running a `required` mod refuses a client that lacks SlotKit or that mod, before the world loads. A client with a `required` mod on a server without it builds none of that mod's slots.
- A slot anchor's panel ([slot anchors](concepts.md#slot-anchors)) takes a region, `center()` or `pixel(...)` position. `screenAnchor(...)`, `main()` and a panel with no placement throw when its `AnchorPanelAdapter` or `SlotGroupPanelAdapter` is built. An adapter whose panel names no anchor, and that never called the deprecated `.on(...)`, fails at the first screen open with the panel id in the message.
- Adapters, slot anchors, slot groups, operations, vetoes and channels are declarations. Declare them at init. Declaring one after the client starts throws.
- `CustomMenu` handler factories run on both sides and must produce identical storages.
- A `SHARED` slot-state channel with no `canWrite` rule cannot be written from a client, only by server code.
- Slot-state writes from one player are limited to a burst of 64, then 32 a second. Past that the server refuses them until the allowance refills.
- A client sends a slot-state write only to a server that registered the channel. Otherwise the write is neither sent nor shown.
- A custom menu with no `validWhen` cannot be opened by a client's request, only by the server.
- A custom menu panel the client has no `ClientMenu.panel` for, as on a server running another version of the mod, is logged as an error and shown as a plain panel of its slots.
- The slot state a shulker box carries as an item is never sent to clients. A creative player who moves such an item sends the game's copy back without it, so its slot state is lost. Survival is unaffected.
- Hoppers and dispensers ask a slot's gate, not the vetoes. No automation operation exists, so a lock meant to stop automation needs its own seam.
- Only the `api` packages are public. Everything else is internal and may change in any release.
- `ItemSources`, `ItemRef` and `LiveItem` writes run on the server thread only. A nested `LiveItem` can't tell two identical items apart: if one is swapped for an exact copy at its path, it carries on with the copy. A walk goes at most 16 containers deep by default (`ItemScope.maxDepth`), and `ItemPath.STREAM_CODEC` refuses a path deeper than 16.
- `ItemSources.pathOf` returns `null` on the client for an ender chest's own menu slots, which vanilla backs with a stand-in container there.
- An `ItemState` is a data component, so a stack carrying it doesn't merge with one that doesn't. Only a mod that declares `bothSides: required` declares one.
- `SlotDoubleClick` gives one pair to one registration: the first registered whose `Starts` accepts the slot. Two mods taking the same slot depend on load order.
- `Window` declarations are about a slot, not about a player. A player inventory address names that slot for every player, so a per-player rule is a veto that reads `ref.player()` or a `PRIVATE` slot-state channel. On a menu that shows another player's inventory beside the viewer's own (an admin view), both players' slot 4 have one address, so a rule on it reaches both, and an element showing it shows the first on the menu.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
