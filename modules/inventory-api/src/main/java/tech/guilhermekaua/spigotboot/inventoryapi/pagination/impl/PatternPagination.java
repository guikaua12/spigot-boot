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
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cycles through a list of {@link InventoryLayout} patterns as the viewer pages forward, with
 * column-aware indexing so logical items map sensibly across heterogeneous layouts.
 *
 * <p>Schedules a one-tick warmup on {@link #init(Viewer)} (changes to page 2 then back to 1)
 * to populate {@code lastPattern} for clean clears on the first real navigation.
 */
@RequiredArgsConstructor
@Getter
public class PatternPagination<T> implements Pagination<T> {
    public static final int COLUMN_CENTER = 4;
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

        Bukkit.getScheduler().runTaskLater(viewer.getPlugin(), () -> {
            changePage(2);
            changePage(1);
        }, 1L);
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
            if (pageIndex <= pageMaxIndex) {
                T current = this.source.get(pageIndex);
                InventoryItem item = this.itemSupplier.get(this.viewer, current);

                inventoryItems.add(item);
            } else {
                inventoryItems.add(fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer));
            }
        }

        inventoryItems.sort(Comparator.comparing(Objects::isNull));

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
        this.apply();
        CustomInventory customInventory = viewer.getCustomInventory();
        customInventory.updateInventory(viewer.getPlayer());
    }

    @Override
    public int getTotalPages() {
        int currentPage = 1;

        for (int i = 0; i < this.source.size(); i++) {
            if (hasEmptySpaces(currentPage + 1)) {
                return currentPage;
            }

            currentPage++;
        }

        return currentPage;
    }

    private boolean hasEmptySpaces(int currentPage) {
        int itemPageLimit = this.fromPage(currentPage).getSlots().size();
        int pageIndex = this.getPageIndex(currentPage);
        int pageMaxIndex = this.getPageMaxIndex(currentPage, itemPageLimit);

        for (int i = 0; i < itemPageLimit; i++, pageIndex++) {
            if (pageIndex > pageMaxIndex) {
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

        return index >= pageIndex && index <= pageMaxIndex;
    }

    private int getColumnOfIndex(int currentPage, int index) {
        if (!isIndexInPage(currentPage, index)) return -1;

        int pageIndex = this.getPageIndex(currentPage);

        final InventoryLayout layout = fromPage(currentPage);

        final Map<Integer, Integer> columnSizes = layout.getColumnSizes();

        int currentColumnIndex = pageIndex;
        int currentColumnIndexMax = pageIndex;

        for (final Map.Entry<Integer, Integer> entry : columnSizes.entrySet()) {
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
        this.source.clear();
        this.source = source;
    }

    private int getPageIndex(int currentPage) {
        int firstColumnItemSize = fromPage(currentPage).getColumnSizes().get(0);
        return ((currentPage - 1) * 5 + (5 - firstColumnItemSize)) / 2;
    }

    private int getPageIndex() {
        return getPageIndex(currentPage);
    }

    private int getPageEndIndex(int currentPage, int itemPageLimit) {
        return (getPageIndex(currentPage) + itemPageLimit) - 1;
    }

    private int getPageEndIndex() {
        return getPageEndIndex(currentPage, itemPageLimit);
    }

    private int getPageMaxIndex() {
        return getPageMaxIndex(currentPage, itemPageLimit);
    }

    private int getPageMaxIndex(int currentPage, int itemPageLimit) {
        return Math.min(this.getPageEndIndex(currentPage, itemPageLimit), this.source.size() - 1);
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
