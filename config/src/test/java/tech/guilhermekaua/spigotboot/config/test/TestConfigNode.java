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
package tech.guilhermekaua.spigotboot.config.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.*;

/**
 * Simple ConfigNode implementation for testing purposes.
 */
class TestConfigNode implements ConfigNode {

    private final Object value;
    private final PropertyPath path;

    TestConfigNode(@Nullable Object value) {
        this.value = value;
        this.path = PropertyPath.root();
    }

    TestConfigNode(@Nullable Object value, @NotNull PropertyPath path) {
        this.value = value;
        this.path = path;
    }

    @Override
    public @Nullable Object raw() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    @Override
    public <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue) {
        T result = get(type);
        return result != null ? result : defaultValue;
    }

    @Override
    public @NotNull ConfigNode node(@NotNull Object... pathSegments) {
        if (pathSegments.length == 0) {
            return this;
        }

        Object current = value;
        PropertyPath currentPath = path;

        for (Object segment : pathSegments) {
            if (current == null) {
                return new TestConfigNode(null, currentPath.child(String.valueOf(segment)));
            }

            if (current instanceof Map && segment instanceof String) {
                current = ((Map<?, ?>) current).get(segment);
                currentPath = currentPath.child((String) segment);
            } else if (current instanceof List && segment instanceof Integer) {
                List<?> list = (List<?>) current;
                int index = (Integer) segment;
                if (index >= 0 && index < list.size()) {
                    current = list.get(index);
                } else {
                    current = null;
                }
                currentPath = currentPath.child(index);
            } else {
                return new TestConfigNode(null, currentPath.child(String.valueOf(segment)));
            }
        }

        return new TestConfigNode(current, currentPath);
    }

    @Override
    public boolean hasChild(@NotNull Object... pathSegments) {
        return !node(pathSegments).isVirtual();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, ConfigNode> result = new LinkedHashMap<>();
        Map<String, Object> map = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), new TestConfigNode(entry.getValue(), path.child(entry.getKey())));
        }
        return result;
    }

    @Override
    public @NotNull List<? extends ConfigNode> childrenList() {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }

        List<ConfigNode> result = new ArrayList<>();
        List<?> list = (List<?>) value;
        for (int i = 0; i < list.size(); i++) {
            result.add(new TestConfigNode(list.get(i), path.child(i)));
        }
        return result;
    }

    @Override
    public boolean isMap() {
        return value instanceof Map;
    }

    @Override
    public boolean isList() {
        return value instanceof List;
    }

    @Override
    public boolean isScalar() {
        return value != null && !(value instanceof Map) && !(value instanceof List);
    }

    @Override
    public boolean isNull() {
        return value == null;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public @NotNull PropertyPath path() {
        return path;
    }
}
