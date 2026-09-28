6.0.0 (unreleased, in progress): one of everything. Requires MenuKit 6.0.0. Breaking; the migration guide lists every change.

Containers has its own root package, `com.trevlar.menukit.containers`; every import changes. Containers starts on a dedicated server, and a player can join it. On the server a created slot is addressed as a created slot and its group has its category, so operations, vetoes and gates judge it the way singleplayer does. A mod that registers a slot-state channel no longer crashes the server. `MKCMenu`'s screen is set on the client with `menu.screen(MyScreen::new)` from your client initializer; `MKCMenu.Builder.screen(...)` is gone, since naming a screen class in common code crashed every dedicated server.

5.2.0 (unreleased): `SlotGroups.of(slotRef)` names a created slot's own group, so a veto can tell a pocket from the main inventory. Requires MenuKit 5.2.0. Adds API and breaks nothing.

5.1.0: Created slots follow MenuKit's slot operations. Requires MenuKit 5.1.0. This release adds API and breaks nothing.

Shift-clicking into or out of a created slot now goes through the same shift-click in and shift-click out operations as a vanilla slot. A locking mod's veto covers pockets and other created slots without any extra work from you.

Created slot groups appear in MenuKit's slot group list without a menu open. Groups on a container panel are listed from mod init. Groups added with `MKCSlots.onto` are listed once a menu carries them, or from init if you call `SlotGroups.declare`.

`SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated and will be removed in 6.0.0. They still work, and `quickMove` now sets the two shift-click operations for you. New code should call `set(BehaviorKeys.SHIFT_CLICK_OUT, ...)` and `set(BehaviorKeys.SHIFT_CLICK_IN, ...)`.
