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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit-order {@link Layout} backing {@link Layout#ofSlots}: the i-th given index is
 * the i-th fill position.
 */
@ApiStatus.Internal
public final class OrderedSlotsLayout implements Layout {

    private static final int MAX_SLOT_EXCLUSIVE = 6 * ROW_WIDTH;

    private final List<Integer> slots;

    /**
     * Creates a layout that fills slots in exactly the given order.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    public OrderedSlotsLayout(int... slots) {
        List<Integer> ordered = new ArrayList<>(slots.length);
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
            ordered.add(slot);
        }

        this.slots = Collections.unmodifiableList(ordered);
    }

    @Override
    public @NotNull List<Integer> slots() {
        return slots;
    }
}
