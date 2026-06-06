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
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

/**
 * Page-by-page paginator. Each page renders a contiguous slice of the source, clamped to
 * {@code itemPageLimit} (the number of slots in the configured layout). Pages come from a
 * {@link PageSource}: an in-memory list by default, or an async supplier configured through the
 * builder's {@code async(...)} method.
 */
@Getter
public class NormalPagination<T> extends AbstractPageSourcePagination<T, Integer> {

    private final InventoryLayout layout;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param layout       the slots the page renders into, not null
     */
    public NormalPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout) {
        this(fallbackItem, itemSupplier, layout, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param layout       the slots the page renders into, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public NormalPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout,
                            InventoryItemSupplier loadingItem,
                            PageSource<T> pageSource) {
        super(fallbackItem, itemSupplier, loadingItem, pageSource);
        this.layout = layout;
    }

    @Override
    protected void initNavigationState() {
        this.itemPageLimit = layout.getSlots().size();
    }

    @Override
    protected int requestOffset() {
        return (this.currentPage - 1) * this.itemPageLimit;
    }

    @Override
    protected Integer navigationSnapshot() {
        return this.currentPage;
    }

    @Override
    protected void restoreNavigation(Integer snapshot) {
        this.currentPage = snapshot;
    }

    @Override
    protected void commitNavigation(int target) {
        this.currentPage = target;
    }

    @Override
    protected InventoryLayout renderLayout() {
        return this.layout;
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        if (total == 0) {
            return 1;
        }
        return (int) (((long) total + this.itemPageLimit - 1) / this.itemPageLimit);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        return index / this.itemPageLimit + 1;
    }
}
