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
 * Skeleton for {@link Pagination} implementations backed by a {@link PageSource}. Owns the
 * machinery shared by every pagination type: request dispatch with inline-settle detection,
 * transactional navigation with rollback on failed loads, downward re-clamping when totals
 * shrink, loading-frame rendering and the eager {@code setSource} switch.
 *
 * <p>Subclasses contribute the per-type geometry: how the current page maps to a request offset
 * ({@link #requestOffset()}), which layout the page renders into ({@link #renderLayout()}), and
 * which navigation state must be snapshotted for rollback ({@link #navigationSnapshot()} /
 * {@link #restoreNavigation(Object)} / {@link #commitNavigation(int)}).
 *
 * <p>Settle failures are logged through a per-class JUL logger (visible in the server console)
 * rather than a plugin logger, since a viewer may not be bound when they occur.
 *
 * @param <T> the source element type
 * @param <S> the navigation state snapshot used for failure rollback
 */
@Getter
abstract class AbstractPageSourcePagination<T, S> implements Pagination<T> {

    /**
     * Named after the concrete class so warnings point at the actual pagination type.
     */
    @Getter(AccessLevel.NONE)
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected final InventoryItemSupplier fallbackItem;
    protected final GenericInventoryItemSupplier<T> itemSupplier;
    @Getter(AccessLevel.NONE)
    protected final InventoryItemSupplier loadingItem;
    @Getter(AccessLevel.NONE)
    protected PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    // inline-settle detection assumes a single thread dispatches for this paginator at a time
    // (the main thread by default); concurrent dispatches may misclassify an inline settle as
    // asynchronous, costing only a redundant render
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    protected Viewer viewer;
    protected int currentPage = 1;
    protected int itemPageLimit;

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    AbstractPageSourcePagination(InventoryItemSupplier fallbackItem,
                                 GenericInventoryItemSupplier<T> itemSupplier,
                                 InventoryItemSupplier loadingItem,
                                 PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemSupplier = itemSupplier;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
    }

    /**
     * Derives the layout state (page limit, pattern, ...) for the current page. Invoked by
     * {@link #init(Viewer)} after the viewer is bound and before the initial request dispatch.
     */
    protected abstract void initNavigationState();

    /**
     * @return the global element offset of the first item of the current page
     */
    protected abstract int requestOffset();

    /**
     * @return a snapshot of the navigation state to restore if the dispatched load fails
     */
    protected abstract S navigationSnapshot();

    /**
     * Restores the navigation state captured by {@link #navigationSnapshot()} after a failed
     * load, including clearing anything the failed navigation already painted.
     *
     * @param snapshot the pre-dispatch navigation state
     */
    protected abstract void restoreNavigation(S snapshot);

    /**
     * Commits navigation to the already-clamped target page: updates the current page and any
     * per-type layout state. Only invoked with a viewer bound.
     *
     * @param target the 1-indexed page to commit
     */
    protected abstract void commitNavigation(int target);

    /**
     * @return the layout the current page renders into
     */
    protected abstract InventoryLayout renderLayout();

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        initNavigationState();
        dispatch(navigationSnapshot(), false);
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
        return this.currentPage > 1;
    }

    @Override
    public void insertPageItems() {
        InventoryEditor editor = this.viewer.getEditor();
        InventoryLayout layout = renderLayout();
        int limit = layout.getSlots().size();
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<InventoryItem> inventoryItems = new LinkedList<>();

        for (int i = 0; i < limit; i++) {
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
        // no viewer bound yet: record the target only; init derives the layout state for it
        // and dispatches the load
        if (this.viewer == null) {
            this.currentPage = target;
            return;
        }
        S rollback = navigationSnapshot();
        commitNavigation(target);
        dispatch(rollback, true);
    }

    private void dispatch(S rollback, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit, requestOffset(), this.viewer);
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollback, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }

    private void onSettle(S rollback, PageResult<T> result, Throwable error) {
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                restoreNavigation(rollback);
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
            logger.log(Level.WARNING, "Failed to apply a settled page load.", t);
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
    public void setSource(List<T> source) {
        if (this.pageSource instanceof AsyncPageSource) {
            logger.warning("setSource(List) called on an async-built pagination: the async supplier"
                    + " (and its loading item, error callback, timeout and cache) is discarded.");
        }
        this.pageSource = new EagerPageSource<>(source);
        this.currentItems = Collections.emptyList();
        if (this.viewer != null) {
            dispatch(navigationSnapshot(), false);
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

    /**
     * @return the configured fallback item, or an empty item when none was set
     */
    protected final InventoryItem emptyOrFallback() {
        return fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer);
    }

    /**
     * @return the configured loading item, falling back to {@link #emptyOrFallback()}
     */
    protected final InventoryItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get(viewer) : emptyOrFallback();
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
