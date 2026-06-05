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

import lombok.Getter;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layout defined directly from explicit slot indices: the i-th index given to the constructor (or
 * to {@link InventoryLayout#ofSlots(int...)}) is the i-th item placed on a page. Use it for fill
 * orders the grid letter vocabulary cannot express, such as fully custom orders over more than 26
 * slots.
 *
 * <p>Instances are immutable; {@link #withBackSlot(int)} and {@link #withNextSlot(int)} return
 * adjusted copies. Navigation slots default to 45 and 53, matching {@link GridLayout}.
 */
@Getter
public final class OrderedSlotsLayout implements InventoryLayout {

    private static final int MAX_SLOT_EXCLUSIVE = 6 * INVENTORY_ROW_WIDTH;
    private static final int DEFAULT_BACK_SLOT = 45;
    private static final int DEFAULT_NEXT_SLOT = 53;

    private final List<InventorySlot> slots;
    private final int backSlot, nextSlot;

    /**
     * Creates a layout that places items in exactly the order of the given slot indices.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    public OrderedSlotsLayout(int... slots) {
        this(toInventorySlots(slots), DEFAULT_BACK_SLOT, DEFAULT_NEXT_SLOT);
    }

    private OrderedSlotsLayout(List<InventorySlot> slots, int backSlot, int nextSlot) {
        this.slots = slots;
        this.backSlot = backSlot;
        this.nextSlot = nextSlot;
    }

    /**
     * Returns a copy of this layout with the back-navigation slot replaced.
     *
     * @param backSlot the new back slot; must be within 0-53 and collide with neither an item
     *                 slot nor the next slot
     * @return a new layout with the back slot replaced; this instance is unchanged
     * @throws IllegalArgumentException if {@code backSlot} is out of bounds or collides
     */
    public OrderedSlotsLayout withBackSlot(int backSlot) {
        validateNavSlot(backSlot, this.nextSlot, "backSlot");
        return new OrderedSlotsLayout(this.slots, backSlot, this.nextSlot);
    }

    /**
     * Returns a copy of this layout with the next-navigation slot replaced.
     *
     * @param nextSlot the new next slot; must be within 0-53 and collide with neither an item
     *                 slot nor the back slot
     * @return a new layout with the next slot replaced; this instance is unchanged
     * @throws IllegalArgumentException if {@code nextSlot} is out of bounds or collides
     */
    public OrderedSlotsLayout withNextSlot(int nextSlot) {
        validateNavSlot(nextSlot, this.backSlot, "nextSlot");
        return new OrderedSlotsLayout(this.slots, this.backSlot, nextSlot);
    }

    private void validateNavSlot(int slot, int otherNavSlot, String name) {
        if (slot < 0 || slot >= MAX_SLOT_EXCLUSIVE) {
            throw new IllegalArgumentException(
                    name + " " + slot + " is out of bounds (0-" + (MAX_SLOT_EXCLUSIVE - 1) + ")"
            );
        }
        if (slot == otherNavSlot) {
            throw new IllegalArgumentException(
                    name + " " + slot + " collides with the other navigation slot"
            );
        }
        for (InventorySlot itemSlot : this.slots) {
            if (itemSlot.getSlot() == slot) {
                throw new IllegalArgumentException(name + " " + slot + " collides with an item slot");
            }
        }
    }

    private static List<InventorySlot> toInventorySlots(int[] slots) {
        List<InventorySlot> inventorySlots = new ArrayList<>(slots.length);
        Set<Integer> seen = new HashSet<>();

        for (int slot : slots) {
            if (slot < 0 || slot >= MAX_SLOT_EXCLUSIVE) {
                throw new IllegalArgumentException(
                        "slot index " + slot + " is out of bounds (0-" + (MAX_SLOT_EXCLUSIVE - 1) + ")"
                );
            }
            if (!seen.add(slot)) {
                throw new IllegalArgumentException("duplicate slot index " + slot);
            }
            inventorySlots.add(new InventorySlot(slot));
        }

        return Collections.unmodifiableList(inventorySlots);
    }
}
