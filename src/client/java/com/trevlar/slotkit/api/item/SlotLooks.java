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
 * How a slot draws an item while something holds for it: an animation, another model, or a
 * mark under or over it. "Something" is an {@link ItemState} the item carries, or a
 * {@link When} the mod answers on the client from what it already knows (an open session,
 * a lock). SlotKit times the animation by the item and MenuKit draws ({@code SlotItemLooks},
 * {@code SlotDecorations}), so a mod writes neither a mixin nor a lookup of which item a
 * slot shows. Client side; declare from the client
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

    /** Whether a look applies to {@code slot}'s {@code stack} this frame. Client side, asked per frame. */
    @FunctionalInterface
    public interface When {
        boolean test(Slot slot, ItemStack stack);
    }

    private static When of(ItemState<?> state) {
        Objects.requireNonNull(state, "state");
        return (slot, stack) -> state.is(stack);
    }

    /**
     * The item plays {@code animation} toward its end while it carries {@code state}, and
     * back when the state clears. The timing follows the item ({@link ItemSources#pathOf}
     * where the item has a path, otherwise its place in the slot's container), so a screen
     * opened in place of another picks a running animation up where it stood. Compose a
     * model swap with {@link ItemAnimation#swapped}.
     */
    public static void animate(String id, ItemState<?> state, ItemAnimation animation, Transition transition) {
        animate(id, of(state), animation, transition);
    }

    /** {@link #animate(String, ItemState, ItemAnimation, Transition)}, while {@code when} holds. */
    public static void animate(String id, When when, ItemAnimation animation, Transition transition) {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(transition, "transition");
        SlotItemLooks.declare(id, (screen, slot, stack) -> {
            float t = transition.toward(keyOf(slot), when.test(slot, stack));
            return t <= 0f ? null : animation.at(t);
        });
    }

    /** The item draws as {@code swap}'s stack while it carries {@code state} (another model); its count stays its own. */
    public static void swapWhen(String id, ItemState<?> state, UnaryOperator<ItemStack> swap) {
        swapWhen(id, of(state), swap);
    }

    /** {@link #swapWhen(String, ItemState, UnaryOperator)}, while {@code when} holds. */
    public static void swapWhen(String id, When when, UnaryOperator<ItemStack> swap) {
        Objects.requireNonNull(when, "when");
        ItemAnimation.Frame frame = ItemAnimation.AS_IS.swapped(swap).at(1f);
        SlotItemLooks.declare(id, (screen, slot, stack) -> when.test(slot, stack) ? frame : null);
    }

    /** {@code decoration} draws under the item, and answers its tooltip, while the item carries {@code state}. */
    public static void under(String id, ItemState<?> state, SlotDecorations.Decoration decoration) {
        under(id, of(state), decoration);
    }

    /** {@code decoration} draws under the item, and answers its tooltip, while {@code when} holds. */
    public static void under(String id, When when, SlotDecorations.Decoration decoration) {
        SlotDecorations.under(id, gated(when, decoration));
    }

    /** {@code decoration} draws over the item, and answers its tooltip, while the item carries {@code state}. */
    public static void over(String id, ItemState<?> state, SlotDecorations.Decoration decoration) {
        over(id, of(state), decoration);
    }

    /** {@code decoration} draws over the item, and answers its tooltip, while {@code when} holds. */
    public static void over(String id, When when, SlotDecorations.Decoration decoration) {
        SlotDecorations.over(id, gated(when, decoration));
    }

    private static SlotDecorations.Decoration gated(When when, SlotDecorations.Decoration decoration) {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(decoration, "decoration");
        return new SlotDecorations.Decoration() {
            @Override
            public void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
                if (when.test(slot, stack)) decoration.draw(g, screen, slot, stack, x, y);
            }

            @Override
            public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                     int mouseX, int mouseY) {
                return when.test(slot, stack) ? decoration.tooltip(screen, slot, stack, mouseX, mouseY) : null;
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
