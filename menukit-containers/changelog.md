## 6.0.0

One of everything. Requires MenuKit 6.0.0. Breaking: every mod that uses Containers migrates. The README's "To 6.0.0" section lists the changes most mods meet.

### Breaking: the public API is declared, and renamed

The public API is `com.trevlar.menukit.containers.api.slot`, `.storage`, `.state` and `.menu`. The rest of Containers is internal. Containers has its own root package, `com.trevlar.menukit.containers`, so every import changes. The `MKC` prefix is gone, and custom menus take Minecraft's own names:

| 5.x | 6.0.0 |
|---|---|
| `MKCContainerPanel` | `ContainerPanel` |
| `MKCSlot`, `MKCSlots` | `CreatedSlot`, `CreatedSlots` |
| `MKCBehaviorKeys` | `ContainerKeys` |
| `MKSlotState` | `SlotState` |
| `MKCMenu` | `CustomMenu` |
| `MKCScreenHandler` | `CustomContainerMenu` |
| `MKCHandledScreen` | `CustomContainerScreen` |

`CreatedSlot.of(slot)` tells a created slot from any other on any screen, and slot state reads and writes by `Slot` as well as by `Address`.

### Breaking: a custom menu splits by side

Containers has a client source set, and the compiler keeps screens out of common code. A custom menu's handler is its slots and its panels as the server holds them (`MenuPanel`: an id and whether it is shown). `CustomContainerMenu.builder(type)` returns a `SlotLayout`. How each panel looks is declared on the client with MenuKit's `Panel.Builder`: `ClientMenu.of(menu).panel(id, p -> ...)`, with `.screen(MyScreen::new)` and `.requestOpen()`. A container panel's chrome and screen scope are declared with `ClientContainerPanel.of(id)`, and `renderGroup` is on `ClientSlots`. `SlotElement` and `SlotFlowElement` are built by builders.

### Breaking: the server judges every client write

The server judges every slot-state write a client sends, in order: the channel exists; the value parses with its `StreamCodec`, and the canonical re-encoding is stored; the write names the player's open menu and an active slot on it; the channel's writer rule allows it; the player is within a write rate. A `SHARED` channel refuses every client write until it declares who may write, the sixth argument of `SlotState.register`. A refused write changes nothing and the writer gets the server's value back. Writing the default removes the entry, and reading never creates one.

A custom menu's panel visibility is the server's, and a player may toggle only a panel declared `toggleable()`. `CustomMenu.Builder.validWhen(predicate)` says who may open a menu and for how long. Every packet has a new id, `menukit-containers:v1/...`.

### Breaking: declarations freeze

Storage attachments, slot-state channels, container panels and menus are declared at init. A late one throws. Parity slots are added to a menu sorted by panel id, the same on both sides.

### Breaking: removed

- The gate, Curse of Binding and shift-click routing are MenuKit's (`SlotGate`, `GATING`, `BINDING`). `ContainerKeys` keeps only `MENDING`.
- `QUICK_MOVE`, `SlotSpec.quickMove` and `QuickMoveParticipation`. `SlotSpec.shiftClickOut(false)` and `shiftClickIn(false)` declare the two shift-click operations.
- `MKCSlotQuickMove.route`, `CreatedSlotAdapter.addressOf(panelId, groupId, index)`, `ContainerPanel.address` and `CustomContainerMenu.address`. A created slot's address is `Address.createdSlot(CreatedSlots.groupId(panelId, groupId), index)`.
- The positional element adders on a menu's panel builder. A panel's elements are added on the client, on `ClientMenu.of(menu).panel(id, p -> ...)`, which is MenuKit's `Panel.Builder`.
- `SlotState.isSlotStateCapable`. Every joined player has Containers, so it was always true.

### Containers runs on a dedicated server

It starts on a server, and a player can join. A server with Containers checks, before the world loads, that a joining client has it, and disconnects one that does not with a message naming MenuKit and MenuKit: Containers and linking their pages. A client with Containers on a server without it builds no created slots and sends nothing Containers-only.

### Fixed

- A custom menu's `rightClick` handler ran only on the client, so what it did never reached the server. It runs as a click now, on both sides, and the server's run is the one that counts.
- A created slot could have one index on the client and another on the server.
- A mod that registered a slot-state channel crashed a dedicated server, and so did a slider in a custom menu.
- An update to one container's slot state could land on another container's slot with the same index.
- Opening a container grew its saved state.
- A lectern disconnected a player when parity slots were registered.
- A player's ender chest slots had no address on the server.
- The slot state a shulker box carries as an item was sent to every client.
