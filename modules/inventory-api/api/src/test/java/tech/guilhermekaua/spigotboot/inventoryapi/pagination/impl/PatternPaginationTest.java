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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.PatternPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PatternPaginationTest {

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
    void applyAndChangePage_withCenteredPatterns_doNotThrow() {
        Pagination<Integer> pagination = samplePatternPagination();
        Viewer viewer = mockViewer();

        pagination.init(viewer);
        pagination.setSource(sourceOfFifty());

        assertDoesNotThrow(pagination::apply);
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
    void centeredDiamondLayout_doesNotUseColumnZero() {
        assertFalse(centeredDiamondLayout().getColumnSizes().containsKey(0));
    }

    @Test
    void diamondPattern_pageOne_mapsCenteredSourceWindow() {
        InventoryLayout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOfTwenty());
        pagination.apply();

        List<Integer> expectedValues = IntStream.rangeClosed(3, 15).boxed().toList();
        assertMappedSourceValues(editor, diamond, pagination, expectedValues);
    }

    @Test
    void diamondPattern_twentyOneItems_tailIndexReachableOnLastPage() {
        InventoryLayout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond);

        pagination.init(mockViewer());
        pagination.setSource(sourceOfTwentyOne());

        assertEquals(4, pagination.getTotalPages());
        assertTrue(pagination.hasNextPage());

        pagination.changePage(4);
        pagination.apply();

        assertEquals(4, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void diamondPattern_allPages_unionCoversReachableSourceValues() {
        InventoryLayout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOfTwentyOne());

        for (int page = 1; page <= pagination.getTotalPages(); page++) {
            pagination.changePage(page);
            pagination.apply();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(itemsCaptor.capture(), eq(diamond), eq(pagination));

        Set<Integer> renderedValues = new HashSet<>();
        for (List<InventoryItem> items : itemsCaptor.getAllValues()) {
            for (InventoryItem item : items) {
                ItemStack stack = item.getItemStack();
                if (stack != null && stack.getType() == Material.DIAMOND) {
                    renderedValues.add(stack.getAmount());
                }
            }
        }

        IntStream.rangeClosed(3, 21).forEach(expected ->
                assertTrue(
                        renderedValues.contains(expected),
                        "source value " + expected + " must appear on some page"
                )
        );
    }

    @Test
    void diamondPattern_pageTwo_advancesSourceWindow() {
        InventoryLayout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOfTwenty());
        pagination.changePage(2);
        pagination.apply();

        List<Integer> expectedValues = IntStream.rangeClosed(5, 17).boxed().toList();
        assertMappedSourceValues(editor, diamond, pagination, expectedValues, true);
    }

    private static void assertMappedSourceValues(
            InventoryEditor editor,
            InventoryLayout layout,
            Pagination<Integer> pagination,
            List<Integer> expectedValues
    ) {
        assertMappedSourceValues(editor, layout, pagination, expectedValues, false);
    }

    private static void assertMappedSourceValues(
            InventoryEditor editor,
            InventoryLayout layout,
            Pagination<Integer> pagination,
            List<Integer> expectedValues,
            boolean lastFillPageOnly
    ) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        if (lastFillPageOnly) {
            verify(editor, atLeastOnce()).fillPage(itemsCaptor.capture(), eq(layout), eq(pagination));
        } else {
            verify(editor).fillPage(itemsCaptor.capture(), eq(layout), eq(pagination));
        }

        List<InventoryItem> items = lastFillPageOnly
                ? itemsCaptor.getAllValues().get(itemsCaptor.getAllValues().size() - 1)
                : itemsCaptor.getValue();
        assertEquals(expectedValues.size(), items.size());

        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(
                    expectedValues.get(i),
                    items.get(i).getItemStack().getAmount(),
                    "slot letter order index " + i
            );
        }
    }

    private static InventoryLayout centeredDiamondLayout() {
        return new InventoryLayout(
                "    O    ",
                "   OOO   ",
                "  OOOOO  ",
                "   OOO   ",
                "    O    ",
                "         "
        );
    }

    private static PatternPagination<Integer> diamondOnlyPagination(InventoryLayout diamond) {
        return new PatternPaginationBuilder<Integer>()
                .fallbackItem(viewer -> InventoryItem.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)))
                .pattern(diamond)
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private static List<Integer> sourceOfTwenty() {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, 20).forEach(source::add);
        return source;
    }

    private static List<Integer> sourceOfTwentyOne() {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, 21).forEach(source::add);
        return source;
    }

    private static Pagination<Integer> samplePatternPagination() {
        return new PatternPaginationBuilder<Integer>()
                .fallbackItem(viewer -> InventoryItem.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)))
                .pattern(new InventoryLayout(
                        "    O    ",
                        "   OOO   ",
                        "  OOOOO  ",
                        "   OOO   ",
                        "    O    ",
                        "         "
                ))
                .pattern(new InventoryLayout(
                        "         ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        "         "
                ))
                .pattern(new InventoryLayout(
                        "         ",
                        "         ",
                        "  OOOOO  ",
                        "         ",
                        "         ",
                        "         "
                ))
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private Viewer mockViewer() {
        return mockViewer(mock(InventoryEditor.class));
    }

    private Viewer mockViewer(InventoryEditor editor) {
        Viewer viewer = mock(Viewer.class);
        CustomInventory customInventory = mock(CustomInventory.class);

        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(customInventory);

        return viewer;
    }

    private static List<Integer> sourceOfFifty() {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, 50).forEach(source::add);
        return source;
    }
}
