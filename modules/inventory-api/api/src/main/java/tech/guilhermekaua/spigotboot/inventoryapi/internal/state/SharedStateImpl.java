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
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Shared state token: one atomic value per view singleton, readable and writable from any
 * thread. Every successful write invokes the flush hook so open sessions of the owning
 * view repaint their watchers.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class SharedStateImpl<T> implements SharedState<T>, IdentifiableToken {

    private final View owner;
    private final AtomicReference<T> value;
    private final int id;

    // wired by ViewEngine at registration (plan task 16) to flush every open session of
    // the owning view; null until the engine wires it; volatile so writes from any thread
    // are visible to the runFlushHook() reader without synchronization
    @Nullable volatile Runnable flushHook;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the initial shared value, possibly {@code null}
     * @throws IllegalStateException when the table is already frozen
     */
    public SharedStateImpl(@NotNull View owner, @NotNull TokenTable table, @Nullable T initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.value = new AtomicReference<>(initialValue);
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id.
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
     * Wires the engine flush callback invoked after every {@link #set}; assigned once by the
     * engine on the view's first open.
     *
     * @param hook the flush callback, or null to clear
     */
    @ApiStatus.Internal
    public void flushHook(@Nullable Runnable hook) {
        this.flushHook = hook;
    }

    @Override
    public @Nullable T get() {
        return value.get();
    }

    @Override
    public void set(@Nullable T newValue) {
        value.set(newValue);
        runFlushHook();
    }

    @Override
    public void update(@NotNull UnaryOperator<T> fn) {
        Objects.requireNonNull(fn, "fn");
        T current;
        T next;
        do {
            current = value.get();
            next = fn.apply(current);
        } while (!value.compareAndSet(current, next));
        runFlushHook();
    }

    private void runFlushHook() {
        Runnable hook = flushHook;
        if (hook != null) {
            hook.run();
        }
    }
}
