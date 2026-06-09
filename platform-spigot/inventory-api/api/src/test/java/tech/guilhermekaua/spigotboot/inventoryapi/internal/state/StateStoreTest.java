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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @Test
    void get_withoutSet_returnsNull() {
        StateStore store = new StateStore(3);

        assertNull(store.get(0));
        assertNull(store.get(2));
    }

    @Test
    void setAndGet_roundTripById() {
        StateStore store = new StateStore(3);

        store.set(1, "value");
        store.set(2, 42);

        assertEquals("value", store.get(1));
        assertEquals(42, store.get(2));
        assertNull(store.get(0));
    }

    @Test
    void set_overwritesPreviousValue() {
        StateStore store = new StateStore(1);

        store.set(0, "first");
        store.set(0, "second");

        assertEquals("second", store.get(0));
    }

    @Test
    void markDirty_setsHasDirty() {
        StateStore store = new StateStore(2);
        assertFalse(store.hasDirty());

        store.markDirty(1);

        assertTrue(store.hasDirty());
    }

    @Test
    void drainDirty_returnsMarkedIdsAndClears() {
        StateStore store = new StateStore(4);
        store.markDirty(0);
        store.markDirty(3);
        // duplicate marks coalesce
        store.markDirty(0);

        Set<Integer> drained = store.drainDirty();

        assertEquals(new HashSet<>(Arrays.asList(0, 3)), drained);
        assertFalse(store.hasDirty());
        assertTrue(store.drainDirty().isEmpty());
    }
}
