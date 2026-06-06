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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous {@link PageSource} backed by an {@link AsyncPageSupplier}. Owns all async
 * machinery: monotonically increasing request ids with at-most-once settle semantics, stale
 * response discarding, optional request timeouts and an optional TTL-bounded page cache.
 *
 * <p>Thread-safety: every state transition happens inside one internal lock; the id check and the
 * state write are atomic. Asynchronously completed settles are routed through the configured
 * {@link SettleDispatcher}; synchronous settles (cache hits, immediate failures) run on the
 * calling thread.
 */
public final class AsyncPageSource<T> implements PageSource<T> {

    private static final Logger LOGGER = Logger.getLogger(AsyncPageSource.class.getName());
    private static volatile ScheduledExecutorService sharedTimeoutScheduler;

    private final AsyncPageSupplier<T> supplier;
    private final PaginationErrorCallback errorCallback;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final SettleDispatcher settleDispatcher;
    private final ScheduledExecutorService timeoutScheduler;

    private final Object lock = new Object();
    private final AtomicLong requestIds = new AtomicLong();
    private final Map<String, CacheEntry<T>> cache;

    // all guarded by lock
    private long settledId;
    private boolean loading;
    private Throwable lastError;
    private List<T> elements = Collections.emptyList();
    private int totalElements;
    private boolean totalsKnown;

    /**
     * Creates an async page source.
     *
     * @param supplier         loads pages, not null
     * @param errorCallback    invoked on failed loads, may be null
     * @param requestTimeout   per-request timeout, may be null to disable
     * @param cacheTtl         cache entry freshness window, may be null to disable caching
     * @param cacheMaxPages    cache LRU bound, must be {@code >= 1}
     * @param settleDispatcher routes asynchronously completed settles, not null
     * @throws NullPointerException     if {@code supplier} or {@code settleDispatcher} is null
     * @throws IllegalArgumentException if {@code cacheMaxPages < 1}
     */
    public AsyncPageSource(AsyncPageSupplier<T> supplier,
                           PaginationErrorCallback errorCallback,
                           Duration requestTimeout,
                           Duration cacheTtl,
                           int cacheMaxPages,
                           SettleDispatcher settleDispatcher) {
        this(supplier, errorCallback, requestTimeout, cacheTtl, cacheMaxPages, settleDispatcher, null);
    }

    AsyncPageSource(AsyncPageSupplier<T> supplier,
                    PaginationErrorCallback errorCallback,
                    Duration requestTimeout,
                    Duration cacheTtl,
                    int cacheMaxPages,
                    SettleDispatcher settleDispatcher,
                    ScheduledExecutorService timeoutScheduler) {
        Objects.requireNonNull(supplier, "supplier is required.");
        Objects.requireNonNull(settleDispatcher, "settleDispatcher is required.");
        if (cacheMaxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be >= 1.");
        }
        this.supplier = supplier;
        this.errorCallback = errorCallback;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.settleDispatcher = settleDispatcher;
        this.timeoutScheduler = timeoutScheduler;
        this.cache = new LinkedHashMap<String, CacheEntry<T>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<T>> eldest) {
                return size() > cacheMaxPages;
            }
        };
    }

    @Override
    public void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle) {
        Objects.requireNonNull(request, "request is required.");
        Objects.requireNonNull(onSettle, "onSettle is required.");

        long id;
        PageResult<T> cached;
        synchronized (lock) {
            // the id must be claimed under the lock: claimed outside it, an older claimant
            // could mark loading=true after the newest request already settled, and its own
            // discarded settle would then strand the flag at true forever
            id = requestIds.incrementAndGet();
            loading = true;
            lastError = null;
            cached = cacheTtl == null ? null : cachedResult(cacheKey(request));
        }
        if (cached != null) {
            settle(id, request, cached, null, onSettle, true);
            return;
        }

        CompletableFuture<PageResult<T>> future;
        try {
            future = supplier.load(request);
        } catch (Throwable t) {
            settle(id, request, null, t, onSettle, true);
            return;
        }
        if (future == null) {
            settle(id, request, null,
                    new IllegalStateException("AsyncPageSupplier returned a null future."), onSettle, true);
            return;
        }

        ScheduledFuture<?> timeoutTask = scheduleTimeout(id, request, future, onSettle);
        future.whenComplete((result, error) -> {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
            }
            settle(id, request, result, unwrap(error), onSettle, false);
        });
    }

    private void settle(long id, PageRequest request, PageResult<T> result, Throwable error,
                        BiConsumer<PageResult<T>, Throwable> onSettle, boolean inline) {
        Runnable task = () -> applyAndDeliver(id, request, result, error, onSettle);
        if (inline) {
            task.run();
        } else {
            settleDispatcher.dispatch(request, task);
        }
    }

    private void applyAndDeliver(long id, PageRequest request, PageResult<T> rawResult,
                                 Throwable error, BiConsumer<PageResult<T>, Throwable> onSettle) {
        PageResult<T> result = rawResult;
        if (error == null && result == null) {
            error = new IllegalStateException("AsyncPageSupplier completed with a null result.");
        }
        synchronized (lock) {
            // superseded requests must not settle, mutate state, or clear the newer
            // request's loading flag; an already-settled id (timeout vs late completion)
            // must not settle twice
            if (id != requestIds.get() || id <= settledId) {
                return;
            }
            settledId = id;
            loading = false;
            if (error == null) {
                result = truncateIfOversized(request, result);
                elements = result.getItems();
                totalElements = result.getTotalElements();
                totalsKnown = true;
                if (cacheTtl != null) {
                    cache.put(cacheKey(request), new CacheEntry<>(result, System.nanoTime()));
                }
            } else {
                lastError = error;
            }
        }
        if (error != null && errorCallback != null) {
            try {
                errorCallback.onError(request, error);
            } catch (Throwable callbackError) {
                LOGGER.log(Level.WARNING, "PaginationErrorCallback threw while handling a page load failure.", callbackError);
            }
        }
        if (error != null) {
            onSettle.accept(null, error);
        } else {
            onSettle.accept(result, null);
        }
    }

    private PageResult<T> truncateIfOversized(PageRequest request, PageResult<T> result) {
        if (result.getItems().size() <= request.getPageSize()) {
            return result;
        }
        LOGGER.warning("AsyncPageSupplier returned " + result.getItems().size()
                + " items for a page of size " + request.getPageSize() + "; truncating.");
        return PageResult.of(new ArrayList<>(result.getItems().subList(0, request.getPageSize())),
                result.getTotalElements());
    }

    private ScheduledFuture<?> scheduleTimeout(long id, PageRequest request,
                                               CompletableFuture<PageResult<T>> future,
                                               BiConsumer<PageResult<T>, Throwable> onSettle) {
        if (requestTimeout == null) {
            return null;
        }
        return timeoutScheduler().schedule(() -> {
            // the TimeoutException settle is dispatched before cancel(true) triggers the
            // future's CancellationException settle for the same id; FIFO dispatchers (inline,
            // Bukkit scheduler) therefore always surface the TimeoutException, with the
            // cancellation settle discarded by the at-most-once check. A dispatcher that
            // reorders tasks could surface CancellationException instead.
            settle(id, request, null,
                    new TimeoutException("page request timed out after " + requestTimeout), onSettle, false);
            future.cancel(true);
        }, requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private ScheduledExecutorService timeoutScheduler() {
        if (timeoutScheduler != null) {
            return timeoutScheduler;
        }
        ScheduledExecutorService shared = sharedTimeoutScheduler;
        if (shared == null) {
            synchronized (AsyncPageSource.class) {
                shared = sharedTimeoutScheduler;
                if (shared == null) {
                    ThreadFactory factory = runnable -> {
                        Thread thread = new Thread(runnable, "spigotboot-inventoryapi-pagination-timeout");
                        thread.setDaemon(true);
                        return thread;
                    };
                    shared = Executors.newSingleThreadScheduledExecutor(factory);
                    sharedTimeoutScheduler = shared;
                }
            }
        }
        return shared;
    }

    private PageResult<T> cachedResult(String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.nanoTime() - entry.createdNanos > cacheTtl.toNanos()) {
            cache.remove(key);
            return null;
        }
        return entry.result;
    }

    private static String cacheKey(PageRequest request) {
        return request.getOffset() + ":" + request.getPageSize();
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    @Override
    public int totalElements() {
        synchronized (lock) {
            return totalElements;
        }
    }

    @Override
    public boolean totalsKnown() {
        synchronized (lock) {
            return totalsKnown;
        }
    }

    @Override
    public boolean isLoading() {
        synchronized (lock) {
            return loading;
        }
    }

    @Override
    public Throwable lastError() {
        synchronized (lock) {
            return lastError;
        }
    }

    @Override
    public List<T> elements() {
        synchronized (lock) {
            return elements;
        }
    }

    @Override
    public void invalidate() {
        synchronized (lock) {
            cache.clear();
        }
    }

    private static final class CacheEntry<T> {
        private final PageResult<T> result;
        private final long createdNanos;

        private CacheEntry(PageResult<T> result, long createdNanos) {
            this.result = result;
            this.createdNanos = createdNanos;
        }
    }
}
