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
package tech.guilhermekaua.spigotboot.inventoryapi.layout;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.GridSlotsLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.OrderedSlotsLayout;

import java.util.List;

/**
 * An ordered sequence of inventory slot positions used as a fill order, created from an
 * ASCII grid ({@link #ofGrid}) or from explicit slot indices ({@link #ofSlots}).
 */
public interface Layout {

    /**
     * The width of a chest inventory row.
     */
    int ROW_WIDTH = 9;

    /**
     * Returns the slot positions in fill order.
     *
     * @return an unmodifiable ordered list of slot indices
     */
    @NotNull List<Integer> slots();

    /**
     * Parses a row-by-row ASCII grid: {@code ' '} marks an empty slot, every other character
     * is a fill slot; fill order is alphabetical by character, then occurrence order.
     *
     * @param rows the grid rows, each exactly {@link #ROW_WIDTH} characters wide
     * @return the parsed layout
     * @throws IllegalArgumentException if a row is not exactly {@link #ROW_WIDTH} characters wide
     */
    static @NotNull Layout ofGrid(@NotNull String... rows) {
        return new GridSlotsLayout(rows);
    }

    /**
     * Creates a layout that fills slots in exactly the given order.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @return the layout
     * @throws IllegalArgumentException if an index is out of bounds or duplicated
     */
    static @NotNull Layout ofSlots(int... slots) {
        return new OrderedSlotsLayout(slots);
    }
}
