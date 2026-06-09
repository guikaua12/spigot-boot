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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPaginationTest {

    private static final PageItemFactory<Integer> ITEM_FACTORY =
            (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value));

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

    @Test
    void applyAndChangePage_withCyclingPatterns_doNotThrow() {
        Paginator<Integer> pagination = samplePatternPagination(sourceOfFifty());

        pagination.bind(newHost());

        assertDoesNotThrow(pagination::insertPageItems);
        assertTrue(pagination.getTotalPages() >= 1);

        if (pagination.hasNextPage()) {
            assertDoesNotThrow(pagination::nextPage);
        }
        if (pagination.hasPreviousPage()) {
            assertDoesNotThrow(pagination::previousPage);
        }
        assertDoesNotThrow(() -> pagination.changePage(pagination.getTotalPages()));
    }

    @Test
    void cyclingPatterns_fiftyItems_renderEverySourceValueExactlyOnce() {
        Paginator<Integer> pagination = samplePatternPagination(sourceOfFifty());
        FakePaginationHost host = newHost();

        pagination.bind(host);

        for (int page = 1; page <= pagination.getTotalPages(); page++) {
            pagination.changePage(page);
            pagination.insertPageItems();
        }

        List<Integer> rendered = diamondAmounts(host.fillPageCalls());

        assertEquals(
                IntStream.rangeClosed(1, 50).boxed().toList(),
                rendered.stream().sorted().toList(),
                "every source value 1..50 must appear exactly once across all pages (no skips, no overlap)"
        );
    }

    @Test
    void diamondLayout_leavesColumnZeroEmpty() {
        // v3 Layout exposes no column map; the 2.x getColumnSizes().containsKey(0) assertion
        // becomes: no fill slot may sit in column zero
        for (int slot : centeredDiamondLayout().slots()) {
            assertNotEquals(0, slot % 9, "slot " + slot + " must not sit in column zero");
        }
    }

    @Test
    void diamondPattern_pageOne_fillsSequentiallyFromFirstItem() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwenty());
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(1, 13).boxed().toList();
        assertMappedSourceValues(host, diamond, expectedValues, false);
    }

    @Test
    void getPageOfIndex_diamondLayout_mapsSequentialPages() {
        PatternPagination<Integer> pagination =
                diamondOnlyPagination(centeredDiamondLayout(), sourceOfTwentyOne());

        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(12));
        assertEquals(2, pagination.getPageOfIndex(13));
        assertEquals(2, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(21));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void diamondPattern_twentyOneItems_tailIndexReachableOnLastPage() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwentyOne());

        pagination.bind(newHost());

        assertEquals(2, pagination.getTotalPages());
        assertTrue(pagination.hasNextPage());

        pagination.changePage(2);
        pagination.insertPageItems();

        assertEquals(2, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void diamondPattern_allPages_unionCoversEverySourceValue() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwentyOne());
        FakePaginationHost host = newHost();

        pagination.bind(host);

        for (int page = 1; page <= pagination.getTotalPages(); page++) {
            pagination.changePage(page);
            pagination.insertPageItems();
        }

        Set<Integer> renderedValues = new HashSet<>(diamondAmounts(host.fillPageCalls()));

        IntStream.rangeClosed(1, 21).forEach(expected ->
                assertTrue(
                        renderedValues.contains(expected),
                        "source value " + expected + " must appear on some page"
                )
        );
    }

    @Test
    void diamondPattern_pageTwo_advancesSourceWindow() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOf(26));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.changePage(2);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(14, 26).boxed().toList();
        assertMappedSourceValues(host, diamond, expectedValues, true);
    }

    @Test
    void mixedGridAndOrderedSlotsPatterns_pageTwo_fillsOrderedLayoutInGivenOrder() {
        Layout diamond = centeredDiamondLayout(); // 13 slots, grid-based
        Layout snake = Layout.ofSlots(36, 27, 18, 9, 0, 1, 10, 19); // 8 slots
        PatternPagination<Integer> pagination = new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(diamond, snake),
                null,
                new EagerPageSource<>(sourceOf(21))); // 13 on the diamond page + 8 on the snake page
        FakePaginationHost host = newHost();

        pagination.bind(host);

        assertEquals(2, pagination.getTotalPages());

        pagination.changePage(2);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(14, 21).boxed().toList();
        assertMappedSourceValues(host, snake, expectedValues, true);
    }

    private static void assertMappedSourceValues(
            FakePaginationHost host,
            Layout layout,
            List<Integer> expectedValues,
            boolean lastFillPageOnly
    ) {
        List<FakePaginationHost.FillPageCall> calls = new ArrayList<>();
        for (FakePaginationHost.FillPageCall call : host.fillPageCalls()) {
            if (call.layout == layout) {
                calls.add(call);
            }
        }
        assertFalse(calls.isEmpty(), "expected at least one fillPage call for the layout");
        if (!lastFillPageOnly) {
            assertEquals(1, calls.size(), "expected exactly one fillPage call for the layout");
        }

        List<RenderedItem> items = calls.get(calls.size() - 1).items;
        assertEquals(expectedValues.size(), items.size());

        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(
                    expectedValues.get(i).intValue(),
                    items.get(i).plainItem().getAmount(),
                    "slot fill order index " + i
            );
        }
    }

    private static List<Integer> diamondAmounts(List<FakePaginationHost.FillPageCall> calls) {
        List<Integer> rendered = new ArrayList<>();
        for (FakePaginationHost.FillPageCall call : calls) {
            for (RenderedItem item : call.items) {
                ItemStack stack = item.plainItem();
                if (stack != null && stack.getType() == Material.DIAMOND) {
                    rendered.add(stack.getAmount());
                }
            }
        }
        return rendered;
    }

    private static RenderedItem glassFiller() {
        return RenderedItem.ofItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
    }

    private static Layout centeredDiamondLayout() {
        return Layout.ofGrid(
                "    O    ",
                "   OOO   ",
                "  OOOOO  ",
                "   OOO   ",
                "    O    ",
                "         "
        );
    }

    private static PatternPagination<Integer> diamondOnlyPagination(Layout diamond, List<Integer> source) {
        return new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(diamond),
                null,
                new EagerPageSource<>(source));
    }

    private static Paginator<Integer> samplePatternPagination(List<Integer> source) {
        return new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(
                        Layout.ofGrid(
                                "    O    ",
                                "   OOO   ",
                                "  OOOOO  ",
                                "   OOO   ",
                                "    O    ",
                                "         "
                        ),
                        Layout.ofGrid(
                                "         ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                "         "
                        ),
                        Layout.ofGrid(
                                "         ",
                                "         ",
                                "  OOOOO  ",
                                "         ",
                                "         ",
                                "         "
                        )),
                null,
                new EagerPageSource<>(source));
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static List<Integer> sourceOfTwenty() {
        return sourceOf(20);
    }

    private static List<Integer> sourceOfTwentyOne() {
        return sourceOf(21);
    }

    private static List<Integer> sourceOfFifty() {
        return sourceOf(50);
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }
}
