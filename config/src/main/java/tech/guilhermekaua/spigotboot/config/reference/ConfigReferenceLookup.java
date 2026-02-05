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
 * This interface abstracts the underlying config storage (single configs and folder configs)
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
     * Finds the root node of a folder config item.
     *
     * @param folderConfigName the folder config name (from {@code @FolderConfig(name="...")})
     * @param itemId         the item ID (typically the filename without extension)
     * @return the root node of the item, or null if not found
     */
    @Nullable ConfigNode findFolderConfigItemRoot(@NotNull String folderConfigName, @NotNull String itemId);

    /**
     * Finds a node at a specific path within a folder config item.
     *
     * @param folderConfigName the folder config name
     * @param itemId         the item ID
     * @param path           the dot-separated path
     * @return the node at the path, or null if folder config, item, or path not found
     */
    @Nullable ConfigNode findFolderConfigItemPath(
            @NotNull String folderConfigName,
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
                return findFolderConfigItemRoot(reference.getFolderConfigName(), reference.getItemId());
            } else {
                return findFolderConfigItemPath(
                        reference.getFolderConfigName(),
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
     * Gets all available folder config names.
     * <p>
     * Used for providing suggestions in error messages.
     *
     * @return set of folder config names
     */
    @NotNull Set<String> getAvailableFolderConfigNames();

    /**
     * Gets all available item IDs within a folder config.
     * <p>
     * Used for providing suggestions in error messages.
     *
     * @param folderConfigName the folder config name
     * @return set of item IDs, or empty set if folder config not found
     */
    @NotNull Set<String> getAvailableItemIds(@NotNull String folderConfigName);

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
     * Checks if a folder config exists.
     *
     * @param folderConfigName the folder config name
     * @return true if the folder config is registered
     */
    boolean hasFolderConfig(@NotNull String folderConfigName);

    /**
     * Checks if a folder config item exists.
     *
     * @param folderConfigName the folder config name
     * @param itemId         the item ID
     * @return true if the item exists in the folder config
     */
    boolean hasFolderConfigItem(@NotNull String folderConfigName, @NotNull String itemId);
}
