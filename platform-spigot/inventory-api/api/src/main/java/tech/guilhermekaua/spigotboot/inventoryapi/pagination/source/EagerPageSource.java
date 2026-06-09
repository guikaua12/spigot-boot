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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * In-memory {@link PageSource}: serves slices of a fixed list and settles every request
 * synchronously on the calling thread. Totals are always known, loading is never observable and
 * requests never fail.
 */
public final class EagerPageSource<T> implements PageSource<T> {

    private final List<T> elements;

    /**
     * Creates an eager source over a defensive copy of the given list.
     *
     * @param elements the backing elements, not null
     * @throws NullPointerException if {@code elements} is null
     */
    public EagerPageSource(List<T> elements) {
        Objects.requireNonNull(elements, "elements is required.");
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /**
     * @param <T> the element type
     * @return an eager source over an empty list
     */
    public static <T> EagerPageSource<T> empty() {
        return new EagerPageSource<>(Collections.emptyList());
    }

    @Override
    public void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle) {
        // clamp BOTH ends: a stale currentPage left over from a larger previous source must
        // yield an empty page, not an IndexOutOfBoundsException
        int from = Math.min(Math.max(0, request.getOffset()), elements.size());
        int to = Math.min(from + request.getPageSize(), elements.size());
        onSettle.accept(PageResult.of(elements.subList(from, to), elements.size()), null);
    }

    @Override
    public int totalElements() {
        return elements.size();
    }

    @Override
    public boolean totalsKnown() {
        return true;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public Throwable lastError() {
        return null;
    }

    @Override
    public List<T> elements() {
        return elements;
    }
}
