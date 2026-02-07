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

public class TestConfigNode implements ConfigNode {

    private final Object value;
    private final PropertyPath path;
    private final boolean virtual;

    public TestConfigNode(@Nullable Object value) {
        this(value, PropertyPath.root(), false);
    }

    public TestConfigNode(@Nullable Object value, @NotNull PropertyPath path) {
        this(value, path, false);
    }

    private TestConfigNode(@Nullable Object value, @NotNull PropertyPath path, boolean virtual) {
        this.value = value;
        this.path = path;
        this.virtual = virtual;
    }

    private static @NotNull TestConfigNode virtualNode(@NotNull PropertyPath path) {
        return new TestConfigNode(null, path, true);
    }

    @Override
    public @Nullable Object raw() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull Class<T> type) {
        if (virtual || value == null) {
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
        if (virtual) {
            return this;
        }
        if (pathSegments.length == 0) {
            return this;
        }

        Object current = value;
        PropertyPath currentPath = path;

        for (Object segment : pathSegments) {
            if (current == null) {
                return virtualNode(currentPath.child(String.valueOf(segment)));
            }

            if (current instanceof Map<?, ?> map && segment instanceof String key) {

                if (!map.containsKey(key)) {
                    return virtualNode(currentPath.child(key));
                }

                current = map.get(key);
                currentPath = currentPath.child(key);
            } else if (current instanceof List<?> list && segment instanceof Integer) {
                int index = (Integer) segment;

                PropertyPath childPath = currentPath.child(index);
                if (index < 0 || index >= list.size()) {
                    return virtualNode(childPath);
                }

                current = list.get(index);
                currentPath = childPath;
            } else {
                return virtualNode(currentPath.child(String.valueOf(segment)));
            }
        }

        return new TestConfigNode(current, currentPath, false);
    }

    @Override
    public @NotNull ConfigNode node(@NotNull PropertyPath path) {
        return node(path.elements());
    }

    @Override
    public boolean hasChild(@NotNull Object... pathSegments) {
        return !node(pathSegments).isVirtual();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
        if (virtual || !(value instanceof Map)) {
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
        if (virtual || !(value instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<ConfigNode> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(new TestConfigNode(list.get(i), path.child(i)));
        }
        return result;
    }

    @Override
    public boolean isMap() {
        return !virtual && value instanceof Map;
    }

    @Override
    public boolean isList() {
        return !virtual && value instanceof List;
    }

    @Override
    public boolean isScalar() {
        return !virtual && value != null && !(value instanceof Map) && !(value instanceof List);
    }

    @Override
    public boolean isNull() {
        return value == null;
    }

    @Override
    public boolean isVirtual() {
        return virtual;
    }

    @Override
    public @NotNull PropertyPath path() {
        return path;
    }
}
