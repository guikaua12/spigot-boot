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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Sliding-window paginator. The source list is held fixed, and each page advances the visible
 * window by one position rather than chunking into discrete pages.
 */
@RequiredArgsConstructor
@Getter
public class ScrollPagination<T> implements Pagination<T> {
    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final InventoryLayout layout;
    private Viewer viewer;
    private List<T> source = new LinkedList<>();
    private int currentPage = 1;
    private int itemPageLimit;

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.itemPageLimit = layout.getSlots().size();
    }

    @Override
    public void apply() {
        insertPageItems();
    }

    @Override
    public void nextPage() {
        this.changePage(currentPage + 1);
    }

    @Override
    public boolean hasNextPage() {
        return this.currentPage < getTotalPages();
    }

    @Override
    public void previousPage() {
        this.changePage(currentPage - 1);
    }

    @Override
    public boolean hasPreviousPage() {
        return this.getPageIndex() > 0;
    }

    @Override
    public void insertPageItems() {
        InventoryEditor editor = this.viewer.getEditor();

        int pageIndex = this.getPageIndex();

        List<InventoryItem> itemList = new ArrayList<>();

        for (int i = 0; i < this.itemPageLimit; i++, pageIndex++) {
            if (pageIndex < this.source.size()) {
                T current = this.source.get(pageIndex);
                InventoryItem item = this.itemSupplier.get(this.viewer, current);
                itemList.add(item);
            } else {
                itemList.add(fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer));
            }
        }

        editor.fillPage(itemList, layout, this);
    }

    @Override
    public void changePage(int page) {
        this.currentPage = Math.max(1, Math.min(page, this.getTotalPages()));
        CustomInventory customInventory = viewer.getCustomInventory();
        customInventory.updateInventory(viewer.getPlayer());
    }

    @Override
    public int getTotalPages() {
        int pageSize = this.source.size();
        return Math.max(1, (pageSize - itemPageLimit) + 1);
    }

    @Override
    public int getPageOfIndex(int index) {
        return index / itemPageLimit + 1;
    }

    @Override
    public void setSource(List<T> source) {
        this.source = new ArrayList<>(source);
    }

    private int getPageIndex() {
        return (currentPage - 1);
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
