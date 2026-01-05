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

/**
 * Represents an immutable path through a property tree.
 * <p>
 * Paths are sequences of keys that navigate from the root to a specific node.
 * Supports both string keys (for maps) and integer indices (for lists).
 * <p>
 * This interface provides a generic abstraction for property paths that can be
 * used across the framework for validation, configuration, and other purposes.
 */
public interface PropertyPath {

    /**
     * Returns the root path (empty path).
     *
     * @return the root path
     */
    static @NotNull PropertyPath root() {
        return ConfigPath.root();
    }

    /**
     * Creates a path from the given elements.
     *
     * @param elements the path elements (strings or integers)
     * @return a new path
     */
    static @NotNull PropertyPath of(@NotNull Object... elements) {
        return ConfigPath.of(elements);
    }

    /**
     * Parses a dot-separated path string.
     * <p>
     * Example: "database.connection.host" becomes ["database", "connection", "host"]
     *
     * @param dotPath the dot-separated path
     * @return a new path
     */
    static @NotNull PropertyPath parse(@NotNull String dotPath) {
        return ConfigPath.parse(dotPath);
    }

    /**
     * Creates a child path by appending a segment.
     *
     * @param segment the segment to append (String or Integer)
     * @return a new path with the segment appended
     */
    @NotNull PropertyPath child(@NotNull Object segment);

    /**
     * Returns the parent path.
     *
     * @return the parent path, or root if this is already root
     */
    @NotNull PropertyPath parent();

    /**
     * Returns the path elements.
     *
     * @return a copy of the elements array
     */
    @NotNull Object[] elements();

    /**
     * Returns the number of elements in this path.
     *
     * @return the path length
     */
    int size();

    /**
     * Checks if this path is empty (root path).
     *
     * @return true if this is the root path
     */
    boolean isEmpty();

    /**
     * Returns the last element of the path.
     *
     * @return the last element, or null if empty
     */
    @Nullable Object last();

    /**
     * Converts the path to a dot-separated string.
     *
     * @return the path as a string
     */
    @NotNull String asString();
}
