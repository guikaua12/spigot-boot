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
package tech.guilhermekaua.spigotboot.config.spigot.collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionSnapshot;

import java.util.*;

/**
 * Default implementation of ConfigCollectionSnapshot.
 * <p>
 * This is an immutable snapshot that is swapped atomically on reload.
 *
 * @param <T> the item type
 */
public final class DefaultConfigCollectionSnapshot<T> implements ConfigCollectionSnapshot<T> {

    private final Class<T> itemType;
    private final String collectionName;
    private final Map<String, T> itemsById;
    private final List<T> orderedValues;
    private final List<T> enabledValues;

    /**
     * Creates a new snapshot.
     *
     * @param itemType       the item type class
     * @param collectionName the collection name
     * @param itemsById      map of items by ID
     * @param orderedValues  ordered list of all values
     * @param enabledValues  list of enabled values
     */
    public DefaultConfigCollectionSnapshot(
            @NotNull Class<T> itemType,
            @NotNull String collectionName,
            @NotNull Map<String, T> itemsById,
            @NotNull List<T> orderedValues,
            @Nullable List<T> enabledValues) {
        this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName cannot be null");
        this.itemsById = Collections.unmodifiableMap(new LinkedHashMap<>(itemsById));
        this.orderedValues = Collections.unmodifiableList(new ArrayList<>(orderedValues));
        this.enabledValues = enabledValues != null
                ? Collections.unmodifiableList(new ArrayList<>(enabledValues))
                : this.orderedValues;
    }

    /**
     * Creates an empty snapshot.
     *
     * @param itemType       the item type class
     * @param collectionName the collection name
     * @param <T>            the item type
     * @return an empty snapshot
     */
    public static <T> DefaultConfigCollectionSnapshot<T> empty(
            @NotNull Class<T> itemType,
            @NotNull String collectionName) {
        return new DefaultConfigCollectionSnapshot<>(
                itemType,
                collectionName,
                Collections.emptyMap(),
                Collections.emptyList(),
                null
        );
    }

    @Override
    public @NotNull T get(@NotNull String id) {
        T item = itemsById.get(id);
        if (item == null) {
            throw new IllegalArgumentException("No item with ID '" + id + "' in collection '" + collectionName + "'");
        }
        return item;
    }

    @Override
    public @Nullable T find(@NotNull String id) {
        return itemsById.get(id);
    }

    @Override
    public @NotNull Collection<T> values() {
        return orderedValues;
    }

    @Override
    public @NotNull Set<String> ids() {
        return itemsById.keySet();
    }

    @Override
    public @NotNull Collection<T> enabled() {
        return enabledValues;
    }

    @Override
    public boolean contains(@NotNull String id) {
        return itemsById.containsKey(id);
    }

    @Override
    public int size() {
        return itemsById.size();
    }

    @Override
    public boolean isEmpty() {
        return itemsById.isEmpty();
    }

    @Override
    public @NotNull Class<T> getItemType() {
        return itemType;
    }

    @Override
    public @NotNull String getCollectionName() {
        return collectionName;
    }

    /**
     * Gets the internal items map for building diffs.
     *
     * @return the items map
     */
    public @NotNull Map<String, T> getItemsMap() {
        return itemsById;
    }
}
