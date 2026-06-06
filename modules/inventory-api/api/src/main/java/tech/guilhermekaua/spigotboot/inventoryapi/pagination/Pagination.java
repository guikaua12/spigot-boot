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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.List;

public interface Pagination<T> {
    /**
     * Initializes the paginator against a freshly opened viewer. Must be called once before any
     * navigation method is invoked.
     */
    void init(Viewer viewer);

    /**
     * Renders the items of the current page into the viewer's inventory.
     */
    void apply();

    /**
     * Advances to the next page if one exists.
     */
    void nextPage();

    /**
     * @return {@code true} if there is at least one page after the current one
     */
    boolean hasNextPage();

    /**
     * Returns to the previous page if one exists.
     */
    void previousPage();

    /**
     * @return {@code true} if there is at least one page before the current one
     */
    boolean hasPreviousPage();

    /**
     * Inserts the items of the current page into the viewer's inventory.
     */
    void insertPageItems();

    /**
     * Navigates directly to the given 1-indexed page.
     *
     * <p>The target is clamped to at least 1, and to {@code getTotalPages()} once the backing
     * source's totals are known. For async sources, navigation issued before the first load
     * completes is honored optimistically and re-clamped downward when totals arrive. A call that
     * targets the current page while a request for it is already in flight is ignored; use
     * {@link #refresh()} to force a reload. Before {@link #init(Viewer)} the call only records
     * the target page; {@code init} dispatches the load for it.
     */
    void changePage(int page);

    /**
     * @return the total number of pages backing the current source
     */
    int getTotalPages();

    /**
     * @return the 1-indexed current page number
     */
    int getCurrentPage();

    /**
     * @return the 1-indexed page containing the given global source index (for scroll paginators:
     * the first page on which the index becomes visible), or {@code -1} if the index is outside
     * {@code [0, getTotalElements())}
     */
    int getPageOfIndex(int index);

    /**
     * Replaces the backing source with an eager in-memory list and resets internal state
     * accordingly. On a paginator built with an async source this discards the async supplier —
     * the configured loading item, error callback, timeout and cache become inert.
     */
    void setSource(List<T> source);

    /**
     * @return an unmodifiable view of the elements currently loaded locally — the full backing
     * list for eager sources, the items of the most recently delivered page for async sources.
     * Mutations must go through {@link #setSource(List)}; use {@link #getTotalElements()} for
     * counts.
     */
    List<T> getSource();

    /**
     * @return the maximum number of items rendered per page
     */
    int getItemPageLimit();

    /**
     * @return the fallback item for empty slots, or {@code null} if none was configured
     */
    InventoryItem getFallbackItem();

    /**
     * @return {@code true} while an async page load for this paginator is in flight; always
     * {@code false} for eager sources
     */
    default boolean isLoading() {
        return false;
    }

    /**
     * @return the failure of the most recent async page load, or {@code null}; cleared when a new
     * load is dispatched. Always {@code null} for eager sources.
     */
    default Throwable lastError() {
        return null;
    }

    /**
     * @return the total number of elements in the backing source, independent of how many are
     * loaded locally
     */
    default int getTotalElements() {
        return getSource().size();
    }

    /**
     * Re-requests the current page, invalidating any cached copy first. For async sources this is
     * the supported idiom to re-query after the backing store changed; for eager sources it
     * re-renders the current page.
     */
    default void refresh() {
        changePage(getCurrentPage());
    }
}
