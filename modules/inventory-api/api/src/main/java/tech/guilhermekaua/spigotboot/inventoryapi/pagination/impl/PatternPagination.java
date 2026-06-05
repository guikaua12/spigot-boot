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
 * Paginates a source list across one or more {@link InventoryLayout} patterns, cycling through the
 * patterns as the viewer pages forward. Each page renders a contiguous slice of the source whose
 * length equals the slot count of that page's pattern, so every source item appears exactly once
 * across the pages with no gaps or overlap.
 *
 * <p>The pattern controls only <em>where</em> and in <em>what order</em> items are placed: slots are
 * filled in the letter order defined by {@link InventoryLayout}, which lets a pattern lay items out
 * horizontally, vertically, or in any custom order. Each row must be exactly
 * {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide.
 *
 * <p>{@link #changePage(int)} records the previous layout in {@code lastPattern} and clears its
 * slots before rendering the new page, so cycling between patterns of different sizes leaves no
 * residual items behind.
 */
@RequiredArgsConstructor
@Getter
public class PatternPagination<T> implements Pagination<T> {

    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final List<InventoryLayout> patterns;
    private Viewer viewer;
    private List<T> source = new LinkedList<>();
    private int currentPage = 1;
    private int itemPageLimit;
    private InventoryLayout currentPattern;
    private InventoryLayout lastPattern;

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.currentPattern = fromPage(currentPage);
        this.itemPageLimit = currentPattern.getSlots().size();
    }

    @Override
    public void apply() {
        insertPageItems();
    }

    @Override
    public void nextPage() {
        this.changePage(this.currentPage + 1);
    }

    @Override
    public boolean hasNextPage() {
        return this.currentPage + 1 <= this.getTotalPages();
    }

    @Override
    public void previousPage() {
        this.changePage(this.currentPage - 1);
    }

    @Override
    public boolean hasPreviousPage() {
        return hasPreviousPage(this.currentPage);
    }

    private boolean hasPreviousPage(int currentPage) {
        return currentPage > 1;
    }

    @Override
    public void insertPageItems() {
        InventoryEditor editor = this.viewer.getEditor();
        List<InventoryItem> inventoryItems = new LinkedList<>();

        int pageMaxIndex = this.getPageMaxIndex();
        int pageIndex = this.getPageIndex();

        for (int i = 0; i < this.itemPageLimit; i++, pageIndex++) {
            if (pageIndex < pageMaxIndex) {
                T current = this.source.get(pageIndex);
                InventoryItem item = this.itemSupplier.get(this.viewer, current);

                inventoryItems.add(item);
            } else {
                inventoryItems.add(fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer));
            }
        }

        editor.fillPage(inventoryItems, currentPattern, this);
    }

    private void clearLastPattern() {
        if (lastPattern == null) return;

        InventoryEditor editor = this.viewer.getEditor();
        List<InventoryItem> inventoryItems2 = new LinkedList<>();

        for (int i = 0; i < lastPattern.getSlots().size(); i++) {
            inventoryItems2.add(fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer));
        }

        editor.fillPage(inventoryItems2, lastPattern, this);
    }

    private InventoryLayout fromPage(int page) {
        return patterns.get((page - 1) % patterns.size());
    }

    @Override
    public void changePage(int page) {
        this.currentPage = Math.max(1, Math.min(page, this.getTotalPages()));

        this.lastPattern = currentPattern;
        this.currentPattern = fromPage(currentPage);

        this.itemPageLimit = currentPattern.getSlots().size();

        clearLastPattern();
        CustomInventory customInventory = viewer.getCustomInventory();
        customInventory.updateInventory(viewer.getPlayer());
    }

    @Override
    public int getTotalPages() {
        if (this.source.isEmpty()) {
            return 1;
        }

        int page = 1;
        int consumed = fromPage(page).getSlots().size();
        while (consumed < this.source.size()) {
            page++;
            consumed += fromPage(page).getSlots().size();
        }

        return page;
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.source.size()) {
            return -1;
        }

        int startIndex = 0;
        int totalPages = this.getTotalPages();
        for (int page = 1; page <= totalPages; page++) {
            int pageSize = fromPage(page).getSlots().size();
            if (index < startIndex + pageSize) {
                return page;
            }
            startIndex += pageSize;
        }

        return -1;
    }

    @Override
    public void setSource(List<T> source) {
        this.source = new ArrayList<>(source);
    }

    private int getPageIndex(int currentPage) {
        int startIndex = 0;
        for (int previousPage = 1; previousPage < currentPage; previousPage++) {
            startIndex += fromPage(previousPage).getSlots().size();
        }
        return startIndex;
    }

    private int getPageIndex() {
        return getPageIndex(currentPage);
    }

    private int getPageMaxIndex() {
        return getPageMaxIndex(currentPage, itemPageLimit);
    }

    private int getPageMaxIndex(int currentPage, int itemPageLimit) {
        return Math.min(getPageIndex(currentPage) + itemPageLimit, this.source.size());
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
