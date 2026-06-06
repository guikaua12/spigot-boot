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

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sliding-window paginator. Each "page" advances the visible window by one source element rather
 * than chunking into discrete pages. Pages come from a {@link PageSource}: an in-memory list by
 * default, or an async supplier configured through the builder's {@code async(...)} method.
 */
@Getter
public class ScrollPagination<T> implements Pagination<T> {

    private static final Logger LOGGER = Logger.getLogger(ScrollPagination.class.getName());

    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final InventoryLayout layout;
    @Getter(AccessLevel.NONE)
    private final InventoryItemSupplier loadingItem;
    @Getter(AccessLevel.NONE)
    private PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    private Viewer viewer;
    private int currentPage = 1;
    private int itemPageLimit;

    public ScrollPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout) {
        this(fallbackItem, itemSupplier, layout, null, EagerPageSource.empty());
    }

    public ScrollPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout,
                            InventoryItemSupplier loadingItem,
                            PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemSupplier = itemSupplier;
        this.layout = layout;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
    }

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.itemPageLimit = layout.getSlots().size();
        dispatch(this.currentPage, false);
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
        return this.currentPage < getTotalPages();
    }

    @Override
    public void previousPage() {
        this.changePage(this.currentPage - 1);
    }

    @Override
    public boolean hasPreviousPage() {
        return this.currentPage > 1;
    }

    @Override
    public void insertPageItems() {
        InventoryEditor editor = this.viewer.getEditor();
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<InventoryItem> inventoryItems = new LinkedList<>();

        for (int i = 0; i < this.itemPageLimit; i++) {
            if (loading) {
                inventoryItems.add(loadingOrFallback());
            } else if (i < items.size()) {
                inventoryItems.add(this.itemSupplier.get(this.viewer, items.get(i)));
            } else {
                inventoryItems.add(emptyOrFallback());
            }
        }

        editor.fillPage(inventoryItems, layout, this);
    }

    @Override
    public void changePage(int page) {
        changePageInternal(page, false);
    }

    private void changePageInternal(int page, boolean forceDispatch) {
        int target = Math.max(1, page);
        if (this.pageSource.totalsKnown()) {
            target = Math.min(target, this.getTotalPages());
        }
        if (!forceDispatch && target == this.currentPage && this.pageSource.isLoading()) {
            return;
        }
        // no viewer bound yet: record the target only; init dispatches the load for it
        if (this.viewer == null) {
            this.currentPage = target;
            return;
        }
        int rollbackPage = this.currentPage;
        this.currentPage = target;
        dispatch(rollbackPage, true);
    }

    private void dispatch(int rollbackPage, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit,
                this.currentPage - 1, this.viewer);
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollbackPage, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }

    private void onSettle(int rollbackPage, PageResult<T> result, Throwable error) {
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                this.currentPage = rollbackPage;
            } else {
                this.currentItems = result.getItems();
                if (this.pageSource.totalsKnown() && this.currentPage > this.getTotalPages()) {
                    changePageInternal(this.getTotalPages(), true);
                    return;
                }
            }
            if (!inline) {
                renderIfOnline();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to apply a settled page load.", t);
        }
    }

    private void renderIfOnline() {
        Viewer viewer = this.viewer;
        if (viewer == null) {
            return;
        }
        Player player = viewer.getPlayer();
        if (player == null) {
            return;
        }
        CustomInventory customInventory = viewer.getCustomInventory();
        if (customInventory == null) {
            return;
        }
        customInventory.updateInventory(player);
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        return Math.max(1, (total - this.itemPageLimit) + 1);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        return Math.max(1, index - this.itemPageLimit + 2);
    }

    @Override
    public void setSource(List<T> source) {
        if (this.pageSource instanceof AsyncPageSource) {
            LOGGER.warning("setSource(List) called on an async-built pagination: the async supplier"
                    + " (and its loading item, error callback, timeout and cache) is discarded.");
        }
        this.pageSource = new EagerPageSource<>(source);
        this.currentItems = Collections.emptyList();
        if (this.viewer != null) {
            dispatch(this.currentPage, false);
        }
    }

    @Override
    public List<T> getSource() {
        return this.pageSource.elements();
    }

    @Override
    public boolean isLoading() {
        return this.pageSource.isLoading();
    }

    @Override
    public Throwable lastError() {
        return this.pageSource.lastError();
    }

    @Override
    public int getTotalElements() {
        return this.pageSource.totalElements();
    }

    @Override
    public void refresh() {
        this.pageSource.invalidate();
        changePageInternal(this.currentPage, true);
    }

    private InventoryItem emptyOrFallback() {
        return fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer);
    }

    private InventoryItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get(viewer) : emptyOrFallback();
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
