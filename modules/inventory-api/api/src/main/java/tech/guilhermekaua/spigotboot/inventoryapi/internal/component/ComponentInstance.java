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

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Immutable materialized item component: fixed slots, item source, visibility predicate,
 * click handlers, post-actions and watched token ids. Renderer and predicate failures are
 * swallowed and logged at most once per minute per instance (§9 error table).
 */
@ApiStatus.Internal
public final class ComponentInstance {

    private static final Logger LOGGER = Logger.getLogger(ComponentInstance.class.getName());
    private static final long LOG_INTERVAL_MILLIS = 60_000L;

    /**
     * Identity sentinel returned by {@link #renderForPaint} when evaluation failed: the caller
     * must skip painting so the slot keeps its previous content.
     */
    public static final ItemStack RENDER_FAILURE = new ItemStack(Material.BARRIER);

    private final int[] slots;
    private final ItemStack staticItem;
    private final Function<ViewContext, ItemStack> renderer;
    private final Predicate<ViewContext> displayIf;
    private final Map<ClickType, Consumer<SlotClickContext>> typedHandlers;
    private final Consumer<SlotClickContext> defaultHandler;
    private final Boolean cancelOnClick;
    private final boolean closeOnClick;
    private final Class<? extends View> openOnClickTarget;
    private final ViewArguments openOnClickArguments;
    private final int[] watchedTokenIds;

    // rate limit for renderer/displayIf failure logs; a racy double log is acceptable
    private volatile long lastLogMillis;

    ComponentInstance(@NotNull int[] slots,
                      @Nullable ItemStack staticItem,
                      @Nullable Function<ViewContext, ItemStack> renderer,
                      @Nullable Predicate<ViewContext> displayIf,
                      @NotNull Map<ClickType, Consumer<SlotClickContext>> typedHandlers,
                      @Nullable Consumer<SlotClickContext> defaultHandler,
                      @Nullable Boolean cancelOnClick,
                      boolean closeOnClick,
                      @Nullable Class<? extends View> openOnClickTarget,
                      @NotNull ViewArguments openOnClickArguments,
                      @NotNull List<Integer> watchedTokenIds) {
        this.slots = slots.clone();
        this.staticItem = staticItem;
        this.renderer = renderer;
        this.displayIf = displayIf;
        EnumMap<ClickType, Consumer<SlotClickContext>> handlers = new EnumMap<>(ClickType.class);
        handlers.putAll(typedHandlers);
        this.typedHandlers = handlers;
        this.defaultHandler = defaultHandler;
        this.cancelOnClick = cancelOnClick;
        this.closeOnClick = closeOnClick;
        this.openOnClickTarget = openOnClickTarget;
        this.openOnClickArguments = Objects.requireNonNull(openOnClickArguments, "openOnClickArguments");
        this.watchedTokenIds = toIntArray(watchedTokenIds);
    }

    /**
     * @return the container slots this component paints, in paint order
     */
    public @NotNull int[] slots() {
        return slots.clone();
    }

    /**
     * Evaluates the {@code displayIf} predicate; visible by default.
     *
     * @return false when the predicate returns false or throws (throw is rate-limit logged)
     */
    public boolean isVisible(@NotNull ViewContext ctx) {
        if (displayIf == null) {
            return true;
        }
        try {
            return displayIf.test(ctx);
        } catch (RuntimeException error) {
            logFailure("displayIf predicate", error);
            return false;
        }
    }

    /**
     * Produces this component's item: the static item, or the renderer applied to the context.
     *
     * @return the item, or null when the renderer throws (throw is rate-limit logged)
     */
    public @Nullable ItemStack renderItem(@NotNull ViewContext ctx) {
        if (staticItem != null) {
            return staticItem;
        }
        if (renderer == null) {
            return null;
        }
        try {
            return renderer.apply(ctx);
        } catch (RuntimeException error) {
            logFailure("item renderer", error);
            return null;
        }
    }

    /**
     * Single paint entry point: returns null to clear the slot (hidden component), the
     * {@link #RENDER_FAILURE} sentinel (compare by identity) when displayIf or the renderer
     * threw, or the item to paint.
     *
     * @param ctx the rendering context
     * @return the item, null, or the failure sentinel
     */
    public @Nullable ItemStack renderForPaint(@NotNull ViewContext ctx) {
        boolean visible;
        try {
            visible = displayIf == null || displayIf.test(ctx);
        } catch (RuntimeException e) {
            logFailure("displayIf", e);
            return RENDER_FAILURE;
        }
        if (!visible) {
            return null;
        }
        if (renderer == null) {
            return staticItem;
        }
        try {
            return renderer.apply(ctx);
        } catch (RuntimeException e) {
            logFailure("item renderer", e);
            return RENDER_FAILURE;
        }
    }

    /**
     * Resolves the handler for a click: the matching per-type handler, else the untyped
     * default handler, else null.
     */
    public @Nullable Consumer<SlotClickContext> handlerFor(@NotNull ClickType type) {
        Consumer<SlotClickContext> typed = typedHandlers.get(type);
        return typed != null ? typed : defaultHandler;
    }

    /**
     * @return the per-component cancellation override, or null to inherit the config default
     */
    public @Nullable Boolean cancelOnClick() {
        return cancelOnClick;
    }

    /**
     * @return true when a click on this component closes the view at end of tick
     */
    public boolean closeOnClick() {
        return closeOnClick;
    }

    /**
     * @return the view opened on click, or null when no navigation was declared
     */
    public @Nullable Class<? extends View> openOnClickTarget() {
        return openOnClickTarget;
    }

    /**
     * @return the arguments passed to the open-on-click target; empty when none were given
     */
    public @NotNull ViewArguments openOnClickArguments() {
        return openOnClickArguments;
    }

    /**
     * @return the watched token ids in declaration order
     */
    public @NotNull int[] watchedTokenIds() {
        return watchedTokenIds.clone();
    }

    /**
     * @return true when a static item or a renderer was declared; checked by {@link ComponentTable#add}
     */
    boolean hasItemSource() {
        return staticItem != null || renderer != null;
    }

    private void logFailure(@NotNull String stage, @NotNull RuntimeException error) {
        long now = System.currentTimeMillis();
        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }
        lastLogMillis = now;
        LOGGER.log(Level.SEVERE,
                "component " + stage + " failed for slots " + Arrays.toString(slots), error);
    }

    private static int[] toIntArray(@NotNull List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}
