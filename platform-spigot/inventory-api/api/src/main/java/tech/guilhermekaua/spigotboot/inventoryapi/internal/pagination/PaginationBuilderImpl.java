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
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@link PaginationBuilder} implementation behind {@code View.paginate*}: collects the
 * declaration fluently (value errors rejected at setter time), validates the combination
 * at {@link #build()}, resolves geometry and target, and constructs the registered
 * {@link PaginationImpl} token. One builder produces at most one token.
 *
 * @param <T> the element type
 */
@ApiStatus.Internal
public final class PaginationBuilderImpl<T> implements PaginationBuilder<T> {

    private static final char DEFAULT_LAYOUT_CHAR = 'O';
    private static final int DEFAULT_CACHE_MAX_PAGES = 128;

    private final View owner;
    private final TokenTable table;
    private final PaginationSourceSpec<T> source;

    private char layoutChar = DEFAULT_LAYOUT_CHAR;
    private boolean layoutCharCalled;
    private Layout explicitLayout;
    private boolean scroll;
    // null until patterns(...) was called; a non-null empty list records the explicit empty call
    private List<Layout> patterns;
    private PaginationItemRenderer<T> renderer;
    private Function<ViewContext, ItemStack> fallbackItem;
    private Function<ViewContext, ItemStack> loadingItem;
    private PaginationErrorCallback errorCallback;
    private Duration requestTimeout;
    private Duration cacheTtl;
    private int cacheMaxPages = DEFAULT_CACHE_MAX_PAGES;
    private boolean cacheMaxPagesCalled;
    private boolean built;

    /**
     * Creates a builder for one pagination declaration of a view.
     *
     * @param owner  the view declaring the pagination
     * @param table  the owner's token table; {@link #build()} registers into it
     * @param source the source declaration produced by the {@code View.paginate*} factory
     */
    public PaginationBuilderImpl(@NotNull View owner, @NotNull TokenTable table,
                                 @NotNull PaginationSourceSpec<T> source) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.table = Objects.requireNonNull(table, "table");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public @NotNull PaginationBuilder<T> layoutChar(char character) {
        this.layoutChar = character;
        this.layoutCharCalled = true;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> layout(@NotNull Layout layout) {
        this.explicitLayout = Objects.requireNonNull(layout, "layout");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> scroll() {
        this.scroll = true;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns) {
        Objects.requireNonNull(patterns, "patterns");
        List<Layout> copied = new ArrayList<>(patterns.length);
        for (Layout pattern : patterns) {
            copied.add(Objects.requireNonNull(pattern, "patterns must not contain null"));
        }
        this.patterns = copied;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item) {
        this.fallbackItem = Objects.requireNonNull(item, "item");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item) {
        this.loadingItem = Objects.requireNonNull(item, "item");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback) {
        this.errorCallback = Objects.requireNonNull(callback, "callback");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeout = timeout;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
        this.cacheTtl = ttl;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be at least 1");
        }
        this.cacheMaxPages = maxPages;
        this.cacheMaxPagesCalled = true;
        return this;
    }

    @Override
    public @NotNull Pagination<T> build() {
        if (built) {
            throw new IllegalStateException("build() may only be called once per paginate* call");
        }
        validate();

        PaginationSpec.Geometry geometry;
        PaginationSpec.Target target;
        if (patterns != null) {
            geometry = PaginationSpec.Geometry.PATTERN;
            target = PaginationSpec.Target.PATTERNS;
        } else {
            geometry = scroll ? PaginationSpec.Geometry.SCROLL : PaginationSpec.Geometry.NORMAL;
            target = explicitLayout != null
                    ? PaginationSpec.Target.EXPLICIT_LAYOUT
                    : PaginationSpec.Target.LAYOUT_CHAR;
        }

        PaginationSpec<T> spec = new PaginationSpec<>(geometry, target, layoutChar, explicitLayout,
                patterns == null ? Collections.<Layout>emptyList() : patterns, renderer, fallbackItem,
                loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages);
        // registration happens here, AT BUILD TIME: a frozen table throws before built is set
        PaginationImpl<T> token = new PaginationImpl<>(owner, table, spec);
        built = true;
        return token;
    }

    // build-time validation in the pinned order; value errors were already rejected at setter time
    private void validate() {
        if (renderer == null) {
            throw new ViewConfigurationException("pagination of view " + owner.getClass().getName()
                    + " declares no itemRenderer(...)");
        }
        if (patterns != null && (layoutCharCalled || explicitLayout != null || scroll)) {
            throw new ViewConfigurationException(
                    "patterns(...) cannot be combined with layoutChar(...), layout(...) or scroll()");
        }
        if (patterns != null && patterns.isEmpty()) {
            throw new ViewConfigurationException("patterns(...) requires at least one pattern layout");
        }
        if (patterns != null) {
            for (Layout pattern : patterns) {
                if (pattern.slots().isEmpty()) {
                    throw new ViewConfigurationException(
                            "every pattern layout must contain at least one slot");
                }
            }
        }
        if (explicitLayout != null && explicitLayout.slots().isEmpty()) {
            throw new ViewConfigurationException("layout(...) must contain at least one slot");
        }
        if (!source.isAsync()) {
            String asyncOnly = firstAsyncOnlyOption();
            if (asyncOnly != null) {
                throw new ViewConfigurationException(asyncOnly
                        + " is only legal on an async pagination source");
            }
        }
        if (cacheMaxPagesCalled && cacheTtl == null) {
            throw new ViewConfigurationException("cacheMaxPages(...) requires cacheTtl(...)");
        }
    }

    // the first async-only option used on this builder, or null when none was called
    private @Nullable String firstAsyncOnlyOption() {
        if (loadingItem != null) {
            return "loadingItem(...)";
        }
        if (errorCallback != null) {
            return "onError(...)";
        }
        if (requestTimeout != null) {
            return "requestTimeout(...)";
        }
        if (cacheTtl != null) {
            return "cacheTtl(...)";
        }
        if (cacheMaxPagesCalled) {
            return "cacheMaxPages(...)";
        }
        return null;
    }
}
