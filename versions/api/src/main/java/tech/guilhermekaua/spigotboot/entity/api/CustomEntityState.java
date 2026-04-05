/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Mutable typed state storage attached to a single live entity instance.
 *
 * @since 2.0.2
 */
public interface CustomEntityState {

    /**
     * Returns whether the supplied key exists.
     *
     * @param key the key to inspect
     * @return {@code true} when the key exists
     */
    boolean contains(@NotNull String key);

    /**
     * Returns the raw value for the supplied key.
     *
     * @param key the key to inspect
     * @return the stored value, or {@code null}
     */
    @Nullable Object get(@NotNull String key);

    /**
     * Returns a typed value for the supplied key.
     *
     * @param key the key to inspect
     * @param type the expected value type
     * @param <T> the value type
     * @return the typed value, or {@code null}
     */
    <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type);

    /**
     * Stores a value for the supplied key.
     *
     * @param key the key to store
     * @param value the value to store
     */
    void put(@NotNull String key, @Nullable Object value);

    /**
     * Removes the value associated with the supplied key.
     *
     * @param key the key to remove
     * @return the previous value, or {@code null}
     */
    @Nullable Object remove(@NotNull String key);

    /**
     * Returns a snapshot of the stored values.
     *
     * @return the stored values
     */
    @NotNull Map<String, Object> asMap();
}
