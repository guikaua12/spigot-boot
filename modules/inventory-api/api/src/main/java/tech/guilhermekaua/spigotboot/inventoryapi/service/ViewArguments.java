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
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, typed key-value arguments passed to a view when it is opened.
 *
 * <p>Use {@link #builder()} for more than two key-value pairs; the {@code of(...)}
 * overloads stop at two.
 */
public final class ViewArguments {

    private static final ViewArguments EMPTY = new ViewArguments(Collections.emptyMap());

    private final Map<String, Object> values;

    private ViewArguments(@NotNull Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Returns the shared empty arguments instance.
     *
     * @return arguments containing no entries
     */
    public static @NotNull ViewArguments empty() {
        return EMPTY;
    }

    /**
     * Creates arguments containing a single entry.
     *
     * @param key   the entry key
     * @param value the entry value
     * @return immutable arguments with one entry
     */
    public static @NotNull ViewArguments of(@NotNull String key, @NotNull Object value) {
        return builder().put(key, value).build();
    }

    /**
     * Creates arguments containing two entries.
     *
     * @param k1 the first entry key
     * @param v1 the first entry value
     * @param k2 the second entry key
     * @param v2 the second entry value
     * @return immutable arguments with two entries
     */
    public static @NotNull ViewArguments of(@NotNull String k1, @NotNull Object v1,
                                            @NotNull String k2, @NotNull Object v2) {
        return builder().put(k1, v1).put(k2, v2).build();
    }

    /**
     * Creates a new builder for arguments with any number of entries.
     *
     * @return a fresh builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Returns the value bound to the given key, or {@code null} when absent.
     *
     * @param key  the entry key
     * @param type the expected value type
     * @param <T>  the value type
     * @return the typed value, or {@code null} when the key is absent
     * @throws IllegalArgumentException if a value is present but not assignable to {@code type}
     */
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        return cast(key, type, value);
    }

    /**
     * Returns the value bound to the given key, failing when absent.
     *
     * @param key  the entry key
     * @param type the expected value type
     * @param <T>  the value type
     * @return the typed value, never {@code null}
     * @throws IllegalArgumentException if the key is absent or the value is not assignable to {@code type}
     */
    public <T> @NotNull T require(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required argument '" + key + "'");
        }
        return cast(key, type, value);
    }

    /**
     * Returns whether a value is bound to the given key.
     *
     * @param key the entry key
     * @return {@code true} when the key is present
     */
    public boolean has(@NotNull String key) {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    private static <T> T cast(String key, Class<T> type, Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("argument '" + key + "' is of type "
                    + value.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(value);
    }

    /**
     * Mutable accumulator for {@link ViewArguments}.
     */
    public static final class Builder {

        private final Map<String, Object> values = new HashMap<>();

        private Builder() {
        }

        /**
         * Adds an entry, replacing any previous value for the same key.
         *
         * @param key   the entry key
         * @param value the entry value
         * @return this builder
         */
        public @NotNull Builder put(@NotNull String key, @NotNull Object value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Builds an immutable snapshot of the accumulated entries.
         *
         * @return the immutable arguments
         */
        public @NotNull ViewArguments build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new ViewArguments(Collections.unmodifiableMap(new HashMap<>(values)));
        }
    }
}
