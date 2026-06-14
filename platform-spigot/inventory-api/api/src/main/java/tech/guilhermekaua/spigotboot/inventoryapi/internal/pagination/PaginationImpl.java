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
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.Paginator;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.ContextStateAccess;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.util.ThreadUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.Objects;

/**
 * The {@link Pagination} token implementation: a per-view singleton declared at view
 * construction time whose per-context runtime lives in a {@link PaginationBinding} stored
 * in this token's own {@code StateStore} slot. Every method resolves the binding through
 * the shared {@link ContextStateAccess#storeFor(ViewContext, View)} guard, then delegates
 * to the pending-navigation recorder before initialization and to the bound
 * {@link Paginator} afterwards; mutators additionally assert the main thread.
 *
 * @param <T> the element type
 */
@ApiStatus.Internal
public final class PaginationImpl<T> implements Pagination<T>, IdentifiableToken {

    private final View owner;
    private final PaginationSpec<T> spec;
    private final int id;

    /**
     * Creates and registers the token; called by {@link PaginationBuilderImpl#build()}, so
     * registration happens at build time.
     *
     * @param owner the view declaring the token
     * @param table the owner's token table
     * @param spec  the immutable pagination declaration
     * @throws IllegalStateException when the table is already frozen
     */
    public PaginationImpl(@NotNull View owner, @NotNull TokenTable table, @NotNull PaginationSpec<T> spec) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id, the index into the per-session state store.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    @Override
    public int tokenId() {
        return id;
    }

    /**
     * Returns the immutable declaration this token was built from.
     *
     * @return the pagination spec
     */
    public @NotNull PaginationSpec<T> spec() {
        return spec;
    }

    @Override
    public int currentPage(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return binding.pendingTarget();
        }
        return paginatorOf(binding).getCurrentPage();
    }

    @Override
    public int totalPages(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return 1;
        }
        return paginatorOf(binding).getTotalPages();
    }

    @Override
    public int totalElements(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return 0;
        }
        return paginatorOf(binding).getTotalElements();
    }

    @Override
    public boolean canAdvance(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return false;
        }
        return paginatorOf(binding).hasNextPage();
    }

    @Override
    public boolean canBack(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return binding.pendingTarget() > 1;
        }
        return paginatorOf(binding).hasPreviousPage();
    }

    @Override
    public void advance(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.advance");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(binding.pendingTarget() + 1);
            return;
        }
        paginatorOf(binding).nextPage();
    }

    @Override
    public void back(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.back");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(binding.pendingTarget() - 1);
            return;
        }
        paginatorOf(binding).previousPage();
    }

    @Override
    public void switchTo(@NotNull ViewContext context, int page) {
        ThreadUtils.assertMainThread("Pagination.switchTo");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(page);
            return;
        }
        paginatorOf(binding).changePage(page);
    }

    @Override
    public boolean isLoading(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return false;
        }
        return paginatorOf(binding).isLoading();
    }

    @Override
    public @Nullable Throwable lastError(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return null;
        }
        return paginatorOf(binding).lastError();
    }

    @Override
    public void refresh(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.refresh");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            // pre-init refresh is a no-op: there is no source to re-request yet
            return;
        }
        if (spec.source().kind() == PaginationSourceSpec.Kind.EAGER_LAZY) {
            binding.refreshLazy(context);
            return;
        }
        paginatorOf(binding).refresh();
    }

    // resolves the per-context binding through the shared owner/liveness guard
    private @NotNull PaginationBinding bindingFor(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = ContextStateAccess.storeFor(context, owner);
        Object raw = store.get(id);
        if (raw == null) {
            throw new IllegalStateException("pagination binding missing for " + owner.getClass().getName());
        }
        return (PaginationBinding) raw;
    }

    // post-initialization accessor; PaginationBinding.initialize guarantees the paginator exists
    private @NotNull Paginator<?> paginatorOf(@NotNull PaginationBinding binding) {
        return Objects.requireNonNull(binding.paginator(), "paginator");
    }
}
