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
package tech.guilhermekaua.spigotboot.versions.runtime.lifecycle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class SimpleCustomEntityState implements CustomEntityState {
    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<String, Object>();

    @Override
    public boolean contains(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.containsKey(key);
    }

    @Override
    public @Nullable Object get(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.get(key);
    }

    @Override
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "State value '" + key + "' is not of type " + type.getName() + "."
            );
        }
        return type.cast(value);
    }

    @Override
    public void put(@NotNull String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        if (value == null) {
            values.remove(key);
            return;
        }
        values.put(key, value);
    }

    @Override
    public @Nullable Object remove(@NotNull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return values.remove(key);
    }

    @Override
    public @NotNull Map<String, Object> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }
}
