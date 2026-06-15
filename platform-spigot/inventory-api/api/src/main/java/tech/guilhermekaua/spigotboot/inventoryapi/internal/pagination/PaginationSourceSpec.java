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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable source declaration of one pagination token: which of the four source kinds the
 * view declared, and how to construct the {@link PageSource} for one context.
 * {@link Kind#EAGER_STATIC} carries the ONE {@link EagerPageSource} shared by every context —
 * safe because that source is immutable; every other kind constructs a fresh source per
 * context, preserving per-context isolation.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.Internal
public final class PaginationSourceSpec<T> {

    /**
     * The four ways a view can declare where page elements come from.
     */
    public enum Kind {
        /**
         * {@code View.paginate(List)}: one immutable in-memory source shared by every context.
         */
        EAGER_STATIC,
        /**
         * {@code View.paginate(Function)}: the function runs once per context at init (and
         * again on refresh), each result wrapped in a fresh eager source.
         */
        EAGER_LAZY,
        /**
         * {@code View.paginateAsync(AsyncPageSupplier)}: a fresh async source per context.
         */
        ASYNC,
        /**
         * {@code View.paginateSource(Function)}: the escape hatch, the factory runs once per
         * context.
         */
        CUSTOM
    }

    private final Kind kind;
    private final PageSource<T> sharedEagerSource;
    private final Function<ViewContext, List<T>> lazyFunction;
    private final AsyncPageSupplier<T> asyncSupplier;
    private final Function<ViewContext, PageSource<T>> customFactory;

    /**
     * Creates a source spec. Package-private: the static factories are the only entry points;
     * exactly the field matching {@code kind} is non-null.
     *
     * @param kind              the source kind
     * @param sharedEagerSource the shared source; non-null only for {@link Kind#EAGER_STATIC}
     * @param lazyFunction      the per-context list function; non-null only for
     *                          {@link Kind#EAGER_LAZY}
     * @param asyncSupplier     the async supplier; non-null only for {@link Kind#ASYNC}
     * @param customFactory     the source factory; non-null only for {@link Kind#CUSTOM}
     */
    PaginationSourceSpec(@NotNull Kind kind,
                         @Nullable PageSource<T> sharedEagerSource,
                         @Nullable Function<ViewContext, List<T>> lazyFunction,
                         @Nullable AsyncPageSupplier<T> asyncSupplier,
                         @Nullable Function<ViewContext, PageSource<T>> customFactory) {
        this.kind = Objects.requireNonNull(kind, "kind is required.");
        this.sharedEagerSource = sharedEagerSource;
        this.lazyFunction = lazyFunction;
        this.asyncSupplier = asyncSupplier;
        this.customFactory = customFactory;
    }

    /**
     * Declares a static eager source. The ONE shared {@link EagerPageSource} every context
     * will use is built immediately; the defensive copy of {@code source} is taken by the
     * {@link EagerPageSource} constructor itself, so later mutation of the caller's list
     * never leaks into pages.
     *
     * @param <T>    the element type
     * @param source the backing elements
     * @return the source spec
     * @throws NullPointerException if {@code source} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> eager(@NotNull List<T> source) {
        Objects.requireNonNull(source, "source is required.");
        return new PaginationSourceSpec<>(Kind.EAGER_STATIC, new EagerPageSource<>(source), null, null, null);
    }

    /**
     * Declares a lazy eager source: {@code source} runs once per context at initialization
     * (and again on {@code Pagination.refresh}), each result wrapped in a fresh
     * {@link EagerPageSource}.
     *
     * @param <T>    the element type
     * @param source produces the backing elements for one context
     * @return the source spec
     * @throws NullPointerException if {@code source} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> lazy(@NotNull Function<ViewContext, List<T>> source) {
        Objects.requireNonNull(source, "source is required.");
        return new PaginationSourceSpec<>(Kind.EAGER_LAZY, null, source, null, null);
    }

    /**
     * Declares an async source: a fresh {@link AsyncPageSource} is constructed per context
     * from the supplier and the owning spec's async options.
     *
     * @param <T>      the element type
     * @param supplier loads pages on demand
     * @return the source spec
     * @throws NullPointerException if {@code supplier} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> async(@NotNull AsyncPageSupplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier is required.");
        return new PaginationSourceSpec<>(Kind.ASYNC, null, null, supplier, null);
    }

    /**
     * Declares a custom source: the factory runs once per context and its result is used
     * as-is.
     *
     * @param <T>     the element type
     * @param factory produces the page source for one context
     * @return the source spec
     * @throws NullPointerException if {@code factory} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> custom(@NotNull Function<ViewContext, PageSource<T>> factory) {
        Objects.requireNonNull(factory, "factory is required.");
        return new PaginationSourceSpec<>(Kind.CUSTOM, null, null, null, factory);
    }

    /**
     * Returns the source kind.
     *
     * @return the kind
     */
    public @NotNull Kind kind() {
        return kind;
    }

    /**
     * Returns whether this is an async declaration — the gate for the async-only builder
     * options.
     *
     * @return {@code true} when {@link #kind()} is {@link Kind#ASYNC}
     */
    public boolean isAsync() {
        return kind == Kind.ASYNC;
    }

    /**
     * Returns the lazy list function, used by the {@code Pagination.refresh} path to
     * re-evaluate the source.
     *
     * @return the function; non-null only for {@link Kind#EAGER_LAZY}
     */
    public @Nullable Function<ViewContext, List<T>> lazyFunction() {
        return lazyFunction;
    }

    /**
     * Creates (or returns) the {@link PageSource} for one context.
     *
     * <ul>
     *   <li>{@link Kind#EAGER_STATIC}: returns the single shared {@link EagerPageSource}
     *       built by {@link #eager(List)}.</li>
     *   <li>{@link Kind#EAGER_LAZY}: invokes the source function with {@code context} and
     *       wraps the result in a fresh {@link EagerPageSource} (whose constructor takes the
     *       defensive copy).</li>
     *   <li>{@link Kind#ASYNC}: constructs a fresh {@link AsyncPageSource} from the supplier
     *       and the spec's async options, dispatching settles through a
     *       {@link BukkitSettleDispatcher} backed by the given scheduler.</li>
     *   <li>{@link Kind#CUSTOM}: invokes the factory with {@code context} and returns its
     *       result as-is.</li>
     * </ul>
     *
     * @param context   the context the source will serve
     * @param spec      the owning spec, read for the async options
     * @param scheduler the platform scheduler used to route async settles to the viewer's
     *                  region thread; passed through to the {@link BukkitSettleDispatcher}
     *                  for {@link Kind#ASYNC} sources
     * @return the page source for this context
     * @throws NullPointerException if the lazy function returns null
     *         ("lazy pagination source function returned null"), or the custom factory
     *         returns null ("paginateSource factory returned null"), or an argument is null
     */
    public @NotNull PageSource<T> createSource(@NotNull ViewContext context, @NotNull PaginationSpec<T> spec,
                                               @NotNull PlatformScheduler scheduler) {
        Objects.requireNonNull(context, "context is required.");
        Objects.requireNonNull(spec, "spec is required.");
        Objects.requireNonNull(scheduler, "scheduler is required.");
        switch (kind) {
            case EAGER_STATIC:
                return sharedEagerSource;
            case EAGER_LAZY:
                return new EagerPageSource<>(Objects.requireNonNull(lazyFunction.apply(context),
                        "lazy pagination source function returned null"));
            case ASYNC:
                return new AsyncPageSource<>(asyncSupplier, spec.errorCallback(), spec.requestTimeout(),
                        spec.cacheTtl(), spec.cacheMaxPages(), new BukkitSettleDispatcher(scheduler));
            case CUSTOM:
                return Objects.requireNonNull(customFactory.apply(context),
                        "paginateSource factory returned null");
            default:
                // unreachable: the enum is exhaustive, but javac requires the branch
                throw new IllegalStateException("unknown source kind: " + kind);
        }
    }
}
