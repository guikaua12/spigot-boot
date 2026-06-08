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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResultTest {

    @Test
    void of_copiesItemsDefensively() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b"));
        PageResult<String> result = PageResult.of(items, 10);

        items.add("c");

        assertEquals(2, result.getItems().size());
        assertEquals(10, result.getTotalElements());
    }

    @Test
    void of_returnsUnmodifiableItems() {
        PageResult<String> result = PageResult.of(Arrays.asList("a"), 1);

        assertThrows(UnsupportedOperationException.class, () -> result.getItems().add("b"));
    }

    @Test
    void of_rejectsNullItems() {
        assertThrows(NullPointerException.class, () -> PageResult.of(null, 0));
    }

    @Test
    void of_rejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(Arrays.asList("a"), -1));
    }
}
