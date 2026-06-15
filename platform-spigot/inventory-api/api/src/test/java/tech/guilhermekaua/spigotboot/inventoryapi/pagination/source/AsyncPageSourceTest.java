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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPageSourceTest {

    private static PageRequest request(int page) {
        return new PageRequest(page, 3, (page - 1) * 3, null, null, null);
    }

    private static <T> AsyncPageSource<T> source(AsyncPageSupplier<T> supplier) {
        return new AsyncPageSource<>(supplier, null, null, null, 128, SettleDispatcher.inline());
    }

    @Test
    void completedFuture_settlesInlineAndPublishesState() {
        AsyncPageSource<Integer> source = source(req ->
                CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3), 9)));
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result));

        assertNotNull(settled.get());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
        assertEquals(9, source.totalElements());
        assertTrue(source.totalsKnown());
        assertFalse(source.isLoading());
        assertNull(source.lastError());
    }

    @Test
    void pendingFuture_reportsLoadingUntilCompletion() {
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = source(req -> future);
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result));

        assertTrue(source.isLoading());
        assertFalse(source.totalsKnown());
        assertNull(settled.get());

        future.complete(PageResult.of(Arrays.asList(1), 1));

        assertFalse(source.isLoading());
        assertEquals(Arrays.asList(1), settled.get().getItems());
    }

    @Test
    void staleCompletion_afterNewerRequest_isDiscardedEntirely() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });
        AtomicInteger settleCount = new AtomicInteger();

        source.request(request(1), (result, error) -> settleCount.incrementAndGet());
        source.request(request(2), (result, error) -> settleCount.incrementAndGet());

        futures.get(1).complete(PageResult.of(Arrays.asList(4, 5, 6), 9));
        assertEquals(Arrays.asList(4, 5, 6), source.elements());
        assertFalse(source.isLoading());

        futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9));
        assertEquals(Arrays.asList(4, 5, 6), source.elements());
        assertFalse(source.isLoading());
        assertEquals(1, settleCount.get());
    }

    @Test
    void supersededRequest_doesNotClearLoadingOfNewerInFlightRequest() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });

        source.request(request(1), (result, error) -> { });
        source.request(request(2), (result, error) -> { });

        futures.get(0).complete(PageResult.of(Arrays.asList(1), 9));

        assertTrue(source.isLoading(), "newer request still in flight; stale settle must not clear loading");
    }

    @Test
    void failedFuture_capturesErrorAndKeepsLastGoodElements() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> { });
        futures.get(0).complete(PageResult.of(Arrays.asList(1, 2), 6));

        source.request(request(2), (result, error) -> settledError.set(error));
        RuntimeException boom = new RuntimeException("db down");
        futures.get(1).completeExceptionally(boom);

        assertEquals(boom, settledError.get());
        assertEquals(Arrays.asList(1, 2), source.elements(), "last good elements survive a failure");
        assertFalse(source.isLoading());
        assertNotNull(source.lastError());
    }

    @Test
    void newRequest_clearsLastError() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = source(req -> calls.incrementAndGet() == 1
                ? failedFuture(new RuntimeException("boom"))
                : CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1)));

        source.request(request(1), (result, error) -> { });
        assertNotNull(source.lastError());

        source.request(request(1), (result, error) -> { });
        assertNull(source.lastError());
    }

    @Test
    void nullFuture_settlesAsFailure() {
        AsyncPageSource<Integer> source = source(req -> null);
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));

        assertInstanceOf(IllegalStateException.class, settledError.get());
        assertFalse(source.isLoading());
        assertNotNull(source.lastError());
    }

    @Test
    void synchronousSupplierThrow_settlesAsFailure() {
        RuntimeException boom = new RuntimeException("sync boom");
        AsyncPageSource<Integer> source = source(req -> {
            throw boom;
        });
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));

        assertEquals(boom, settledError.get());
        assertFalse(source.isLoading());
    }

    @Test
    void errorCallback_isInvokedOnFailure() {
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        PaginationErrorCallback callback = (request, error) -> callbackError.set(error);
        AsyncPageSource<Integer> source = new AsyncPageSource<>(
                req -> failedFuture(new RuntimeException("boom")),
                callback, null, null, 128, SettleDispatcher.inline());

        source.request(request(1), (result, error) -> { });

        assertNotNull(callbackError.get());
    }

    @Test
    void oversizedResult_isTruncatedToPageSize() {
        AsyncPageSource<Integer> source = source(req ->
                CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 5)));
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result));

        assertEquals(Arrays.asList(1, 2, 3), settled.get().getItems());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void racingRequests_fromTwoThreads_latestDispatchAlwaysWins() throws Exception {
        for (int round = 0; round < 50; round++) {
            List<CompletableFuture<PageResult<Integer>>> futures =
                    Collections.synchronizedList(new ArrayList<>());
            AsyncPageSource<Integer> source = source(req -> {
                CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
                futures.add(f);
                return f;
            });

            CountDownLatch start = new CountDownLatch(1);
            Runnable dispatch = () -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                source.request(request(1), (result, error) -> { });
            };
            Thread t1 = new Thread(dispatch);
            Thread t2 = new Thread(dispatch);
            t1.start();
            t2.start();
            start.countDown();
            t1.join(5000);
            t2.join(5000);

            assertEquals(2, futures.size());
            futures.get(0).complete(PageResult.of(Arrays.asList(10), 1));
            futures.get(1).complete(PageResult.of(Arrays.asList(20), 1));

            assertFalse(source.isLoading(), "round " + round + ": loading must clear once the latest request settles");
        }
    }

    @Test
    void racingRequests_staleDispatchCannotStrandLoadingFlag() throws Exception {
        for (int round = 0; round < 100; round++) {
            AsyncPageSource<Integer> source = source(req ->
                    CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1)));
            Object lock = stateLockOf(source);

            Thread first;
            Thread second;
            // hold the internal state lock so both request() calls park right before their
            // state writes; once released, the later dispatch may run to completion before
            // the earlier one resumes -- the earlier dispatch must not strand loading=true
            synchronized (lock) {
                first = startRequestThread(source);
                awaitBlockedOnMonitor(first);
                second = startRequestThread(source);
                awaitBlockedOnMonitor(second);
            }
            first.join(5000);
            second.join(5000);

            assertFalse(source.isLoading(),
                    "round " + round + ": every dispatch settled, loading must be false");
        }
    }

    private static Thread startRequestThread(AsyncPageSource<Integer> source) {
        Thread thread = new Thread(() -> source.request(request(1), (result, error) -> { }));
        thread.start();
        return thread;
    }

    private static void awaitBlockedOnMonitor(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (thread.getState() != Thread.State.BLOCKED) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("request thread never blocked on the state lock");
            }
            Thread.sleep(1);
        }
    }

    private static Object stateLockOf(AsyncPageSource<?> source) throws Exception {
        Field lockField = AsyncPageSource.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        return lockField.get(source);
    }

    /**
     * Deterministic scheduler: captures scheduled tasks so tests fire timeouts by hand.
     */
    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        private final List<Runnable> scheduled = new ArrayList<>();
        private final List<ScheduledFutureStub> futures = new ArrayList<>();

        private ManualScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled.add(command);
            ScheduledFutureStub stub = new ScheduledFutureStub();
            futures.add(stub);
            return stub;
        }

        private void fire(int index) {
            scheduled.get(index).run();
        }

        private static final class ScheduledFutureStub implements ScheduledFuture<Object> {
            private boolean cancelled;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled = true;
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(java.util.concurrent.Delayed o) {
                return 0;
            }

            @Override
            public boolean isDone() {
                return cancelled;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }
        }
    }

    private static <T> AsyncPageSource<T> timeoutSource(AsyncPageSupplier<T> supplier, ManualScheduler scheduler) {
        return new AsyncPageSource<>(supplier, null, java.time.Duration.ofSeconds(5), null, 128,
                SettleDispatcher.inline(), scheduler);
    }

    @Test
    void timeout_settlesAsTimeoutExceptionFailure() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));
        scheduler.fire(0);

        assertInstanceOf(TimeoutException.class, settledError.get());
        assertFalse(source.isLoading());
        assertTrue(future.isCancelled(), "best-effort cancel must be attempted");
    }

    @Test
    void lateCompletion_afterTimeout_isDiscarded() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);
        AtomicInteger settleCount = new AtomicInteger();

        source.request(request(1), (result, error) -> settleCount.incrementAndGet());
        scheduler.fire(0);
        future.obtrudeValue(PageResult.of(Arrays.asList(1, 2), 6));

        assertEquals(1, settleCount.get(), "timeout already settled this id; late completion must be discarded");
        assertTrue(source.elements().isEmpty(), "late result must not be applied");
    }

    @Test
    void completionBeforeTimeout_cancelsTheTimeoutTask() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);

        source.request(request(1), (result, error) -> { });
        future.complete(PageResult.of(Arrays.asList(1), 1));

        assertTrue(scheduler.futures.get(0).isCancelled());
        scheduler.fire(0);
        assertEquals(Arrays.asList(1), source.elements());
        assertNull(source.lastError());
    }

    private static <T> AsyncPageSource<T> cachedSource(AsyncPageSupplier<T> supplier, int maxPages) {
        return new AsyncPageSource<>(supplier, null, null, java.time.Duration.ofMinutes(5), maxPages,
                SettleDispatcher.inline());
    }

    @Test
    void cacheHit_skipsSupplierAndSettlesSynchronously() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3), 9));
        }, 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(1, calls.get(), "second request for the same offset:pageSize must hit the cache");
        assertFalse(source.isLoading());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void cacheHit_updatesTotalsToo() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req ->
                CompletableFuture.completedFuture(PageResult.of(
                        Arrays.asList(10 * calls.incrementAndGet()), 7)), 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(2), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(Arrays.asList(10), source.elements());
        assertEquals(7, source.totalElements());
    }

    @Test
    void failures_areNeverCached() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> calls.incrementAndGet() == 1
                ? failedFuture(new RuntimeException("boom"))
                : CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1)), 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get(), "a failure must not populate the cache");
        assertEquals(Arrays.asList(1), source.elements());
    }

    @Test
    void invalidate_clearsCache() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1));
        }, 128);

        source.request(request(1), (result, error) -> { });
        source.invalidate();
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get());
    }

    @Test
    void cache_evictsLeastRecentlyUsedBeyondMaxPages() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(req.getPage()), 9));
        }, 2);

        source.request(request(1), (result, error) -> { });
        source.request(request(2), (result, error) -> { });
        source.request(request(3), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(4, calls.get());
    }

    @Test
    void expiredEntry_missesAndRefetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = new AsyncPageSource<>(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1));
        }, null, null, java.time.Duration.ofMillis(20), 128, SettleDispatcher.inline());

        source.request(request(1), (result, error) -> { });
        Thread.sleep(60);
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get(), "an entry older than the TTL must not be served");
    }

    @Test
    void shutdownSharedTimeoutScheduler_lazilyRecreatesForNewTimeouts() throws Exception {
        // force the shared scheduler into existence (no test-scheduler seam), then kill it
        AsyncPageSource<Integer> first = new AsyncPageSource<>(req -> new CompletableFuture<>(),
                null, Duration.ofMillis(30), null, 128, SettleDispatcher.inline());
        CountDownLatch firstSettled = new CountDownLatch(1);
        first.request(request(1), (result, error) -> firstSettled.countDown());
        assertTrue(firstSettled.await(2, TimeUnit.SECONDS),
                "the shared scheduler must fire the priming timeout");

        AsyncPageSource.shutdownSharedTimeoutScheduler();

        CountDownLatch settled = new CountDownLatch(1);
        AtomicReference<Throwable> settledError = new AtomicReference<>();
        AsyncPageSource<Integer> source = new AsyncPageSource<>(req -> new CompletableFuture<>(),
                null, Duration.ofMillis(30), null, 128, SettleDispatcher.inline());
        source.request(request(1), (result, error) -> {
            settledError.set(error);
            settled.countDown();
        });

        assertTrue(settled.await(2, TimeUnit.SECONDS),
                "lazy init must recreate the shared scheduler after shutdown");
        assertInstanceOf(TimeoutException.class, settledError.get());
    }

    @Test
    void shutdownSharedTimeoutScheduler_isIdempotent() {
        assertDoesNotThrow(() -> {
            AsyncPageSource.shutdownSharedTimeoutScheduler();
            AsyncPageSource.shutdownSharedTimeoutScheduler();
        });
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
