package com.trevlar.slotkit.api.item;

import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.menukit.api.panel.SlotItemLooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * How a slot draws an item that carries an {@link ItemState}: an animation, another model,
 * or a mark under or over it. SlotKit reads the state from the stack on screen and MenuKit
 * draws ({@code SlotItemLooks}, {@code SlotDecorations}), so a mod writes neither a
 * mixin nor a lookup of which item a slot shows. Client side; declare from the client
 * initializer, with ids unique per mod.
 *
 * <pre>{@code
 * static final Transition OPENING = Transition.of(250, Easing.EASE_OUT);
 *
 * SlotLooks.animate("mymod:open_lid", OPEN, ItemAnimation.OPEN_LID, OPENING);
 * SlotLooks.over("mymod:open_mark", OPEN, (g, screen, slot, stack, x, y) -> g.fill(x, y, x + 16, y + 16, 0x80606060));
 * }</pre>
 *
 * Since 2.2.0.
 */
public final class SlotLooks {

    private SlotLooks() {}

    /**
     * The item plays {@code animation} toward its end while it carries {@code state}, and
     * back when the state clears. The timing follows the item ({@link ItemSources#pathOf}
     * where the item has a path, otherwise its place in the slot's container), so a screen
     * opened in place of another picks a running animation up where it stood. Compose a
     * model swap with {@link ItemAnimation#swapped}.
     */
    public static void animate(String id, ItemState<?> state, ItemAnimation animation, Transition transition) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(transition, "transition");
        SlotItemLooks.declare(id, (screen, slot, stack) -> {
            float t = transition.toward(keyOf(slot), state.is(stack));
            return t <= 0f ? null : animation.at(t);
        });
    }

    /** The item draws as {@code swap}'s stack while it carries {@code state} (another model); its count stays its own. */
    public static void swapWhen(String id, ItemState<?> state, UnaryOperator<ItemStack> swap) {
        Objects.requireNonNull(state, "state");
        ItemAnimation.Frame frame = ItemAnimation.AS_IS.swapped(swap).at(1f);
        SlotItemLooks.declare(id, (screen, slot, stack) -> state.is(stack) ? frame : null);
    }

    /** {@code decoration} draws under the item, and answers its tooltip, while the item carries {@code state}. */
    public static void under(String id, ItemState<?> state, SlotDecorations.Decoration decoration) {
        SlotDecorations.under(id, when(state, decoration));
    }

    /** {@code decoration} draws over the item, and answers its tooltip, while the item carries {@code state}. */
    public static void over(String id, ItemState<?> state, SlotDecorations.Decoration decoration) {
        SlotDecorations.over(id, when(state, decoration));
    }

    private static SlotDecorations.Decoration when(ItemState<?> state, SlotDecorations.Decoration decoration) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(decoration, "decoration");
        return new SlotDecorations.Decoration() {
            @Override
            public void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
                if (state.is(stack)) decoration.draw(g, screen, slot, stack, x, y);
            }

            @Override
            public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                     int mouseX, int mouseY) {
                return state.is(stack) ? decoration.tooltip(screen, slot, stack, mouseX, mouseY) : null;
            }
        };
    }

    // What an item's timing is keyed by: its path where it has one, so it follows the item
    // across screens; otherwise the slot's container and index, which last while it's open.
    private static Object keyOf(Slot slot) {
        var player = Minecraft.getInstance().player;
        ItemPath path = player == null ? null : ItemSources.pathOf(player, slot);
        if (path != null) return path;
        Slot real = com.trevlar.slotkit.core.Slots.target(slot);
        return List.of(real.container, real.getContainerSlot());
    }
}
