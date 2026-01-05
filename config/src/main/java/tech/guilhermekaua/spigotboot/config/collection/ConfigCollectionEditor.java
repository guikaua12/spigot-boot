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

import java.util.function.UnaryOperator;

/**
 * Editor interface for modifying a configuration collection.
 * <p>
 * All operations persist changes to disk and update the in-memory
 * snapshot atomically. Listeners are notified of all changes.
 *
 * @param <T> the item type
 */
public interface ConfigCollectionEditor<T> {

    /**
     * Creates a new item in the collection.
     *
     * @param id    the item ID (will be used as filename without extension)
     * @param value the item value
     * @return the edit result
     */
    @NotNull EditResult<T> create(@NotNull String id, @NotNull T value);

    /**
     * Saves (overwrites) an item in the collection.
     * <p>
     * If the item doesn't exist, it will be created.
     *
     * @param id    the item ID
     * @param value the new item value
     * @return the edit result
     */
    @NotNull EditResult<T> save(@NotNull String id, @NotNull T value);

    /**
     * Updates an existing item in the collection.
     * <p>
     * The mutator receives a copy of the current item and should
     * return the modified version. The original item is not modified.
     *
     * @param id      the item ID
     * @param mutator the function to apply to the item copy
     * @return the edit result
     */
    @NotNull EditResult<T> update(@NotNull String id, @NotNull UnaryOperator<T> mutator);

    /**
     * Deletes an item from the collection.
     *
     * @param id the item ID
     * @return the edit result (value is null for delete operations)
     */
    @NotNull EditResult<Void> delete(@NotNull String id);
}
