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

import java.util.Objects;

/**
 * Represents a change to a collection item.
 * <p>
 * This class captures all information about a single item change:
 * what collection it belongs to, what type of change occurred, and
 * the before/after values.
 *
 * @param <T> the item type
 */
public final class CollectionItemChange<T> {

    private final String collectionName;
    private final Class<T> itemType;
    private final String id;
    private final ItemChangeType type;
    private final @Nullable T oldItem;
    private final @Nullable T newItem;

    /**
     * Creates a new collection item change.
     *
     * @param collectionName the collection name
     * @param itemType       the item type class
     * @param id             the item ID
     * @param type           the type of change
     * @param oldItem        the old item (null for ADDED)
     * @param newItem        the new item (null for REMOVED)
     */
    public CollectionItemChange(
            @NotNull String collectionName,
            @NotNull Class<T> itemType,
            @NotNull String id,
            @NotNull ItemChangeType type,
            @Nullable T oldItem,
            @Nullable T newItem) {
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName cannot be null");
        this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    /**
     * Creates an ADDED change.
     *
     * @param collectionName the collection name
     * @param itemType       the item type class
     * @param id             the item ID
     * @param newItem        the new item
     * @param <T>            the item type
     * @return the change
     */
    public static <T> CollectionItemChange<T> added(
            @NotNull String collectionName,
            @NotNull Class<T> itemType,
            @NotNull String id,
            @NotNull T newItem) {
        return new CollectionItemChange<>(collectionName, itemType, id, ItemChangeType.ADDED, null, newItem);
    }

    /**
     * Creates a MODIFIED change.
     *
     * @param collectionName the collection name
     * @param itemType       the item type class
     * @param id             the item ID
     * @param oldItem        the old item
     * @param newItem        the new item
     * @param <T>            the item type
     * @return the change
     */
    public static <T> CollectionItemChange<T> modified(
            @NotNull String collectionName,
            @NotNull Class<T> itemType,
            @NotNull String id,
            @NotNull T oldItem,
            @NotNull T newItem) {
        return new CollectionItemChange<>(collectionName, itemType, id, ItemChangeType.MODIFIED, oldItem, newItem);
    }

    /**
     * Creates a REMOVED change.
     *
     * @param collectionName the collection name
     * @param itemType       the item type class
     * @param id             the item ID
     * @param oldItem        the removed item
     * @param <T>            the item type
     * @return the change
     */
    public static <T> CollectionItemChange<T> removed(
            @NotNull String collectionName,
            @NotNull Class<T> itemType,
            @NotNull String id,
            @NotNull T oldItem) {
        return new CollectionItemChange<>(collectionName, itemType, id, ItemChangeType.REMOVED, oldItem, null);
    }

    /**
     * Gets the collection name.
     *
     * @return the collection name
     */
    public @NotNull String getCollectionName() {
        return collectionName;
    }

    /**
     * Gets the item type class.
     *
     * @return the item type
     */
    public @NotNull Class<T> getItemType() {
        return itemType;
    }

    /**
     * Gets the item ID.
     *
     * @return the item ID
     */
    public @NotNull String getId() {
        return id;
    }

    /**
     * Gets the type of change.
     *
     * @return the change type
     */
    public @NotNull ItemChangeType getType() {
        return type;
    }

    /**
     * Gets the old item value.
     *
     * @return the old item, or null for ADDED changes
     */
    public @Nullable T getOldItem() {
        return oldItem;
    }

    /**
     * Gets the new item value.
     *
     * @return the new item, or null for REMOVED changes
     */
    public @Nullable T getNewItem() {
        return newItem;
    }

    @Override
    public String toString() {
        return "CollectionItemChange{" +
                "collection='" + collectionName + '\'' +
                ", id='" + id + '\'' +
                ", type=" + type +
                '}';
    }
}
