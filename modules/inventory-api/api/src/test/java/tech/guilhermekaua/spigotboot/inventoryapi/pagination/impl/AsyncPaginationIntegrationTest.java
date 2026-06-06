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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.PatternPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.ScrollPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AsyncPaginationIntegrationTest {

    private ServerMock server;
    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
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

    private Viewer mockViewer(InventoryEditor editor, CustomInventory customInventory) {
        Viewer viewer = mock(Viewer.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(customInventory);
        return viewer;
    }

    private static InventoryLayout threeSlots() {
        return InventoryLayout.ofSlots(0, 1, 2);
    }

    private static NormalPagination<Integer> asyncNormal(CapturingSupplier supplier) {
        return new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .loadingItem(viewer -> InventoryItem.of(new ItemStack(Material.CLOCK))))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<InventoryItem> lastFillPage(InventoryEditor editor, Pagination<?> pagination) {
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    void whileLoading_rendersLoadingItemInEverySlot() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor, mock(CustomInventory.class)));
        assertTrue(pagination.isLoading());
        pagination.apply();

        List<InventoryItem> items = lastFillPage(editor, pagination);
        assertEquals(3, items.size());
        for (InventoryItem item : items) {
            assertEquals(Material.CLOCK, item.getItemStack().getType());
        }
    }

    @Test
    void offThreadCompletion_appliesOnNextSchedulerTick() throws Exception {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor, mock(CustomInventory.class)));

        Thread completer = new Thread(() ->
                supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)));
        completer.start();
        completer.join(5000);

        // the settle was dispatched to the main thread, not applied inline on the completer
        assertTrue(pagination.isLoading(), "state must not be applied before the scheduler tick");
        server.getScheduler().performOneTick();

        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        pagination.apply();
        assertEquals(Material.DIAMOND, lastFillPage(editor, pagination).get(0).getItemStack().getType());
    }

    @Test
    void failedLoad_rollsBackPageAndInvokesErrorCallback() {
        CapturingSupplier supplier = new CapturingSupplier();
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        NormalPagination<Integer> pagination = new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .errorCallback((request, error) -> callbackError.set(error)))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)); // page 1 ok

        pagination.nextPage();
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back");
        assertNotNull(callbackError.get());
        assertNotNull(pagination.lastError());
        assertFalse(pagination.isLoading());
    }

    @Test
    void navigationBeforeFirstSettle_isHonoredThenReclampedByTotals() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        pagination.changePage(5); // totals unknown: honored optimistically

        assertEquals(5, pagination.getCurrentPage());
        assertEquals(12, supplier.requests.get(1).getOffset(), "page 5 of size 3 => offset 12");

        // totals arrive: only 2 pages exist => re-clamp dispatches page 2
        supplier.futures.get(1).complete(PageResult.of(Arrays.asList(), 6));

        assertEquals(2, pagination.getCurrentPage());
        assertEquals(3, supplier.requests.get(2).getOffset());
    }

    @Test
    void changePage_targetingInFlightPage_doesNotRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        int dispatched = supplier.requests.size(); // init's page-1 request

        pagination.changePage(1);
        pagination.changePage(1);

        assertEquals(dispatched, supplier.requests.size(), "click spam on the loading page must not re-dispatch");
    }

    @Test
    void refresh_invalidatesCacheAndForcesRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .cacheTtl(java.time.Duration.ofMinutes(5)))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)); // page 1 now cached
        int dispatched = supplier.requests.size();

        pagination.refresh();

        // without invalidation this would be a cache hit and the supplier would not be called
        assertEquals(dispatched + 1, supplier.requests.size());
        assertEquals(1, supplier.requests.get(supplier.requests.size() - 1).getPage());
    }

    @Test
    void patternFailedLoad_restoresPagePatternAndLimit() {
        CapturingSupplier supplier = new CapturingSupplier();
        InventoryLayout five = InventoryLayout.ofSlots(0, 1, 2, 3, 4);
        InventoryLayout two = InventoryLayout.ofSlots(9, 10);
        PatternPagination<Integer> pagination = new PatternPaginationBuilder<Integer>()
                .pattern(five)
                .pattern(two)
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 9));

        pagination.nextPage(); // switches to pattern `two`, limit 2
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back the page");
        assertEquals(five, pagination.getCurrentPattern(), "failed navigation must roll back the pattern");
        assertEquals(5, pagination.getItemPageLimit(), "failed navigation must roll back the page limit");
    }

    @Test
    void setSource_afterInit_neverTriggersUpdateInventory() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        CustomInventory customInventory = mock(CustomInventory.class);

        pagination.init(mockViewer(mock(InventoryEditor.class), customInventory));
        pagination.setSource(Arrays.asList(1, 2, 3));

        verify(customInventory, never()).updateInventory(any());
        assertFalse(pagination.isLoading());
        assertEquals(3, pagination.getTotalElements());
    }

    @Test
    void scrollSupplier_receivesSlidingWindowOffsets() {
        CapturingSupplier supplier = new CapturingSupplier();
        ScrollPagination<Integer> pagination = new ScrollPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 10));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(1, second.getOffset(), "scroll slides one element per page");
        assertEquals(3, second.getPageSize());
    }

    @Test
    void patternSupplier_receivesCumulativeOffsetsAndPerPatternSizes() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPaginationBuilder<Integer>()
                .pattern(InventoryLayout.ofSlots(0, 1, 2, 3, 4))      // 5 slots
                .pattern(InventoryLayout.ofSlots(9, 10))               // 2 slots
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 9));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(5, second.getOffset(), "page 2 starts after pattern 1's 5 slots");
        assertEquals(2, second.getPageSize(), "page 2 uses pattern 2's slot count");
    }

    @Test
    void asyncSettle_repaintsInventoryForOnlineViewer() throws Exception {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        CustomInventory customInventory = mock(CustomInventory.class);
        Viewer viewer = mockViewer(mock(InventoryEditor.class), customInventory);
        Player player = mock(Player.class);
        lenient().when(viewer.getPlayer()).thenReturn(player);

        pagination.init(viewer);
        verify(customInventory, never()).updateInventory(any());

        Thread completer = new Thread(() ->
                supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)));
        completer.start();
        completer.join(5000);

        // the settle is queued on the scheduler; the repaint happens on the main thread
        verify(customInventory, never()).updateInventory(any());
        server.getScheduler().performOneTick();

        verify(customInventory).updateInventory(player);
    }

    @Test
    void settleAfterLogoff_doesNotThrow() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        // mock viewer's getPlayer() defaults to null => "player offline"
        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));

        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9));

        assertFalse(pagination.isLoading());
    }
}
