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
package tech.guilhermekaua.spigotboot.config.reference.key;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReference;

/**
 * Identifies a config or collection item in the reference dependency graph.
 * <p>
 * This is used as a key for tracking dependencies between configs during
 * load ordering and reload propagation.
 */
public abstract class ReferenceKey {

    /**
     * Creates a key for a single config.
     *
     * @param configName the config name
     * @return the reference key
     */
    public static @NotNull ReferenceKey singleConfig(@NotNull String configName) {
        return new SingleConfigKey(configName);
    }

    /**
     * Creates a key for a collection item.
     *
     * @param collectionName the collection name
     * @param itemId         the item ID
     * @return the reference key
     */
    public static @NotNull ReferenceKey collectionItem(@NotNull String collectionName, @NotNull String itemId) {
        return new CollectionItemKey(collectionName, itemId);
    }

    /**
     * Creates a key from a parsed config reference.
     *
     * @param reference the config reference
     * @return the reference key
     */
    public static @NotNull ReferenceKey fromReference(@NotNull ConfigReference reference) {
        if (reference.isSingleConfig()) {
            return singleConfig(reference.getConfigName());
        } else {
            return collectionItem(reference.getCollectionName(), reference.getItemId());
        }
    }

    /**
     * Checks if this key represents a single config.
     *
     * @return true if single config key
     */
    public abstract boolean isSingleConfig();

    /**
     * Checks if this key represents a collection item.
     *
     * @return true if collection item key
     */
    public abstract boolean isCollectionItem();

    /**
     * Gets a human-readable display name for this key.
     *
     * @return the display name
     */
    public abstract @NotNull String getDisplayName();
}
