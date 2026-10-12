# Limits

What SlotKit does not do, and the open gaps a consumer can hit. MenuKit lists its own on [MenuKit's limits page](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md). [concepts.md](concepts.md) defines the terms.

## Open gaps

Behaviour that is incomplete in the current release.

| Area | Current behaviour |
|---|---|
| Shift-click into a created slot | Not routed on a vanilla menu, since vanilla's `quickMoveStack` does not know the slot. Direct click works. The consumer overrides `quickMoveStack` in its own mixin, or accepts the gap. |
| Created slot in an overlay panel | Not supported. Overlay panels draw after vanilla's slot pass, so a slot they host is placed one frame late and drawn under the panel. Put created slots in flow panels. |
| Reactions | `ON_INSERT_OBSERVED` and `ON_TAKE_OBSERVED` fire on the client when a slot's synced contents change. They are UI feedback. No server-side reaction key exists. Server code that must act on a change hooks the slot or menu method that makes it. |
| Window scope | Every address resolves in the primary scope. Per-tab and per-sub-window scopes are not active. |
| The window | Gates, operations, reactions and behaviour keys apply to slots only. A panel's visibility and opacity are MenuKit's, set on the panel with `visibleWhen` and `opaque`. |
| A slot group built with a menu, before any menu | `CreatedSlots.onto(menu, player)` registers a group when that menu is built, so `SlotGroups` lists it only after the first one. Declare the group at init with `SlotGroups.declare` to list it from the title screen. |
| Operations on a slot reached with no menu open | An inventory insert (`Inventory.getFreeSlot`) has no menu, so a vanilla slot's category is unknown there. `INVENTORY_INSERT` on such a slot resolves from the slot's own declaration or the key's default, then the vetoes. The group and category rungs need a menu. |
| Operations the server works out for itself, on a server without SlotKit | The client does not send a click that its own slot does not allow (plain click, shift-click out, drop, swap, a slot joining a drag). Where a shift-click lands, what double-click collect sweeps, and where a picked-up item goes are decided by the server. Those hold in singleplayer and on a LAN host, where SlotKit runs the server side, and not on a server that does not run it. The same goes for a Q action a mod sends to the connection itself instead of through `LocalPlayer.drop`. |
| Slot layouts the handshake does not compare | The join check compares each required mod's `ContainerPanel` slot groups. Custom menus and groups built with `CreatedSlots.onto` are not compared, so two versions with different layouts there desync. Change the menu or panel id when its layout changes. |
| Drop rule key | `dropsOnDeath(DropRule)` on a player storage attachment covers death. No window key covers drop rules. |
| Advancements | Created slots use a separate container and do not fire vanilla's inventory-change trigger. The consumer fires it. |
| Item-attached storage | Uses vanilla's container component only. |
| Block-portable content | Slot state travels with a carried shulker box. General content travel for other blocks does not. |

## Runtime constraints

- Only a mod that declares `"custom": { "slotkit": { "bothSides": "required" } }` builds slots. `ContainerPanel.define` and `CustomMenu.define` throw for any other, and for an id whose namespace is not a loaded mod's id.
- A server running a `required` mod refuses a client that lacks SlotKit or that mod, before the world loads. A client with a `required` mod on a server without it builds none of that mod's slots.
- A `SlotGroupPanelAdapter` with no `.on(...)` fails at the first screen open with the panel id in the message.
- Adapters, slot groups, operations, vetoes and channels are declarations. Declare them at init. Declaring one after the client starts throws.
- `CustomMenu` handler factories run on both sides and must produce identical storages.
- A `SHARED` slot-state channel with no `canWrite` rule cannot be written from a client, only by server code.
- Slot-state writes from one player are limited to a burst of 64, then 32 a second. Past that the server refuses them until the allowance refills.
- A client sends a slot-state write only to a server that registered the channel. Otherwise the write is neither sent nor shown.
- A custom menu with no `validWhen` cannot be opened by a client's request, only by the server.
- The slot state a shulker box carries as an item is never sent to clients. A creative player who moves such an item sends the game's copy back without it, so its slot state is lost. Survival is unaffected.
- Hoppers and dispensers ask a slot's gate, not the vetoes. No automation operation exists, so a lock meant to stop automation needs its own seam.
- Only the `api` packages are public. Everything else is internal and may change in any release.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
