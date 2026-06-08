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
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end flows for the four sample shapes shipped in test-plugin (spec §12, §14 item 10):
 * scroll over a layout-char row, normal over an explicit serpentine fill order, cycled
 * patterns, and async with a loading indicator. The test-plugin classes are not on this
 * module's test classpath, so the same shapes are restated as nested views here — keep them
 * in sync with the test-plugin samples.
 */
class PaginationSampleFlowsTest {

    // 'O' slots of the async layout, row-major: rows 1-3, columns 1-7
    private static final int[] ASYNC_O_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    // explicit fill order of the normal sample: right-to-left, bottom-to-top across rows 3..1
    private static final int[] SERPENTINE_SLOTS = {
            34, 33, 32, 31, 30, 29, 28,
            25, 24, 23, 22, 21, 20, 19,
            16, 15, 14, 13, 12, 11, 10
    };

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewService service;
    private ViewListener listener;
    private ScrollFlowView scrollView;
    private NormalFlowView normalView;
    private PatternFlowView patternView;
    private AsyncFlowView asyncView;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        ViewRegistry registry = new ViewRegistry();
        sessions = new SessionRegistry();
        engine = new ViewEngine(plugin, registry, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
                });
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
        scrollView = new ScrollFlowView();
        normalView = new NormalFlowView();
        patternView = new PatternFlowView();
        asyncView = new AsyncFlowView();
        registry.register(scrollView);
        registry.register(normalView);
        registry.register(patternView);
        registry.register(asyncView);
        AsyncFlowView.PENDING.set(null);
        AsyncFlowView.LAST_REQUEST.set(null);
        AsyncFlowView.ERRORS.clear();
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks — drop anything still queued first
        server.getScheduler().cancelTasks(plugin);
        // never let the shared 10s timeout watchdog outlive the mocked server
        AsyncPageSource.shutdownSharedTimeoutScheduler();
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private ViewContext context() {
        return service.contextOf(player).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(int rawSlot) {
        Inventory top = session().inventory();
        Inventory bottom = player.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(player);
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

    private static Material typeAt(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        return item == null ? Material.AIR : item.getType();
    }

    private static void assertItem(Inventory inventory, int slot, Material type, int amount) {
        ItemStack item = inventory.getItem(slot);
        assertNotNull(item, "slot " + slot + " should hold an item");
        assertEquals(type, item.getType(), "slot " + slot + " material");
        assertEquals(amount, item.getAmount(), "slot " + slot + " amount");
    }

    private static void assertSlotEmpty(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        assertTrue(item == null || item.getType() == Material.AIR, "slot " + slot + " should be empty");
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    @Test
    void scrollFlow_openNavigateClampAndClose() {
        service.open(player, ScrollFlowView.class);
        assertEquals(ViewSession.Status.ACTIVE, session().status());
        Inventory inventory = session().inventory();

        // first frame: items 1..5 in the five 'O' slots; back hidden, next shown (46 pages)
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, i + 1);
        }
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // next arrow: deny-by-default cancellation + the window slides ONE element
        InventoryClickEvent next = click(35);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, i + 2);
        }
        assertEquals(Material.ARROW, typeAt(inventory, 27), "back arrow appears on page 2");

        // switchTo clamps exactly like 2.x changePage: upper to totalPages, lower to 1
        ViewContext context = context();
        scrollView.numbers.switchTo(context, 999);
        assertEquals(46, scrollView.numbers.currentPage(context));
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, 46 + i);
        }
        assertSlotEmpty(inventory, 35);
        assertEquals(Material.ARROW, typeAt(inventory, 27));

        scrollView.numbers.switchTo(context, -3);
        assertEquals(1, scrollView.numbers.currentPage(context));
        assertItem(inventory, 29, Material.GOLD_INGOT, 1);
        assertSlotEmpty(inventory, 27);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void normalFlow_serpentineFillOrderAndFallbackTail() {
        service.open(player, NormalFlowView.class);
        Inventory inventory = session().inventory();

        // page 1 fills the explicit serpentine order exactly
        for (int i = 0; i < SERPENTINE_SLOTS.length; i++) {
            assertItem(inventory, SERPENTINE_SLOTS[i], Material.EMERALD, i + 1);
        }
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // page 2 of 3: items 22..42
        listener.onClick(click(35));
        assertItem(inventory, 34, Material.EMERALD, 22);
        assertItem(inventory, 10, Material.EMERALD, 42);
        assertEquals(Material.ARROW, typeAt(inventory, 27));

        // page 3 of 3: items 43..50, then the fallback pane fills the tail
        listener.onClick(click(35));
        assertItem(inventory, 34, Material.EMERALD, 43);
        assertItem(inventory, 25, Material.EMERALD, 50);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, typeAt(inventory, 24));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, typeAt(inventory, 10));
        assertSlotEmpty(inventory, 35);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void patternFlow_cyclesPatternsAndClearsStaleSlots() {
        service.open(player, PatternFlowView.class);
        Inventory inventory = session().inventory();

        // page 1 — clockwise ring, letters A..L: A=2..E=6, F=15, G=24..K=20, L=11
        assertItem(inventory, 2, Material.DIAMOND, 1);
        assertItem(inventory, 6, Material.DIAMOND, 5);
        assertItem(inventory, 15, Material.DIAMOND, 6);
        assertItem(inventory, 24, Material.DIAMOND, 7);
        assertItem(inventory, 20, Material.DIAMOND, 11);
        assertItem(inventory, 11, Material.DIAMOND, 12);
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // page 2 — X pattern (A=2, B=6, C=13, D=20, E=24) with items 13..17; ring-only slots clear
        InventoryClickEvent next = click(35);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        assertItem(inventory, 2, Material.DIAMOND, 13);
        assertItem(inventory, 6, Material.DIAMOND, 14);
        assertItem(inventory, 13, Material.DIAMOND, 15);
        assertItem(inventory, 20, Material.DIAMOND, 16);
        assertItem(inventory, 24, Material.DIAMOND, 17);
        assertSlotEmpty(inventory, 3);
        assertSlotEmpty(inventory, 15);
        assertSlotEmpty(inventory, 11);
        // complete stale-slot-clearing proof for page 1→2: slots 4, 5, 21, 22, 23 were in
        // the ring pattern but are absent from the X pattern and must be cleared
        assertSlotEmpty(inventory, 4);
        assertSlotEmpty(inventory, 5);
        assertSlotEmpty(inventory, 21);
        assertSlotEmpty(inventory, 22);
        assertSlotEmpty(inventory, 23);

        // page 3 — serpentine block (A=3,B=4,C=5,D=14,E=13,F=12,G=21,H=22,I=23) with items 18..26
        listener.onClick(click(35));
        assertItem(inventory, 3, Material.DIAMOND, 18);
        assertItem(inventory, 5, Material.DIAMOND, 20);
        assertItem(inventory, 14, Material.DIAMOND, 21);
        assertItem(inventory, 13, Material.DIAMOND, 22);
        assertItem(inventory, 12, Material.DIAMOND, 23);
        assertItem(inventory, 22, Material.DIAMOND, 25);
        assertItem(inventory, 23, Material.DIAMOND, 26);
        assertSlotEmpty(inventory, 2);
        assertSlotEmpty(inventory, 6);
        // complete stale-slot-clearing proof for page 2→3: slots 20 and 24 were in
        // the X pattern but are absent from the serpentine block and must be cleared
        assertSlotEmpty(inventory, 20);
        assertSlotEmpty(inventory, 24);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void asyncFlow_loadingFrameThenSettleRepaintsAreaAndFlipsIndicator() throws InterruptedException {
        service.open(player, AsyncFlowView.class);
        Inventory inventory = session().inventory();
        ViewContext context = context();

        // loading frame: every page slot shows the loading item; indicator reads loading/page 1;
        // both arrows hidden (totals unknown → 1 page)
        for (int slot : ASYNC_O_SLOTS) {
            assertEquals(Material.CLOCK, typeAt(inventory, slot), "slot " + slot + " shows the loading item");
        }
        assertItem(inventory, 49, Material.CLOCK, 1);
        assertSlotEmpty(inventory, 45);
        assertSlotEmpty(inventory, 53);
        assertTrue(asyncView.numbers.isLoading(context));

        CompletableFuture<PageResult<Integer>> pending = AsyncFlowView.PENDING.get();
        assertNotNull(pending, "open dispatched the initial page load");
        PageRequest request = AsyncFlowView.LAST_REQUEST.get();
        assertEquals(21, request.getPageSize());
        assertEquals(0, request.getOffset());
        assertEquals(player.getUniqueId(), request.playerId());

        // complete off-main on purpose: the settle must marshal back through the Bukkit scheduler
        List<Integer> items = numbersUpTo(21);
        Thread completer = new Thread(() -> pending.complete(PageResult.of(items, 100)));
        completer.start();
        completer.join();
        server.getScheduler().performOneTick();

        // settled frame: emeralds 1..21, indicator flips to PAPER page 1, next arrow appears (5 pages)
        for (int i = 0; i < ASYNC_O_SLOTS.length; i++) {
            assertItem(inventory, ASYNC_O_SLOTS[i], Material.EMERALD, i + 1);
        }
        assertItem(inventory, 49, Material.PAPER, 1);
        assertEquals(Material.ARROW, typeAt(inventory, 53), "next arrow appears once totals are known");
        assertSlotEmpty(inventory, 45);
        assertFalse(asyncView.numbers.isLoading(context));
        assertEquals(5, asyncView.numbers.totalPages(context));
        assertEquals(100, asyncView.numbers.totalElements(context));
        assertTrue(AsyncFlowView.ERRORS.isEmpty(), "no error callback fired");

        // click navigation: advance repaints the loading frame inline (the click's dispatch
        // requests page 2 and the settle pass paints CLOCKs), the off-main settle then
        // repaints the area with the second page
        InventoryClickEvent next = click(53);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        assertTrue(asyncView.numbers.isLoading(context));
        for (int slot : ASYNC_O_SLOTS) {
            assertEquals(Material.CLOCK, typeAt(inventory, slot),
                    "slot " + slot + " shows the loading frame while page 2 loads");
        }
        assertItem(inventory, 49, Material.CLOCK, 2);

        CompletableFuture<PageResult<Integer>> pageTwo = AsyncFlowView.PENDING.get();
        assertNotSame(pending, pageTwo, "advance dispatched a fresh page-2 load");
        assertEquals(21, AsyncFlowView.LAST_REQUEST.get().getOffset(), "(2 - 1) * 21 layout slots");
        List<Integer> secondItems = new ArrayList<>();
        for (int value = 22; value <= 42; value++) {
            secondItems.add(value);
        }
        Thread secondCompleter = new Thread(() -> pageTwo.complete(PageResult.of(secondItems, 100)));
        secondCompleter.start();
        secondCompleter.join();
        server.getScheduler().performOneTick();

        for (int i = 0; i < ASYNC_O_SLOTS.length; i++) {
            assertItem(inventory, ASYNC_O_SLOTS[i], Material.EMERALD, 22 + i);
        }
        assertItem(inventory, 49, Material.PAPER, 2);
        assertEquals(Material.ARROW, typeAt(inventory, 45), "back arrow appears on page 2");
        assertEquals(Material.ARROW, typeAt(inventory, 53), "next arrow stays: 3 <= 5 pages");
        assertTrue(AsyncFlowView.ERRORS.isEmpty(), "no error callback fired across navigation");

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    public static final class ScrollFlowView extends View {
        final Pagination<Integer> numbers = paginate(numbersUpTo(50))
                .scroll()
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.GOLD_INGOT, value)))
                .fallbackItem(ctx -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aScroll Flow")
                    .layout("         ",
                            "         ",
                            "         ",
                            "< OOOOO >",
                            "         ",
                            "         ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.layoutSlot('>', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
        }
    }

    public static final class NormalFlowView extends View {
        final Pagination<Integer> numbers = paginate(numbersUpTo(50))
                .layout(Layout.ofSlots(
                        34, 33, 32, 31, 30, 29, 28,
                        25, 24, 23, 22, 21, 20, 19,
                        16, 15, 14, 13, 12, 11, 10))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
                .fallbackItem(ctx -> new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aNormal Flow")
                    .rows(6);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(27, new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.slot(35, new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
        }
    }

    public static final class PatternFlowView extends View {
        final Pagination<Integer> gems = paginate(numbersUpTo(50))
                .patterns(
                        Layout.ofGrid(
                                "  ABCDE  ",
                                "  L   F  ",
                                "  KJIHG  "),
                        Layout.ofGrid(
                                "  A   B  ",
                                "    C    ",
                                "  D   E  "),
                        Layout.ofGrid(
                                "   ABC   ",
                                "   FED   ",
                                "   GHI   "))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.DIAMOND, value)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aPattern Flow")
                    .rows(4);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(27, new ItemStack(Material.ARROW))
                    .displayIf(gems::canBack)
                    .updateOnStateChange(gems)
                    .onClick(gems::back);
            render.slot(35, new ItemStack(Material.ARROW))
                    .displayIf(gems::canAdvance)
                    .updateOnStateChange(gems)
                    .onClick(gems::advance);
        }
    }

    public static final class AsyncFlowView extends View {
        static final AtomicReference<CompletableFuture<PageResult<Integer>>> PENDING = new AtomicReference<>();
        static final AtomicReference<PageRequest> LAST_REQUEST = new AtomicReference<>();
        static final List<Throwable> ERRORS = new CopyOnWriteArrayList<>();

        final Pagination<Integer> numbers = paginateAsync(AsyncFlowView::loadPage)
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
                .loadingItem(ctx -> new ItemStack(Material.CLOCK))
                .onError((request, error) -> ERRORS.add(error))
                .requestTimeout(Duration.ofSeconds(10))
                .cacheTtl(Duration.ofSeconds(15))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&bAsync Flow")
                    .layout("         ",
                            " OOOOOOO ",
                            " OOOOOOO ",
                            " OOOOOOO ",
                            "         ",
                            "<   I   >");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.layoutSlot('>', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
            render.layoutSlot('I')
                    .item(ctx -> new ItemStack(numbers.isLoading(ctx) ? Material.CLOCK : Material.PAPER,
                            Math.max(1, numbers.currentPage(ctx))))
                    .updateOnStateChange(numbers);
        }

        private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
            LAST_REQUEST.set(request);
            CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
            PENDING.set(future);
            return future;
        }
    }
}
