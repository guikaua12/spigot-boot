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
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.LinkedList;
import java.util.List;

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
public class PatternPagination<T> extends AbstractPageSourcePagination<T, PatternPagination.PatternState> {

    private final List<InventoryLayout> patterns;
    @Getter(AccessLevel.NONE)
    private final int cycleSize;
    private InventoryLayout currentPattern;
    private InventoryLayout lastPattern;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param patterns     the layout patterns cycled across pages, not null or empty
     */
    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns) {
        this(fallbackItem, itemSupplier, patterns, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param patterns     the layout patterns cycled across pages, not null or empty
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns,
                             InventoryItemSupplier loadingItem,
                             PageSource<T> pageSource) {
        super(fallbackItem, itemSupplier, loadingItem, pageSource);
        this.patterns = patterns;
        int slots = 0;
        for (InventoryLayout pattern : patterns) {
            slots += pattern.getSlots().size();
        }
        this.cycleSize = slots;
    }

    @Override
    protected void initNavigationState() {
        this.currentPattern = fromPage(this.currentPage);
        this.itemPageLimit = this.currentPattern.getSlots().size();
    }

    @Override
    protected int requestOffset() {
        return getPageIndex(this.currentPage);
    }

    @Override
    protected PatternState navigationSnapshot() {
        return new PatternState(this.currentPage, this.currentPattern, this.lastPattern, this.itemPageLimit);
    }

    @Override
    protected void restoreNavigation(PatternState snapshot) {
        // clear the failed pattern's slots before restoring, so the last good items are not
        // left scrambled into the failed pattern's shape
        clearPattern(this.currentPattern);
        this.currentPage = snapshot.page;
        this.currentPattern = snapshot.currentPattern;
        this.lastPattern = snapshot.lastPattern;
        this.itemPageLimit = snapshot.itemPageLimit;
    }

    @Override
    protected void commitNavigation(int target) {
        this.currentPage = target;
        this.lastPattern = this.currentPattern;
        this.currentPattern = fromPage(target);
        this.itemPageLimit = this.currentPattern.getSlots().size();
        clearPattern(this.lastPattern);
    }

    @Override
    protected InventoryLayout renderLayout() {
        return this.currentPattern;
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

    /**
     * Pre-dispatch navigation state restored when a page load fails.
     */
    static final class PatternState {
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
