# Concepts

Definitions of the terms SlotKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term. Panels, elements, regions, anchors and claims are MenuKit's, defined on [MenuKit's concepts page](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md).

## SlotKit and MenuKit

SlotKit is one mod, `slotkit`, built on MenuKit. It holds everything about slots: slot groups and categories, slot operations and vetoes, addresses and the window, created slots, custom menus, storage, and per-slot state. SlotKit depends on MenuKit 7 and uses only MenuKit's public API. MenuKit does not depend on SlotKit.

SlotKit was MenuKit: Containers, plus the slot code MenuKit carried before 7.0.0. [upgrading.md](upgrading.md) moves a mod from Containers or from MenuKit 6's slot code.

## Public API

The API is the `api` packages: `com.trevlar.slotkit.api.*`. [Versioning](versioning.md) covers them. Every other package carries `@ApiStatus.Internal` and is outside the contract, so a mod that calls into one can break in any release. A class that names a screen type is client-only, and its javadoc says so.

| Package | Holds | Tier |
|---|---|---|
| `api` | `Presence`, what the other side runs | Both |
| `api.group` | Slot groups, categories, sets, resolvers, `SlotGroupPanelAdapter` | Client |
| `api.window` | Addresses, the window, behaviour keys, slot operations, gates and vetoes | Client |
| `api.slot` | Created slots, container panels, `SlotSpec` | Client and server |
| `api.menu` | Custom container menus and their screens | Client and server |
| `api.storage` | `Storage` and storage attachments | Client and server |
| `api.state` | Slot-state channels | Client and server |

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

A client's answer about itself is its claim. It decides what the server sends that client and whether the client is admitted. It never authorises a write.

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

A container-panel group registers at init and is listed from the title screen. A group built with a menu (`CreatedSlots.onto`) is listed after the first menu that carries it. To list it from the title screen, its mod declares it at init with `SlotGroups.declare`.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search, including searches run by mods with no knowledge of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Operation

An operation is something done to a slot. Vanilla ships eleven, one key per thing a player can do: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT` (double-click), `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `INVENTORY_INSERT`. The last covers every item given to the inventory, not only a pickup from the ground. `/give`, pick block and a crafting grid's returns go through the same two vanilla methods. The eleven are `BehaviorKeys.VANILLA_OPERATIONS`, every one on by default. SlotKit enforces them at vanilla's own seams for every slot kind. A plain click takes, puts, or both when it swaps different items. A swap key has two slots, and both must allow it. The client does not send a click its own slot does not allow, so that part holds on any server. Q, Ctrl-Q and F while playing, with no screen open, are the same `DROP`, `DROP_STACK` and `OFFHAND_SWAP` on the selected hotbar slot. The client refuses them before it drops or sends, and the server refuses them when the action arrives. An offhand swap also needs the offhand slot to allow it.

An operation has a role: it takes items out of a slot, puts items in, or both. `SlotOperations.define(op, Role.TAKE)` records it and `SlotOperations.role(op)` reads it. A settings screen uses the role to tell which operations matter for a lock on an item already in a slot. `define(op)` without a role means both.

An operation also declares which slot groups it can act on at all, as in `SlotOperations.define(op, role, AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_INVENTORY))`. It names vanilla's groups. Every group a mod adds applies automatically, unless the operation opts out with `.except(groupId)` or `.except(slotGroupSet)`. `AppliesTo.vanillaExcept(...)` names the vanilla groups it skips instead. `AppliesTo.only(PLAYER_HOTBAR, PLAYER_INVENTORY)` (since 1.1.0) takes a mod's group only when the group declared one of the named categories, so pockets declared `PLAYER_INVENTORY` apply and an elytra slot in its mod's own category does not, with neither mod naming the other. An operation defined without an `AppliesTo` gets `AppliesTo.EVERY_GROUP`. `SlotOperations.appliesTo(op, groupId)` answers for one group. `SlotOperations.groups(op)` lists every declared group the operation applies to, which is what a reach setting offers. SlotKit declares vanilla's eleven from what vanilla does. Nothing is placed, dragged or shift-clicked into an output slot. The crafter's result slot cannot be touched by anything. Double-click collect skips the crafting, smithing, stonecutter, cartography and trade results, whose menus refuse it. An inventory insert lands only in the hotbar, the main inventory and the offhand. This is a declaration, not a rule SlotKit enforces. It tells a settings screen where an operation can reach.

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, ships a name and a description in its lang file, and asks `SlotOperations.allows` in its own code before acting. `SlotOperations.all()` lists every published operation. `SlotOperations.name(op)` and `description(op)` are translatable components on `slot_operation.<namespace>.<path>` and `.description`, for a settings screen to list. SlotKit needs no change for a new operation to exist.

Two things decide what a slot allows. The first is the cascade, which holds what the slot's author declared:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. `SlotOperations.inherent(category, operation, value)` sets the category rung once. A group's own declaration outranks it. A per-slot declaration outranks the group. A vanilla slot has one group per category on a menu, so for vanilla slots the group and the category are one rung. "Shift-click may land in the 9 by 3 but not the hotbar" is `inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)`.

The second is a veto. `SlotOperations.veto(rule)` registers a rule that can only say no, for a mod that is not the slot's author. A player's lock is the example it exists for. A veto sits beside the cascade and subtracts. It never overwrites what the slot's author declared, and it has nothing to restore when the lock lifts. A veto that throws is logged once and skipped.

`SlotOperations.allows(menu, slot, player, operation)` is the one check. It passes when the cascade says yes and no veto says no. Every seam calls it, and every operation a mod adds calls it too. The `SlotRef` a veto sees carries the container and index, the live slot and menu when there is one, the acting player when there is one, and the slot's category. A move a mod makes on the server with no click behind it, such as a direct `quickMoveStack` call, still names a player. That player is the one whose inventory the menu shows.

A mod that performs its operation by sending clicks wraps them in `SlotOperations.as(take, put, () -> sendClicks())`, or in `as(op, ...)` when both sides are one operation. Each click sent inside carries the operation it serves. The first operation applies to the slot it takes from and the second to the slot it puts into. The vetoes judge that operation instead of the gesture. A lock that refuses shift-click but allows restock therefore lets the restock's shift-click through. The slot's author still judges the gesture. The tag reaches the integrated server. Send clicks inside the block, on the calling thread. A simulated click that is not wrapped counts as the gesture it looks like.

Registration order does not matter. Declarations freeze once every mod has initialised, through MenuKit's `Declarations`. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)` and `dragFill(false)`.

## Gate

A gate is the slot author's other rule. It sets what the slot accepts and releases, and how many of an item it holds. It is a `SlotGate` on `BehaviorKeys.GATING`, set with `Window.slot(address).gate(...)` or `SlotSpec.gate(...)`. `BehaviorKeys.BINDING` adds Curse of Binding to any slot. `BehaviorKeys.MENDING` lets the items in a slot on the player's inventory menu take part in XP-orb Mending; it needs SlotKit on the server, which runs the orb seam. Declared on a category, it reaches vanilla's slots and created ones alike (since 1.1.0), so Mending across the whole inventory is two lines, and a group that says `mending(false)` still wins:

```java
SlotOperations.inherent(SlotGroupCategory.PLAYER_INVENTORY, BehaviorKeys.MENDING, TriBool.TRUE);
SlotOperations.inherent(SlotGroupCategory.PLAYER_HOTBAR, BehaviorKeys.MENDING, TriBool.TRUE);
```
 SlotKit enforces gates and binding at the slot's own `mayPlace`, `mayPickup` and `getMaxStackSize`, which every click, shift-click and automation path consults. It also enforces them in the merge pass of a shift-click and at the hopper and dispenser seams, once per vanilla method.

A gate is not a lock. A lock is another mod's policy laid over a slot, and that is a veto. It only says no and leaves the author's gate alone.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner, which is a player, block entity, entity, or item stack.

`ParityGroup` creates slots on the player's inventory menu and projects them onto every container menu. `CustomContainerMenu` creates slots on a custom menu. `CreatedSlot.of(slot)` tells a created slot from any other, on any screen, creative's included.

The code that builds slots, stores state and judges writes is common, and runs on the server and the client alike. The code that draws is client-only: `SlotElement`, `SlotFlowElement`, `CustomContainerScreen`, `ClientMenu` and `ClientSlots`. SlotKit builds no panel. A slot is an element the mod puts in its own MenuKit panel, next to buttons and labels, and the mod's panel decides where it sits, how it looks and which screens show it. A custom menu's handler holds each panel's id, its slot groups and whether it is shown. The panel itself is a MenuKit `Panel` the mod declares with `ClientMenu.of(menu).panel(id, ...)`, slots included. The server decides whether a panel is shown and syncs that to the client. A player may toggle only a panel declared `toggleable()`, and a client may open a menu only when its `validWhen` holds, which is also how long it stays open.

Only a `required` mod builds created slots ([Declaring sides](#declaring-sides)). A client on a server without SlotKit, or without the mod, builds none of that mod's slots and sends nothing SlotKit-only there, instead of desyncing.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `Address.createdSlot(group, index)` names a created slot, with the group from `CreatedSlots.groupId(panelId, groupId)` or `ParityGroup.idOf(owner, groupId)` (a registered group's `id()`). `Window.slot(address).set(key, value)` attaches behaviour by address. A veto reads a slot's address from its `SlotRef` with `address()`. `asString()`, `Address.parse` and `Address.CODEC` save one as text. Never save its parts.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `SlotState.register(id, codec, streamCodec, defaultValue, visibility, canWrite)` creates one at common init. Read and write it by address (`get(address)`, `set(address, value)`), or by the live `Slot` when one is at hand (`get(slot)`, `set(slot, value)`, and the forms that take a player). `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`. A value equal to the default is no entry at all, and reading never creates one.

A client sends a write only when the server registered the channel (`Presence.serverHasChannel`). Otherwise the write is neither sent nor shown on the client.

The server judges every slot-state write a client sends with these checks, in order:

1. The channel exists.
2. The value parses with the channel's `StreamCodec`. What is stored is the channel's own re-encoding of it.
3. The write names the player's open menu and an active slot on it.
4. The channel's `canWrite(player, slot)` allows it.
5. The player is within a write rate.

A `SHARED` channel's rule refuses every client by default, so a shared channel names who may write it. A refused write changes nothing, and the writer's client gets the server's value back. Values travel as the channel's `StreamCodec` bytes, and a client that cannot decode one skips it. The server sends SlotKit payloads only to players whose handshake recorded SlotKit, and drops SlotKit payloads from any other player.

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. `EphemeralStorage.of(size)` builds one that lasts for the menu session. SlotKit also provides block-scoped and item-scoped attachments.

## Saved ids

SlotKit reads and writes the ids MenuKit and Containers 6.x saved. They keep the `menukit:` namespace: slot group categories (`menukit:player_inventory`), behaviour keys and reaction causes (`menukit:mending`), the slot-state attachments (`menukit:slot_state_player_inventory` and its siblings), the keyed storage NBT keys (`menukit:scope`, `menukit:owner`), and a shulker box's carried state (`menukit:portable_slot_state`). Do not rename any of them to `slotkit:`. A renamed id reads as a new, empty one, and what players saved under the old id is lost.

Your own storage and channel ids are used as you wrote them, never prefixed or rewritten.
