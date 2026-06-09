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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;

/**
 * Renders one element of the current page into a component builder. The engine invokes the
 * renderer once per element on every repaint of the pagination area, always on the main
 * thread, handing it a fresh {@link ItemComponentBuilder} that is materialized into a
 * single-slot component afterwards — {@code displayIf}, {@code updateOnStateChange},
 * {@code onClick}, {@code cancelOnClick}, {@code closeOnClick} and {@code openOnClick} behave
 * exactly as on statically declared components.
 *
 * <p>The renderer must declare an item source via {@code item(...)}. A renderer that throws,
 * or declares no item source, is logged rate-limited and the slot keeps its previous content
 * entirely (item and click handlers); on a first paint with no previous content the
 * pagination's fallback item is painted instead.
 *
 * @param <T> the element type served by the pagination's page source
 */
@FunctionalInterface
public interface PaginationItemRenderer<T> {

    /**
     * Renders one page element.
     *
     * @param context the context of the session being painted
     * @param item    the fresh component builder to declare the element on
     * @param index   the ZERO-BASED position of the element within the CURRENT page — not the
     *                global element index
     * @param value   the element value served by the page source
     */
    void render(@NotNull ViewContext context, @NotNull ItemComponentBuilder item, int index, @NotNull T value);
}
