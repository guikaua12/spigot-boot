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
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.ScrollPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScrollPaginationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ScrollPagination<Integer> sevenSlotScroll() {
        return new ScrollPaginationBuilder<Integer>()
                .layout(InventoryLayout.ofSlots(0, 1, 2, 3, 4, 5, 6))
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private static Viewer mockViewer(InventoryEditor editor) {
        Viewer viewer = mock(Viewer.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getCustomInventory()).thenReturn(mock(CustomInventory.class));
        return viewer;
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void totals_windowOfSevenOverTen_isFourPages() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(10));

        assertEquals(4, pagination.getTotalPages());
    }

    @Test
    void pageTwo_slidesWindowByOneElement() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        InventoryEditor editor = mock(InventoryEditor.class);
        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOf(10));

        pagination.changePage(2);
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        List<InventoryItem> items = captor.getAllValues().get(captor.getAllValues().size() - 1);

        // window slid by ONE element: values 2..8
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 2, items.get(i).getItemStack().getAmount());
        }
    }

    @Test
    void getPageOfIndex_usesFirstVisiblePageSemantics() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(10)); // window 7 => 4 pages

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
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(3));

        assertEquals(1, pagination.getTotalPages());
        assertEquals(1, pagination.getPageOfIndex(2));
    }
}
