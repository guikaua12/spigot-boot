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
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;

/**
 * Context for {@code View.onFirstRender}: declares this session's components. Declaration
 * order is paint order. Main thread, engine-invoked, once per open.
 */
@ApiStatus.NonExtendable
public interface RenderContext extends ViewContext {

    /**
     * Starts a component bound to a single slot.
     *
     * @param slot the 0-based raw slot, less than {@code rows * 9}
     * @return the component builder for fluent configuration
     */
    @NotNull ItemComponentBuilder slot(int slot);

    /**
     * Starts a component bound to a single slot addressed by grid position.
     *
     * @param row    the 1-based row, 1 to 6
     * @param column the 1-based column, 1 to 9
     * @return the component builder
     */
    @NotNull ItemComponentBuilder slot(int row, int column);

    /**
     * Starts a single-slot component with a static item already set.
     *
     * @param slot the 0-based raw slot
     * @param item the static item to display
     * @return the component builder
     */
    @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item);

    /**
     * Starts one component applied to every slot bound to the given layout character.
     *
     * @param character a character present in the configured layout
     * @return the component builder
     */
    @NotNull ItemComponentBuilder layoutSlot(char character);

    /**
     * Starts a layout-character component with a static item already set.
     *
     * @param character a character present in the configured layout
     * @param item      the static item displayed on every bound slot
     * @return the component builder
     */
    @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item);
}
