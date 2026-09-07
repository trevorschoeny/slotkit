4.0.0: Created slots are drawn by vanilla now. See MenuKit 4.0.0 for the whole picture. SlotElement positions the slot and draws the frame, nothing more. A slot that no frame presents is parked off screen instead of being left where it last sat.

Breaking: MKCSlot.renderX, MKCSlot.renderY and MKCSlot.setRenderPosition are removed. A created slot's only presentation position is vanilla's Slot.x and Slot.y.

3.1.0: no changes in this artifact. Requires MenuKit 3.1.0, released alongside it.

3.0.0: the Java package is now com.trevlar.menukit (was com.trevorschoeny.menukit); update imports. MenuKit and MenuKit: Containers now share one repository and one set of docs at github.com/trevorschoeny/menukit. No class or method names changed.
