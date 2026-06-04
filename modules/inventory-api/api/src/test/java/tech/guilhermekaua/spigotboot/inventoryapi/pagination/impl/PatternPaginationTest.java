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
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        InventoryLayout layout = new InventoryLayout(
                "    O    ",
                "   OOO   ",
                "  OOOOO  ",
                "   OOO   ",
                "    O    ",
                "         "
        );

        assertFalse(layout.getColumnSizes().containsKey(0));
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
        Viewer viewer = mock(Viewer.class);
        InventoryEditor editor = mock(InventoryEditor.class);
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
