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

import java.util.function.UnaryOperator;

/**
 * One value per view singleton, shared by all viewers; not context-keyed. All methods are
 * atomic and callable from any thread; the watcher flush for open sessions is marshalled
 * to the main thread and coalesced per view per tick.
 *
 * @param <T> the value type
 */
public interface SharedState<T> extends StateToken {

    /**
     * Returns the current shared value.
     *
     * @return the current value, possibly {@code null}
     */
    @Nullable T get();

    /**
     * Atomically replaces the shared value and triggers a watcher flush for every open
     * session of the owning view.
     *
     * @param value the new value, possibly {@code null}
     */
    void set(@Nullable T value);

    /**
     * Atomically updates the shared value with a compare-and-set loop; {@code fn} may run
     * more than once under contention and must be side-effect free.
     *
     * @param fn the function producing the new value from the current one
     */
    void update(@NotNull UnaryOperator<T> fn);
}
