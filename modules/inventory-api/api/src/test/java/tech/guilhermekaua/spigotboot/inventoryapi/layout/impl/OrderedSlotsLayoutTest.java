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
package tech.guilhermekaua.spigotboot.inventoryapi.layout.impl;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedSlotsLayoutTest {

    @Test
    void ofSlots_preservesInputOrderVerbatim() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(36, 27, 18, 9, 10, 11);

        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of(36, 27, 18, 9, 10, 11), slots);
    }

    @Test
    void ofSlots_rejectsNegativeIndex() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(0, -1));

        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void ofSlots_rejectsIndexBeyondLargestChest() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(54));

        assertTrue(exception.getMessage().contains("54"));
    }

    @Test
    void ofSlots_rejectsDuplicateIndex() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(10, 11, 10));

        assertTrue(exception.getMessage().contains("10"));
    }

    @Test
    void ofSlots_emptyConstructs_butRequireItemSlotsRejects() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots();

        assertTrue(layout.getSlots().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> InventoryLayout.requireItemSlots(layout));
    }

    @Test
    void navSlots_defaultTo45And53() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(0);

        assertEquals(45, layout.getBackSlot());
        assertEquals(53, layout.getNextSlot());
    }

    @Test
    void slots_carryNoLetter() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(7);

        assertEquals(InventorySlot.NO_LETTER, layout.getSlots().get(0).getLetter());
    }

    @Test
    void getColumnSizes_countsItemSlotsPerColumn() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(1, 10, 3);

        assertEquals(2, layout.getColumnSizes().get(1));
        assertEquals(1, layout.getColumnSizes().get(3));
        assertFalse(layout.getColumnSizes().containsKey(0));
    }

    @Test
    void withBackSlot_returnsNewInstance_originalUnchanged() {
        OrderedSlotsLayout original = InventoryLayout.ofSlots(10, 11);
        OrderedSlotsLayout adjusted = original.withBackSlot(36);

        assertNotSame(original, adjusted);
        assertEquals(45, original.getBackSlot());
        assertEquals(36, adjusted.getBackSlot());
        assertEquals(original.getSlots(), adjusted.getSlots());
        assertEquals(original.getNextSlot(), adjusted.getNextSlot());
    }

    @Test
    void withNextSlot_returnsNewInstance_originalUnchanged() {
        OrderedSlotsLayout original = InventoryLayout.ofSlots(10, 11);
        OrderedSlotsLayout adjusted = original.withNextSlot(44);

        assertNotSame(original, adjusted);
        assertEquals(53, original.getNextSlot());
        assertEquals(44, adjusted.getNextSlot());
        assertEquals(original.getBackSlot(), adjusted.getBackSlot());
    }

    @Test
    void withBackSlot_rejectsOutOfBounds() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(54));
    }

    @Test
    void withBackSlot_rejectsItemSlotCollision() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(10));
    }

    @Test
    void withBackSlot_rejectsNextSlotCollision() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(53));
    }

    @Test
    void withNextSlot_rejectsAllCollisions() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(54));
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(10)); // item slot
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(45)); // back slot
    }
}
