# Upgrading

## From SlotKit 2.1 to 2.2

2.2 needs MenuKit 7.5.0. Change the dependencies to `menukit` `>=7.5.0 <8.0.0` and `slotkit` `>=2.2.0 <3.0.0` ([versioning](versioning.md#what-to-declare)).

A Fabric-backed `StorageAttachment` (`playerAttached`, `playerAttachedVisible`, `blockScoped`, `entityAttached`) now shares one live list per owner and attachment, the same list for every `bind` call on that owner, instead of handing each call its own copy. `itemContainer` is data-component backed, not Fabric-backed, and already shared one list per stack in 2.1.0; it changes nothing here. A player attachment bound twice in the same tick, from two menus or two mods, now sees one list and one set of edits; previously each bind read its own copy, so a stack tracked by identity across binds looked like it moved every tick even when nothing changed. A mod that only reads and writes through `bind(...)` sees no difference. A mod that kept its own separate list instead of calling `bind` again, or that relied on two binds being independent copies (editing one without the other seeing it), now sees both binds reflect the same edits. `custom(...)`, `customPlayerAttached(...)` and `ephemeral(...)` are unaffected: each `bind` on them still gets its own list. Nothing else changes; no saved data needs migration.

## From SlotKit 2.0 to 2.1

SlotKit 2.1 adds a mode scope to every declaration about a slot: `SURVIVAL`, `CREATIVE` or `BOTH`, MenuKit's `ModeScope`, read from the acting player on each check ([mode scope](concepts.md#mode-scope)). It also adds slot anchors, per-mode placements, `Address.playerInventory(n)` and `SlotGroupElement`. 2.1 needs MenuKit 7.4.0. Change the dependencies to `menukit` `>=7.4.0 <8.0.0` and `slotkit` `>=2.1.0 <3.0.0` ([versioning](versioning.md#what-to-declare)).

Nothing breaks. Code built against 2.0 compiles and runs on 2.1. Each form below still works, means `BOTH`, and is deprecated. Saved data needs no migration, and a player inventory address 2.0 saved, which named the player by UUID, parses as `Address.playerInventory(n)`.

| 2.0 | 2.1 |
|---|---|
| `SlotOperations.veto(veto)` | `SlotOperations.veto(ModeScope.BOTH, veto)` |
| `SlotOperations.inherent(category, operation, value)` | `SlotOperations.inherent(ModeScope.BOTH, category, operation, value)` |
| `Window.slot(address)` | `Window.slot(ModeScope.BOTH, address)` |
| `Window.view(menu).slot(address)` | `Window.view(menu).slot(ModeScope.BOTH, address)` |
| `SlotState.register(id, codec, streamCodec, defaultValue, ...)` | `SlotState.register(ModeScope.BOTH, id, codec, streamCodec, defaultValue, ...)` |
| `WindowEngine.set(address, key, decl)` and `setGroup(group, key, decl)` | `WindowEngine.set(ModeScope.BOTH, address, key, decl)` and `setGroup(ModeScope.BOTH, group, key, decl)` |
| `.position(position)` on the panel and `new SlotGroupPanelAdapter(panel).on(category)` | `.in(ModeScope.BOTH, SlotPlacement.group(category, position))` on the panel and `new AnchorPanelAdapter(panel)` |
| `new SlotGroupPanelAdapter(panel).onGroup(groupId)` | `.in(ModeScope.BOTH, SlotPlacement.group(groupId, position))` and `new AnchorPanelAdapter(panel)` |
| `new SlotGroupPanelAdapter(panel).on(a, b, c)`, one panel on several groups | One panel per group, each with its own `SlotPlacement.group` ([recipe](recipes.md#put-a-panel-next-to-the-player-inventory-on-every-screen)) |
| MenuKit's `.position(position)` on any other panel, a custom menu's included | `.in(ModeScope.BOTH, Placement.screen(position))` |

A `SlotGroupPanelAdapter` built on a panel with `SlotPlacement` placements needs no `on(...)`, so `new SlotGroupPanelAdapter(panel)` keeps working in place of `new AnchorPanelAdapter(panel)`. MenuKit's own deprecated forms, `position(...)` and `AnchorPanelAdapter.on(...)` among them, are on [MenuKit's upgrading page](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#to-740).

Where a mod used two panels and its own creative check to put one slot in two places, one panel with a placement per mode replaces them ([recipe](recipes.md#put-a-slot-in-another-place-in-creative)). Where a mod took a player inventory slot's address from a live slot, `Address.playerInventory(n)` names it at init.

One behavior changes. A player inventory slot's address no longer names the player, so `SlotRef.address()` for slot 4 is the same for every player. A rule declared at runtime with `Window.slot(scope, ref.address())` now applies to that slot for every player, where in 2.0 it applied to one. A rule for one player is a veto that reads `ref.player()`, or a `PRIVATE` slot-state channel, which stores one value per player.

Behavior that changes in 2.1, with no change to the code:

- Vetoes and the operation cascade hold in creative too. SlotKit judges creative's clicks on the client, on both tabs.
- Creative's item tabs carry no SlotKit slots. A parity group is not on them, as vanilla shows only the hotbar there, and `ClientSlots.isShown` is false.
- A menu's own shift-click routing stops before every slot SlotKit appended, groups added with `CreatedSlots.onto` included, so a chest's item goes to the player's inventory ([separate containers](concepts.md#separate-containers)).
- With Inventory Profiles Next installed, its sort and move-all act on one container at a time ([limits](limits.md#inventory-profiles-next)).

## From SlotKit 1.x to 2.0

SlotKit 2.0 builds no panels. A mod registers its slot groups as data and shows them in MenuKit panels of its own, next to anything else it puts there. 2.0 needs MenuKit 7.3. Saved data needs no migration: a group's id is derived exactly as before, so its contents, slot state and every setting another mod keyed to it carry over.

| 1.x | 2.0 |
|---|---|
| `ContainerPanel.define(id).addSlot(spec).register()` | `ParityGroup.register(id, spec)`, once per group, in common code |
| `.at(...)`, `.on(...)`, `.onGroup(...)`, `.style(...)`, `.opaque(...)`, `.visibleWhen(...)`, `.pinnedWidth/Height(...)`, `.size(...)` on the definition | The same on your own `Panel` (its `position`, `style`, `opaque`, `visibleWhen`, pinned size), built in the client initializer, holding `SlotFlowElement.of(group)` |
| A panel `on(...)` a slot group | `new SlotGroupPanelAdapter(panel, 0).on(...)`, with `.gap(0)` on the panel's position |
| Any other placement | `new ScreenPanelAdapter(panel, padding).onMatching(ScreenMatcher.all())` |
| `ContainerPanel.groupId(id, group)` | `ParityGroup.idOf(id, group)`, or the registered group's `id()` |
| `ClientContainerPanel.of(id).chrome(...)` | More elements in your panel |
| `ClientContainerPanel.of(id).parity(matcher)` | The adapter's `onMatching(matcher)` |
| `ClientSlots.renderGroup(panel, group, count, ...)` | `SlotFlowElement.of(CreatedSlots.groupId(panel, group), count)` in your panel |
| `CreatedSlots.Builder.layout(x, y, columns)` | Removed; the panel lays slots out |
| `p.group(id, category, storage, priority, columns[, rowGapAfter, rowGapSize])` on a custom menu | `p.group(id, category, storage, priority)`; place the slots in the panel |
| A custom menu panel with no `ClientMenu.panel(...)` | Declare one, with its slots: `.add(SlotFlowElement.of(SlotGroupId.created(panelId, groupId), count))` |

The table gives the 2.0 forms. 2.1 deprecates some of them, and [2.0 to 2.1](#from-slotkit-20-to-21) has their replacements.

Pass the 1.x panel id as the group's owner id, unchanged, so its id stays the same. To keep a styled panel hidden where its slots aren't (a server without SlotKit), add `visibleWhen(() -> ClientSlots.isShown(group.id()))`. New in 2.0: a `SlotElement` can show a vanilla slot ([recipe](recipes.md#show-a-vanilla-slot-in-your-own-panel)).

## From MenuKit: Containers or MenuKit 6

This section moves a mod to SlotKit 1.0.0 from MenuKit: Containers 6.x, or from the slot code MenuKit 6 carried (`api.slot`, `api.window`, `SlotGroupPanelAdapter`). SlotKit is Containers renamed, plus that slot code. Then follow [1.x to 2.0](#from-slotkit-1x-to-20), [2.0 to 2.1](#from-slotkit-20-to-21) and [2.1 to 2.2](#from-slotkit-21-to-22).

Do MenuKit's 7.0.0 upgrade first: [MenuKit's upgrading page](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md). It also tells you whether your mod needs SlotKit at all.

Saved data needs no migration. See [What does not change](#what-does-not-change).

## Checklist

Do these in order.

1. [Change the dependency lines.](#1-change-the-dependency-lines)
2. [Change the `depends` block.](#2-change-the-depends-block)
3. [Run the import map.](#3-run-the-import-map)
4. [Run the two renames.](#4-run-the-two-renames)
5. [Fix what does not compile.](#5-fix-what-does-not-compile)
6. [If your mod builds slots or custom menus, declare sides.](#6-declare-sides)
7. Build, then start a client and a dedicated server and join one from the other.

## 1. Change the dependency lines

`build.gradle`:

```diff
 dependencies {
-    implementation 'maven.modrinth:menukit:6.2.0+26.3'
-    implementation 'maven.modrinth:menukit-containers:6.2.0+26.3'
+    implementation 'maven.modrinth:menukit:7.0.0+26.3'
+    implementation 'maven.modrinth:xQUBXV8I:1.0.0+26.3'   // SlotKit
 }
```

`xQUBXV8I` is SlotKit's Modrinth project id, the same project Containers used. It resolves whatever the project's slug is. A mod that used only MenuKit's slot code, never Containers, adds the SlotKit line and has no Containers line to remove.

## 2. Change the `depends` block

`fabric.mod.json`:

```diff
-"depends": { "menukit": ">=6.2.0 <7.0.0", "menukit-containers": ">=6.2.0 <7.0.0" }
+"depends": { "menukit": ">=7.0.0 <8.0.0", "slotkit": ">=1.0.0 <2.0.0" }
```

The mod id `menukit-containers` is `slotkit`. MenuKit and SlotKit each have their own version, so pin each against its own number. Containers released in step with MenuKit; SlotKit does not. Keep both bounds, separated by a space.

## 3. Run the import map

The import map rewrites every fully qualified name SlotKit moved: import lines, static imports, and names written out in code. SlotKit generates it from the actual moves. It also moves `Declarations` to `com.trevlar.menukit.api.Declarations`, the one class that stays in MenuKit.

From your mod's root:

```sh
curl -fLO https://raw.githubusercontent.com/trevorschoeny/slotkit/main/tools/amethyst-imports.sed
curl -fLO https://raw.githubusercontent.com/trevorschoeny/slotkit/main/tools/amethyst-renames.sed
find src -name '*.java' -exec sed -E -i '' -f amethyst-imports.sed {} +
```

On Linux, write `-i` with no `''` after it, here and in step 4.

The map, by package. A row with one package moves every class in it.

| From | To |
|---|---|
| `com.trevlar.menukit.api.slot` (every class but `Storage` and `VirtualStorage`) | `com.trevlar.slotkit.api.group` |
| `com.trevlar.menukit.api.slot.Storage`, `VirtualStorage` | `com.trevlar.slotkit.api.storage` |
| `com.trevlar.menukit.api.panel.SlotGroupPanelAdapter` | `com.trevlar.slotkit.api.group.SlotGroupPanelAdapter` |
| `com.trevlar.menukit.api.window` (every class but `Declarations`) | `com.trevlar.slotkit.api.window` |
| `com.trevlar.menukit.api.window.Declarations` | `com.trevlar.menukit.api.Declarations` (stays in MenuKit) |
| `com.trevlar.menukit.containers.api.slot` | `com.trevlar.slotkit.api.slot` |
| `com.trevlar.menukit.containers.api.slot.SlotGroup` | `com.trevlar.slotkit.api.slot.CreatedSlotGroup` |
| `com.trevlar.menukit.containers.api.slot.ContainerKeys` | `com.trevlar.slotkit.api.window.BehaviorKeys` |
| `com.trevlar.menukit.containers.api.menu` | `com.trevlar.slotkit.api.menu` |
| `com.trevlar.menukit.containers.api.storage` | `com.trevlar.slotkit.api.storage` |
| `com.trevlar.menukit.containers.api.state` | `com.trevlar.slotkit.api.state` |
| (new) | `com.trevlar.slotkit.api.Presence` |

Slot groups that exist in any menu are in `api.group`, the client tier. The created slots own `api.slot`. A wildcard import of one of the four Containers packages maps to its new package.

Imports, before and after:

```diff
-import com.trevlar.menukit.api.panel.SlotGroupPanelAdapter;
-import com.trevlar.menukit.api.slot.SlotGroupCategory;
-import com.trevlar.menukit.api.window.SlotOperations;
-import com.trevlar.menukit.containers.api.slot.ContainerPanel;
-import com.trevlar.menukit.containers.api.slot.SlotSpec;
-import com.trevlar.menukit.containers.api.storage.StorageAttachment;
+import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;
+import com.trevlar.slotkit.api.group.SlotGroupCategory;
+import com.trevlar.slotkit.api.window.SlotOperations;
+import com.trevlar.slotkit.api.slot.ContainerPanel;
+import com.trevlar.slotkit.api.slot.SlotSpec;
+import com.trevlar.slotkit.api.storage.StorageAttachment;
```

The code under these imports does not change.

## 4. Run the two renames

The import map changes names in imports and qualified names. Two renames are in code, where an import alone does not reach them.

```sh
grep -rlE 'import com\.trevlar\.slotkit\.api\.slot\.(CreatedSlotGroup|\*);' src \
    | while read -r f; do sed -E -i '' -f amethyst-renames.sed "$f"; done
find src -name '*.java' -exec sed -E -i '' 's/(^|[^A-Za-z0-9_])ContainerKeys\.MENDING([^A-Za-z0-9_]|$)/\1BehaviorKeys.MENDING\2/g' {} +
```

The first command renames `SlotGroup` to `CreatedSlotGroup`, only in files that import SlotKit's created slots. Those are the files that imported Containers' `SlotGroup`. A class of your own named `SlotGroup` keeps its name: Inventory Plus has one, and the command leaves it alone. The second command renames `ContainerKeys.MENDING` to `BehaviorKeys.MENDING` in every file.

```diff
-import com.trevlar.menukit.containers.api.slot.SlotGroup;
+import com.trevlar.slotkit.api.slot.CreatedSlotGroup;

-List<SlotGroup> mainGroups = menu.getGroupsFor("mymod:main");
-SlotGroup ownerOfSlot0 = menu.getGroupContaining(0);
+List<CreatedSlotGroup> mainGroups = menu.getGroupsFor("mymod:main");
+CreatedSlotGroup ownerOfSlot0 = menu.getGroupContaining(0);
```

```diff
-import com.trevlar.menukit.containers.api.slot.ContainerKeys;
+import com.trevlar.slotkit.api.window.BehaviorKeys;

-Window.slot(address).set(ContainerKeys.MENDING, TriBool.TRUE);
+Window.slot(address).set(BehaviorKeys.MENDING, TriBool.TRUE);
```

Steps 3 and 4 together took Inventory Plus 1.10.0 and Inventory Max 1.3.2 from 19 and 20 files naming moved types to none.

## 5. Fix what does not compile

What is left after the maps, one row per break:

| Break | Why | Do this |
|---|---|---|
| `import com.trevlar.menukit.api.window.*;` or `import com.trevlar.menukit.api.slot.*;` | These packages split: `Declarations` stays in MenuKit, `Storage` goes to `api.storage`, the rest to `api.group` or `api.window`. A wildcard has no single target. | Write one import per class, then run the import map again. |
| A file imports `com.trevlar.slotkit.api.slot.*` and also uses a `SlotGroup` class of its own | The rename in step 4 ran on it. | Restore your own class's name in that file. |
| `Window.panel`, `Window.element`, `PanelHandle`, `ElementHandle`, `BehaviorKeys.VISIBILITY`, `BehaviorKeys.OPACITY`, `VisibilityRule`, `SlotHandle.visibility` | Removed. A panel's own settings do the same job. | Use the panel's `visibleWhen` and `opaque`. [MenuKit's upgrading page](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#2-fix-what-does-not-compile) shows the change. |
| A mixin or call into a `com.trevlar.menukit.containers` package that is not `api` | Internal code moved to `com.trevlar.slotkit` with new class names, and its mixins are `SK...` now. | Use the public API. A task that needs an internal type is a gap: open an issue. |

Two changes need no edit in your code. Payload ids are `slotkit:v1/...`; they were never saved, so nothing reads the old ones. And a check for whether a player can hold slot state is `Presence.clientHasSlotKit(player)`. In a gate or veto it is `Presence.clientHasSlotKit(ctx.actingPlayer())`, after checking `actingPlayer()` for null. `GatingContext.actingPlayerCapable()` and `SlotState.isSlotStateCapable` left in 6.0.0 and do not return.

## 6. Declare sides

This step applies to a mod that builds slots or custom menus: `ParityGroup` (1.x: `ContainerPanel`), `CustomMenu`, `CreatedSlots`, or storage behind them. A mod that only finds and acts on slots in existing menus skips it and adds no `slotkit` block.

`fabric.mod.json` (6.x had no declaration):

```diff
 {
   "id": "mymod",
   "depends": { "menukit": ">=7.0.0 <8.0.0", "slotkit": ">=1.0.0 <2.0.0" },
+  "custom": { "slotkit": { "bothSides": "required" } }
 }
```

| Value | Use it when |
|---|---|
| `required` | The mod builds slots or custom menus. |
| `preferred` | The mod works on one side and does more with both, for example shared slot state. |
| No block | The mod only finds and acts on slots. |

[Declaring sides](concepts.md#declaring-sides) has what each value does on a server.

**Namespace your ids with your mod id.** `ParityGroup.register` (in 1.x, `ContainerPanel.define`) and `CustomMenu.define` throw at startup unless the id's namespace is a loaded mod that declares `required`. A mod whose id is `mymod`:

```java
POCKET_SLOTS = ParityGroup.register("mymod:pockets",
        SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)));
```

An id under another namespace, such as `"my-mod:pockets"` or `"pockets"`, throws with a message naming the group and the fix. Containers accepted any id, so check every call.

**Set `contact.homepage`** in your `fabric.mod.json`. A player refused for lacking your mod gets a link to it from there.

### What players see

| Server | Client | Result |
|---|---|---|
| SlotKit and your `required` mod | SlotKit and your mod | Joins. The slots are there on both sides. |
| SlotKit and your `required` mod | Vanilla, or Fabric without SlotKit | Refused before the world loads: "This server needs SlotKit and your mod on your game too", with links. |
| SlotKit and your `required` mod | SlotKit without your mod | Refused, naming your mod. |
| SlotKit and your `required` mod | Your mod, but a version that registers different `ParityGroup` slot groups | Refused: "Your mod doesn't match the server's: its slots differ." |
| SlotKit and only `preferred` mods | Vanilla | Joins as a vanilla player. Server-side enforcement still applies. |
| Without SlotKit | SlotKit and your `required` mod | Joins. Your mod builds no slots on this server. |
| Any | A mod with no `slotkit` block | Joins. The mod works as it did on 6.x. |
| Singleplayer, or the host of a LAN game | n/a | Everything on, both sides. The host is never judged. |
| A Containers 6.2 server | SlotKit | Refused by that server, which asks for MenuKit and Containers 6.2. |

The handshake compares `ParityGroup` slot groups, not version numbers. A custom menu's slots are not compared, so when a release of your mod changes a custom menu's layout, change that menu's id.

### Asking what the other side has

`Presence` is new. It answers what the other side runs, fixed when the player joins.

| Side | Method |
|---|---|
| Client | `Presence.serverHasSlotKit()`, `Presence.serverHas(String modId)`, `Presence.serverHasChannel(Identifier channelId)` |
| Server | `Presence.clientHasSlotKit(ServerPlayer player)`, `Presence.clientHas(ServerPlayer player, String modId)` |

In singleplayer every answer is true. [Presence](concepts.md#presence) has the rules.

## What does not change

SlotKit reads every value MenuKit and Containers 6.x saved. Worlds, player files and your mod's saved settings keep working with no conversion.

| Saved form | Unchanged |
|---|---|
| Address text from `Address.asString()`, read by `Address.parse` and `Address.CODEC` | Byte for byte, all four kinds, every scope and token form. One form reads but is written differently from 2.1: a player inventory address that names the player by UUID reads as `Address.playerInventory(n)` and saves in that form. |
| `SlotGroupId.asString()`, `SlotGroupSet.asString()` | Byte for byte. |
| Slot group category ids | `menukit:player_inventory`, `menukit:chest_storage`, and the rest keep the `menukit:` namespace. |
| Behavior key and reaction cause ids | Same ids. `MENDING` keeps `menukit:mending` under its new owner. |
| Slot-state attachments | `menukit:slot_state_player_inventory` and its four siblings, same layout inside. |
| Keyed storage NBT keys | `menukit:scope`, `menukit:owner`. |
| Your own storage and channel ids | Used as you wrote them, never prefixed or rewritten. |
| Shulker boxes carrying slot state | Load with their state and their contents. 6.x stored that state in a registered component; SlotKit reads it as items load and keeps it in `minecraft:custom_data` under `menukit:portable_slot_state`. |

Do not rename `menukit:` to `slotkit:` in any saved id. The ids keep the old namespace on purpose. A renamed id reads as a new, empty one, and what players saved under the old one is lost.

## Errors you may see

| Error | Cause | Fix |
|---|---|---|
| Fabric refuses to start: SlotKit `breaks` `menukit-containers` | A Containers jar is in the mods folder beside SlotKit. | Remove the Containers jar. SlotKit replaces it. |
| Fabric refuses to start: SlotKit `breaks` `inventorymax` | Inventory Max is installed. It has no SlotKit build. | Remove Inventory Max, or keep the whole 6.2 set in a separate profile. |
| Fabric refuses to start: SlotKit needs `menukit` `>=7.0.0` | MenuKit 6 is installed. | Update MenuKit to 7. |
| A mod does not load, with a malformed version in its `menukit` or `slotkit` range | The two bounds are joined with a comma. | Separate them with a space. |
| `cannot find symbol` on a `com.trevlar.menukit.api.slot` or `.api.window` class | A wildcard import the map could not reach. | Write one import per class and run the map again. |
| `cannot find symbol: class SlotGroup` | Containers' created group type, in a file the first rename in step 4 did not reach. | Rename it to `CreatedSlotGroup` in that file. Leave a `SlotGroup` class of your own alone. |
| `cannot find symbol: variable ContainerKeys` | A `ContainerKeys.MENDING` the second rename did not reach. | Write `BehaviorKeys.MENDING`. |
| Startup throws `IllegalStateException: Mod 'mymod' has a "slotkit" block in fabric.mod.json, but "bothSides" must be "required" or "preferred"` | A misspelt value. | Write `"required"` or `"preferred"`. |
| Startup throws `IllegalStateException: ParityGroup 'mymod:pockets' builds slots, so SlotKit is needed on both sides` (or `CustomMenu ...`) | The mod does not declare `required`. | Add `"custom": { "slotkit": { "bothSides": "required" } }`. |
| Startup throws `... its namespace 'my-mod' is not a loaded mod's id`, or `... an id that builds slots is namespaced by the id of the mod that defines it` | The panel or menu id's namespace is not your mod id. | Use your mod id as the namespace. |
| A player is refused: "This server needs SlotKit and ... on your game too" | Expected for a `required` mod. The player lacks SlotKit or the mod. | The player installs both. |
| A player is refused: "Your ... doesn't match the server's: its slots differ" | The player's copy of the mod registers different `ParityGroup` slot groups. | The player installs the version the server uses. |
| A player is refused: "This server runs a different version of SlotKit (protocol ..., yours ...)" | The player's SlotKit speaks another protocol. | The player installs the SlotKit version the server uses. |
