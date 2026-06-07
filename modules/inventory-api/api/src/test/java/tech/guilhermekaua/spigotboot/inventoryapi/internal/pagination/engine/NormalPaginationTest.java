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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalPaginationTest {

    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Layout nineSlotLayout() {
        return Layout.ofSlots(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static NormalPagination<Integer> eagerPagination(List<Integer> source) {
        return new NormalPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                nineSlotLayout(),
                null,
                new EagerPageSource<>(source));
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void eagerFlow_rendersFirstPageSlice() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;
        assertEquals(9, items.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, items.get(i).plainItem().getAmount());
        }
    }

    @Test
    void eagerFlow_totalsAndNavigationMatchListSize() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        assertEquals(3, pagination.getTotalPages());
        assertEquals(20, pagination.getTotalElements());

        pagination.changePage(3);
        assertEquals(3, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void changePage_beyondTotal_clampsToLastPage() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        pagination.changePage(99);

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void getPageOfIndex_returnsMinusOneOutOfRange() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(8));
        assertEquals(2, pagination.getPageOfIndex(9));
        assertEquals(3, pagination.getPageOfIndex(19));
        assertEquals(-1, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void replaceSource_beforeBind_isHonoredByBind() {
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                nineSlotLayout());
        FakePaginationHost host = newHost();

        pagination.replaceSource(new EagerPageSource<>(sourceOf(5)));
        pagination.bind(host);
        pagination.insertPageItems();

        assertEquals(1, host.lastFillPage().items.get(0).plainItem().getAmount());
    }

    @Test
    void changePage_beforeBind_onNonEmptyEagerSource_recordsAndReclamps() {
        // regression for the pinned rule-2 reorder: the 2.x body clamped via getTotalPages()
        // before the unbound check, dividing by the still-zero itemPageLimit for a non-empty
        // eager source; the record-only branch must run first and the settle's downward
        // re-clamp must correct the overshoot after bind
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));

        pagination.changePage(99);
        assertEquals(99, pagination.getCurrentPage());

        pagination.bind(newHost());

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void changePage_beforeBind_inRangeTarget_isDispatchedByBind() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        FakePaginationHost host = newHost();

        pagination.changePage(2);
        pagination.bind(host);
        pagination.insertPageItems();

        assertEquals(2, pagination.getCurrentPage());
        // page 2 of a 20-element source over 9 slots renders amounts 10..18
        assertEquals(10, host.lastFillPage().items.get(0).plainItem().getAmount());
    }

    @Test
    void partialLastPage_rendersItemsThenClearsRemainingSlots() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.changePage(3);
        pagination.insertPageItems();

        FakePaginationHost.FillPageCall fill = host.lastFillPage();
        // page 3 of 20 over 9 slots: items 19 and 20, then seven empty clears
        assertEquals(9, fill.items.size());
        assertEquals(19, fill.items.get(0).plainItem().getAmount());
        assertEquals(20, fill.items.get(1).plainItem().getAmount());
        for (int i = 2; i < 9; i++) {
            assertNull(fill.items.get(i).plainItem(), "slot " + i + " clears on the partial page");
            assertFalse(fill.items.get(i).isFailure());
        }
        assertEquals(9, fill.layout.slots().size(), "the fill layout is the engine's render layout");
    }
}
