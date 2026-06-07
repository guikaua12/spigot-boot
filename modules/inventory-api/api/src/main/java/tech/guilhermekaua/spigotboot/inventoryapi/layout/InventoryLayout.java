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

import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.impl.GridLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.impl.OrderedSlotsLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Defines where and in what order pagination items are placed inside an inventory.
 *
 * <p>The core contract is {@link #getSlots()}: an ordered list of fill positions. Consumers
 * place the i-th page item into the i-th slot of that list.
 *
 * <p>Use {@link #ofGrid(String...)} to build a layout from a visual row-by-row ASCII grid whose
 * letters define the fill order alphabetically.
 *
 * <p>Use {@link #ofSlots(int...)} to state the fill order directly as slot indices when the grid
 * vocabulary cannot express it.
 */
public interface InventoryLayout {

    /**
     * Width of a single chest inventory row in slots.
     */
    int INVENTORY_ROW_WIDTH = 9;

    /**
     * Returns the ordered item slot positions: the i-th page item is placed into the i-th element
     * of this list.
     *
     * @return the ordered fill positions, never null
     */
    List<InventorySlot> getSlots();

    /**
     * @return the inventory slot holding the back-navigation button
     */
    int getBackSlot();

    /**
     * @return the inventory slot holding the next-navigation button
     */
    int getNextSlot();

    /**
     * Counts the item slots in each inventory column, keyed by column index (0-8). Columns
     * without item slots are absent from the map.
     *
     * @return the number of item slots per column
     */
    default Map<Integer, Integer> getColumnSizes() {
        Map<Integer, Integer> columnSizes = new HashMap<>();
        for (InventorySlot slot : getSlots()) {
            columnSizes.merge(slot.getSlot() % INVENTORY_ROW_WIDTH, 1, Integer::sum);
        }
        return columnSizes;
    }

    /**
     * Creates a grid layout using the default {@code ' '} (empty), {@code '<'} (back) and
     * {@code '>'} (next) control characters.
     *
     * @param rows the grid rows, each exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     * @return the parsed grid layout
     * @throws IllegalArgumentException if a row is not exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     */
    static GridLayout ofGrid(String... rows) {
        return new GridLayout(rows);
    }

    /**
     * Creates a grid layout using custom control characters.
     *
     * @param empty the character marking a slot without an item
     * @param back  the character marking the back-navigation slot
     * @param next  the character marking the next-navigation slot
     * @param rows  the grid rows, each exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     * @return the parsed grid layout
     * @throws IllegalArgumentException if a row is not exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     */
    static GridLayout ofGrid(char empty, char back, char next, String... rows) {
        return new GridLayout(empty, back, next, rows);
    }

    /**
     * Creates a layout that places items in exactly the order of the given slot indices, for fill
     * orders the grid letters cannot express (for example fully custom orders over more than 26
     * slots).
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @return the ordered layout
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    static OrderedSlotsLayout ofSlots(int... slots) {
        return new OrderedSlotsLayout(slots);
    }

    /**
     * @throws IllegalArgumentException if {@code layout} defines no item slots (only empty/back/next)
     */
    static void requireItemSlots(InventoryLayout layout) {
        Objects.requireNonNull(layout, "layout");
        if (layout.getSlots().isEmpty()) {
            throw new IllegalArgumentException("layout must define at least one item slot");
        }
    }
}
