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

class ScrollPaginationTest {

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

    private static ScrollPagination<Integer> sevenSlotScroll(List<Integer> source) {
        return new ScrollPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                Layout.ofSlots(0, 1, 2, 3, 4, 5, 6),
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
    void totals_windowOfSevenOverTen_isFourPages() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10));
        pagination.bind(newHost());

        assertEquals(4, pagination.getTotalPages());
    }

    @Test
    void pageTwo_slidesWindowByOneElement() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10));
        FakePaginationHost host = newHost();
        pagination.bind(host);

        pagination.changePage(2);
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;

        // window slid by ONE element: values 2..8
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 2, items.get(i).plainItem().getAmount());
        }
    }

    @Test
    void getPageOfIndex_usesFirstVisiblePageSemantics() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10)); // window 7 => 4 pages
        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(5));
        assertEquals(1, pagination.getPageOfIndex(6));
        assertEquals(2, pagination.getPageOfIndex(7));
        assertEquals(4, pagination.getPageOfIndex(9));
        assertEquals(-1, pagination.getPageOfIndex(10));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void sourceSmallerThanWindow_isSinglePage() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(3));
        pagination.bind(newHost());

        assertEquals(1, pagination.getTotalPages());
        assertEquals(1, pagination.getPageOfIndex(2));
    }
}
