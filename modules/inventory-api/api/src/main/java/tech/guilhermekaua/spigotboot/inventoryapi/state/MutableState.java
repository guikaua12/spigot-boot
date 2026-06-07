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
package tech.guilhermekaua.spigotboot.inventoryapi.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

import java.util.function.UnaryOperator;

/**
 * Mutable per-context state. Writes are main-thread only and mark watching components
 * dirty; dirty tokens flush coalesced at the end of the current engine entry point.
 *
 * @param <T> the value type
 */
public interface MutableState<T> extends State<T> {

    /**
     * Writes this token's value for the given context and marks watchers dirty.
     *
     * @param context an active context of the owning view
     * @param value   the new value, possibly {@code null}
     * @throws StaleContextException when the context belongs to another view or is closed
     * @throws IllegalStateException when called off the main thread
     */
    void set(@NotNull ViewContext context, @Nullable T value);

    /**
     * Read-modify-write convenience: applies {@code fn} to the current value and stores
     * the result. Main thread only.
     *
     * @param context an active context of the owning view
     * @param fn      the function producing the new value from the current one
     * @throws StaleContextException when the context belongs to another view or is closed
     * @throws IllegalStateException when called off the main thread
     */
    void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn);
}
