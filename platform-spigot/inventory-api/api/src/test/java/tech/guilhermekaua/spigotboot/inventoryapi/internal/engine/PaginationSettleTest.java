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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaginationSettleTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;

    private EagerPaintView eagerPaintView;
    private AsyncSettleView asyncSettleView;
    private CachedCounterView cachedCounterView;
    private EagerNavView eagerNavView;
    private ElementWatcherView elementWatcherView;
    private RollbackView rollbackView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        eagerPaintView = new EagerPaintView();
        asyncSettleView = new AsyncSettleView();
        cachedCounterView = new CachedCounterView();
        eagerNavView = new EagerNavView();
        elementWatcherView = new ElementWatcherView();
        rollbackView = new RollbackView();
        views.register(eagerPaintView);
        views.register(asyncSettleView);
        views.register(cachedCounterView);
        views.register(eagerNavView);
        views.register(elementWatcherView);
        views.register(rollbackView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        }, new BukkitPlatformScheduler(plugin));
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

    private InventoryClickEvent click(PlayerMock who, ViewSession session, int rawSlot) {
        Inventory top = session.inventory();
        Inventory bottom = who.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(who);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(invView, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    // ---------------------------------------------------------------- fixture views

    static final class EagerPaintView extends View {
        final Pagination<String> pagination = this.<String>paginate(Arrays.asList("1", "2", "3"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, Integer.parseInt(value))))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerPaint").layout("OOO      ");
        }
    }

    static final class AsyncSettleView extends View {
        volatile CompletableFuture<PageResult<String>> pendingFuture;
        final List<UpdateTrigger> triggers = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            pendingFuture = future;
            return future;
        })
                .loadingItem(ctx -> new ItemStack(Material.CLOCK))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, index + 1)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // deliberately NO scheduleUpdate: repaints must be settle-driven
            config.title("AsyncSettle").layout("OOO      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // arrow: visible only once a next page exists; watches the pagination token
            context.slot(8)
                    .item(new ItemStack(Material.ARROW))
                    .displayIf(ctx -> pagination.canAdvance(ctx))
                    .updateOnStateChange(pagination);
        }

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            triggers.add(context.trigger());
        }
    }

    static final class CachedCounterView extends View {
        final AtomicInteger supplierCalls = new AtomicInteger();
        final AtomicInteger rendererCalls = new AtomicInteger();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            supplierCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    PageResult.of(Arrays.asList("a", "b"), 2));
        })
                .cacheTtl(Duration.ofMinutes(1))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, rendererCalls.incrementAndGet())))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CachedCounter").layout("OO       ");
        }
    }

    static final class EagerNavView extends View {
        final Pagination<String> pagination = this.<String>paginate(
                        Arrays.asList("1", "2", "3", "4"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, Integer.parseInt(value))))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerNav").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.ARROW))
                    .onClick(ctx -> pagination.advance(ctx));
        }
    }

    static final class ElementWatcherView extends View {
        final MutableState<Integer> badge = mutableState(0);
        final AtomicInteger rendererCalls = new AtomicInteger();
        final AtomicInteger itemFunctionCalls = new AtomicInteger();
        final Pagination<String> pagination = this.<String>paginate(Arrays.asList("a", "b"))
                .itemRenderer((ctx, item, index, value) -> {
                    rendererCalls.incrementAndGet();
                    item.item(c -> {
                        itemFunctionCalls.incrementAndGet();
                        Integer current = badge.get(c);
                        return new ItemStack(Material.PAPER, (current == null ? 0 : current) + 1);
                    }).updateOnStateChange(badge);
                })
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ElementWatcher").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.STONE))
                    .onClick(ctx -> badge.set(ctx, 5));
        }
    }

    /**
     * Controllable async view that captures every future in a list so individual in-flight
     * requests can be completed or failed independently. Wires an onError callback to verify
     * the error path end-to-end through the ViewEngine.
     */
    static final class RollbackView extends View {
        final List<CompletableFuture<PageResult<String>>> futures = new CopyOnWriteArrayList<>();
        final AtomicInteger errorCallbackCount = new AtomicInteger();
        volatile Throwable lastCallbackError;
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        })
                .loadingItem(ctx -> new ItemStack(Material.CLOCK))
                .onError((request, error) -> {
                    lastCallbackError = error;
                    errorCallbackCount.incrementAndGet();
                })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, index + 1)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Rollback").layout("OOO      ");
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void eagerOpen_paintsItemsIntoCharSlots_beforeTheContainerIsShown() {
        engine.open(player, EagerPaintView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        // firstRender paints static components, then pagination areas, BEFORE
        // player.openInventory and activation — so these contents were visible at show time
        assertEquals(Material.PAPER, inventory.getItem(0).getType());
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(2, inventory.getItem(1).getAmount());
        assertEquals(3, inventory.getItem(2).getAmount());
        assertNull(inventory.getItem(3), "slots outside the area stay untouched");
        assertSame(inventory, player.getOpenInventory().getTopInventory());
    }

    @Test
    void asyncOpen_loadingFrame_settleDeliversOnTick_repaintsAreaAndWatcher()
            throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        // the loading frame is painted at first render; the arrow is hidden (no next page yet)
        assertEquals(Material.CLOCK, inventory.getItem(0).getType());
        assertEquals(Material.CLOCK, inventory.getItem(1).getType());
        assertEquals(Material.CLOCK, inventory.getItem(2).getType());
        assertNull(inventory.getItem(8));

        // off-thread completion: the settle must land on the main thread via the scheduler
        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();

        assertEquals(Material.CLOCK, inventory.getItem(0).getType(),
                "nothing repaints before the scheduled settle runs");
        assertFalse(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));

        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, inventory.getItem(0).getType());
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(3, inventory.getItem(2).getAmount());
        assertTrue(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));
        assertEquals(Material.ARROW, inventory.getItem(8).getType(),
                "the arrow watches the token and repaints when canAdvance flips");
    }

    @Test
    void settleDrivenRepaint_requiresNoScheduledUpdateTask() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        // no scheduleUpdate was configured: there is no tick poll to lean on (2.x caveat gone)
        assertNull(session.updateTask());

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, session.inventory().getItem(0).getType(),
                "the repaint is settle-driven, not poll-driven");
    }

    @Test
    void settleForClosedSession_isDropped_noPaintNoOnUpdateNoThrow() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        engine.close(session, CloseReason.API);
        asyncSettleView.triggers.clear();

        // the scheduled settle now runs against a CLOSED session and must be dropped
        server.getScheduler().performOneTick();

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(Material.CLOCK, inventory.getItem(0).getType(),
                "the dead container must not repaint");
        assertTrue(asyncSettleView.triggers.isEmpty(), "no onUpdate fires for a closed session");
    }

    @Test
    void settleWhileTransitioning_isDelivered_andRepaints() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        // a deferred navigation is pending: the session left ACTIVE but is not torn down
        session.status(ViewSession.Status.TRANSITIONING);

        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, session.inventory().getItem(0).getType(),
                "PAGINATION_SETTLE must be delivered to a TRANSITIONING session");
        assertTrue(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));
    }

    @Test
    void explicitUpdate_fullPass_reRendersThePaginationArea() {
        engine.open(player, CachedCounterView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(2, cachedCounterView.rendererCalls.get(),
                "first paint renders each of the two elements once");
        assertEquals(1, cachedCounterView.supplierCalls.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        new PlainViewContextImpl(session, engine).update();

        assertEquals(4, cachedCounterView.rendererCalls.get(),
                "a full pass re-renders every element of the pagination area");
        assertEquals(3, inventory.getItem(0).getAmount(), "the re-rendered item is repainted");
        assertEquals(1, cachedCounterView.supplierCalls.get(),
                "the settled cached page is reused; no new load is dispatched");
    }

    @Test
    void advanceFromClick_eagerSource_repaintsInlineWithinTheClick() {
        engine.open(player, EagerNavView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(2, inventory.getItem(1).getAmount());

        engine.click(session, click(player, session, 8));

        // the eager advance settles inline; its settle pass repaints within the click
        assertEquals(3, inventory.getItem(0).getAmount(),
                "page 2 must be visible right after the click");
        assertEquals(4, inventory.getItem(1).getAmount());
    }

    @Test
    void stateChangeFlush_repaintsElementWatchers_withoutReRunningTheRenderer() {
        engine.open(player, ElementWatcherView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(2, elementWatcherView.rendererCalls.get());
        assertEquals(2, elementWatcherView.itemFunctionCalls.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        // the click handler writes the watched MutableState; the click's coalesced flush
        // runs a STATE_CHANGE pass scoped to the badge token
        engine.click(session, click(player, session, 8));

        assertEquals(6, inventory.getItem(0).getAmount());
        assertEquals(6, inventory.getItem(1).getAmount());
        assertEquals(4, elementWatcherView.itemFunctionCalls.get(),
                "both element item functions re-evaluate on the watched flush");
        assertEquals(2, elementWatcherView.rendererCalls.get(),
                "the pagination renderer must not re-run for an element-watcher repaint");
    }

    @Test
    void failedSettleAfterOptimisticAdvances_rollsBackToLastRequestedPage_throughTheEngine() {
        // mirrors AsyncPaginationEngineTest#failedLoad_rollsBackToLastRequestedPageAndInvokesErrorCallback
        // but drives the same quirk through the full ViewEngine path with MockBukkit
        engine.open(player, RollbackView.class, ViewArguments.empty());
        ViewSession session = session();
        PlainViewContextImpl ctx = new PlainViewContextImpl(session, engine);

        // future[0]: page-1 initial load — complete inline on the primary thread so the settle
        // runs synchronously via BukkitSettleDispatcher (isPrimaryThread → runs inline, no tick)
        rollbackView.futures.get(0).complete(PageResult.of(Arrays.asList("a", "b", "c"), 9));
        // drain the repaint task queued by the inline settle before advancing
        server.getScheduler().performOneTick();

        assertEquals(1, rollbackView.pagination.currentPage(ctx),
                "page 1 must be current after the initial settle");

        // advance twice while both loads are in flight — future[1] = page 2, future[2] = page 3
        rollbackView.pagination.advance(ctx); // page 2 dispatched; future[1] created
        rollbackView.pagination.advance(ctx); // page 3 dispatched; future[2] created, supersedes page 2
        assertEquals(3, rollbackView.pagination.currentPage(ctx),
                "optimistic cursor must sit at page 3 before either advance settles");

        // fail the page-3 load inline on the primary thread (isPrimaryThread → inline settle,
        // no tick needed for the state rollback itself)
        rollbackView.futures.get(2).completeExceptionally(new RuntimeException("db down"));

        // preserved 2.x quirk (navigationSnapshot / restoreNavigation — do NOT "correct" this):
        // the rollback target is the last REQUESTED page (page 2, whose load was superseded),
        // NOT the last successfully rendered page (page 1).
        assertEquals(2, rollbackView.pagination.currentPage(ctx),
                "failed navigation must roll back to the last requested page (2.x quirk)");
        assertNotNull(rollbackView.pagination.lastError(ctx),
                "lastError must be set after a failed settle");
        assertFalse(rollbackView.pagination.isLoading(ctx),
                "pagination must not remain in the loading state after failure");
        assertNotNull(rollbackView.lastCallbackError,
                "the onError callback must have fired");
        assertEquals(1, rollbackView.errorCallbackCount.get(),
                "the onError callback must fire exactly once for the one failed load");

        // drain any repaint task queued by the rollback settle so tearDown stays clean
        server.getScheduler().performOneTick();
    }
}
