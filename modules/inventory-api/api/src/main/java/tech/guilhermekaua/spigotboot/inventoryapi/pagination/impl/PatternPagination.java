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
import java.util.Map;
import java.util.TreeMap;

/**
 * Cycles through a list of {@link InventoryLayout} patterns as the viewer pages forward, with
 * column-aware indexing so logical items map sensibly across heterogeneous layouts.
 *
 * <p>Layouts are expected to follow the same constraints as {@link InventoryLayout}: each row is
 * exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide, and content rows used for
 * vertical paging are {@link #PATTERN_CONTENT_ROW_COUNT} tall with center-symmetric columns so
 * {@link #COLUMN_CENTER} (the middle column of a chest row) anchors page placement. Arbitrary
 * patterns with uneven column heights can skip or duplicate source indices.
 *
 * <p>{@link #changePage(int)} records the previous layout in {@code lastPattern} and clears
 * its slots before rendering the new page.
 */
@RequiredArgsConstructor
@Getter
public class PatternPagination<T> implements Pagination<T> {

    /**
     * Zero-based column index at the horizontal center of a 9-wide chest row.
     */
    public static final int COLUMN_CENTER = 4;

    /**
     * Number of content rows used when advancing the source window between pages.
     */
    private static final int PATTERN_CONTENT_ROW_COUNT = 5;
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

    private boolean hasNextPage(int currentPage) {
        return currentPage + 1 <= this.getTotalPages();
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

        int totalPages = 1;
        int highestExclusiveEnd = getPageMaxIndex(1, fromPage(1).getSlots().size());

        while (highestExclusiveEnd < this.source.size()) {
            int nextPage = totalPages + 1;
            if (getPageIndex(nextPage) >= this.source.size()) {
                break;
            }

            int nextPageLimit = fromPage(nextPage).getSlots().size();
            int nextExclusiveEnd = getPageMaxIndex(nextPage, nextPageLimit);

            if (nextExclusiveEnd <= highestExclusiveEnd) {
                break;
            }

            totalPages = nextPage;
            highestExclusiveEnd = nextExclusiveEnd;
        }

        return totalPages;
    }

    private boolean hasEmptySpaces(int currentPage) {
        int itemPageLimit = this.fromPage(currentPage).getSlots().size();
        int pageIndex = this.getPageIndex(currentPage);
        int pageMaxIndex = this.getPageMaxIndex(currentPage, itemPageLimit);

        for (int i = 0; i < itemPageLimit; i++, pageIndex++) {
            if (pageIndex >= pageMaxIndex) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getPageOfIndex(int index) {
        for (int currentPage = 1; currentPage <= this.source.size(); currentPage++) {
            final int columnOfIndex = getColumnOfIndex(currentPage, index);

            if (columnOfIndex == -1) continue;

            if (columnOfIndex == COLUMN_CENTER || (columnOfIndex < COLUMN_CENTER && !hasPreviousPage(currentPage)) || (columnOfIndex > COLUMN_CENTER && !hasNextPage(currentPage))) {
                return currentPage;
            }
        }

        return -1;
    }

    private boolean isIndexInPage(int currentPage, int index) {
        int itemPageLimit = this.fromPage(currentPage).getSlots().size();
        int pageIndex = this.getPageIndex(currentPage);
        int pageMaxIndex = this.getPageMaxIndex(currentPage, itemPageLimit);

        return index >= pageIndex && index < pageMaxIndex;
    }

    private int getColumnOfIndex(int currentPage, int index) {
        if (!isIndexInPage(currentPage, index)) return -1;

        int pageIndex = this.getPageIndex(currentPage);

        final InventoryLayout layout = fromPage(currentPage);

        final Map<Integer, Integer> columnSizes = layout.getColumnSizes();

        int currentColumnIndex = pageIndex;
        int currentColumnIndexMax = pageIndex;

        for (final Map.Entry<Integer, Integer> entry : new TreeMap<>(columnSizes).entrySet()) {
            final int columnSize = entry.getValue();

            currentColumnIndex = currentColumnIndexMax;
            currentColumnIndexMax = currentColumnIndexMax + columnSize;

            if (index >= currentColumnIndex && index <= currentColumnIndexMax - 1) {
                return entry.getKey();
            }

        }

        return -1;
    }

    @Override
    public void setSource(List<T> source) {
        this.source = new ArrayList<>(source);
    }

    private int getPageIndex(int currentPage) {
        int firstColumnItemSize = getFirstColumnItemSize(fromPage(currentPage));
        return ((currentPage - 1) * PATTERN_CONTENT_ROW_COUNT
                + (PATTERN_CONTENT_ROW_COUNT - firstColumnItemSize)) / 2;
    }

    private static int getFirstColumnItemSize(InventoryLayout layout) {
        Map<Integer, Integer> columnSizes = layout.getColumnSizes();
        if (columnSizes.isEmpty()) {
            return 0;
        }

        int leftmostColumn = columnSizes.keySet().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

        return columnSizes.getOrDefault(leftmostColumn, 0);
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
