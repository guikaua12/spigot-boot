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
package tech.guilhermekaua.spigotboot.config.collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Immutable read-only snapshot of a configuration collection.
 * <p>
 * A snapshot represents the state of a collection at a specific point in time.
 * All operations are lock-free as the underlying data is immutable.
 *
 * @param <T> the item type
 */
public interface ConfigCollectionSnapshot<T> {

    /**
     * Gets an item by its ID.
     *
     * @param id the item ID (filename without extension)
     * @return the item
     * @throws IllegalArgumentException if no item with this ID exists
     */
    @NotNull T get(@NotNull String id);

    /**
     * Finds an item by its ID.
     *
     * @param id the item ID (filename without extension)
     * @return the item, or null if not found
     */
    @Nullable T find(@NotNull String id);

    /**
     * Returns all items in the collection in their configured order.
     *
     * @return an unmodifiable collection of all items
     */
    @NotNull Collection<T> values();

    /**
     * Returns all item IDs in the collection.
     *
     * @return an unmodifiable set of all IDs
     */
    @NotNull Set<String> ids();

    /**
     * Returns only enabled items if enabled filtering is configured.
     * <p>
     * If enabled filtering is not configured (enabledField is empty or
     * the field doesn't exist), this returns the same as {@link #values()}.
     *
     * @return an unmodifiable collection of enabled items
     */
    @NotNull Collection<T> enabled();

    /**
     * Checks if an item with the given ID exists.
     *
     * @param id the item ID to check
     * @return true if an item with this ID exists
     */
    boolean contains(@NotNull String id);

    /**
     * Returns the number of items in this snapshot.
     *
     * @return the item count
     */
    int size();

    /**
     * Returns true if this snapshot contains no items.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Gets the item type class.
     *
     * @return the item class
     */
    @NotNull Class<T> getItemType();

    /**
     * Gets the collection name.
     *
     * @return the collection name
     */
    @NotNull String getCollectionName();
}
