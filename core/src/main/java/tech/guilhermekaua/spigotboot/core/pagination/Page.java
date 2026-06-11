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
package tech.guilhermekaua.spigotboot.core.pagination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Page<T> {
    private final List<T> content;
    private final long totalElements;
    private final int pageNumber;
    private final int pageSize;

    public Page(List<T> content, long totalElements, int pageNumber, int pageSize) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }

        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }

        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be >= 0");
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }

        this.content = Collections.unmodifiableList(new ArrayList<>(content));
        this.totalElements = totalElements;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public List<T> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalPages() {
        if (totalElements == 0) {
            return 0;
        }

        return (totalElements + pageSize - 1L) / pageSize;
    }

    public boolean hasNext() {
        return pageNumber + 1L < getTotalPages();
    }

    public boolean hasPrevious() {
        return pageNumber > 0;
    }
}
