# Concepts

Definitions of the terms SlotKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term. Panels, elements, regions, anchors and claims are MenuKit's, defined on [MenuKit's concepts page](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md).

## SlotKit and MenuKit

SlotKit is one mod, `slotkit`, built on MenuKit. It holds everything about slots: slot groups and categories, slot operations and vetoes, addresses and the window, created slots, custom menus, storage, and per-slot state. SlotKit 2.2 depends on MenuKit 7.5 and uses only MenuKit's public API. MenuKit does not depend on SlotKit.

SlotKit was MenuKit: Containers, plus the slot code MenuKit carried before 7.0.0. [upgrading.md](upgrading.md) moves a mod from Containers or from MenuKit 6's slot code.

## Public API

The API is the `api` packages: `com.trevlar.slotkit.api.*`. [Versioning](versioning.md) covers them. Every other package carries `@ApiStatus.Internal` and is outside the contract, so a mod that calls into one can break in any release. A class that names a screen type is client-only, and its javadoc says so.

| Package | Holds | Tier |
|---|---|---|
| `api` | `Presence`, what the other side runs | Both |
| `api.group` | Slot groups, categories, sets, resolvers, `SlotGroupPanelAdapter` | Client |
| `api.window` | Addresses, the window, behavior keys, slot operations, gates and vetoes | Client |
| `api.slot` | Created slots, `ParityGroup`, `SlotSpec`, shift-click routes, and the client-only `SlotElement`, `SlotFlowElement`, `SlotGroupElement`, `SlotPlacement`, `ClientSlots` and `SlotDoubleClick` | Client and server |
| `api.menu` | Custom container menus and their screens | Client and server |
| `api.storage` | `Storage` and storage attachments | Client and server |
| `api.state` | Slot-state channels | Client and server |
| `api.item` | `ItemSources`: items in the inventory, the ender chest, a supplied container, shulker boxes and bundles; `LiveItem` for a menu on a nested item; `ItemState` on both sides and the client-only `SlotLooks` | Server; `find`, `pathOf` and `ItemState` on both |

## Two tiers

A tier says what the other side needs. It is not a jar or a source set. SlotKit is one jar, and the client tier's code also runs on a server that has SlotKit, so the server enforces the same rules.

**The client tier finds and acts on slots in any menu.** It is `api.group` and `api.window`. A mod that uses only this tier needs SlotKit on the client alone and works on any server, vanilla included. It holds three promises:

- It never makes a server refuse a client. A server refuses only for a `required` mod, and the definition check stops a client-tier mod from building slots or menus.
- It is never the reason its player is refused. SlotKit's client never disconnects itself. A server without SlotKit sends no hello, and the client carries on.
- It works on a vanilla server. Slot groups resolve from the client's own menu. Vetoes refuse at the client before a click is sent. The client sends no SlotKit payload unless `Presence.serverHasSlotKit()` is true.

When the server also has SlotKit and the mod, the same rules hold on the server too.

**The client and server tier builds real slots and keeps state with them.** `api.slot`, `api.menu` and `api.storage` build slots, and a mod that builds slots declares `required` ([Declaring sides](#declaring-sides)). `api.state` is per-slot state. It is on when both sides have the channel and off otherwise. A client skips values for a channel it does not have, so state needs no declaration to be safe.

## Declaring sides

A mod states in its `fabric.mod.json` whether it needs SlotKit on both sides:

```json
"custom": { "slotkit": { "bothSides": "required" } }
```

| Value | Use it when | Effect |
|---|---|---|
| `required` | The mod builds slots or custom menus. | A server with the mod admits only clients that have SlotKit and the mod. On a server without the mod, the client builds none of its slots. |
| `preferred` | The mod works on one side and does more with both, for example shared slot state. | Nobody is refused for it. The handshake lists it, so the other side can ask `Presence` about it. |
| No block | The mod only finds and acts on slots. | The mod works on any server and never causes a refusal. |

Any other value throws when the mod loads. The message names the mod and the line to write.

**The definition check.** `ParityGroup.register(owner, ...)` and `CustomMenu.define(id, ...)` throw unless the id's namespace is the id of a loaded mod that declares `required`. A group's owner id is namespaced by the id of the mod that defines it: `"mymod:pockets"` for a mod whose id is `mymod`. The message names the group or menu and what to change:

- An id with no namespace: `ParityGroup 'pockets': an id that builds slots is namespaced by the id of the mod that defines it ('mymod:pockets').`
- A namespace that is no loaded mod: `... its namespace 'my-mod' is not a loaded mod's id.`
- A mod without `required`: `ParityGroup 'mymod:pockets' builds slots, so SlotKit is needed on both sides wherever 'mymod' is installed. Add to mymod's fabric.mod.json: "custom": { "slotkit": { "bothSides": "required" } }`

The check fails in development, before any player joins. It also keeps a server with only `preferred` mods safe for vanilla clients, because such a server never changes a menu's slot count.

**The handshake.** Before the world loads, a server with SlotKit says hello. The hello carries SlotKit's protocol, each of the server's mods with a `slotkit` block, and every registered slot-state channel. The client answers with its own declaring mods and the slots each of them builds. The server then judges the client:

| Client | The server has a `required` mod | The server has none |
|---|---|---|
| Cannot hear the hello (vanilla, Fabric without SlotKit, old Containers) | Refused. The message names SlotKit and each required mod. | Admitted, without SlotKit |
| Another SlotKit protocol | Refused. The message names both protocols. | Admitted, without SlotKit |
| Missing some required mods | Refused. The message names the missing mods. | Cannot happen |
| Has every required mod, but one of them builds different slots | Refused. The message names that mod: its slots differ. | Cannot happen |
| Has every required mod, with the same slots | Admitted, with SlotKit | Admitted, with SlotKit |

The slot comparison is per mod: each slot group the mod registers through `ParityGroup`, with its slot count. Two versions of a mod that register the same groups admit each other. A version that adds, removes or resizes one refuses players on the other version, with a message, instead of desyncing them. Custom menus and groups built with `CreatedSlots.onto` are not compared. When a new version of a mod changes their slot layout, change the menu or group id in the same release, so mismatched players do not desync.

Each message links the homepage from each named mod's `contact.homepage`. A mod with no homepage is named only. The singleplayer owner and a LAN host are never judged.

## Presence

`Presence` answers what the other side runs, as the handshake found it when the player joined.

| Side | Method | True when |
|---|---|---|
| Client | `Presence.serverHasSlotKit()` | The server runs SlotKit. |
| Client | `Presence.serverHas(String modId)` | The server runs that mod with a `slotkit` block. |
| Client | `Presence.serverHasChannel(Identifier channelId)` | The server registered that slot-state channel. |
| Server | `Presence.clientHasSlotKit(ServerPlayer player)` | That player's client has SlotKit. |
| Server | `Presence.clientHas(ServerPlayer player, String modId)` | That player's client has that mod with a `slotkit` block. |

The client methods return false when the game is not connected. The answers are fixed when the player joins and reset at the next join. In singleplayer every answer is true for every mod in the game. There is no event: read `Presence` when you build your UI, or on Fabric's `ClientPlayConnectionEvents.JOIN`.

In a veto or a gate, ask about the acting player with `Presence.clientHasSlotKit(ctx.actingPlayer())`. `actingPlayer()` is null for automation, so check it first.

A client's answer about itself is its claim. It decides what the server sends that client and whether the client is admitted. It never authorizes a write.

## Slot group category

A `SlotGroupCategory` is a name for a group of slots, such as `PLAYER_INVENTORY`, `PLAYER_HOTBAR`, `CHEST_STORAGE`, or `FURNACE_INPUT`. It carries no rendering rule. SlotKit maps categories to slot indices per menu each frame.

Every created slot group declares a category and is listed under it. Read the registry three ways:

| Call | Answers |
|---|---|
| `SlotGroupCategories.all()` | every category that exists, with no menu open |
| `SlotGroupCategories.of(menu)` | every category on this menu, with its slots |
| `SlotGroupCategories.categoriesBySlot(menu)` | the category of each slot on this menu |

A mod finds another mod's slots this way, with the client tier only.

Groups are listed too, with no menu open, for a settings screen that runs from the title screen:

| Call | Answers |
|---|---|
| `SlotGroups.all()` | every declared group: one per vanilla category, and every created group |
| `SlotGroups.listing()` | the player-facing rows: each lone group, and each named set once |
| `SlotGroups.of(slotRef)` | the group one slot is in, as a veto sees it; `null` when nothing is known |

A category cannot tell a mod's pockets from the main inventory when both declare `PLAYER_INVENTORY`, but a group can. A mod that splits one thing into many groups, one per anchor, puts them in one `SlotGroupSet` with `SlotGroups.declare(id, category, set)`. The listing then shows the set as one row. A choice saved under a set's key reaches every group in it.

Groups and sets are named like operations. `SlotGroups.name(id)` and `name(set)` are translatable on `slot_group.<...>` and `slot_group_set.<namespace>.<path>`. SlotKit ships vanilla's names, and a mod ships its own. `SlotGroupId.asString()` and `SlotGroupSet.asString()` are stable text for a config file, with `parse` and a `CODEC` each. A row's `source()` names the mod it comes from, for a screen to show as "Satchel (My Mod)". It is `null` for vanilla's groups. Otherwise it is the display name of the mod whose namespace the row is named in: a set's namespace, a created group's panel id before the colon, or a mod's own category. With no loaded mod of that id it is the namespace itself. `SlotGroups.source(id)` answers the same for a group on a live menu.

`SlotGroups.of(slotRef)` names the group of the slot a `SlotRef` describes. For a created slot it is the slot's own group, never its category's, so a veto can tell a pocket from the main inventory. For a vanilla slot it is the slot's group on its menu. For a player-inventory slot with no menu, as in a world pickup, it is the vanilla group its index sits in.

A `ParityGroup` registers at init and is listed from the title screen. A group built with a menu (`CreatedSlots.onto`) is listed after the first menu that carries it. To list it from the title screen, its mod declares it at init with `SlotGroups.declare`.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search, including searches run by mods with no knowledge of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Mode scope

A mode scope says which game mode a declaration is for (since 2.1.0). It is MenuKit's `ModeScope` (`com.trevlar.menukit.api.panel.ModeScope`), with three values:

| Value | Applies while the player |
|---|---|
| `SURVIVAL` | does not have infinite materials: survival, adventure and spectator |
| `CREATIVE` | has infinite materials (`Player.hasInfiniteMaterials()`, the switch vanilla reads) |
| `BOTH` | is in either mode, and also when there is no player |

A container a creative player opens is `CREATIVE`. MenuKit defines the value and uses it for panel placements ([MenuKit's mode scope](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#mode-scope)). SlotKit uses the same value for every declaration about a slot:

| Declaration | Scoped form |
|---|---|
| A veto | `SlotOperations.veto(scope, veto)` |
| A category rule | `SlotOperations.inherent(scope, category, operation, value)` |
| A per-slot rule or gate | `Window.slot(scope, address)` |
| A slot-state channel | `SlotState.register(scope, id, ...)` |
| Where a panel goes | MenuKit's `Panel.Builder.in(scope, placement)` ([slot anchors](#slot-anchors)) |

The mode is read from the acting player on each check and never kept. A `/gamemode` with a screen open therefore applies to the next click, with no reopen. At each level of the [cascade](#operation), a declaration for the player's current mode wins over that level's `BOTH` declaration, so `BOTH` plus `CREATIVE` reads "this, except that in creative". A more specific level still wins whatever its scope: a `BOTH` declaration on one slot outranks a `CREATIVE` one on its category. A `SURVIVAL` or `CREATIVE` veto applies only while the acting player is in that mode. A slot-state channel out of its scope reads as its default value, and a client neither shows nor sends a write to it. The stored values are kept and show again when the mode comes back. With no player, as for a hopper, only `BOTH` declarations apply ([limits](limits.md#deliberate-limits)).

The 2.0 forms with no scope (`veto(veto)`, `inherent(category, ...)`, `Window.slot(address)`, `SlotState.register(id, ...)`) still work. They mean `BOTH` and are deprecated ([upgrading](upgrading.md#from-slotkit-20-to-21)).

## Operation

An operation is something done to a slot. Vanilla ships eleven, one key per thing a player can do: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT` (double-click), `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `INVENTORY_INSERT`. The last covers every item given to the inventory, not only a pickup from the ground. `/give`, pick block and a crafting grid's returns go through the same two vanilla methods. The eleven are `BehaviorKeys.VANILLA_OPERATIONS`, every one on by default. SlotKit enforces them at vanilla's own seams for every slot kind. A plain click takes, puts, or both when it swaps different items. A swap key has two slots, and both must allow it. The client does not send a click its own slot does not allow, so that part holds on any server. Q, Ctrl-Q and F while playing, with no screen open, are the same `DROP`, `DROP_STACK` and `OFFHAND_SWAP` on the selected hotbar slot. The client refuses them before it drops or sends, and the server refuses them when the action arrives. An offhand swap also needs the offhand slot to allow it.

An operation has a role: it takes items out of a slot, puts items in, or both. `SlotOperations.define(op, Role.TAKE)` records it and `SlotOperations.role(op)` reads it. A settings screen uses the role to tell which operations matter for a lock on an item already in a slot. `define(op)` without a role means both.

An operation also declares which slot groups it can act on at all, as in `SlotOperations.define(op, role, AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_INVENTORY))`. It names vanilla's groups. Every group a mod adds applies automatically, unless the operation opts out with `.except(groupId)` or `.except(slotGroupSet)`. `AppliesTo.vanillaExcept(...)` names the vanilla groups it skips instead. `AppliesTo.only(PLAYER_HOTBAR, PLAYER_INVENTORY)` (since 1.1.0) takes a mod's group only when the group declared one of the named categories, so pockets declared `PLAYER_INVENTORY` apply and an elytra slot in its mod's own category does not, with neither mod naming the other. An operation defined without an `AppliesTo` gets `AppliesTo.EVERY_GROUP`. `SlotOperations.appliesTo(op, groupId)` answers for one group. `SlotOperations.groups(op)` lists every declared group the operation applies to, which is what a reach setting offers. SlotKit declares vanilla's eleven from what vanilla does. Nothing is placed, dragged or shift-clicked into an output slot. The crafter's result slot cannot be touched by anything. Double-click collect skips the crafting, smithing, stonecutter, cartography and trade results, whose menus refuse it. An inventory insert lands only in the hotbar, the main inventory and the offhand. This is a declaration, not a rule SlotKit enforces. It tells a settings screen where an operation can reach.

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, ships a name and a description in its lang file, and asks `SlotOperations.allows` in its own code before acting. `SlotOperations.all()` lists every published operation. `SlotOperations.name(op)` and `description(op)` are translatable components on `slot_operation.<namespace>.<path>` and `.description`, for a settings screen to list. SlotKit needs no change for a new operation to exist.

Two things decide what a slot allows. The first is the cascade, which holds what the slot's author declared:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. `SlotOperations.inherent(scope, category, operation, value)` sets the category rung once, for a [mode scope](#mode-scope). A group's own declaration outranks it. A per-slot declaration outranks the group. A vanilla slot has one group per category on a menu, so for vanilla slots the group and the category are one rung. "Shift-click may land in the 9 by 3 but not the hotbar" is `inherent(ModeScope.BOTH, PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)`.

The second is a veto. `SlotOperations.veto(scope, rule)` registers a rule that can only say no, for a mod that is not the slot's author. A player's lock is the example it exists for. A veto sits beside the cascade and subtracts. It never overwrites what the slot's author declared, and it has nothing to restore when the lock lifts. A veto that throws is logged once and skipped.

`SlotOperations.allows(menu, slot, player, operation)` is the one check. It passes when the cascade says yes and no veto says no. Every seam calls it, and every operation a mod adds calls it too. The `SlotRef` a veto sees carries the container and index, the live slot and menu when there is one, the acting player when there is one, and the slot's category. A move a mod makes on the server with no click behind it, such as a direct `quickMoveStack` call, still names a player. That player is the one whose inventory the menu shows.

A mod that performs its operation by sending clicks wraps them in `SlotOperations.as(take, put, () -> sendClicks())`, or in `as(op, ...)` when both sides are one operation. Each click sent inside carries the operation it serves. The first operation applies to the slot it takes from and the second to the slot it puts into. The vetoes judge that operation instead of the gesture. A lock that refuses shift-click but allows restock therefore lets the restock's shift-click through. The slot's author still judges the gesture. The tag reaches the integrated server. Send clicks inside the block, on the calling thread. A simulated click that is not wrapped counts as the gesture it looks like.

In creative, vanilla applies a click on the client and sends the server only the slots' new contents. SlotKit therefore judges each click there, on the client, on both tabs, so a veto holds in creative as it does in survival (since 2.1.0). Shift-clicking the bin leaves the items in slots that refuse a take.

On the creative screen, send clicks the same way, with `handleContainerInput` and the slot's `index` on the open menu, on either tab. SlotKit runs each one through creative's own click path, which applies it on the client and syncs the slots the way creative does, so the server never takes it as a container click on the wrong slot. Each slot there reports the player's inventory slot it shows from `getContainerSlot()`, as on the survival inventory.

Registration order does not matter. Declarations freeze once every mod has initialized, through MenuKit's `Declarations`. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)` and `dragFill(false)`.

## Gate

A gate is the slot author's other rule. It sets what the slot accepts and releases, and how many of an item it holds. It is a `SlotGate` on `BehaviorKeys.GATING`, set with `Window.slot(scope, address).gate(...)` or `SlotSpec.gate(...)`. `BehaviorKeys.BINDING` adds Curse of Binding to any slot. `BehaviorKeys.MENDING` lets the items in a slot on the player's inventory menu take part in XP-orb Mending; it needs SlotKit on the server, which runs the orb seam. Declared on a category, it reaches vanilla's slots and created ones alike (since 1.1.0), so Mending across the whole inventory is two lines, and a group that says `mending(false)` still wins:

```java
SlotOperations.inherent(ModeScope.BOTH, SlotGroupCategory.PLAYER_INVENTORY, BehaviorKeys.MENDING, TriBool.TRUE);
SlotOperations.inherent(ModeScope.BOTH, SlotGroupCategory.PLAYER_HOTBAR, BehaviorKeys.MENDING, TriBool.TRUE);
```
 SlotKit enforces gates and binding at the slot's own `mayPlace`, `mayPickup` and `getMaxStackSize`, which every click, shift-click and automation path consults. It also enforces them in the merge pass of a shift-click and at the hopper and dispenser seams, once per vanilla method.

A gate is not a lock. A lock is another mod's policy laid over a slot, and that is a veto. It only says no and leaves the author's gate alone.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner, which is a player, block entity, entity, or item stack.

`ParityGroup` creates slots on the player's inventory menu and projects them onto every container menu. `CustomContainerMenu` creates slots on a custom menu. `CreatedSlot.of(slot)` tells a created slot from any other, on any screen, creative's included.

The code that builds slots, stores state and judges writes is common, and runs on the server and the client alike. The code that draws is client-only: `SlotElement`, `SlotFlowElement`, `CustomContainerScreen`, `ClientMenu` and `ClientSlots`. SlotKit builds no panel. A slot is an element the mod puts in its own MenuKit panel, next to buttons and labels, and the mod's panel decides where it sits, how it looks and which screens show it. A custom menu's handler holds each panel's id, its slot groups and whether it is shown. The panel itself is a MenuKit `Panel` the mod declares with `ClientMenu.of(menu).panel(id, ...)`, slots included. The server decides whether a panel is shown and syncs that to the client. A player may toggle only a panel declared `toggleable()`, and a client may open a menu only when its `validWhen` holds, which is also how long it stays open.

Only a `required` mod builds created slots ([Declaring sides](#declaring-sides)). A client on a server without SlotKit, or without the mod, builds none of that mod's slots and sends nothing SlotKit-only there, instead of desyncing.

## Parity group

A `ParityGroup` is a group of created slots on the player, registered once in common code with `ParityGroup.register(owner, spec)`. The `SlotSpec` holds the group's id, category, slot count, storage and rules. Both sides build the same slots from the same registration, which is what parity means here, and the join check compares each mod's parity groups and their slot counts ([Declaring sides](#declaring-sides)). The slots sit on the player's inventory menu and on every container menu, so a player reaches them from any screen.

A parity group has no position and no look. The mod shows it in a MenuKit panel of its own, from the client initializer:

- `SlotFlowElement.of(group)` lays a group's slots out at vanilla's 18 px pitch and wraps them to the panel's width. `SlotFlowElement.builder().addGroup(...)` puts several groups in one flow.
- `SlotElement.builder().slot(group.id(), i).at(x, y)` places one slot by hand. A `SlotElement` can also show a slot the menu built itself, named by an `Address`.
- `SlotGroupElement.of(group.id())` shows the group in rows, found by its id on whatever menu is open ([slot group element](#slot-group-element)).
- `ClientSlots.isShown(group.id())` is false where the group's slots are not live, such as on a server without SlotKit, so a panel can hide with them.

One element presents a slot at a time. On a screen with no element for it, the slot stays on the menu, parked where nothing draws or clicks it.

Creative's item tabs hold only the hotbar, as in vanilla, and SlotKit adds no slot to them (since 2.1.0). A parity group's slots are not on that menu, so `ClientSlots.isShown` is false there, and they are back on the inventory tab.

**Active count.** `SlotSpec.activeCount(start)` lets the server decide how many of the group's slots are live for each player, changed with `group.setActiveCount(player, n)`. Slots past the count are inert on both sides: they sync to the client as empty and refuse clicks and shift-clicks. `SlotFlowElement` lays out only the live slots. The count belongs to the player, so it holds on every menu. It returns to the declared start after death and on a new login, and is not saved. Lowering the count leaves the items in storage. A mod that shrinks a group decides what happens to them.

**Shift-click routes.** A route registered with `QuickMoveRoutes.register` answers which destinations a shift-clicked stack tries, in order, for the slot it came from. Routes are registered in common code and run on both sides, the client to predict the move. The first route with an answer wins. With no route, a group declared `shiftClickIn(true)` is tried around the menu's own routing, and a shift-click out of a created slot goes to the player. Every slot on the way still judges the move by its gate, the vetoes, `shiftClickIn` and its active count.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `Address.createdSlot(group, index)` names a created slot, with the group from `CreatedSlots.groupId(panelId, groupId)` or `ParityGroup.idOf(owner, groupId)` (a registered group's `id()`). `Window.slot(scope, address).set(key, value)` attaches behavior by address, for a [mode scope](#mode-scope).

`Address.playerInventory(index)` (since 2.1.0) names a slot of the viewing player's own inventory, by its inventory index: 0 to 8 are the hotbar, 9 to 35 the main grid, 36 to 39 the armor from feet to head, and 40 the offhand. It is the same slot on every screen that shows it, survival's inventory, both creative tabs and every container screen included, and it equals `SlotRef.address()` for that slot. A rule declared on it at init therefore holds wherever the slot is. An index outside 0 to 40 throws. A player inventory address saved by 2.0, which named the player by UUID, parses as this form. `Address.menuSlot(type, index)` is for a menu's own containers, such as a furnace's fuel slot. It names a slot by its index on one menu type and is not equal to the slot's own address, so a rule keyed on a slot's address does not see it. A veto reads a slot's address from its `SlotRef` with `address()`. `asString()`, `Address.parse` and `Address.CODEC` save one as text. Never save its parts.

## Slot anchors

A slot anchor is a MenuKit anchor whose box is one slot's frame or one slot group's box on the open screen (since 2.1.0). A panel goes on one with a `Placement` from `SlotPlacement`, given to MenuKit's `Panel.Builder.in(scope, placement)`, and is registered with MenuKit's `AnchorPanelAdapter` ([MenuKit's placements](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#placement)). Both are client only.

| Placement | Where the panel goes |
|---|---|
| `SlotPlacement.slot(address, dx, dy)` | At `(dx, dy)` from the top-left of the slot's 18 by 18 frame, at the panel's natural size. It does not stack with other panels and is not kept on the screen. A panel holding one slot at `(0, -18)` with no padding sits one slot above, on vanilla's grid. The address names a vanilla or created slot, and any other address throws. |
| `SlotPlacement.group(group, position)` | Around a slot group's box. `region(OutsideRegion)` stacks with the group's other panels by priority; `center()` and `pixel(...)` work as anywhere. The group is a `SlotGroupId` or a `SlotGroupCategory` (its vanilla group). |

A panel on a slot anchor shows where that slot or group is on the open menu this frame, and nowhere else. Armor is not on a creative item tab, so a panel placed on an armor slot does not draw there, with no tab check in the mod. A slot or group has no screen spots, so `screenAnchor(...)`, `main()` and a panel with no placement are rejected when the adapter is built. The first placement on a slot or group declares its MenuKit anchor, which MenuKit allows only before its declarations freeze at client start, so build these panels at init.

**Per-mode placements.** A panel takes one placement per mode scope, and the two modes can differ entirely: above the offhand in survival and beside the chestplate in creative, or a screen region in one mode and a slot in the other. Each frame the panel takes the current mode's placement, else its `BOTH` one, and with neither it does not show in that mode ([recipe](recipes.md#put-a-slot-in-another-place-in-creative)).

`SlotGroupPanelAdapter` reads the panel's placements the same way and also warns when a panel of slots on a group leaves its gap unset. Its `on(...)` and `onGroup(...)` are deprecated since 2.1.0. They put the panel's screen placements around each named group, in both modes.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `SlotState.register(scope, id, codec, streamCodec, defaultValue, visibility, canWrite)` creates one at common init, in effect in its [mode scope](#mode-scope). Read and write it by address (`get(address)`, `set(address, value)`), or by the live `Slot` when one is at hand (`get(slot)`, `set(slot, value)`, and the forms that take a player). `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`. A value equal to the default is no entry at all, and reading never creates one.

A client sends a write only when the server registered the channel (`Presence.serverHasChannel`). Otherwise the write is neither sent nor shown on the client.

The server judges every slot-state write a client sends with these checks, in order:

1. The channel exists.
2. The value parses with the channel's `StreamCodec`. What is stored is the channel's own re-encoding of it.
3. The write names the player's open menu and an active slot on it.
4. The channel's `canWrite(player, slot)` allows it.
5. The player is within a write rate.

A `SHARED` channel's rule refuses every client by default, so a shared channel names who may write it. A refused write changes nothing, and the writer's client gets the server's value back. Values travel as the channel's `StreamCodec` bytes, and a client that cannot decode one skips it. The server sends SlotKit payloads only to players whose handshake recorded SlotKit, and drops SlotKit payloads from any other player.

## Slot group element

A `SlotGroupElement` is one slot group as one MenuKit element (client, since 2.1.0). It finds the group by its `SlotGroupId` on whatever menu is open and shows its slots in rows, nine to a row by default (`SlotGroupElement.builder(id).columns(n)`). `SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE))` shows a single chest's 27 slots, a double chest's 54, or a barrel's, with no menu-type indices. A created group's id shows that group: a mod's own (`group.id()`), or another mod's by its saved form (`SlotGroupId.parse("created|othermod:panel|group")`), with no compile dependency on that mod.

It resolves the group through `SlotGroupCategories.groups(menu)`, so it sees what any mod sees: vanilla's groups, groups another mod's resolver adds, and every created group. It is shown only while at least one of the group's slots is, so it is hidden on a menu without the group, while a created group is not revealed or its custom menu panel is hidden, and when every slot is past the group's active count. It packs the shown slots, so a group with an active count draws only its open ones. A MenuKit container region section whose content is a `SlotGroupElement` therefore shows and hides with the group ([MenuKit's container region](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region)).

`SlotElement.builder().slot(SlotGroupId, index)` (since 2.1.0) names one slot of any group the same way. Slot 0 of `CHEST_STORAGE` is the same element on every chest. It is not shown on a menu without the group, or where the group is shorter, and the slot keeps everything vanilla gives it.

## Separate containers

Every slot group on a menu is its own container, to vanilla and to every other mod. SlotKit guarantees this since 2.1.0. Each group has its own `Container`, stable for the menu's life. All of a group's slots share it, no two groups share one (even over the same storage), and none is shared with the menu's own slots. Vanilla and other mods that tell containers apart by `slot.container`, for sorting, shift-click or move-all, therefore treat each group as a container of its own. `SlotGroupCategories.groups(menu)` is the public way to ask which group a slot is in.

A menu's own shift-click routing stops before the first slot SlotKit appended to it, whether parity, projected or added with `CreatedSlots.onto`. A chest's item shift-clicked out goes to the player's inventory, as it does without SlotKit, and an appended group takes a shift-click only through its own route or `shiftClickIn(true)` ([shift-click routes](#parity-group)). A custom menu's own groups are its own slots, so on a custom menu only parity slots count as appended.

Showing a group somewhere else does not change any of this. `SlotElement`, `SlotFlowElement` and `SlotGroupElement` move slots on the screen only, and the menu keeps the same slots. A panel may hold several slot groups as several `SlotGroupElement`s, from one mod or from several through a MenuKit [shared panel](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#shared-panels), and each group stays its own container. One group is shown by one panel per screen. Two shown panels holding the same group both place its slots, and SlotKit logs a warning, so gate them with `visibleWhen` to show one at a time.

Inventory Profiles Next treats every slot that is not the player's as one container. SlotKit carries a compatibility fix for it ([limits](limits.md#inventory-profiles-next)).

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. Only that player's client sees its contents, through the menu. `StorageAttachment.playerAttachedVisible(...)` is the same, and also sends the contents to every client tracking the player, for gear drawn on the player model. Another client reads them with `bind(thatPlayer)`. `EphemeralStorage.of(size)` builds one that lasts for the menu session. `blockScoped`, `entityAttached` and `itemContainer` keep contents on a block entity, an entity, or an item stack's container component.

## Item sources

`ItemSources` reaches the items a player carries, including those inside shulker boxes and bundles, to any depth. An `ItemScope` says where to look: the inventory's 36 main slots always, and the ender chest, shulker boxes and bundles when switched on. Armor, the offhand and the cursor are not part of it. Items are visited nearest first. Every top-level slot comes first, then everything one container deep, then two deep, and the ender chest follows in the same order.

On the server, `walk` and `take` hand out an `ItemRef` for each item. A take, a put or a committed change writes back up through every container to the top-level slot. A ref whose slot or containers changed since it was read writes nothing.

An `ItemPath` names one item by its top-level slot and its index inside each container on the way down. `find` lists matches with their paths on either side, read-only. A client sends a path in its own packet, and the server turns it back into a ref with `at`, which returns `null` when the item is no longer there. Check the item again on the server before acting on it. The [recipe](recipes.md#find-and-use-items-inside-shulker-boxes-and-bundles) shows both.

The same `walk`, `find` and `at` also take a `Container` the caller supplies, such as the chest a player has open. Its paths have source `CONTAINER` and name the container's own slots. The server resolves one only against a container it supplies, usually the one behind the open menu, so a client can't point it at another.

A menu that stays open on a nested item, such as a bundle inside a shulker box in a chest, binds a `LiveItem` with `ItemSources.live`. Its `stack()` is one object for as long as it's bound, so the mod's own storage over an item stack can sit on it; `live.storage(...)` writes every change back up the chain. It goes stale when the top-level stack moves or the item itself is changed by anything else, and the menu's `stillValid` returns `!isStale()` so vanilla closes it.

A player attachment declared `playerAttached(...).carried()` holds the player's own things, such as a pocket or a carried ender chest. A scope with `carried(true)` walks it after the inventory and before the ender chest. Its paths have source `CARRIED` and name the attachment by its id, and `containerOf(player, path)` returns its contents. Every binding of the attachment (the menu's slots, a shift-click, `ItemSources`) shares one list of stacks, so a stack is the same object wherever it's read until something replaces it.

`pathOf(player, slot)` names the item in a menu slot: an inventory main slot, an ender chest slot or a carried attachment's slot. The client and the server give the same answer for the same slot, so a client drawing a slot can name its item as the server will. Creative's tabs name the slot they show. On the client an ender chest's own menu shows a stand-in container, so its slots there are `null`.

## Item state and slot looks

An `ItemState` is a value that rides on an item. It is a data component SlotKit registers for the mod, so it follows the item into a shulker box and back and vanilla syncs it to every client with the stack. `ItemState.Saved.YES` saves it with the item; `Saved.NO` drops it at a relog or a restart. The server sets it with `set` and `clear`, on a stack or through an `ItemRef` so the change writes back up the chain. Only a mod that declares `bothSides: required` declares a state, because a client without the mod can't read the stack.

`SlotLooks` changes how a slot draws an item while a condition holds. The condition is an `ItemState` the item carries, or a `SlotLooks.When` the mod answers on the client from what it already knows. Use `When` when the state belongs to the session rather than the item, such as which box a player has open, so copies of the item never carry it. A look is one of:

- `animate`: an `ItemAnimation` plays toward its end while the condition holds and back when it clears. The timing follows the item, so a screen opened in place of another picks up a running animation where it stood.
- `swapWhen`: the item draws as another stack, keeping its own count.
- `under` and `over`: a MenuKit slot decoration drawn under or over the item.

SlotLooks declares the look and MenuKit draws it, through `SlotItemLooks` and `SlotDecorations`. Declare looks from the client initializer, with ids unique per mod.

## Double-click

`SlotDoubleClick` takes a plain left double-click on a slot for a mod's action. The gesture is a left click with no shift, then a second within one second with the cursor on the same pixel. A third click starts over, and moving the cursor ends the window. Only the real mouse counts, so a sort mod's simulated clicks never pair.

The first click stays vanilla's and lifts the stack onto the cursor. It starts a pair when the cursor was empty and a registration's `Starts` accepts the slot and stack; the first registration that accepts owns the pair. While the pair is pending, the lifted stack is drawn back in its slot. The second click, which vanilla would take as "gather all", goes to the owner instead:

- `Kind.PUT_BACK`: the same stack is on the cursor and its slot is empty. SlotKit puts it back on the client first, so it never seems to move.
- `Kind.IN_PLACE`: the stack never left its slot, because the slot is locked, and the cursor is empty.

Anything else stays vanilla's. On creative's inventory tab, `Starts` and `Pair.slot` see the real slot on `player.inventoryMenu`, and `Pair.menuIndex` is its index there, which is the menu the server has open. Creative's item tabs never pair. A player inventory slot still pairs when a screen opens in place of the first one between the clicks; any other slot pairs only on the same screen.

The server side is the mod's. Its handler sends its own packet, and its server puts the stack back too, since the server saw the first click lift it. If the server refuses, its sync corrects the client.

## Saved ids

SlotKit reads and writes the ids MenuKit and Containers 6.x saved. They keep the `menukit:` namespace: slot group categories (`menukit:player_inventory`), behavior keys and reaction causes (`menukit:mending`), the slot-state attachments (`menukit:slot_state_player_inventory` and its siblings), the keyed storage NBT keys (`menukit:scope`, `menukit:owner`), and a shulker box's carried state (`menukit:portable_slot_state`). Do not rename any of them to `slotkit:`. A renamed id reads as a new, empty one, and what players saved under the old id is lost.

Your own storage and channel ids are used as you wrote them, never prefixed or rewritten.
