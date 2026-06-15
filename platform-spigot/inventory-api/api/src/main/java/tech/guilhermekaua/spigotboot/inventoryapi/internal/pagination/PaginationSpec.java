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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable pagination declaration built by {@code PaginationBuilderImpl.build()}: geometry,
 * paint target, renderer and frame items, the source declaration, and the async plumbing
 * consumed by {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec, tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler)}. All
 * combination validation happens in the builder before a spec is constructed; the spec only
 * carries the validated values.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.Internal
public final class PaginationSpec<T> {

    /**
     * How elements are distributed across pages.
     */
    public enum Geometry {
        /**
         * Page-by-page: page {@code p} serves elements {@code (p-1)*limit .. p*limit-1}.
         */
        NORMAL,
        /**
         * Sliding window: each page slides the visible window by exactly one element.
         */
        SCROLL,
        /**
         * Cycled per-page slot patterns.
         */
        PATTERN
    }

    /**
     * Where the page items are painted.
     */
    public enum Target {
        /**
         * All slots of one character of the view's layout, row-major.
         */
        LAYOUT_CHAR,
        /**
         * An explicit {@link Layout} fill order.
         */
        EXPLICIT_LAYOUT,
        /**
         * The cycled per-page patterns.
         */
        PATTERNS
    }

    private final Geometry geometry;
    private final Target target;
    private final char layoutChar;
    private final Layout explicitLayout;
    private final List<Layout> patterns;
    private final PaginationItemRenderer<T> renderer;
    private final Function<ViewContext, ItemStack> fallbackItem;
    private final Function<ViewContext, ItemStack> loadingItem;
    private final PaginationSourceSpec<T> source;
    private final PaginationErrorCallback errorCallback;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final int cacheMaxPages;

    /**
     * Creates a spec. Package-private: only {@code PaginationBuilderImpl} and same-package
     * tests construct specs, after the builder validated the combination.
     *
     * @param geometry       the page geometry
     * @param target         the paint target
     * @param layoutChar     the target layout character; meaningful only when {@code target}
     *                       is {@link Target#LAYOUT_CHAR}; {@code 'O'} by default
     * @param explicitLayout the explicit fill order; non-null only when {@code target} is
     *                       {@link Target#EXPLICIT_LAYOUT}
     * @param patterns       the per-page patterns, defensively copied; empty unless
     *                       {@code target} is {@link Target#PATTERNS}
     * @param renderer       the per-element renderer
     * @param fallbackItem   the frame item for uncovered page slots, or null to clear them
     * @param loadingItem    the async loading frame item, or null
     * @param source         the source declaration
     * @param errorCallback  the async error callback, or null
     * @param requestTimeout the async per-request timeout, or null to disable
     * @param cacheTtl       the async cache TTL, or null to disable caching
     * @param cacheMaxPages  the async cache LRU bound; 128 unless overridden (the 2.x default)
     * @throws NullPointerException if {@code geometry}, {@code target}, {@code patterns},
     *                              {@code renderer} or {@code source} is null
     */
    PaginationSpec(@NotNull Geometry geometry,
                   @NotNull Target target,
                   char layoutChar,
                   @Nullable Layout explicitLayout,
                   @NotNull List<Layout> patterns,
                   @NotNull PaginationItemRenderer<T> renderer,
                   @Nullable Function<ViewContext, ItemStack> fallbackItem,
                   @Nullable Function<ViewContext, ItemStack> loadingItem,
                   @NotNull PaginationSourceSpec<T> source,
                   @Nullable PaginationErrorCallback errorCallback,
                   @Nullable Duration requestTimeout,
                   @Nullable Duration cacheTtl,
                   int cacheMaxPages) {
        this.geometry = Objects.requireNonNull(geometry, "geometry is required.");
        this.target = Objects.requireNonNull(target, "target is required.");
        this.layoutChar = layoutChar;
        this.explicitLayout = explicitLayout;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(patterns, "patterns is required.")));
        this.renderer = Objects.requireNonNull(renderer, "renderer is required.");
        this.fallbackItem = fallbackItem;
        this.loadingItem = loadingItem;
        this.source = Objects.requireNonNull(source, "source is required.");
        this.errorCallback = errorCallback;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.cacheMaxPages = cacheMaxPages;
    }

    /**
     * Returns the page geometry.
     *
     * @return the geometry
     */
    public @NotNull Geometry geometry() {
        return geometry;
    }

    /**
     * Returns the paint target.
     *
     * @return the target
     */
    public @NotNull Target target() {
        return target;
    }

    /**
     * Returns the target layout character, meaningful only when {@link #target()} is
     * {@link Target#LAYOUT_CHAR}.
     *
     * @return the layout character, {@code 'O'} by default
     */
    public char layoutChar() {
        return layoutChar;
    }

    /**
     * Returns the explicit fill order.
     *
     * @return the explicit layout, or null unless {@link #target()} is
     *         {@link Target#EXPLICIT_LAYOUT}
     */
    public @Nullable Layout explicitLayout() {
        return explicitLayout;
    }

    /**
     * Returns the per-page patterns.
     *
     * @return an unmodifiable list; empty unless {@link #target()} is {@link Target#PATTERNS}
     */
    public @NotNull List<Layout> patterns() {
        return patterns;
    }

    /**
     * Returns the per-element renderer.
     *
     * @return the renderer
     */
    public @NotNull PaginationItemRenderer<T> renderer() {
        return renderer;
    }

    /**
     * Returns the frame item painted into page slots not covered by an element.
     *
     * @return the fallback item factory, or null when uncovered slots are cleared
     */
    public @Nullable Function<ViewContext, ItemStack> fallbackItem() {
        return fallbackItem;
    }

    /**
     * Returns the frame item painted into every page slot while an async load is in flight.
     *
     * @return the loading item factory, or null
     */
    public @Nullable Function<ViewContext, ItemStack> loadingItem() {
        return loadingItem;
    }

    /**
     * Returns the source declaration.
     *
     * @return the source spec
     */
    public @NotNull PaginationSourceSpec<T> source() {
        return source;
    }

    /**
     * Returns the async error callback, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec, tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler)}.
     *
     * @return the callback, or null
     */
    public @Nullable PaginationErrorCallback errorCallback() {
        return errorCallback;
    }

    /**
     * Returns the async per-request timeout, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec, tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler)}.
     *
     * @return the timeout, or null when disabled
     */
    public @Nullable Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * Returns the async cache TTL, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec, tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler)}.
     *
     * @return the TTL, or null when caching is disabled
     */
    public @Nullable Duration cacheTtl() {
        return cacheTtl;
    }

    /**
     * Returns the async cache LRU bound, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec, tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler)}.
     *
     * @return the bound; 128 unless overridden (the 2.x {@code DEFAULT_CACHE_MAX_PAGES})
     */
    public int cacheMaxPages() {
        return cacheMaxPages;
    }
}
