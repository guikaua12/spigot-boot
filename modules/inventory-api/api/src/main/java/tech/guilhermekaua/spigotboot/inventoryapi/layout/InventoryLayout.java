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

import lombok.Getter;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses a row-by-row ASCII grid into a list of named slots plus the back/next navigation slots.
 * Each row must be exactly {@link #INVENTORY_ROW_WIDTH} characters; characters not matching
 * {@code empty}, {@code back} or {@code next} are treated as named item slots and sorted
 * alphabetically by letter.
 */
@Getter
public class InventoryLayout {

    /**
     * Width of a single chest inventory row in characters.
     */
    public static final int INVENTORY_ROW_WIDTH = 9;

    private final String[] layout;
    private final int backSlot, nextSlot;
    private final List<InventorySlot> slots = new LinkedList<>();
    private final Map<Integer, Integer> columnSizes = new HashMap<>();

    public InventoryLayout(char empty, char back, char next, String... layout) {
        this.layout = layout;
        int backSlot = 45, nextSlot = 53; // default

        for (int row = 0; row < layout.length; row++) {
            if (layout[row].length() != INVENTORY_ROW_WIDTH) {
                throw new IllegalArgumentException(
                        "layout row " + row + " must be " + INVENTORY_ROW_WIDTH
                                + " characters wide, but was " + layout[row].length()
                );
            }

            for (int column = 0; column < layout[row].length(); column++) {
                char letter = layout[row].charAt(column);

                int slot = row * 9 + column;
                if (letter == back) {
                    backSlot = slot;
                } else if (letter == next) {
                    nextSlot = slot;
                } else if (letter != empty) {
                    slots.add(new InventorySlot(letter, slot));

                    if (columnSizes.containsKey(column)) {
                        columnSizes.put(column, columnSizes.get(column) + 1);
                    } else {
                        columnSizes.put(column, 1);
                    }
                }
            }
        }

        this.backSlot = backSlot;
        this.nextSlot = nextSlot;

        slots.sort(Comparator.comparing(InventorySlot::getLetter));
    }

    public InventoryLayout(String... layout) {
        this(' ', '<', '>', layout);
    }

    /**
     * @throws IllegalArgumentException if {@code layout} defines no item slots (only empty/back/next)
     */
    public static void requireItemSlots(InventoryLayout layout) {
        Objects.requireNonNull(layout, "layout");
        if (layout.getSlots().isEmpty()) {
            throw new IllegalArgumentException("layout must define at least one item slot");
        }
    }
}
