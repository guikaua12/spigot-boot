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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skeleton for {@link Paginator} implementations backed by a {@link PageSource}. Owns the
 * machinery shared by every pagination type: request dispatch with inline-settle detection,
 * transactional navigation with rollback on failed loads, downward re-clamping when totals
 * shrink, loading-frame rendering and the source swap behind {@link #replaceSource(PageSource)}.
 *
 * <p>Subclasses contribute the per-type geometry: how the current page maps to a request offset
 * ({@link #requestOffset()}), which layout the page renders into ({@link #renderLayout()}), and
 * which navigation state must be snapshotted for rollback ({@link #navigationSnapshot()} /
 * {@link #restoreNavigation(Object)} / {@link #commitNavigation(int)}).
 *
 * <p>Settle failures are logged through a per-class JUL logger (visible in the server console)
 * rather than a plugin logger, since a host may not be bound when they occur.
 *
 * @param <T> the source element type
 * @param <S> the navigation state snapshot used for failure rollback
 */
@Getter
abstract class AbstractPageSourcePagination<T, S> implements Paginator<T> {

    /**
     * Named after the concrete class so warnings point at the actual pagination type.
     */
    @Getter(AccessLevel.NONE)
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected final Supplier<RenderedItem> fallbackItem;
    protected final PageItemFactory<T> itemFactory;
    @Getter(AccessLevel.NONE)
    protected final Supplier<RenderedItem> loadingItem;
    @Getter(AccessLevel.NONE)
    protected PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    // inline-settle detection assumes a single thread dispatches for this paginator at a time
    // (the main thread by default); concurrent dispatches may misclassify an inline settle as
    // asynchronous, costing only a redundant render
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    protected PaginationHost host;
    protected int currentPage = 1;
    protected int itemPageLimit;

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    AbstractPageSourcePagination(@Nullable Supplier<RenderedItem> fallbackItem,
                                 @NotNull PageItemFactory<T> itemFactory,
                                 @Nullable Supplier<RenderedItem> loadingItem,
                                 @NotNull PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemFactory = itemFactory;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
    }

    /**
     * Derives the layout state (page limit, pattern, ...) for the current page. Invoked by
     * {@link #bind(PaginationHost)} after the host is bound and before the initial request
     * dispatch.
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
     * per-type layout state. Only invoked with a host bound.
     *
     * @param target the 1-indexed page to commit
     */
    protected abstract void commitNavigation(int target);

    /**
     * @return the layout the current page renders into
     */
    protected abstract Layout renderLayout();

    @Override
    public void bind(@NotNull PaginationHost host) {
        this.host = host;
        initNavigationState();
        dispatch(navigationSnapshot(), false);
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
        Layout layout = renderLayout();
        int limit = layout.slots().size();
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<RenderedItem> renderedItems = new LinkedList<>();

        for (int i = 0; i < limit; i++) {
            if (loading) {
                renderedItems.add(loadingOrFallback());
            } else if (i < items.size()) {
                renderedItems.add(this.itemFactory.create(i, items.get(i)));
            } else {
                renderedItems.add(emptyOrFallback());
            }
        }

        this.host.fillPage(renderedItems, layout);
    }

    @Override
    public void changePage(int page) {
        changePageInternal(page, false);
    }

    private void changePageInternal(int page, boolean forceDispatch) {
        // no host bound yet: record the target only; bind derives the layout state for it and
        // dispatches the load. this branch sits BEFORE the totals-known clamp on purpose (a
        // deliberate reorder of the 2.x body): pre-bind itemPageLimit is 0, so the clamp's
        // getTotalPages() would divide by zero for a non-empty eager source; the clamp is
        // unnecessary here anyway — there is never an in-flight request pre-bind, and an
        // overshooting recorded target is corrected by the settle's downward re-clamp
        if (this.host == null) {
            this.currentPage = Math.max(1, page);
            return;
        }
        int target = Math.max(1, page);
        if (this.pageSource.totalsKnown()) {
            target = Math.min(target, this.getTotalPages());
        }
        if (!forceDispatch && target == this.currentPage && this.pageSource.isLoading()) {
            return;
        }
        S rollback = navigationSnapshot();
        commitNavigation(target);
        dispatch(rollback, true);
    }

    private void dispatch(S rollback, boolean render) {
        PageRequest request = new PageRequest(this.currentPage, this.itemPageLimit,
                requestOffset(), this.host.playerId(), this.host.plugin());
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
        PaginationHost host = this.host;
        if (host == null || !host.isActive()) {
            return;
        }
        host.requestRender();
    }

    @Override
    public void replaceSource(@NotNull PageSource<T> source) {
        this.pageSource = Objects.requireNonNull(source, "source is required.");
        this.currentItems = Collections.emptyList();
    }

    @Override
    public boolean isLoading() {
        return this.pageSource.isLoading();
    }

    @Override
    public @Nullable Throwable lastError() {
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
     * @return the configured fallback item, or a slot clear when none was set
     */
    protected final @NotNull RenderedItem emptyOrFallback() {
        return fallbackItem == null ? RenderedItem.ofItem(null) : fallbackItem.get();
    }

    /**
     * @return the configured loading item, falling back to {@link #emptyOrFallback()}
     */
    protected final @NotNull RenderedItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get() : emptyOrFallback();
    }
}
