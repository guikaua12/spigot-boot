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
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.GridSlotsLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.OrderedSlotsLayout;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    @Test
    void ofGrid_returnsGridSlotsLayout() {
        assertTrue(Layout.ofGrid("         ") instanceof GridSlotsLayout);
    }

    @Test
    void ofGrid_blankGrid_hasNoSlots() {
        Layout layout = Layout.ofGrid("         ");

        assertEquals(Collections.emptyList(), layout.slots());
    }

    @Test
    void ofGrid_distinctChars_fillAlphabetically() {
        Layout layout = Layout.ofGrid("   CAB   ");

        // A sits at column 4, B at 5, C at 3; fill order is A, B, C
        assertEquals(Arrays.asList(4, 5, 3), layout.slots());
    }

    @Test
    void ofGrid_repeatedChars_keepOccurrenceOrder() {
        Layout layout = Layout.ofGrid(
                "  OO     ",
                " O       ");

        assertEquals(Arrays.asList(2, 3, 10), layout.slots());
    }

    @Test
    void ofGrid_mixedChars_groupByCharThenOccurrence() {
        Layout layout = Layout.ofGrid(
                " B A     ",
                " A B     ");

        // all A occurrences (slots 3, 10) come before all B occurrences (slots 1, 12)
        assertEquals(Arrays.asList(3, 10, 1, 12), layout.slots());
    }

    @Test
    void ofGrid_rejectsRowNotExactlyNineWide() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid("        ")); // 8 chars
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid("          ")); // 10 chars
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid(
                "         ",
                "    O   ")); // second row 8 chars
    }

    @Test
    void ofSlots_returnsOrderedSlotsLayout() {
        assertTrue(Layout.ofSlots(0) instanceof OrderedSlotsLayout);
    }

    @Test
    void ofSlots_keepsExplicitOrder() {
        Layout layout = Layout.ofSlots(14, 10, 12);

        assertEquals(Arrays.asList(14, 10, 12), layout.slots());
    }

    @Test
    void ofSlots_acceptsBoundarySlots() {
        Layout layout = Layout.ofSlots(0, 53);

        assertEquals(Arrays.asList(0, 53), layout.slots());
    }

    @Test
    void ofSlots_rejectsOutOfBoundsSlots() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(-1));
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(54));
    }

    @Test
    void ofSlots_rejectsDuplicateSlots() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(3, 3));
    }

    @Test
    void slots_areUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> Layout.ofGrid("A        ").slots().add(9));
        assertThrows(UnsupportedOperationException.class,
                () -> Layout.ofSlots(0).slots().add(9));
    }
}
