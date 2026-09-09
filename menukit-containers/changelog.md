5.0.0: Every created slot group declares what it is. SlotSpec.at(groupId, category) and MKCSlots.onto(...).category(...) take a SlotGroupCategory, required, and the group is published under it in MenuKit's slot-group registry on every menu it sits on, so another mod can find it and decide what it is. SlotSpec gains collect(false) and dragFill(false) alongside quickMove(NONE) to keep a group out of the bulk shortcuts. Breaks: SlotSpec.at(String) is now SlotSpec.at(String, SlotGroupCategory); MKCSlots.Builder.register() refuses a group with no category; SlotGroup constructors take a category; MKCScreenHandler.PanelBuilder.group(...) takes a category. Every consumer adds one call per group.
4.0.0: Created slots are drawn by vanilla now. See MenuKit 4.0.0 for the whole picture. SlotElement positions the slot and draws the frame, nothing more. A slot that no frame presents is parked off screen instead of being left where it last sat.

Breaking: MKCSlot.renderX, MKCSlot.renderY and MKCSlot.setRenderPosition are removed. A created slot's only presentation position is vanilla's Slot.x and Slot.y.

3.1.0: no changes in this artifact. Requires MenuKit 3.1.0, released alongside it.

3.0.0: the Java package is now com.trevlar.menukit (was com.trevorschoeny.menukit); update imports. MenuKit and MenuKit: Containers now share one repository and one set of docs at github.com/trevorschoeny/menukit. No class or method names changed.
