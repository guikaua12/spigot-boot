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
import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

/**
 * Immutable description of one page load issued by a paginator.
 *
 * <p>{@code offset} and {@code pageSize} are the <strong>authoritative query
 * bounds</strong> — a backing store should fetch with {@code LIMIT pageSize OFFSET offset}.
 * {@code page} is informational only: scroll paginators advance one element per page, so
 * deriving the offset as {@code (page - 1) * pageSize} is wrong for them.
 */
@Getter
@RequiredArgsConstructor
public final class PageRequest {

    /**
     * The 1-indexed page being requested.
     */
    private final int page;

    /**
     * The maximum number of items the requested page can display.
     */
    private final int pageSize;

    /**
     * The global element offset of the first item on the requested page.
     */
    private final int offset;

    /**
     * The viewer the page is being loaded for. Never {@code null} for requests dispatched by the
     * built-in paginations: navigation issued before {@code init(Viewer)} only records the target
     * page, and {@code init} dispatches the load for it.
     */
    private final Viewer viewer;
}
