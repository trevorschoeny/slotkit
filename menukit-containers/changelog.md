6.0.0 (unreleased, in progress): one of everything. Requires MenuKit 6.0.0. Breaking; the migration guide lists every change.

Containers has its own root package, `com.trevlar.menukit.containers`; every import changes. Containers starts on a dedicated server, and a player can join it. On the server a created slot is addressed as a created slot and its group has its category, so operations, vetoes and gates judge it the way singleplayer does. A mod that registers a slot-state channel no longer crashes the server. `MKCMenu`'s screen is set on the client with `menu.screen(MyScreen::new)` from your client initializer; `MKCMenu.Builder.screen(...)` is gone, since naming a screen class in common code crashed every dedicated server.

A created slot's address comes from `Address.createdSlot(MKCSlots.groupId(panelId, groupId), index)`; `CreatedSlotAdapter.addressOf`, `MKCContainerPanel.address` and `MKCScreenHandler.address` are gone. Fixed: a created slot could have one index on the client and another on the server. Parity slots were added to a menu in the order mods initialised, and a client and a server with different mods can initialise in different orders, which desynced the slot. They are now added sorted by panel id, the same on both sides. Storage attachments, slot-state channels, container panels and menus are declared at init; a late one throws.

`SlotGroups.of(slotRef)` names a created slot's own group, so a veto can tell a pocket from the main inventory. Adds API and breaks nothing.

The gate, Curse of Binding and shift-click routing are MenuKit's. `SlotGate`, `GatingContext`, `GATING` and `BINDING` moved to MenuKit; every gate implementation's import changes and `MKCBehaviorKeys` keeps only `MENDING`, whose seam is Containers'. `QUICK_MOVE`, `SlotSpec.quickMove` and `QuickMoveParticipation` are gone, deprecated since 5.1.0: `SlotSpec.shiftClickOut(false)` and `shiftClickIn(false)` declare the two shift-click operations at the group rung. `MKCSlotQuickMove.route`, which nothing called, is deleted; a menu's own `quickMoveStack` still routes between its groups. Containers' four gating mixins are gone with the gate; a created slot no longer gates itself, and resolves through MenuKit's slot-level seam by its created address.

5.1.0: Created slots follow MenuKit's slot operations. Requires MenuKit 5.1.0. This release adds API and breaks nothing.

Shift-clicking into or out of a created slot now goes through the same shift-click in and shift-click out operations as a vanilla slot. A locking mod's veto covers pockets and other created slots without any extra work from you.

Created slot groups appear in MenuKit's slot group list without a menu open. Groups on a container panel are listed from mod init. Groups added with `MKCSlots.onto` are listed once a menu carries them, or from init if you call `SlotGroups.declare`.

`SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated and will be removed in 6.0.0. They still work, and `quickMove` now sets the two shift-click operations for you. New code should call `set(BehaviorKeys.SHIFT_CLICK_OUT, ...)` and `set(BehaviorKeys.SHIFT_CLICK_IN, ...)`.
