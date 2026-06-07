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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateImplTest {

    private static final class OwnerView extends View {
    }

    /**
     * Minimal context backed by a real {@link StateStore}; pins the seam that
     * {@code AbstractViewContext} implements in the internal context layer.
     */
    private static final class FakeViewContext implements ViewContext, StateBackedContext {

        private final View owner;
        private final StateStore store;
        private boolean active = true;

        FakeViewContext(View owner, StateStore store) {
            this.owner = owner;
            this.store = store;
        }

        void deactivate() {
            active = false;
        }

        @Override
        public @NotNull StateStore stateStore() {
            return store;
        }

        @Override
        public @NotNull View owner() {
            return owner;
        }

        @Override
        public boolean contextActive() {
            return active;
        }

        @Override
        public @NotNull View view() {
            return owner;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        // remaining ViewContext methods are not exercised by state tokens
        @Override
        public @NotNull Player player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull UUID playerId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ViewConfig config() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Plugin plugin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ViewArguments arguments() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Inventory inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTitle(@NotNull String title) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void openView(@NotNull Class<? extends View> target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void mutableState_setGetRoundTripPerContext() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        assertEquals("initial", token.get(context));
        token.set(context, "changed");
        assertEquals("changed", token.get(context));
    }

    @Test
    void mutableState_explicitNullIsNotReinitialized() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.set(context, null);

        assertNull(token.get(context));
    }

    @Test
    void mutableState_isolatesValuesPerContext() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext first = new FakeViewContext(view, new StateStore(view.tokenTable().size()));
        FakeViewContext second = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.set(first, "first-value");

        assertEquals("first-value", token.get(first));
        assertEquals("initial", token.get(second));
    }

    @Test
    void mutableState_setMarksTokenDirty() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        StateStore store = new StateStore(view.tokenTable().size());
        FakeViewContext context = new FakeViewContext(view, store);

        assertFalse(store.hasDirty());
        token.set(context, "changed");

        assertTrue(store.hasDirty());
        assertEquals(Collections.singleton(token.id()), store.drainDirty());
    }

    @Test
    void mutableState_updateAppliesFunctionToCurrentValue() {
        OwnerView view = new OwnerView();
        MutableStateImpl<Integer> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> 0);
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.update(context, value -> value + 1);
        token.update(context, value -> value + 1);

        assertEquals(2, token.get(context));
    }

    @Test
    void foreignOwnersContext_throwsStaleContextException() {
        OwnerView owner = new OwnerView();
        OwnerView foreign = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(owner, owner.tokenTable(), ctx -> "initial");
        FakeViewContext foreignContext = new FakeViewContext(foreign, new StateStore(1));

        assertThrows(StaleContextException.class, () -> token.get(foreignContext));
        assertThrows(StaleContextException.class, () -> token.set(foreignContext, "x"));
    }

    @Test
    void closedContext_throwsStaleContextException() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> mutable = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        LazyStateImpl<String> lazy = new LazyStateImpl<>(view, view.tokenTable(), ctx -> "lazy");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        context.deactivate();

        assertThrows(StaleContextException.class, () -> mutable.get(context));
        assertThrows(StaleContextException.class, () -> mutable.set(context, "x"));
        assertThrows(StaleContextException.class, () -> lazy.get(context));
    }

    @Test
    void lazyState_computesOncePerContext() {
        OwnerView view = new OwnerView();
        AtomicInteger calls = new AtomicInteger();
        LazyStateImpl<String> lazy = new LazyStateImpl<>(view, view.tokenTable(),
                ctx -> "v" + calls.incrementAndGet());
        FakeViewContext first = new FakeViewContext(view, new StateStore(view.tokenTable().size()));
        FakeViewContext second = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        assertEquals("v1", lazy.get(first));
        assertEquals("v1", lazy.get(first));
        assertEquals(1, calls.get());

        assertEquals("v2", lazy.get(second));
        assertEquals(2, calls.get());
    }

    @Test
    void initialState_returnsNullUntilSet_thenRoundTrips() {
        OwnerView view = new OwnerView();
        InitialStateImpl<Integer> token = new InitialStateImpl<>(view, view.tokenTable(), "count", Integer.class);
        StateStore store = new StateStore(view.tokenTable().size());
        FakeViewContext context = new FakeViewContext(view, store);

        assertNull(token.get(context));
        assertEquals("count", token.key());
        assertSame(Integer.class, token.type());

        token.set(context, 5);

        assertEquals(5, token.get(context));
        assertEquals(Collections.singleton(token.id()), store.drainDirty());
    }

    @Test
    void sharedState_getSetUpdateRoundTrip() {
        OwnerView view = new OwnerView();
        SharedStateImpl<Integer> shared = new SharedStateImpl<>(view, view.tokenTable(), 0);

        assertEquals(0, shared.get());
        shared.set(10);
        assertEquals(10, shared.get());
        shared.update(value -> value + 5);
        assertEquals(15, shared.get());
    }

    @Test
    void sharedState_setAndUpdateInvokeFlushHook() {
        OwnerView view = new OwnerView();
        SharedStateImpl<String> shared = new SharedStateImpl<>(view, view.tokenTable(), "a");
        AtomicInteger flushes = new AtomicInteger();
        shared.flushHook = flushes::incrementAndGet;

        shared.set("b");
        assertEquals(1, flushes.get());

        shared.update(value -> value + "c");
        assertEquals(2, flushes.get());
        assertEquals("bc", shared.get());
    }

    @Test
    void sharedState_concurrentUpdatesAreAtomic() throws Exception {
        OwnerView view = new OwnerView();
        SharedStateImpl<Integer> counter = new SharedStateImpl<>(view, view.tokenTable(), 0);
        int threads = 4;
        int increments = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    for (int j = 0; j < increments; j++) {
                        counter.update(value -> value + 1);
                    }
                    done.countDown();
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent updates did not finish in time");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads * increments, counter.get());
    }

    @Nested
    class MainThreadGuard {

        @BeforeEach
        void setUp() {
            MockBukkit.mock();
        }

        @AfterEach
        void tearDown() {
            MockBukkit.unmock();
        }

        @Test
        void set_offMainThread_throwsIllegalStateException() throws Exception {
            OwnerView view = new OwnerView();
            MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
            FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

            // the test thread created the mock server, so it is the primary thread
            token.set(context, "on-main");
            assertEquals("on-main", token.get(context));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<Throwable> thrown = new AtomicReference<>();
                executor.submit(() -> {
                    try {
                        token.set(context, "off-main");
                    } catch (Throwable t) {
                        thrown.set(t);
                    }
                }).get(5, TimeUnit.SECONDS);
                assertTrue(thrown.get() instanceof IllegalStateException,
                        "off-main set must throw IllegalStateException, got " + thrown.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
