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
package tech.guilhermekaua.spigotboot.config.spigot.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.core.validation.ConfigPath;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.*;

/**
 * YAML-backed implementation of ConfigNode using SnakeYAML.
 */
public class YamlConfigNode implements MutableConfigNode {

    private final ConfigPath path;
    private final YamlConfigNode parent;
    private Object value;
    private List<String> comments;
    private boolean virtual;
    private final Map<String, List<String>> childComments = new LinkedHashMap<>();

    /**
     * Creates a root node.
     */
    public YamlConfigNode() {
        this(ConfigPath.root(), null, null, false);
    }

    /**
     * Creates a node from existing data.
     *
     * @param data the raw data
     */
    public YamlConfigNode(@Nullable Object data) {
        this(ConfigPath.root(), null, data, false);
    }

    private YamlConfigNode(@NotNull ConfigPath path, @Nullable YamlConfigNode parent, @Nullable Object value, boolean virtual) {
        this.path = path;
        this.parent = parent;
        this.value = value;
        this.virtual = virtual;
        this.comments = null;
    }

    @Override
    public @Nullable Object raw() {
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
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
    public @NotNull MutableConfigNode node(@NotNull Object... pathSegments) {
        Objects.requireNonNull(pathSegments, "path cannot be null");
        if (pathSegments.length == 0) {
            return this;
        }

        YamlConfigNode current = this;
        for (Object segment : pathSegments) {
            current = current.getOrCreateChild(segment);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private YamlConfigNode getOrCreateChild(@NotNull Object key) {
        if (key instanceof Integer) {
            int index = (Integer) key;
            if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                if (index >= 0 && index < list.size()) {
                    Object childValue = list.get(index);
                    return new YamlConfigNode(path.child(key), this, childValue, false);
                }
            }
            return new YamlConfigNode(path.child(key), this, null, true);
        }

        String keyStr = String.valueOf(key);
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey(keyStr)) {
                return new YamlConfigNode(path.child(keyStr), this, map.get(keyStr), false);
            }
        }

        return new YamlConfigNode(path.child(keyStr), this, null, true);

    }

    @Override
    public boolean hasChild(@NotNull Object... pathSegments) {
        ConfigNode child = node(pathSegments);
        return !child.isVirtual() && !child.isNull();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = (Map<String, Object>) value;
        Map<String, YamlConfigNode> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), new YamlConfigNode(path.child(entry.getKey()), this, entry.getValue(), false));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull List<? extends ConfigNode> childrenList() {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<Object> list = (List<Object>) value;
        List<YamlConfigNode> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(new YamlConfigNode(path.child(i), this, list.get(i), false));
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
        return virtual;
    }

    @Override
    public @NotNull PropertyPath path() {
        return path;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull MutableConfigNode set(@Nullable Object newValue) {
        this.value = newValue;
        this.virtual = false;

        if (parent != null) {
            Object lastKey = path.last();
            if (lastKey instanceof Integer) {
                int index = (Integer) lastKey;
                if (!(parent.value instanceof List)) {
                    parent.value = new ArrayList<>();
                }
                List<Object> list = (List<Object>) parent.value;
                while (list.size() <= index) {
                    list.add(null);
                }
                list.set(index, newValue);
            } else {
                String key = String.valueOf(lastKey);
                if (!(parent.value instanceof Map)) {
                    parent.value = new LinkedHashMap<>();
                }
                Map<String, Object> map = (Map<String, Object>) parent.value;
                map.put(key, newValue);
            }
            parent.virtual = false;
        }

        return this;
    }

    @Override
    public @NotNull MutableConfigNode setComment(@Nullable String... lines) {
        if (lines == null || lines.length == 0) {
            this.comments = null;
        } else {
            this.comments = Arrays.asList(lines);
        }
        if (parent != null && !path.isEmpty()) {
            Object lastKey = path.last();
            if (lastKey != null) {
                String key = String.valueOf(lastKey);
                if (this.comments != null) {
                    parent.childComments.put(key, this.comments);
                } else {
                    parent.childComments.remove(key);
                }
            }
        }
        return this;
    }

    /**
     * Gets the comments for this node.
     *
     * @return the comments, or null
     */
    public @Nullable List<String> getComments() {
        return comments;
    }

    /**
     * Gets comments for a specific child key.
     *
     * @param key the child key
     * @return the comments, or null
     */
    public @Nullable List<String> getChildComments(@NotNull String key) {
        return childComments.get(key);
    }

    /**
     * Gets all child comments.
     *
     * @return map of key to comment lines
     */
    public @NotNull Map<String, List<String>> getAllChildComments() {
        return Collections.unmodifiableMap(childComments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull MutableConfigNode remove() {
        if (parent != null && parent.value instanceof Map) {
            Object lastKey = path.last();
            if (lastKey != null) {
                ((Map<String, Object>) parent.value).remove(String.valueOf(lastKey));
            }
        }
        this.value = null;
        this.virtual = true;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull MutableConfigNode appendListItem() {
        if (!(value instanceof List)) {
            value = new ArrayList<>();
        }
        List<Object> list = (List<Object>) value;
        int index = list.size();
        list.add(null);
        return new YamlConfigNode(path.child(index), this, null, false);
    }

}
