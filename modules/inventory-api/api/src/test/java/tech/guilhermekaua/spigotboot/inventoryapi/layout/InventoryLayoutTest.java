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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryLayoutTest {

    @Test
    void constructor_rejectsRowWithWrongWidth() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryLayout(
                "    O    ",
                "  OOOO  " // 8 characters wide — not nine, so it is rejected
        ));
    }

    @Test
    void distinctLetters_sortAlphabetically_definingFillOrder() {
        InventoryLayout layout = new InventoryLayout(
                "   CAB   "
        );

        List<Character> letters = layout.getSlots().stream()
                .map(InventorySlot::getLetter)
                .toList();
        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of('A', 'B', 'C'), letters);
        assertEquals(List.of(4, 5, 3), slots); // A sits at column 4, B at 5, C at 3
    }

    @Test
    void repeatedLetters_keepRowMajorGridOrder() {
        InventoryLayout layout = new InventoryLayout(
                "  OO     ",
                " O       "
        );

        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of(2, 3, 10), slots); // stable sort: ties keep grid order
    }

    @Test
    void backAndNextChars_overrideDefaultNavSlots() {
        InventoryLayout layout = new InventoryLayout(
                "<       >"
        );

        assertEquals(0, layout.getBackSlot());
        assertEquals(8, layout.getNextSlot());
    }

    @Test
    void navSlots_defaultTo45And53() {
        InventoryLayout layout = new InventoryLayout(
                "    O    "
        );

        assertEquals(45, layout.getBackSlot());
        assertEquals(53, layout.getNextSlot());
    }

    @Test
    void columnSizes_countNamedSlotsPerColumn() {
        InventoryLayout layout = new InventoryLayout(
                " A A     ",
                " B       "
        );

        assertEquals(2, layout.getColumnSizes().get(1));
        assertEquals(1, layout.getColumnSizes().get(3));
        assertFalse(layout.getColumnSizes().containsKey(0));
    }
}
