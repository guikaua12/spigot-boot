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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;

import java.util.Objects;

/**
 * One slot's worth of pagination engine output: a user-declared element builder, a plain
 * frame item (a {@code null} item clears the slot), or the failure sentinel that keeps the
 * slot's previous content entirely.
 *
 * <p>Consumers decode in this order: {@link #isFailure()} first, then {@link #elementBuilder()},
 * then {@link #plainItem()} (where {@code null} clears the slot).
 */
@ApiStatus.Internal
public final class RenderedItem {

    private static final RenderedItem FAILURE = new RenderedItem(null, null, true);

    private final ItemComponentBuilderImpl elementBuilder;
    private final ItemStack plainItem;
    private final boolean failure;

    private RenderedItem(ItemComponentBuilderImpl elementBuilder, ItemStack plainItem, boolean failure) {
        this.elementBuilder = elementBuilder;
        this.plainItem = plainItem;
        this.failure = failure;
    }

    /**
     * Creates a slot output backed by a user-declared page element.
     *
     * @param builder the element declaration to materialize at fill time, not null
     * @return the rendered slot content
     * @throws NullPointerException if {@code builder} is null
     */
    public static @NotNull RenderedItem ofElement(@NotNull ItemComponentBuilderImpl builder) {
        return new RenderedItem(Objects.requireNonNull(builder, "builder"), null, false);
    }

    /**
     * Creates a plain frame item: a loading frame, a fallback filler or a slot clear.
     *
     * @param item the stack to paint, or {@code null} to clear the slot
     * @return the rendered slot content
     */
    public static @NotNull RenderedItem ofItem(@Nullable ItemStack item) {
        return new RenderedItem(null, item, false);
    }

    /**
     * Returns the failure sentinel: the slot keeps its previous content (item and click
     * handlers); on a first paint with no previous content the fallback item is painted instead.
     *
     * @return the shared failure sentinel
     */
    public static @NotNull RenderedItem failure() {
        return FAILURE;
    }

    /**
     * Returns the element declaration carried by this output.
     *
     * @return the element builder, or {@code null} unless created by {@link #ofElement}
     */
    public @Nullable ItemComponentBuilderImpl elementBuilder() {
        return elementBuilder;
    }

    /**
     * Returns the plain frame item carried by this output.
     *
     * @return the stack, or {@code null} for elements, failures and slot clears
     */
    public @Nullable ItemStack plainItem() {
        return plainItem;
    }

    /**
     * Returns whether this output is the failure sentinel.
     *
     * @return {@code true} for {@link #failure()} outputs
     */
    public boolean isFailure() {
        return failure;
    }
}
