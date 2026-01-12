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
package tech.guilhermekaua.spigotboot.config.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;

import java.util.Set;

/**
 * Provides lookup capabilities for resolving config references.
 * <p>
 * This interface abstracts the underlying config storage (single configs and collections)
 * so that the reference resolver can fetch raw config nodes without knowing the
 * implementation details.
 */
public interface ConfigReferenceLookup {

    /**
     * Finds the root node of a single config by name.
     *
     * @param configName the config name (from {@code @Config(name="...")})
     * @return the root config node, or null if not found
     */
    @Nullable ConfigNode findSingleConfigRoot(@NotNull String configName);

    /**
     * Finds a node at a specific path within a single config.
     *
     * @param configName the config name
     * @param path       the dot-separated path (e.g., "database.host")
     * @return the node at the path, or null if config or path not found
     */
    @Nullable ConfigNode findSingleConfigPath(@NotNull String configName, @NotNull String path);

    /**
     * Finds the root node of a collection item.
     *
     * @param collectionName the collection name (from {@code @ConfigCollection(name="...")})
     * @param itemId         the item ID (typically the filename without extension)
     * @return the root node of the item, or null if not found
     */
    @Nullable ConfigNode findCollectionItemRoot(@NotNull String collectionName, @NotNull String itemId);

    /**
     * Finds a node at a specific path within a collection item.
     *
     * @param collectionName the collection name
     * @param itemId         the item ID
     * @param path           the dot-separated path
     * @return the node at the path, or null if collection, item, or path not found
     */
    @Nullable ConfigNode findCollectionItemPath(
            @NotNull String collectionName,
            @NotNull String itemId,
            @NotNull String path);

    /**
     * Resolves a config reference to its target node.
     * <p>
     * This is a convenience method that dispatches to the appropriate find method
     * based on the reference type.
     *
     * @param reference the config reference
     * @return the target node, or null if not found
     */
    default @Nullable ConfigNode resolve(@NotNull ConfigReference reference) {
        if (reference.isSingleConfig()) {
            if (reference.isRootReference()) {
                return findSingleConfigRoot(reference.getConfigName());
            } else {
                return findSingleConfigPath(reference.getConfigName(), reference.getPath());
            }
        } else {
            if (reference.isRootReference()) {
                return findCollectionItemRoot(reference.getCollectionName(), reference.getItemId());
            } else {
                return findCollectionItemPath(
                        reference.getCollectionName(),
                        reference.getItemId(),
                        reference.getPath());
            }
        }
    }

    /**
     * Gets all available single config names.
     * <p>
     * Used for providing suggestions in error messages.
     *
     * @return set of config names
     */
    @NotNull Set<String> getAvailableConfigNames();

    /**
     * Gets all available collection names.
     * <p>
     * Used for providing suggestions in error messages.
     *
     * @return set of collection names
     */
    @NotNull Set<String> getAvailableCollectionNames();

    /**
     * Gets all available item IDs within a collection.
     * <p>
     * Used for providing suggestions in error messages.
     *
     * @param collectionName the collection name
     * @return set of item IDs, or empty set if collection not found
     */
    @NotNull Set<String> getAvailableItemIds(@NotNull String collectionName);

    /**
     * Gets available keys at a path within a single config.
     * <p>
     * If the node at the path is a map, returns its keys.
     * Used for providing suggestions in error messages.
     *
     * @param configName the config name
     * @param path       the path (can be empty or null for root)
     * @return set of available keys, or empty set if not a map or not found
     */
    @NotNull Set<String> getAvailableKeysAt(@NotNull String configName, @Nullable String path);

    /**
     * Checks if a single config exists.
     *
     * @param configName the config name
     * @return true if the config is registered
     */
    boolean hasConfig(@NotNull String configName);

    /**
     * Checks if a collection exists.
     *
     * @param collectionName the collection name
     * @return true if the collection is registered
     */
    boolean hasCollection(@NotNull String collectionName);

    /**
     * Checks if a collection item exists.
     *
     * @param collectionName the collection name
     * @param itemId         the item ID
     * @return true if the item exists in the collection
     */
    boolean hasCollectionItem(@NotNull String collectionName, @NotNull String itemId);
}
