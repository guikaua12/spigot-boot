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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EagerPageSourceTest {

    private static PageRequest request(int page, int pageSize, int offset) {
        return new PageRequest(page, pageSize, offset, null, null);
    }

    private static <T> PageResult<T> settleOf(EagerPageSource<T> source, PageRequest request) {
        AtomicReference<PageResult<T>> settled = new AtomicReference<>();
        source.request(request, (result, error) -> settled.set(result));
        return settled.get();
    }

    @Test
    void request_settlesSynchronouslyWithSlice() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3, 4, 5));

        PageResult<Integer> result = settleOf(source, request(2, 2, 2));

        assertEquals(Arrays.asList(3, 4), result.getItems());
        assertEquals(5, result.getTotalElements());
    }

    @Test
    void request_partialLastPage_clampsEnd() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3, 4, 5));

        PageResult<Integer> result = settleOf(source, request(2, 3, 3));

        assertEquals(Arrays.asList(4, 5), result.getItems());
    }

    @Test
    void request_offsetBeyondSize_returnsEmptyItemsWithRealTotal() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3));

        PageResult<Integer> result = settleOf(source, request(5, 3, 12));

        assertTrue(result.getItems().isEmpty());
        assertEquals(3, result.getTotalElements());
    }

    @Test
    void request_offsetEqualToSize_returnsEmptyItems() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3));

        assertTrue(settleOf(source, request(2, 3, 3)).getItems().isEmpty());
    }

    @Test
    void empty_hasNoElementsAndKnownTotals() {
        EagerPageSource<Integer> source = EagerPageSource.empty();

        assertEquals(0, source.totalElements());
        assertTrue(source.totalsKnown());
        assertFalse(source.isLoading());
        assertNull(source.lastError());
        assertTrue(source.elements().isEmpty());
    }

    @Test
    void constructor_copiesListDefensively() {
        List<Integer> backing = new ArrayList<>(Arrays.asList(1, 2));
        EagerPageSource<Integer> source = new EagerPageSource<>(backing);

        backing.add(3);

        assertEquals(2, source.totalElements());
    }
}
