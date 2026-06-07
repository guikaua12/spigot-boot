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
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Mutable per-context state token; value storage lives in the session's state store.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class MutableStateImpl<T> implements MutableState<T> {

    private final View owner;
    private final Function<ViewContext, T> initialValue;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the per-context initial value factory
     * @throws IllegalStateException when the table is already frozen
     */
    public MutableStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull Function<ViewContext, T> initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.initialValue = Objects.requireNonNull(initialValue, "initialValue");
        this.id = Objects.requireNonNull(table, "table").register(this); // safe this-escape: register only stores the reference, no method dispatch
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
    public @Nullable T get(@NotNull ViewContext context) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
