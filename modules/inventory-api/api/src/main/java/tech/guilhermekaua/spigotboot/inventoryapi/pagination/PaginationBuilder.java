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

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.function.Function;

/**
 * Fluent declaration of one pagination token, returned by the {@code View.paginate*}
 * factories. Builder methods only record the declaration; nothing is registered or validated
 * as a whole until {@link #build()}, which validates the combination, registers the token with
 * the owning view and returns it.
 *
 * <p><strong>Defaults.</strong> When neither {@link #layoutChar(char)}, {@link #layout(Layout)}
 * nor {@link #patterns(Layout...)} is called, the pagination targets layout char {@code 'O'}.
 * The default geometry is normal (page-by-page); {@link #scroll()} switches to a sliding
 * window, {@link #patterns(Layout...)} to cycled per-page patterns.
 *
 * <p><strong>Async-only options.</strong> {@link #loadingItem(Function)},
 * {@link #onError(PaginationErrorCallback)}, {@link #requestTimeout(Duration)},
 * {@link #cacheTtl(Duration)} and {@link #cacheMaxPages(int)} are only legal on a builder
 * created by {@code View.paginateAsync}; calling any of them on another builder makes
 * {@link #build()} throw {@link ViewConfigurationException}.
 *
 * <p>All methods return this builder for chaining and reject {@code null} arguments with
 * {@link NullPointerException}. Value errors fail at setter time with
 * {@link IllegalArgumentException}; combination errors fail at {@link #build()} with
 * {@link ViewConfigurationException}.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.NonExtendable
public interface PaginationBuilder<T> {

    /**
     * Targets every slot of the given character in the view's layout, in row-major order.
     * Defaults to {@code 'O'} when never called. The character must exist in the view's
     * layout; both "no layout at all" and "char absent from the layout" are validated at view
     * registration time. An explicit {@link #layout(Layout)} silently overrides this value;
     * combining an explicit call with {@link #patterns(Layout...)} fails at {@link #build()}.
     *
     * @param character the layout character whose slots receive the page items
     * @return this builder
     */
    @NotNull PaginationBuilder<T> layoutChar(char character);

    /**
     * Sets an explicit fill order, silently overriding {@link #layoutChar(char)}. An empty
     * layout, or combining with {@link #patterns(Layout...)}, fails at {@link #build()} with
     * {@link ViewConfigurationException}.
     *
     * @param layout the explicit fill order for the page items
     * @return this builder
     */
    @NotNull PaginationBuilder<T> layout(@NotNull Layout layout);

    /**
     * Switches to sliding-window geometry: each page slides the visible window by exactly one
     * element instead of jumping a full page. Cannot be combined with
     * {@link #patterns(Layout...)} — that fails at {@link #build()}.
     *
     * @return this builder
     */
    @NotNull PaginationBuilder<T> scroll();

    /**
     * Switches to pattern geometry: page {@code p} paints into the slots of pattern
     * {@code (p - 1) % patterns.length}, cycling through the given patterns. Cannot be
     * combined with an explicit {@link #layoutChar(char)} call, {@link #layout(Layout)} or
     * {@link #scroll()}; an empty array or any empty pattern also fails — all at
     * {@link #build()} with {@link ViewConfigurationException}.
     *
     * @param patterns the per-page slot patterns, cycled in order
     * @return this builder
     */
    @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns);

    /**
     * Sets the per-element renderer. Required — a declaration without a renderer fails at
     * {@link #build()} with {@link ViewConfigurationException}.
     *
     * @param renderer renders one element of the current page into its component builder
     * @return this builder
     */
    @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer);

    /**
     * Sets the item painted into page slots not covered by an element (for example the tail
     * of a short last page) and into slots whose element failed on its very first paint.
     * Evaluated against the session's context at paint time. When absent, uncovered slots are
     * cleared instead.
     *
     * @param item the fallback item factory
     * @return this builder
     */
    @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item);

    /**
     * Sets the item painted into every page slot while an async load is in flight. Async-only:
     * on a non-async builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param item the loading placeholder factory
     * @return this builder
     */
    @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item);

    /**
     * Sets the callback invoked when an async page load fails. Async-only: on a non-async
     * builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param callback the failure callback
     * @return this builder
     */
    @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback);

    /**
     * Enables a per-request timeout: a load exceeding it fails with a
     * {@code TimeoutException} and follows the normal error path. Async-only: on a non-async
     * builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param timeout the timeout, must be positive
     * @return this builder
     * @throws IllegalArgumentException if {@code timeout} is zero or negative
     */
    @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout);

    /**
     * Enables page caching: revisiting a page within the TTL renders from cache without
     * calling the supplier; {@link Pagination#refresh(ViewContext)} invalidates the cache.
     * Async-only: on a non-async builder {@link #build()} throws
     * {@link ViewConfigurationException}.
     *
     * @param ttl the cache entry freshness window, must be positive
     * @return this builder
     * @throws IllegalArgumentException if {@code ttl} is zero or negative
     */
    @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl);

    /**
     * Bounds the page cache (least-recently-used eviction). Defaults to 128. Requires
     * {@link #cacheTtl(Duration)} — setting it without a TTL fails at {@link #build()} with
     * {@link ViewConfigurationException}. Async-only: on a non-async builder {@link #build()}
     * throws {@link ViewConfigurationException}.
     *
     * @param maxPages the maximum number of cached pages, at least 1
     * @return this builder
     * @throws IllegalArgumentException if {@code maxPages} is below 1
     */
    @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages);

    /**
     * Validates the declaration, constructs the token and registers it with the owning view.
     * Like every token registration this is legal only while the view's tokens are still
     * open for registration — i.e. from field initializers or the view constructor; the
     * builder construction itself (the {@code paginate*} call) never registers anything, only
     * this method does.
     *
     * @return the registered pagination token
     * @throws ViewConfigurationException when the renderer is missing; when
     *         {@link #patterns(Layout...)} is combined with an explicit
     *         {@link #layoutChar(char)} call, {@link #layout(Layout)} or {@link #scroll()};
     *         when the patterns array or any single pattern, or an explicit layout, is empty;
     *         when an async-only option was used on a non-async source; or when
     *         {@link #cacheMaxPages(int)} was set without {@link #cacheTtl(Duration)}
     * @throws IllegalStateException when called a second time on the same builder
     *         ("build() may only be called once per paginate* call") or after the view's
     *         token table froze
     */
    @NotNull Pagination<T> build();
}
