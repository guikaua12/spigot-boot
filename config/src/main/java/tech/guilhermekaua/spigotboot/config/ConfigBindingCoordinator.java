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
package tech.guilhermekaua.spigotboot.config;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.binding.BindingResult;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.key.FolderConfigItemKey;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.reference.key.SingleConfigKey;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigEntry;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceManager;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Coordinates binding and rebinding of configs and folder config items.
 * <p>
 * This is a package-private helper for {@link DefaultConfigManager} that
 * consolidates the logic for binding configs from their raw nodes.
 * It handles both initial binding during initialization and rebinding
 * during reload operations.
 */
final class ConfigBindingCoordinator {

    private final ConfigReferenceManager referenceManager;
    private final Binder binder;
    private final Logger logger;
    private final ConfigEntryAccessor entryAccessor;

    /**
     * Creates a new binding coordinator.
     *
     * @param referenceManager the reference manager for context tracking
     * @param binder           the config binder
     * @param logger           the logger for status messages
     * @param entryAccessor    accessor for config entries
     */
    ConfigBindingCoordinator(
            @NotNull ConfigReferenceManager referenceManager,
            @NotNull Binder binder,
            @NotNull Logger logger,
            @NotNull ConfigEntryAccessor entryAccessor) {
        this.referenceManager = Objects.requireNonNull(referenceManager, "referenceManager cannot be null");
        this.binder = Objects.requireNonNull(binder, "binder cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.entryAccessor = Objects.requireNonNull(entryAccessor, "entryAccessor cannot be null");
    }

    /**
     * Binds or rebinds a config/folder config item from its current raw node.
     * <p>
     * This method sets the reference context, performs binding, and updates
     * the config entry with the new instance.
     *
     * @param key      the reference key identifying the config or folder config item
     * @param isRebind true if this is a rebind operation (affects log messages only)
     */
    void bindKey(@NotNull ReferenceKey key, boolean isRebind) {
        Objects.requireNonNull(key, "key cannot be null");

        referenceManager.setCurrentSourceKey(key);
        try {
            if (key.isSingleConfig()) {
                bindSingleConfig((SingleConfigKey) key, isRebind);
            } else {
                bindFolderConfigItem((FolderConfigItemKey) key, isRebind);
            }
        } finally {
            referenceManager.clearCurrentSourceKey();
        }
    }

    @SuppressWarnings("unchecked")
    private void bindSingleConfig(SingleConfigKey key, boolean isRebind) {
        String configName = key.getConfigName();
        Class<?> configClass = entryAccessor.getConfigClassByName(configName);
        if (configClass == null) {
            logger.warning("Unknown config in dependency graph: " + configName);
            return;
        }

        ConfigNode node = entryAccessor.getConfigNode(configClass);
        NamingStrategy namingStrategy = entryAccessor.getNamingStrategy(configClass);
        if (node == null) {
            return;
        }

        BindingResult<Object> result = (BindingResult<Object>) binder.bind(node, configClass, namingStrategy);
        Object instance = result.get();
        entryAccessor.setConfigInstance(configClass, instance);

        String action = isRebind ? "Rebound" : "Bound";
        logger.fine(action + " config: " + configName);
    }

    private void bindFolderConfigItem(FolderConfigItemKey key, boolean isRebind) {
        String folderConfigName = key.getFolderConfigName();
        String itemId = key.getItemId();

        FolderConfigEntry<?> folderEntry = entryAccessor.getFolderConfigEntryByName(folderConfigName);
        if (folderEntry == null) {
            return;
        }

        folderEntry.rebindItem(itemId);

        String action = isRebind ? "Rebound" : "Bound";
        logger.fine(action + " folder config item: " + folderConfigName + "." + itemId);
    }
}
