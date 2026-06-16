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
package tech.guilhermekaua.spigotboot.config.bungee;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.bungee.folder.FolderConfigEntry;

/**
 * Callback interface for accessing BungeeConfigManager's internal config entries.
 * <p>
 * This interface allows the coordinator to access config state without
 * exposing the internal ConfigEntry class.
 */
interface ConfigEntryAccessor {

    /**
     * Gets the config class registered under the given name.
     *
     * @param configName the config name
     * @return the config class, or null if not found
     */
    @Nullable Class<?> getConfigClassByName(@NotNull String configName);

    /**
     * Gets the raw config node for a config class.
     *
     * @param configClass the config class
     * @return the config node, or null if not found
     */
    @Nullable ConfigNode getConfigNode(@NotNull Class<?> configClass);

    /**
     * Gets the naming strategy for a config class.
     *
     * @param configClass the config class
     * @return the naming strategy, or null if not found
     */
    @Nullable NamingStrategy getNamingStrategy(@NotNull Class<?> configClass);

    /**
     * Updates the instance for a config class.
     *
     * @param configClass the config class
     * @param instance    the new instance
     */
    void setConfigInstance(@NotNull Class<?> configClass, @NotNull Object instance);

    /**
     * Gets a folder config entry by name.
     *
     * @param folderConfigName the folder config name
     * @return the folder config entry, or null if not found
     */
    @Nullable FolderConfigEntry<?> getFolderConfigEntryByName(@NotNull String folderConfigName);
}
