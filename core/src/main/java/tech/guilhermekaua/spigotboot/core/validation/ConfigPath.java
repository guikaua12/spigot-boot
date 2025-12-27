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
package tech.guilhermekaua.spigotboot.core.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an immutable path through the configuration tree.
 * <p>
 * Paths are sequences of keys that navigate from the root to a specific node.
 * Supports both string keys (for maps) and integer indices (for lists).
 */
public final class ConfigPath implements PropertyPath {

    private static final ConfigPath ROOT = new ConfigPath(new Object[0]);

    private final Object[] elements;

    private ConfigPath(@NotNull Object[] elements) {
        this.elements = elements;
    }

    /**
     * Returns the root path (empty path).
     *
     * @return the root path
     */
    public static @NotNull ConfigPath root() {
        return ROOT;
    }

    /**
     * Creates a path from the given elements.
     *
     * @param elements the path elements (strings or integers)
     * @return a new path
     */
    public static @NotNull ConfigPath of(@NotNull Object... elements) {
        Objects.requireNonNull(elements, "elements cannot be null");
        if (elements.length == 0) {
            return ROOT;
        }
        Object[] copy = new Object[elements.length];
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            if (element == null) {
                throw new IllegalArgumentException("Path element at index " + i + " cannot be null");
            }
            if (!(element instanceof String) && !(element instanceof Integer)) {
                throw new IllegalArgumentException("Path element must be String or Integer, got: " + element.getClass().getName());
            }
            copy[i] = element;
        }
        return new ConfigPath(copy);
    }

    /**
     * Parses a dot-separated path string.
     * <p>
     * Example: "database.connection.host" becomes ["database", "connection", "host"]
     *
     * @param dotPath the dot-separated path
     * @return a new path
     */
    public static @NotNull ConfigPath parse(@NotNull String dotPath) {
        Objects.requireNonNull(dotPath, "dotPath cannot be null");
        if (dotPath.isEmpty()) {
            return ROOT;
        }
        String[] parts = dotPath.split("\\.");
        Object[] elements = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            // try to parse as integer for list indices
            if (part.matches("\\d+")) {
                elements[i] = Integer.parseInt(part);
            } else {
                elements[i] = part;
            }
        }
        return new ConfigPath(elements);
    }

    @Override
    public @NotNull ConfigPath child(@NotNull Object segment) {
        Objects.requireNonNull(segment, "segment cannot be null");
        if (!(segment instanceof String) && !(segment instanceof Integer)) {
            throw new IllegalArgumentException("Segment must be String or Integer, got: " + segment.getClass().getName());
        }
        Object[] newElements = Arrays.copyOf(elements, elements.length + 1);
        newElements[elements.length] = segment;
        return new ConfigPath(newElements);
    }

    @Override
    public @NotNull ConfigPath parent() {
        if (elements.length == 0) {
            return ROOT;
        }
        return new ConfigPath(Arrays.copyOf(elements, elements.length - 1));
    }

    @Override
    public @NotNull Object[] elements() {
        return Arrays.copyOf(elements, elements.length);
    }

    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Override
    public @Nullable Object last() {
        return elements.length > 0 ? elements[elements.length - 1] : null;
    }

    @Override
    public @NotNull String asString() {
        if (elements.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(elements[i]);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigPath that = (ConfigPath) o;
        return Arrays.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public String toString() {
        return "ConfigPath[" + asString() + "]";
    }
}
