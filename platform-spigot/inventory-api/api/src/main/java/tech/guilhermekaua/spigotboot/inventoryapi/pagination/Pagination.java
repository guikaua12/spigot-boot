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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

/**
 * Reactive pagination token, declared once per view through the {@code View.paginate*} factories
 * and built by {@link PaginationBuilder#build()}. The token itself holds no paging state: every
 * method reads or mutates the state of the session behind the given {@link ViewContext}, so a
 * single declaration serves every viewer with fully isolated per-context paging.
 *
 * <p>As a {@link StateToken} the token can be watched via
 * {@code ItemComponentBuilder.updateOnStateChange(StateToken...)}: watching components re-render
 * whenever a page load settles or a navigation repaints the pagination area.
 *
 * <p><strong>Pre-init window.</strong> Between {@code onOpen} and the engine's pagination
 * initialization (which runs before {@code onFirstRender}) the token is not yet backed by a
 * paging engine. In that window reads return defaults — {@link #totalPages} is {@code 1},
 * {@link #totalElements} is {@code 0}, {@link #isLoading} is {@code false} and
 * {@link #lastError} is {@code null} — and {@link #advance}, {@link #back} and
 * {@link #switchTo} record a pending target page (never below 1) that is replayed once
 * initialization completes; {@link #currentPage} reports that pending target.
 *
 * <p><strong>Threading.</strong> {@link #advance}, {@link #back}, {@link #switchTo} and
 * {@link #refresh} are main-thread only and throw {@link IllegalStateException} when invoked
 * off the main server thread. Reads are unsynchronized and only coherent on the main thread.
 *
 * <p>Every method first validates the context: a context belonging to a different view class or
 * to an already closed session fails with {@link StaleContextException}.
 *
 * @param <T> the element type served by the backing page source
 */
@ApiStatus.NonExtendable
public interface Pagination<T> extends StateToken {

    /**
     * Returns the current page, 1-indexed. Before initialization this is the pending
     * navigation target.
     *
     * @param context the context of the session to read
     * @return the current 1-indexed page
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int currentPage(@NotNull ViewContext context);

    /**
     * Returns the total page count, always at least 1. Before initialization — and, for async
     * sources, before the first successful load reveals the totals — this is {@code 1}.
     *
     * @param context the context of the session to read
     * @return the total page count, {@code >= 1}
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int totalPages(@NotNull ViewContext context);

    /**
     * Returns the total element count of the backing source. Before initialization — and, for
     * async sources, before totals are known — this is {@code 0}.
     *
     * @param context the context of the session to read
     * @return the total element count
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int totalElements(@NotNull ViewContext context);

    /**
     * Returns whether a next page exists, i.e. {@code currentPage + 1 <= totalPages}. Always
     * {@code false} before initialization.
     *
     * @param context the context of the session to read
     * @return {@code true} when {@link #advance} would move forward
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean canAdvance(@NotNull ViewContext context);

    /**
     * Returns whether a previous page exists, i.e. {@code currentPage > 1}. Before
     * initialization this reports whether the pending target is above page 1.
     *
     * @param context the context of the session to read
     * @return {@code true} when {@link #back} would move backward
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean canBack(@NotNull ViewContext context);

    /**
     * Navigates one page forward, clamped exactly like {@link #switchTo}. Before
     * initialization the pending target is incremented instead. Main thread only.
     *
     * @param context the context of the session to navigate
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void advance(@NotNull ViewContext context);

    /**
     * Navigates one page backward, clamped exactly like {@link #switchTo}. Before
     * initialization the pending target is decremented instead (never below 1). Main thread
     * only.
     *
     * @param context the context of the session to navigate
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void back(@NotNull ViewContext context);

    /**
     * Switches to the given page. The target is clamped exactly as the 2.x {@code changePage}:
     * lower-clamped to page 1 always, upper-clamped to {@link #totalPages} only once the
     * source's totals are known (always for eager sources; after the first successful load for
     * async sources — an overshooting target is re-clamped downward when that load settles).
     * Re-requesting the page already shown is a no-op while that page is still loading. Before
     * initialization the lower-clamped target is recorded and replayed at initialization. Main
     * thread only.
     *
     * @param context the context of the session to navigate
     * @param page    the 1-indexed target page; out-of-range values are clamped, not rejected
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void switchTo(@NotNull ViewContext context, int page);

    /**
     * Returns whether the latest page request has not settled yet. Always {@code false} for
     * eager sources and before initialization.
     *
     * @param context the context of the session to read
     * @return {@code true} while a page load is in flight
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean isLoading(@NotNull ViewContext context);

    /**
     * Returns the failure of the most recently settled page load, or {@code null}; cleared
     * when a new request is dispatched. Always {@code null} for eager sources and before
     * initialization.
     *
     * @param context the context of the session to read
     * @return the last page-load failure, or {@code null}
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    @Nullable Throwable lastError(@NotNull ViewContext context);

    /**
     * Forces a reload of the current page. Before initialization this is a no-op. After
     * initialization the behavior depends on the source kind: lazy sources
     * ({@code View.paginate(Function)}) re-invoke the source function against this context and
     * swap the fresh result in; async sources invalidate their page cache; every kind then
     * re-requests the current page, forced — the same-page dedupe is bypassed. Main thread
     * only.
     *
     * @param context the context of the session to refresh
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void refresh(@NotNull ViewContext context);
}
