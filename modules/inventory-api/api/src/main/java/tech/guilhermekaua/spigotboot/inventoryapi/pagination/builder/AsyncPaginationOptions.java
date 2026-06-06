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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder;

import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.Objects;

/**
 * Mutable options collected by the single {@code async(...)} method of the pagination builders.
 * Mirrors the {@code configure(InventorySettings)} mutation pattern used by custom inventories.
 */
public final class AsyncPaginationOptions<T> {

    private static final int DEFAULT_CACHE_MAX_PAGES = 128;

    private AsyncPageSupplier<T> source;
    private InventoryItemSupplier loadingItem;
    private PaginationErrorCallback errorCallback;
    private Duration requestTimeout;
    private Duration cacheTtl;
    private int cacheMaxPages = DEFAULT_CACHE_MAX_PAGES;
    private boolean cacheMaxPagesSet;

    /**
     * Sets the async page supplier. Required.
     *
     * @param source loads pages on demand, not null
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> source(AsyncPageSupplier<T> source) {
        this.source = Objects.requireNonNull(source, "source is required.");
        return this;
    }

    /**
     * Sets the item rendered in every page slot while a load is in flight.
     *
     * @param loadingItem the loading placeholder supplier
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> loadingItem(InventoryItemSupplier loadingItem) {
        this.loadingItem = loadingItem;
        return this;
    }

    /**
     * Sets the callback invoked when a page load fails.
     *
     * @param errorCallback the failure callback
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> errorCallback(PaginationErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
        return this;
    }

    /**
     * Enables a per-request timeout; a request exceeding it fails with a
     * {@code TimeoutException} and follows the normal error path.
     *
     * @param requestTimeout the timeout, must be positive
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code requestTimeout} is zero or negative
     */
    public AsyncPaginationOptions<T> requestTimeout(Duration requestTimeout) {
        Objects.requireNonNull(requestTimeout, "requestTimeout is required.");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive.");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * Enables page caching: a revisit within the TTL renders from cache without calling the
     * supplier. {@code refresh()} invalidates the cache.
     *
     * @param cacheTtl entry freshness window, must be positive
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code cacheTtl} is zero or negative
     */
    public AsyncPaginationOptions<T> cacheTtl(Duration cacheTtl) {
        Objects.requireNonNull(cacheTtl, "cacheTtl is required.");
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive.");
        }
        this.cacheTtl = cacheTtl;
        return this;
    }

    /**
     * Bounds the page cache (least-recently-used eviction). Defaults to 128. Only meaningful
     * together with {@link #cacheTtl(Duration)}.
     *
     * @param cacheMaxPages the maximum number of cached pages, at least 1
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code cacheMaxPages} is below 1
     */
    public AsyncPaginationOptions<T> cacheMaxPages(int cacheMaxPages) {
        if (cacheMaxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be >= 1.");
        }
        this.cacheMaxPages = cacheMaxPages;
        this.cacheMaxPagesSet = true;
        return this;
    }

    InventoryItemSupplier getLoadingItem() {
        return loadingItem;
    }

    /**
     * Builds the configured {@link AsyncPageSource}.
     *
     * @return the page source
     * @throws NullPointerException     if no source was configured
     * @throws IllegalArgumentException if {@code cacheMaxPages} was set without {@code cacheTtl}
     */
    PageSource<T> buildPageSource() {
        Objects.requireNonNull(source, "async source is required.");
        if (cacheMaxPagesSet && cacheTtl == null) {
            throw new IllegalArgumentException("cacheMaxPages requires cacheTtl.");
        }
        return new AsyncPageSource<>(source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages,
                new BukkitSettleDispatcher());
    }
}
