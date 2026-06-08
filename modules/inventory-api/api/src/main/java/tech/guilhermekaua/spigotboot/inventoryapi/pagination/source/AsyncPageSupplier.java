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

import java.util.concurrent.CompletableFuture;

/**
 * Loads one page of elements asynchronously, typically from a database.
 *
 * <p>Called on every page change unless a fresh cached entry exists. The returned future may
 * complete on any thread; the framework applies the result on the appropriate thread for the
 * owning inventory. Return at most {@code pageSize} items — oversized results
 * are truncated with a warning.
 *
 * <p>Returning {@code null} is treated as a failed load. A future that never completes leaves the
 * paginator loading until a superseding navigation or the configured request timeout recovers it.
 */
@FunctionalInterface
public interface AsyncPageSupplier<T> {

    /**
     * Loads the requested page.
     *
     * @param request the page being requested, never null
     * @return a future completing with the page's items and total element count
     */
    CompletableFuture<PageResult<T>> load(PageRequest request);
}
