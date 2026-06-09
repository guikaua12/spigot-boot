/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.inventoryapi.component;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent declaration of one item component, returned by
 * {@code RenderContext.slot(...)}/{@code layoutSlot(...)}. Declaration order is paint
 * order; all callbacks run on the main thread, engine-invoked. A component must declare an
 * item source via {@code item(...)} — one without fails at first render with
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException}.
 */
@ApiStatus.NonExtendable
public interface ItemComponentBuilder {

    /**
     * Sets a static item for this component.
     *
     * @param item the item to display
     * @return this builder
     */
    @NotNull ItemComponentBuilder item(@NotNull ItemStack item);

    /**
     * Sets a dynamic item renderer, re-evaluated on every re-render of this component.
     * Renderer failures keep the previous slot content and are logged rate-limited.
     *
     * @param renderer the per-render item factory
     * @return this builder
     */
    @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer);

    /**
     * Shows this component only while the condition holds; a hidden component's slots are
     * cleared and it receives no clicks.
     *
     * @param condition evaluated on every re-render of this component
     * @return this builder
     */
    @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition);

    /**
     * Re-renders this component whenever one of the given tokens changes, at most once per
     * flush. Dependencies are explicit — there is no automatic read tracking.
     *
     * @param tokens the state tokens to watch
     * @return this builder
     */
    @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens);

    /**
     * Sets the untyped click handler; it runs only when no per-{@link ClickType} handler
     * matched the click.
     *
     * @param handler the fallback click handler
     * @return this builder
     */
    @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler);

    /**
     * Sets the handler for one specific click type; it takes precedence over the untyped
     * handler.
     *
     * @param type    the click type to match
     * @param handler the click handler
     * @return this builder
     */
    @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler);

    /**
     * Overrides the config-level click cancellation for this component's slots; handlers
     * may still overturn the decision via {@code SlotClickContext.setCancelled(boolean)}.
     *
     * @param cancel whether clicks on this component are pre-cancelled
     * @return this builder
     */
    @NotNull ItemComponentBuilder cancelOnClick(boolean cancel);

    /**
     * Closes the view after a click on this component; deferred to the end of the tick.
     *
     * @return this builder
     */
    @NotNull ItemComponentBuilder closeOnClick();

    /**
     * Navigates to another registered view after a click on this component; deferred to
     * the end of the tick.
     *
     * @param target the registered view class to open
     * @return this builder
     */
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target);

    /**
     * Same as {@link #openOnClick(Class)}, passing arguments to the target view.
     *
     * @param target    the registered view class to open
     * @param arguments the arguments handed to the target's contexts
     * @return this builder
     */
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
