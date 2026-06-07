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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

/**
 * Internal page-source geometry engine: navigation, page-load dispatch and frame painting
 * over a {@link PaginationHost}. This is the relocated 2.x {@code Pagination} interface,
 * renamed because the public {@code pagination.Pagination} name now belongs to the reactive
 * token.
 *
 * @param <T> the source element type
 */
@ApiStatus.Internal
public interface Paginator<T> {

    /**
     * Binds the engine against a freshly opened host: sets the host, derives the navigation
     * state for the current (possibly pre-recorded) page and dispatches the initial load
     * without requesting a render — the open's initial paint covers it.
     *
     * <p>Must be called once before any painting method is invoked. Eager sources settle
     * inline during bind, so their items are ready before the first paint; async sources
     * leave {@link #isLoading()} {@code true} so the first paint shows the loading frame.
     *
     * @param host the world-facing seam the engine paints and renders through
     */
    void bind(@NotNull PaginationHost host);

    /**
     * Paints the items of the current page through {@link PaginationHost#fillPage}.
     */
    void insertPageItems();

    /**
     * Navigates directly to the given 1-indexed page.
     *
     * <p>The target is clamped to at least 1, and to {@link #getTotalPages()} once the backing
     * source's totals are known. For async sources, navigation issued before the first load
     * completes is honored optimistically and re-clamped downward when totals arrive. A call
     * that targets the current page while a request for it is already in flight is ignored;
     * use {@link #refresh()} to force a reload. Before {@link #bind(PaginationHost)} the call
     * only records the target page; {@code bind} dispatches the load for it.
     *
     * @param page the 1-indexed page to navigate to
     */
    void changePage(int page);

    /**
     * Advances to the next page if one exists.
     */
    void nextPage();

    /**
     * Returns whether forward navigation is possible.
     *
     * @return {@code true} if there is at least one page after the current one
     */
    boolean hasNextPage();

    /**
     * Returns to the previous page if one exists.
     */
    void previousPage();

    /**
     * Returns whether backward navigation is possible.
     *
     * @return {@code true} if there is at least one page before the current one
     */
    boolean hasPreviousPage();

    /**
     * Returns the page count of the current source.
     *
     * @return the total number of pages backing the current source, at least 1
     */
    int getTotalPages();

    /**
     * Returns the current page.
     *
     * @return the 1-indexed current page number
     */
    int getCurrentPage();

    /**
     * Returns the element count of the current source.
     *
     * @return the total number of elements in the backing source, independent of how many
     * are loaded locally
     */
    int getTotalElements();

    /**
     * Returns the page capacity.
     *
     * @return the maximum number of items rendered per page
     */
    int getItemPageLimit();

    /**
     * Maps a global source index onto the page it appears on.
     *
     * @param index the global element index
     * @return the 1-indexed page containing the given index (for scroll paginators: the first
     * page on which the index becomes visible), or {@code -1} if the index is outside
     * {@code [0, getTotalElements())}
     */
    int getPageOfIndex(int index);

    /**
     * Returns the in-flight state of the source.
     *
     * @return {@code true} while an async page load for this paginator is in flight; always
     * {@code false} for eager sources
     */
    boolean isLoading();

    /**
     * Returns the most recent load failure.
     *
     * @return the failure of the most recent async page load, or {@code null}; cleared when a
     * new load is dispatched. Always {@code null} for eager sources.
     */
    @Nullable Throwable lastError();

    /**
     * Re-requests the current page, invalidating any cached copy first. For async sources this
     * is the supported idiom to re-query after the backing store changed; for eager sources it
     * re-renders the current page.
     */
    void refresh();

    /**
     * Swaps the backing source and clears the locally held items. Dispatches nothing and
     * renders nothing — callers drive the re-request; a swap before
     * {@link #bind(PaginationHost)} is picked up by the bind-time dispatch.
     *
     * @param source the new page source, not null
     * @throws NullPointerException if {@code source} is null
     */
    void replaceSource(@NotNull PageSource<T> source);
}
