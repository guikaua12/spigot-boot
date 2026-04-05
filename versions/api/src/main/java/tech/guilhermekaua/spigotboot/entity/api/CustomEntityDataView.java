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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable typed access to arbitrary spawn metadata.
 *
 * @since 2.0.2
 */
public final class CustomEntityDataView {
    private static final CustomEntityDataView EMPTY = new CustomEntityDataView(Collections.<String, Object>emptyMap());

    private final Map<String, Object> values;

    private CustomEntityDataView(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Returns an empty data view.
     *
     * @return an empty data view
     */
    public static @NotNull CustomEntityDataView empty() {
        return EMPTY;
    }

    /**
     * Creates a data view from the supplied values.
     *
     * @param values the source values
     * @return the created data view
     */
    public static @NotNull CustomEntityDataView of(@NotNull Map<String, Object> values) {
        Objects.requireNonNull(values, "values cannot be null");
        if (values.isEmpty()) {
            return empty();
        }
        return new CustomEntityDataView(Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values)));
    }

    /**
     * Returns whether the supplied key exists.
     *
     * @param key the key to inspect
     * @return {@code true} when the key exists
     */
    public boolean contains(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.containsKey(key);
    }

    /**
     * Returns the raw value for the supplied key.
     *
     * @param key the key to inspect
     * @return the stored value, or {@code null}
     */
    public @Nullable Object get(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.get(key);
    }

    /**
     * Returns a typed value for the supplied key.
     *
     * @param key the key to inspect
     * @param type the expected value type
     * @param <T> the value type
     * @return the typed value, or {@code null}
     */
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Value '" + key + "' is not of type " + type.getName() + "."
            );
        }
        return type.cast(value);
    }

    /**
     * Returns a required typed value for the supplied key.
     *
     * @param key the key to inspect
     * @param type the expected value type
     * @param <T> the value type
     * @return the typed value
     */
    public <T> @NotNull T getRequired(@NotNull String key, @NotNull Class<T> type) {
        T value = get(key, type);
        if (value == null) {
            throw new IllegalArgumentException("Missing required value '" + key + "'.");
        }
        return value;
    }

    /**
     * Returns an immutable view of the stored values.
     *
     * @return the stored values
     */
    public @NotNull Map<String, Object> asMap() {
        return values;
    }
}
