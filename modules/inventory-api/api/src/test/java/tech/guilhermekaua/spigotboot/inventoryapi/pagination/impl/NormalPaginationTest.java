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
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private static InventoryLayout nineSlotLayout() {
        return InventoryLayout.ofSlots(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static NormalPagination<Integer> eagerPagination() {
        return new NormalPaginationBuilder<Integer>()
                .layout(nineSlotLayout())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private Viewer mockViewer(InventoryEditor editor) {
        Viewer viewer = mock(Viewer.class);
        CustomInventory customInventory = mock(CustomInventory.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(customInventory);
        return viewer;
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void eagerFlow_rendersFirstPageSlice() {
        NormalPagination<Integer> pagination = eagerPagination();
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOf(20));
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));

        List<InventoryItem> items = captor.getValue();
        assertEquals(9, items.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, items.get(i).getItemStack().getAmount());
        }
    }

    @Test
    void eagerFlow_totalsAndNavigationMatchListSize() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        assertEquals(3, pagination.getTotalPages());
        assertEquals(20, pagination.getTotalElements());

        pagination.changePage(3);
        assertEquals(3, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void changePage_beyondTotal_clampsToLastPage() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        pagination.changePage(99);

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void getPageOfIndex_returnsMinusOneOutOfRange() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(8));
        assertEquals(2, pagination.getPageOfIndex(9));
        assertEquals(3, pagination.getPageOfIndex(19));
        assertEquals(-1, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void setSource_beforeInit_isHonoredByInit() {
        NormalPagination<Integer> pagination = eagerPagination();
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.setSource(sourceOf(5));
        pagination.init(mockViewer(editor));
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        assertEquals(1, captor.getValue().get(0).getItemStack().getAmount());
    }
}
