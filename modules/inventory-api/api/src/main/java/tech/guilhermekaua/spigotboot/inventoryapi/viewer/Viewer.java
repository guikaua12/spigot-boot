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
package tech.guilhermekaua.spigotboot.inventoryapi.viewer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.configuration.ViewerConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.property.ViewerPropertyMap;

import java.util.UUID;

/**
 * One viewer of an open custom inventory. Carries the per-player editor, configuration and
 * scratchpad properties. Pagination implementations and lifecycle hooks read framework
 * collaborators (registry, title updater, plugin) through {@link #getContext()} so they
 * never need static lookups.
 */
public interface Viewer {

    String getName();

    default Player getPlayer() {
        return Bukkit.getPlayer(this.getName());
    }

    default UUID getUniqueId() {
        return this.getPlayer().getUniqueId();
    }

    <T extends CustomInventory> T getCustomInventory();

    <T extends ViewerConfiguration> T getConfiguration();

    ViewerPropertyMap getPropertyMap();

    InventoryEditor getEditor();

    default Inventory getInventory() {
        return this.getEditor().getInventory();
    }

    Inventory createInventory();

    void resetConfigurations();

    void updateTitle(String title);

    void close();

    /**
     * @return the framework collaborators (registry, title updater, plugin) injected at
     * viewer construction time. Implementations must never return {@code null}.
     */
    ViewerContext getContext();

    default ViewerRegistry getRegistry() {
        return getContext().getRegistry();
    }

    default TitleUpdater getTitleUpdater() {
        return getContext().getTitleUpdater();
    }

    default Plugin getPlugin() {
        return getContext().getPlugin();
    }

}
