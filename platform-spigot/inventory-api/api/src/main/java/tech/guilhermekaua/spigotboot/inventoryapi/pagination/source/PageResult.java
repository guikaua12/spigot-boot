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

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of one page load: the page's items plus the total number of elements in the
 * backing store, which drives {@code getTotalPages()} and navigation clamping.
 */
@Getter
public final class PageResult<T> {

    /**
     * The items of the loaded page, as an unmodifiable defensive copy.
     */
    private final List<T> items;

    /**
     * The total number of elements in the backing store.
     */
    private final int totalElements;

    private PageResult(List<T> items, int totalElements) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.totalElements = totalElements;
    }

    /**
     * Creates a page result.
     *
     * @param items         the page's items; copied defensively, must contain at most the
     *                      requested page size (oversized results are truncated with a warning)
     * @param totalElements the total number of elements in the backing store
     * @param <T>           the element type
     * @return the immutable result
     * @throws NullPointerException     if {@code items} is null
     * @throws IllegalArgumentException if {@code totalElements} is negative
     */
    public static <T> PageResult<T> of(List<T> items, int totalElements) {
        Objects.requireNonNull(items, "items is required.");
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements cannot be negative.");
        }
        return new PageResult<>(items, totalElements);
    }
}
