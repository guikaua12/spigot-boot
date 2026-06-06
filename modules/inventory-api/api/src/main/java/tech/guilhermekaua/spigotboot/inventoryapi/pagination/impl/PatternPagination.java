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
 * Paginates a source list across one or more {@link InventoryLayout} patterns, cycling through the
 * patterns as the viewer pages forward. Each page renders a contiguous slice of the source whose
 * length equals the slot count of that page's pattern, so every source item appears exactly once
 * across the pages with no gaps or overlap.
 *
 * <p>The pattern controls only <em>where</em> and in <em>what order</em> items are placed: slots are
 * filled in the order of the layout's {@link InventoryLayout#getSlots()} list, which lets a pattern
 * lay items out horizontally, vertically, or in any custom order. Grid-based layouts derive that
 * order from their letters; ordered-slots layouts state it explicitly.
 *
 * <p>{@link #changePage(int)} records the previous layout in {@code lastPattern} and clears its
 * slots before rendering the new page, so cycling between patterns of different sizes leaves no
 * residual items behind. Pages come from a {@link PageSource}: an in-memory list by default, or an
 * async supplier configured through the builder's {@code async(...)} method.
 */
@Getter
public class PatternPagination<T> implements Pagination<T> {

    private static final Logger LOGGER = Logger.getLogger(PatternPagination.class.getName());

    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final List<InventoryLayout> patterns;
    @Getter(AccessLevel.NONE)
    private final int cycleSize;
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
    private InventoryLayout currentPattern;
    private InventoryLayout lastPattern;

    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns) {
        this(fallbackItem, itemSupplier, patterns, null, EagerPageSource.empty());
    }

    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns,
                             InventoryItemSupplier loadingItem,
                             PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemSupplier = itemSupplier;
        this.patterns = patterns;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
        int slots = 0;
        for (InventoryLayout pattern : patterns) {
            slots += pattern.getSlots().size();
        }
        this.cycleSize = slots;
    }

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.currentPattern = fromPage(currentPage);
        this.itemPageLimit = currentPattern.getSlots().size();
        dispatch(snapshotState(), false);
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
        InventoryLayout pattern = this.currentPattern;
        int limit = pattern.getSlots().size();
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

        editor.fillPage(inventoryItems, pattern, this);
    }

    private void clearPattern(InventoryLayout pattern) {
        if (pattern == null) return;

        InventoryEditor editor = this.viewer.getEditor();
        List<InventoryItem> fillers = new LinkedList<>();

        for (int i = 0; i < pattern.getSlots().size(); i++) {
            fillers.add(emptyOrFallback());
        }

        editor.fillPage(fillers, pattern, this);
    }

    private InventoryLayout fromPage(int page) {
        return patterns.get((page - 1) % patterns.size());
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
        // no viewer bound yet: record the target only; init derives the pattern state for it
        // and dispatches the load
        if (this.viewer == null) {
            this.currentPage = target;
            return;
        }
        PatternState rollback = snapshotState();

        this.currentPage = target;
        this.lastPattern = this.currentPattern;
        this.currentPattern = fromPage(this.currentPage);
        this.itemPageLimit = this.currentPattern.getSlots().size();
        clearPattern(this.lastPattern);

        dispatch(rollback, true);
    }

    private void dispatch(PatternState rollback, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit, getPageIndex(), this.viewer);
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

    private void onSettle(PatternState rollback, PageResult<T> result, Throwable error) {
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                clearPattern(this.currentPattern);
                this.currentPage = rollback.page;
                this.currentPattern = rollback.currentPattern;
                this.lastPattern = rollback.lastPattern;
                this.itemPageLimit = rollback.itemPageLimit;
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

    private PatternState snapshotState() {
        return new PatternState(this.currentPage, this.currentPattern, this.lastPattern, this.itemPageLimit);
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
        if (total == 0) {
            return 1;
        }
        int fullCycles = total / this.cycleSize;
        int remainder = total % this.cycleSize;
        int pages = fullCycles * this.patterns.size();
        int consumed = 0;
        int patternIndex = 0;
        while (consumed < remainder) {
            consumed += this.patterns.get(patternIndex).getSlots().size();
            patternIndex++;
            pages++;
        }
        return Math.max(1, pages);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        int fullCycles = index / this.cycleSize;
        int remainder = index % this.cycleSize;
        int page = fullCycles * this.patterns.size() + 1;
        int consumed = 0;
        int patternIndex = 0;
        while (remainder >= consumed + this.patterns.get(patternIndex).getSlots().size()) {
            consumed += this.patterns.get(patternIndex).getSlots().size();
            patternIndex++;
            page++;
        }
        return page;
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
            dispatch(snapshotState(), false);
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

    private int getPageIndex(int page) {
        int completedPages = page - 1;
        int fullCycles = completedPages / this.patterns.size();
        int partial = completedPages % this.patterns.size();
        int offset = fullCycles * this.cycleSize;
        for (int i = 0; i < partial; i++) {
            offset += this.patterns.get(i).getSlots().size();
        }
        return offset;
    }

    private int getPageIndex() {
        return getPageIndex(currentPage);
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

    private static final class PatternState {
        private final int page;
        private final InventoryLayout currentPattern;
        private final InventoryLayout lastPattern;
        private final int itemPageLimit;

        private PatternState(int page, InventoryLayout currentPattern,
                             InventoryLayout lastPattern, int itemPageLimit) {
            this.page = page;
            this.currentPattern = currentPattern;
            this.lastPattern = lastPattern;
            this.itemPageLimit = itemPageLimit;
        }
    }
}
