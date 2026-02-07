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
package tech.guilhermekaua.spigotboot.config.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.*;

/**
 * Shared immutable/read-only behavior for value-backed {@link ConfigNode} implementations.
 * <p>
 * Subclasses provide storage details and child-node creation strategy.
 */
public abstract class AbstractValueConfigNode implements ConfigNode {

    protected abstract @Nullable Object currentValue();

    protected abstract @NotNull PropertyPath currentPath();

    protected abstract boolean currentVirtual();

    protected abstract @NotNull ConfigNode createChildNode(
            @Nullable Object value,
            @NotNull PropertyPath path,
            boolean virtual);

    @Override
    public @Nullable Object raw() {
        return currentValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");

        if (currentVirtual()) {
            return null;
        }

        Object value = currentValue();
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == Integer.class || type == int.class) {
            if (value instanceof Number) {
                return (T) Integer.valueOf(((Number) value).intValue());
            }
            return (T) Integer.valueOf(String.valueOf(value));
        }
        if (type == Long.class || type == long.class) {
            if (value instanceof Number) {
                return (T) Long.valueOf(((Number) value).longValue());
            }
            return (T) Long.valueOf(String.valueOf(value));
        }
        if (type == Double.class || type == double.class) {
            if (value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            }
            return (T) Double.valueOf(String.valueOf(value));
        }
        if (type == Float.class || type == float.class) {
            if (value instanceof Number) {
                return (T) Float.valueOf(((Number) value).floatValue());
            }
            return (T) Float.valueOf(String.valueOf(value));
        }
        if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean) {
                return (T) value;
            }
            String str = String.valueOf(value).toLowerCase();
            return (T) Boolean.valueOf("true".equals(str) || "yes".equals(str) || "1".equals(str));
        }
        return null;
    }

    @Override
    public <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue) {
        T result = get(type);
        return result != null ? result : defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull ConfigNode node(@NotNull Object... pathSegments) {
        Objects.requireNonNull(pathSegments, "path cannot be null");

        if (currentVirtual()) {
            return this;
        }

        if (pathSegments.length == 0) {
            return this;
        }

        Object current = currentValue();
        PropertyPath path = currentPath();

        for (Object segment : pathSegments) {
            if (current == null) {
                return createChildNode(null, path.child(String.valueOf(segment)), true);
            }

            if (current instanceof Map && segment instanceof String) {
                Map<String, Object> map = (Map<String, Object>) current;
                String key = (String) segment;
                if (!map.containsKey(key)) {
                    return createChildNode(null, path.child(key), true);
                }
                current = map.get(key);
                path = path.child(key);
                continue;
            }

            if (current instanceof List && segment instanceof Integer) {
                List<Object> list = (List<Object>) current;
                int index = (Integer) segment;
                PropertyPath childPath = path.child(index);
                if (index < 0 || index >= list.size()) {
                    return createChildNode(null, childPath, true);
                }
                current = list.get(index);
                path = childPath;
                continue;
            }

            return createChildNode(null, path.child(String.valueOf(segment)), true);
        }

        return createChildNode(current, path, false);
    }

    @Override
    public @NotNull ConfigNode node(@NotNull PropertyPath path) {
        return node(path.elements());
    }

    @Override
    public boolean hasChild(@NotNull Object... path) {
        return !node(path).isVirtual();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
        Object value = currentValue();
        if (currentVirtual() || !(value instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, ConfigNode> result = new LinkedHashMap<>();
        Map<String, Object> map = (Map<String, Object>) value;
        PropertyPath path = currentPath();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), createChildNode(entry.getValue(), path.child(entry.getKey()), false));
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull List<? extends ConfigNode> childrenList() {
        Object value = currentValue();
        if (currentVirtual() || !(value instanceof List)) {
            return Collections.emptyList();
        }

        List<ConfigNode> result = new ArrayList<>();
        List<Object> list = (List<Object>) value;
        PropertyPath path = currentPath();

        for (int i = 0; i < list.size(); i++) {
            result.add(createChildNode(list.get(i), path.child(i), false));
        }

        return result;
    }

    @Override
    public boolean isMap() {
        return !currentVirtual() && currentValue() instanceof Map;
    }

    @Override
    public boolean isList() {
        return !currentVirtual() && currentValue() instanceof List;
    }

    @Override
    public boolean isScalar() {
        Object value = currentValue();
        return !currentVirtual() && value != null && !(value instanceof Map) && !(value instanceof List);
    }

    @Override
    public boolean isNull() {
        return currentValue() == null;
    }

    @Override
    public boolean isVirtual() {
        return currentVirtual();
    }

    @Override
    public @NotNull PropertyPath path() {
        return currentPath();
    }
}
