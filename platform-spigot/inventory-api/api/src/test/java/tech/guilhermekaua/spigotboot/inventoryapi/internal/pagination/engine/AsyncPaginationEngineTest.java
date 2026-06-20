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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPaginationEngineTest {

    private static final PageItemFactory<Integer> ITEM_FACTORY =
            (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value));

    private ServerMock server;
    private MockPlugin plugin;
    private BukkitPlatformScheduler scheduler;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
        scheduler = new BukkitPlatformScheduler(plugin);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel them first so a settle queued by a
        // test that deliberately never ticked is not run against a torn-down fixture
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    /**
     * Captures every request and exposes its futures for hand-completion.
     */
    private static final class CapturingSupplier implements AsyncPageSupplier<Integer> {
        private final List<PageRequest> requests = new ArrayList<>();
        private final List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();

        @Override
        public CompletableFuture<PageResult<Integer>> load(PageRequest request) {
            requests.add(request);
            CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(player.getUniqueId(), plugin, player);
    }

    private static Layout threeSlots() {
        return Layout.ofSlots(0, 1, 2);
    }

    private NormalPagination<Integer> asyncNormal(CapturingSupplier supplier) {
        return new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                () -> RenderedItem.ofItem(new ItemStack(Material.CLOCK)),
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher(scheduler)));
    }

    @Test
    void whileLoading_rendersLoadingItemInEverySlot() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);
        assertTrue(pagination.isLoading());
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;
        assertEquals(3, items.size());
        for (RenderedItem item : items) {
            assertEquals(Material.CLOCK, item.plainItem().getType());
        }
    }

    @Test
    void offThreadCompletion_appliesOnNextSchedulerTick() throws Exception {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);

        Thread completer = new Thread(() ->
                supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)));
        completer.start();
        completer.join(5000);

        // the settle was dispatched to the main thread, not applied inline on the completer
        assertTrue(pagination.isLoading(), "state must not be applied before the scheduler tick");
        assertEquals(0, host.requestRenderCount(), "no render may be requested before the scheduler tick");
        server.getScheduler().performOneTick();

        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        assertEquals(1, host.requestRenderCount(), "the settled load must request exactly one render");
        pagination.insertPageItems();
        assertEquals(Material.DIAMOND, host.lastFillPage().items.get(0).plainItem().getType());
    }

    @Test
    void failedLoad_rollsBackToLastRequestedPageAndInvokesErrorCallback() {
        CapturingSupplier supplier = new CapturingSupplier();
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, (request, error) -> callbackError.set(error),
                        null, null, 128, new BukkitSettleDispatcher(scheduler)));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)); // page 1 ok

        pagination.nextPage(); // page 2 in flight
        pagination.nextPage(); // page 3 in flight, supersedes page 2
        assertEquals(3, pagination.getCurrentPage());
        supplier.futures.get(2).completeExceptionally(new RuntimeException("db down"));

        // preserved 2.x quirk (NEVER "fix"): the rollback restores the page the previous
        // dispatch REQUESTED (page 2, which never rendered), not the last rendered page 1
        assertEquals(2, pagination.getCurrentPage(),
                "failed navigation must roll back to the last requested page");
        assertNotNull(callbackError.get());
        assertNotNull(pagination.lastError());
        assertFalse(pagination.isLoading());
    }

    @Test
    void navigationBeforeFirstSettle_isHonoredThenReclampedByTotals() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.bind(newHost());
        pagination.changePage(5); // totals unknown: honored optimistically

        assertEquals(5, pagination.getCurrentPage());
        assertEquals(12, supplier.requests.get(1).getOffset(), "page 5 of size 3 => offset 12");

        // totals arrive: only 2 pages exist => re-clamp dispatches page 2
        supplier.futures.get(1).complete(PageResult.of(List.of(), 6));

        assertEquals(2, pagination.getCurrentPage());
        assertEquals(3, supplier.requests.get(2).getOffset());
    }

    @Test
    void changePage_targetingInFlightPage_doesNotRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.bind(newHost());
        int dispatched = supplier.requests.size(); // bind's page-1 request

        pagination.changePage(1);
        pagination.changePage(1);

        assertEquals(dispatched, supplier.requests.size(),
                "click spam on the loading page must not re-dispatch");
    }

    @Test
    void refresh_invalidatesCacheAndForcesRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, null, null, Duration.ofMinutes(5), 128,
                        new BukkitSettleDispatcher(scheduler)));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)); // page 1 now cached
        int dispatched = supplier.requests.size();

        pagination.refresh();

        // without invalidation this would be a cache hit and the supplier would not be called
        assertEquals(dispatched + 1, supplier.requests.size());
        assertEquals(1, supplier.requests.get(supplier.requests.size() - 1).getPage());
    }

    @Test
    void patternFailedLoad_restoresPagePatternAndLimitAndClearsFailedPattern() {
        CapturingSupplier supplier = new CapturingSupplier();
        Layout five = Layout.ofSlots(0, 1, 2, 3, 4);
        Layout two = Layout.ofSlots(9, 10);
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(five, two),
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher(scheduler)));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3, 4, 5), 9));

        pagination.nextPage(); // switches to pattern `two`, limit 2
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back the page");
        assertEquals(five, pagination.getCurrentPattern(), "failed navigation must roll back the pattern");
        assertEquals(5, pagination.getItemPageLimit(), "failed navigation must roll back the page limit");

        // the failed pattern's slots were cleared through the host before the state restore
        FakePaginationHost.FillPageCall clearCall = host.lastFillPage();
        assertEquals(two, clearCall.layout, "the failed pattern's slots must be cleared");
        assertEquals(2, clearCall.items.size());
        for (RenderedItem filler : clearCall.items) {
            assertNull(filler.plainItem(), "no fallback configured: the fillers must clear the slots");
            assertFalse(filler.isFailure());
        }
    }

    @Test
    void scrollSupplier_receivesSlidingWindowOffsets() {
        CapturingSupplier supplier = new CapturingSupplier();
        ScrollPagination<Integer> pagination = new ScrollPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher(scheduler)));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 10));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(1, second.getOffset(), "scroll slides one element per page");
        assertEquals(3, second.getPageSize());
    }

    @Test
    void patternSupplier_receivesCumulativeOffsetsAndPerPatternSizes() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(Layout.ofSlots(0, 1, 2, 3, 4),      // 5 slots
                        Layout.ofSlots(9, 10)),              // 2 slots
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher(scheduler)));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3, 4, 5), 9));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(5, second.getOffset(), "page 2 starts after pattern 1's 5 slots");
        assertEquals(2, second.getPageSize(), "page 2 uses pattern 2's slot count");
    }

    @Test
    void changePageBeforeBind_isDeferredToBind() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.changePage(3);

        assertTrue(supplier.requests.isEmpty(), "no host is bound yet; nothing must be dispatched");
        assertEquals(3, pagination.getCurrentPage());

        FakePaginationHost host = newHost();
        pagination.bind(host);

        assertEquals(1, supplier.requests.size());
        PageRequest request = supplier.requests.get(0);
        assertEquals(3, request.getPage());
        assertEquals(6, request.getOffset(), "page 3 of size 3 => offset 6");
        assertEquals(host.playerId(), request.playerId(),
                "the dispatched request must carry the host's player id");
        assertEquals(plugin, request.plugin(), "the dispatched request must carry the host's plugin");
    }

    @Test
    void changePageBeforeBind_onPattern_neverTouchesTheHost() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(Layout.ofSlots(0, 1, 2, 3, 4),      // 5 slots
                        Layout.ofSlots(9, 10)),              // 2 slots
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher(scheduler)));

        pagination.changePage(2);
        pagination.changePage(3); // 2.x regression: NPE'd clearing the last pattern unbound

        assertTrue(supplier.requests.isEmpty(), "no host is bound yet; nothing must be dispatched");

        FakePaginationHost host = newHost();
        pagination.bind(host);

        assertEquals(1, supplier.requests.size());
        assertEquals(3, supplier.requests.get(0).getPage());
        assertEquals(7, supplier.requests.get(0).getOffset(), "page 3 starts after one full 7-slot cycle");
        assertEquals(5, supplier.requests.get(0).getPageSize(), "page 3 cycles back to the 5-slot pattern");
        assertTrue(host.fillPageCalls().isEmpty(),
                "pre-bind navigation is record-only: no pattern clear may reach the host");
    }

    @Test
    void settleWithInactiveHost_performsNoRequestRender() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);
        host.setActive(false);

        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9));

        // the data still applies; only the paint is skipped for the unpaintable session
        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        assertEquals(0, host.requestRenderCount(), "an inactive host must not be asked to render");
    }
}
