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
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigEntry;
import tech.guilhermekaua.spigotboot.core.validation.ConfigPath;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Default platform-neutral implementation of {@link ConfigReferenceLookup}.
 * <p>
 * Provides lookup capabilities for resolving config references by delegating
 * to {@link DefaultConfigManager} for single configs and {@link FolderConfigEntry}
 * for folder config items.
 */
public class DefaultConfigReferenceLookup implements ConfigReferenceLookup {

    private final DefaultConfigManager configManager;

    /**
     * Creates a new lookup instance.
     *
     * @param configManager the config manager providing access to configs and folder configs
     */
    public DefaultConfigReferenceLookup(@NotNull DefaultConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    @Override
    public @Nullable ConfigNode findSingleConfigRoot(@NotNull String configName) {
        Objects.requireNonNull(configName, "configName cannot be null");
        return configManager.getConfigNode(configName);
    }

    @Override
    public @Nullable ConfigNode findSingleConfigPath(@NotNull String configName, @NotNull String path) {
        Objects.requireNonNull(configName, "configName cannot be null");
        Objects.requireNonNull(path, "path cannot be null");

        ConfigNode root = findSingleConfigRoot(configName);
        if (root == null) {
            return null;
        }

        return navigateToPath(root, path);
    }

    @Override
    public @Nullable ConfigNode findFolderConfigItemRoot(@NotNull String folderConfigName, @NotNull String itemId) {
        Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        FolderConfigEntry<?> entry = configManager.getFolderConfigEntryByName(folderConfigName);
        if (entry == null) {
            return null;
        }

        return entry.getItemNode(itemId);
    }

    @Override
    public @Nullable ConfigNode findFolderConfigItemPath(
            @NotNull String folderConfigName,
            @NotNull String itemId,
            @NotNull String path) {
        Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(path, "path cannot be null");

        ConfigNode root = findFolderConfigItemRoot(folderConfigName, itemId);
        if (root == null) {
            return null;
        }

        return navigateToPath(root, path);
    }

    @Override
    public @NotNull Set<String> getAvailableConfigNames() {
        return configManager.getConfigNames();
    }

    @Override
    public @NotNull Set<String> getAvailableFolderConfigNames() {
        return configManager.getAllFolderConfigNames();
    }

    @Override
    public @NotNull Set<String> getAvailableItemIds(@NotNull String folderConfigName) {
        Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");

        FolderConfigEntry<?> entry = configManager.getFolderConfigEntryByName(folderConfigName);
        if (entry == null) {
            return Collections.emptySet();
        }

        return entry.getItemIds();
    }

    @Override
    public @NotNull Set<String> getAvailableKeysAt(@NotNull String configName, @Nullable String path) {
        Objects.requireNonNull(configName, "configName cannot be null");

        ConfigNode root = findSingleConfigRoot(configName);
        if (root == null) {
            return Collections.emptySet();
        }

        ConfigNode targetNode = path == null || path.isEmpty() ? root : navigateToPath(root, path);
        if (targetNode == null || targetNode.isVirtual() || !targetNode.isMap()) {
            return Collections.emptySet();
        }

        return targetNode.childrenMap().keySet();
    }

    @Override
    public boolean hasConfig(@NotNull String configName) {
        Objects.requireNonNull(configName, "configName cannot be null");
        return configManager.getConfigNode(configName) != null;
    }

    @Override
    public boolean hasFolderConfig(@NotNull String folderConfigName) {
        Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");
        return configManager.getFolderConfigEntryByName(folderConfigName) != null;
    }

    @Override
    public boolean hasFolderConfigItem(@NotNull String folderConfigName, @NotNull String itemId) {
        Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        FolderConfigEntry<?> entry = configManager.getFolderConfigEntryByName(folderConfigName);
        if (entry == null) {
            return false;
        }

        return entry.getItemNode(itemId) != null;
    }

    /**
     * Navigates to a path within a config node.
     * <p>
     * Supports dot-separated paths (e.g., "database.host").
     * Returns null if the path doesn't exist (node is virtual).
     *
     * @param root the root node
     * @param path the dot-separated path
     * @return the node at the path, or null if not found
     */
    private @Nullable ConfigNode navigateToPath(@NotNull ConfigNode root, @NotNull String path) {
        if (path.isEmpty()) {
            return root;
        }

        ConfigNode node = root.node(ConfigPath.parse(path));

        if (node.isVirtual()) {
            return null;
        }

        return node;
    }
}
