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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Mutable collector behind {@link ItemComponentBuilder}; gathers a component declaration
 * and materializes it into an immutable {@link ComponentInstance} bound to fixed slots.
 */
@ApiStatus.Internal
public final class ItemComponentBuilderImpl implements ItemComponentBuilder {

    private ItemStack staticItem;
    private Function<ViewContext, ItemStack> renderer;
    private Predicate<ViewContext> displayIf;
    private final List<Integer> watchedTokenIds = new ArrayList<>();
    private final Map<ClickType, Consumer<SlotClickContext>> typedHandlers = new EnumMap<>(ClickType.class);
    private Consumer<SlotClickContext> defaultHandler;
    private Boolean cancelOnClick;
    private boolean closeOnClick;
    private Class<? extends View> openOnClickTarget;
    private ViewArguments openOnClickArguments = ViewArguments.empty();

    @Override
    public @NotNull ItemComponentBuilder item(@NotNull ItemStack item) {
        this.staticItem = Objects.requireNonNull(item, "item");
        this.renderer = null;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.staticItem = null;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition) {
        this.displayIf = Objects.requireNonNull(condition, "condition");
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens) {
        for (StateToken token : tokens) {
            Objects.requireNonNull(token, "token");
            if (!(token instanceof IdentifiableToken)) {
                throw new IllegalArgumentException("unsupported StateToken implementation: "
                        + token.getClass().getName()
                        + "; only tokens created by View state factories are watchable");
            }
            watchedTokenIds.add(((IdentifiableToken) token).tokenId());
        }
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler) {
        this.defaultHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler) {
        Objects.requireNonNull(type, "type");
        typedHandlers.put(type, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder cancelOnClick(boolean cancel) {
        this.cancelOnClick = cancel;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder closeOnClick() {
        this.closeOnClick = true;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target) {
        return openOnClick(target, ViewArguments.empty());
    }

    @Override
    public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        this.openOnClickTarget = Objects.requireNonNull(target, "target");
        this.openOnClickArguments = Objects.requireNonNull(arguments, "arguments");
        return this;
    }

    /**
     * Freezes the collected declaration into a component bound to the given slots.
     *
     * @param slots the container slots this component paints, in paint order
     * @return the immutable materialized component
     */
    public @NotNull ComponentInstance materialize(@NotNull int[] slots) {
        return new ComponentInstance(slots, staticItem, renderer, displayIf, typedHandlers,
                defaultHandler, cancelOnClick, closeOnClick, openOnClickTarget,
                openOnClickArguments, watchedTokenIds);
    }
}
