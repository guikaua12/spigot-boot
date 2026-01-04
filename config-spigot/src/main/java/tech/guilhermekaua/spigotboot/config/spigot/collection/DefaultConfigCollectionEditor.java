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
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionEditor;
import tech.guilhermekaua.spigotboot.config.collection.EditResult;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Default implementation of ConfigCollectionEditor.
 * <p>
 * Delegates to CollectionEntry for actual persistence.
 *
 * @param <T> the item type
 */
public final class DefaultConfigCollectionEditor<T> implements ConfigCollectionEditor<T> {

    private final CollectionEntry<T> entry;

    /**
     * Creates a new editor.
     *
     * @param entry the collection entry
     */
    public DefaultConfigCollectionEditor(@NotNull CollectionEntry<T> entry) {
        this.entry = Objects.requireNonNull(entry, "entry cannot be null");
    }

    @Override
    public @NotNull EditResult<T> create(@NotNull String id, @NotNull T value) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (entry.getRef().get().contains(id)) {
            return EditResult.failure("Item already exists: " + id);
        }

        return entry.saveItem(id, value);
    }

    @Override
    public @NotNull EditResult<T> save(@NotNull String id, @NotNull T value) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        return entry.saveItem(id, value);
    }

    @Override
    public @NotNull EditResult<T> update(@NotNull String id, @NotNull UnaryOperator<T> mutator) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(mutator, "mutator cannot be null");

        T existing = entry.getRef().get().find(id);
        if (existing == null) {
            return EditResult.failure("Item not found: " + id);
        }

        T copy = entry.copyItem(existing);
        if (copy == null) {
            return EditResult.failure("Failed to create copy of item for update");
        }

        T modified = mutator.apply(copy);
        if (modified == null) {
            return EditResult.failure("Mutator returned null");
        }

        return entry.saveItem(id, modified);
    }

    @Override
    public @NotNull EditResult<Void> delete(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");

        return entry.deleteItem(id);
    }
}
