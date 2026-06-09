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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.PaginationInitPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationOpenWiringTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private PlayerMock second;

    private SimpleView simpleView;
    private EagerOrderingView eagerOrderingView;
    private OnOpenNavigationView onOpenNavigationView;
    private IsolationView isolationView;
    private CacheIsolationView cacheIsolationView;
    private LazyInitFailView lazyInitFailView;
    private OutOfBoundsLayoutView outOfBoundsLayoutView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("first");
        second = server.addPlayer("second");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        simpleView = new SimpleView();
        eagerOrderingView = new EagerOrderingView();
        onOpenNavigationView = new OnOpenNavigationView();
        isolationView = new IsolationView();
        cacheIsolationView = new CacheIsolationView();
        lazyInitFailView = new LazyInitFailView();
        outOfBoundsLayoutView = new OutOfBoundsLayoutView();
        views.register(simpleView);
        views.register(eagerOrderingView);
        views.register(onOpenNavigationView);
        views.register(isolationView);
        views.register(cacheIsolationView);
        views.register(lazyInitFailView);
        views.register(outOfBoundsLayoutView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel first so settle tasks queued by
        // tests that never ticked do not run against torn-down state
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    // ---------------------------------------------------------------- fixture views

    static final class SimpleView extends View {
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Simple").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(0, new ItemStack(Material.STONE));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class EagerOrderingView extends View {
        final Pagination<String> pagination = this.<String>paginate(
                        Arrays.asList("a", "b", "c", "d", "e"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, index + 1)))
                .build();

        Integer pageInFirstRender;
        Integer totalPagesInFirstRender;
        Integer totalElementsInFirstRender;
        Boolean loadingInFirstRender;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerOrdering").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // settled values prove init ran after layout resolution and before this
            // handler: pre-init reads would be 1 / 1 / 0 / false
            pageInFirstRender = pagination.currentPage(context);
            totalPagesInFirstRender = pagination.totalPages(context);
            totalElementsInFirstRender = pagination.totalElements(context);
            loadingInFirstRender = pagination.isLoading(context);
        }
    }

    static final class OnOpenNavigationView extends View {
        final List<PageRequest> requests = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requests.add(request);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < request.getPageSize(); i++) {
                items.add("item-" + (request.getOffset() + i));
            }
            return CompletableFuture.completedFuture(PageResult.of(items, 100));
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("OnOpenNavigation").layout("OO       ");
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            // pre-init: records a pending target the init phase replays through the
            // preserved unbound-record branch of changePage
            pagination.switchTo(context, 3);
        }
    }

    static final class IsolationView extends View {
        final Map<UUID, CompletableFuture<PageResult<String>>> futures = new ConcurrentHashMap<>();
        final List<PageRequest> requests = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requests.add(request);
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            futures.put(request.playerId(), future);
            return future;
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Isolation").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // loading indicator outside the pagination area: repaints on the token's
            // settle pass because it watches the pagination token
            context.slot(8)
                    .item(ctx -> new ItemStack(
                            pagination.isLoading(ctx) ? Material.BARRIER : Material.EMERALD))
                    .updateOnStateChange(pagination);
        }
    }

    static final class CacheIsolationView extends View {
        final Map<UUID, List<PageRequest>> requestsByPlayer = new ConcurrentHashMap<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requestsByPlayer
                    .computeIfAbsent(request.playerId(), id -> new CopyOnWriteArrayList<>())
                    .add(request);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < request.getPageSize(); i++) {
                items.add("item-" + (request.getOffset() + i));
            }
            return CompletableFuture.completedFuture(PageResult.of(items, 6));
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .cacheTtl(Duration.ofMinutes(5))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CacheIsolation").layout("OO       ");
        }
    }

    static final class LazyInitFailView extends View {
        CloseReason lastCloseReason;
        boolean firstRendered;
        final Pagination<String> pagination = this.<String>paginate(ctx -> {
            throw new IllegalStateException("lazy boom");
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("LazyInitFail").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            firstRendered = true;
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class OutOfBoundsLayoutView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layout(Layout.ofSlots(50))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // a 1-row view: slot 50 of the explicit layout is out of bounds at init time
            config.title("OutOfBounds").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class LayoutlessCharView extends View {
        @SuppressWarnings("unused")
        private final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layoutChar('P')
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("LayoutlessChar").rows(1);
        }
    }

    static final class MissingCharView extends View {
        @SuppressWarnings("unused")
        private final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layoutChar('P')
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("MissingChar").layout("OOO      ");
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void eagerInit_runsAfterLayoutResolution_beforeOnFirstRender() {
        engine.open(player, EagerOrderingView.class, ViewArguments.empty());

        assertTrue(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(1, eagerOrderingView.pageInFirstRender);
        assertEquals(3, eagerOrderingView.totalPagesInFirstRender,
                "ceil(5 elements / 2 slots) pages; a pre-init read would see 1");
        assertEquals(5, eagerOrderingView.totalElementsInFirstRender,
                "a pre-init read would see 0");
        assertEquals(Boolean.FALSE, eagerOrderingView.loadingInFirstRender,
                "an eager source settles inline during init");
    }

    @Test
    void onOpenNavigation_recordsPendingTarget_initDispatchesThatPage() {
        engine.open(player, OnOpenNavigationView.class, ViewArguments.empty());

        // exactly one request: the pending target was replayed on the unbound paginator
        // (record-only branch), then bind dispatched once for the recorded page
        assertEquals(1, onOpenNavigationView.requests.size());
        PageRequest request = onOpenNavigationView.requests.get(0);
        assertEquals(3, request.getPage());
        assertEquals(4, request.getOffset(), "(3 - 1) * 2 layout slots");
        assertEquals(2, request.getPageSize());
        assertEquals(player.getUniqueId(), request.playerId());

        ViewSession session = session();
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        assertEquals(3, onOpenNavigationView.pagination.currentPage(context));
    }

    @Test
    void perContextIsolation_independentRequests_settlePaintsOnlyTheSettledSession() {
        engine.open(player, IsolationView.class, ViewArguments.empty());
        engine.open(second, IsolationView.class, ViewArguments.empty());

        // each context built its own source: two independent requests, one per player
        assertEquals(2, isolationView.requests.size());
        Set<UUID> requestedIds = new HashSet<>();
        requestedIds.add(isolationView.requests.get(0).playerId());
        requestedIds.add(isolationView.requests.get(1).playerId());
        assertEquals(new HashSet<>(Arrays.asList(player.getUniqueId(), second.getUniqueId())),
                requestedIds);

        ViewSession sessionA = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        ViewSession sessionB = sessions.find(second.getUniqueId()).orElseThrow(IllegalStateException::new);
        assertEquals(Material.BARRIER, sessionA.inventory().getItem(8).getType());
        assertEquals(Material.BARRIER, sessionB.inventory().getItem(8).getType());

        // completing A's future on the main thread settles inline and repaints only A
        isolationView.futures.get(player.getUniqueId())
                .complete(PageResult.of(Arrays.asList("a", "b"), 2));

        assertEquals(Material.EMERALD, sessionA.inventory().getItem(8).getType(),
                "player A's watcher must repaint on A's settle");
        assertEquals(Material.BARRIER, sessionB.inventory().getItem(8).getType(),
                "player B must still be loading");
        PlainViewContextImpl contextA = new PlainViewContextImpl(sessionA, engine);
        PlainViewContextImpl contextB = new PlainViewContextImpl(sessionB, engine);
        assertFalse(isolationView.pagination.isLoading(contextA));
        assertTrue(isolationView.pagination.isLoading(contextB));
        assertEquals(2, isolationView.pagination.totalElements(contextA));
        assertEquals(0, isolationView.pagination.totalElements(contextB),
                "totals are per-context; B's source has not settled");
    }

    @Test
    void perContextIsolation_pageCacheIsNotSharedAcrossContexts() {
        // §14 item 8 also pins cache isolation: the TTL cache lives on the per-context
        // AsyncPageSource instance, so one player's cached page must never serve another
        engine.open(player, CacheIsolationView.class, ViewArguments.empty());
        List<PageRequest> requestsA = cacheIsolationView.requestsByPlayer.get(player.getUniqueId());
        assertEquals(1, requestsA.size(), "A's initial page-1 load");

        ViewSession sessionA = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        PlainViewContextImpl contextA = new PlainViewContextImpl(sessionA, engine);
        cacheIsolationView.pagination.switchTo(contextA, 2);
        assertEquals(2, requestsA.size(), "A's page-2 load");
        cacheIsolationView.pagination.switchTo(contextA, 1);
        assertEquals(2, requestsA.size(),
                "page 1 must come from A's warm cache, not a third supplier call");
        assertEquals(1, cacheIsolationView.pagination.currentPage(contextA));

        engine.open(second, CacheIsolationView.class, ViewArguments.empty());
        List<PageRequest> requestsB = cacheIsolationView.requestsByPlayer.get(second.getUniqueId());
        assertEquals(1, requestsB.size(),
                "B's source must load page 1 itself even though A has it cached");
        assertEquals(0, requestsB.get(0).getOffset());
    }

    @Test
    void initFailure_abortsOpenFailed_registersNothing_closesTheDeadReplacedContainer() {
        Logger logger = Logger.getLogger(PaginationInitPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, SimpleView.class, ViewArguments.empty());
            ViewSession previous = session();
            Inventory previousContainer = previous.inventory();
            assertNotNull(previousContainer);

            engine.open(player, LazyInitFailView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                    "an aborted open must register nothing");
            assertEquals(CloseReason.OPEN_FAILED, lazyInitFailView.lastCloseReason);
            assertFalse(lazyInitFailView.firstRendered,
                    "a failed init must abort before onFirstRender");
            assertTrue(handler.hasSevereContaining("pagination init failed for view"));
            // the commit point already replaced the previous session before init ran
            // (mirrors onFirstRender failures); the dead container must be closed
            assertEquals(CloseReason.REPLACED, simpleView.lastCloseReason);
            assertNotSame(previousContainer, player.getOpenInventory().getTopInventory(),
                    "the replaced view's dead container must be closed, not left clickable");
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void explicitLayoutOutOfBounds_abortsOpenFailed() {
        Logger logger = Logger.getLogger(PaginationInitPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            // the bounds violation is per-open (rows can vary per open when no config
            // layout is declared), so it surfaces at init, not at registration
            engine.open(player, OutOfBoundsLayoutView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent());
            assertEquals(CloseReason.OPEN_FAILED, outOfBoundsLayoutView.lastCloseReason);
            assertTrue(handler.hasSevereContaining("pagination init failed for view"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void register_paginationLayoutChar_withoutLayout_throwsAtRegistration() {
        ViewRegistry registry = new ViewRegistry();

        ViewConfigurationException ex = assertThrows(ViewConfigurationException.class,
                () -> registry.register(new LayoutlessCharView()));

        assertEquals("view " + LayoutlessCharView.class.getName()
                + " declares pagination on layout char 'P' but has no layout", ex.getMessage());
    }

    @Test
    void register_paginationLayoutChar_missingFromLayout_throwsAtRegistration() {
        ViewRegistry registry = new ViewRegistry();

        ViewConfigurationException ex = assertThrows(ViewConfigurationException.class,
                () -> registry.register(new MissingCharView()));

        assertEquals("pagination layout char 'P' is not present in the layout of view "
                + MissingCharView.class.getName(), ex.getMessage());
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasSevereContaining(String fragment) {
            for (LogRecord record : records) {
                if (record.getLevel() == Level.SEVERE && record.getMessage() != null
                        && record.getMessage().contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
