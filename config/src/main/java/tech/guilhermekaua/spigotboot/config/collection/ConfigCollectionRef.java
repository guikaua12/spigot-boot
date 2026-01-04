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

import java.util.function.Supplier;

/**
 * A live reference to a configuration collection that updates on reload.
 * <p>
 * This is the primary injection type for config collections. It provides
 * access to the current snapshot, edit operations, and reload capabilities.
 * <p>
 * The underlying snapshot is swapped atomically on reload, so all reads
 * from a single {@link #get()} call see a consistent state.
 *
 * @param <T> the item type
 */
public interface ConfigCollectionRef<T> extends Supplier<ConfigCollectionSnapshot<T>> {

    /**
     * Gets the current snapshot of the collection.
     *
     * @return the current snapshot, never null
     */
    @Override
    @NotNull ConfigCollectionSnapshot<T> get();

    /**
     * Gets an editor for modifying the collection.
     * <p>
     * The editor provides create/update/delete operations that persist
     * to disk and update the in-memory snapshot atomically.
     *
     * @return the collection editor
     */
    @NotNull ConfigCollectionEditor<T> edit();

    /**
     * Reloads the entire collection from disk.
     * <p>
     * This scans the folder, loads all matching files, and swaps
     * the snapshot atomically. Listeners are notified of any changes.
     */
    void reloadAll();

    /**
     * Reloads a single item from disk.
     * <p>
     * If the file no longer exists, the item is removed from the collection.
     * If this is a new file, the item is added. Listeners are notified.
     *
     * @param id the item ID to reload
     */
    void reloadItem(@NotNull String id);

    /**
     * Adds a listener for collection changes.
     *
     * @param listener the listener to add
     */
    void addListener(@NotNull CollectionChangeListener<T> listener);

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(@NotNull CollectionChangeListener<T> listener);

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
