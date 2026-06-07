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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;

import java.util.Objects;
import java.util.function.Function;

/**
 * Read-only state token computed once per context on the first read and stored in the
 * session's {@link StateStore}; a {@code null} result is cached via a private sentinel so
 * the computation never re-runs for that context.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class LazyStateImpl<T> implements State<T> {

    private static final Object NULL_VALUE = new Object();

    private final View owner;
    private final Function<ViewContext, T> computation;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner       the view declaring the token
     * @param table       the owner's token table
     * @param computation the once-per-context computation
     * @throws IllegalStateException when the table is already frozen
     */
    public LazyStateImpl(@NotNull View owner, @NotNull TokenTable table,
                         @NotNull Function<ViewContext, T> computation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.computation = Objects.requireNonNull(computation, "computation");
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
    @SuppressWarnings("unchecked")
    public @Nullable T get(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        Object raw = store.get(id);
        if (raw == null) {
            T computed = computation.apply(context);
            raw = computed == null ? NULL_VALUE : computed;
            store.set(id, raw);
        }
        return raw == NULL_VALUE ? null : (T) raw;
    }

    private StateStore storeFor(ViewContext context) {
        View contextOwner = ContextStateAccess.ownerOf(context);
        if (contextOwner != owner) {
            throw new StaleContextException("state token of " + owner.getClass().getName()
                    + " used with a context of " + contextOwner.getClass().getName());
        }
        if (!ContextStateAccess.isActive(context)) {
            throw new StaleContextException("context of " + owner.getClass().getName() + " is closed");
        }
        return ContextStateAccess.storeOf(context);
    }
}
