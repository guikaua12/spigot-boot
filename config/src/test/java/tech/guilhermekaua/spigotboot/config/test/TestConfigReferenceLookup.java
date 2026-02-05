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
package tech.guilhermekaua.spigotboot.config.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceLookup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TestConfigReferenceLookup implements ConfigReferenceLookup {

    private final Map<String, ConfigNode> configs = new LinkedHashMap<>();
    private final Map<String, Map<String, ConfigNode>> folderConfigs = new LinkedHashMap<>();

    public void addConfig(@NotNull String name, @Nullable Object data) {
        configs.put(name, new TestConfigNode(data));
    }

    public void addFolderConfigItem(@NotNull String folderConfigName, @NotNull String itemId, @Nullable Object data) {
        folderConfigs
                .computeIfAbsent(folderConfigName, k -> new LinkedHashMap<>())
                .put(itemId, new TestConfigNode(data));
    }

    @Override
    public @Nullable ConfigNode findSingleConfigRoot(@NotNull String configName) {
        return configs.get(configName);
    }

    @Override
    public @Nullable ConfigNode findSingleConfigPath(@NotNull String configName, @NotNull String path) {
        ConfigNode root = configs.get(configName);
        if (root == null) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object[] pathArgs = new Object[parts.length];
        System.arraycopy(parts, 0, pathArgs, 0, parts.length);

        ConfigNode result = root.node(pathArgs);
        return result.isVirtual() ? null : result;
    }

    @Override
    public @Nullable ConfigNode findFolderConfigItemRoot(@NotNull String folderConfigName, @NotNull String itemId) {
        Map<String, ConfigNode> items = folderConfigs.get(folderConfigName);
        return items != null ? items.get(itemId) : null;
    }

    @Override
    public @Nullable ConfigNode findFolderConfigItemPath(@NotNull String folderConfigName, @NotNull String itemId, @NotNull String path) {
        ConfigNode root = findFolderConfigItemRoot(folderConfigName, itemId);
        if (root == null) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object[] pathArgs = new Object[parts.length];
        System.arraycopy(parts, 0, pathArgs, 0, parts.length);

        ConfigNode result = root.node(pathArgs);
        return result.isVirtual() ? null : result;
    }

    @Override
    public @NotNull Set<String> getAvailableConfigNames() {
        return configs.keySet();
    }

    @Override
    public @NotNull Set<String> getAvailableFolderConfigNames() {
        return folderConfigs.keySet();
    }

    @Override
    public @NotNull Set<String> getAvailableItemIds(@NotNull String folderConfigName) {
        Map<String, ConfigNode> items = folderConfigs.get(folderConfigName);
        return items != null ? items.keySet() : Collections.emptySet();
    }

    @Override
    public @NotNull Set<String> getAvailableKeysAt(@NotNull String configName, @Nullable String path) {
        return Collections.emptySet();
    }

    @Override
    public boolean hasConfig(@NotNull String configName) {
        return configs.containsKey(configName);
    }

    @Override
    public boolean hasFolderConfig(@NotNull String folderConfigName) {
        return folderConfigs.containsKey(folderConfigName);
    }

    @Override
    public boolean hasFolderConfigItem(@NotNull String folderConfigName, @NotNull String itemId) {
        Map<String, ConfigNode> items = folderConfigs.get(folderConfigName);
        return items != null && items.containsKey(itemId);
    }
}
