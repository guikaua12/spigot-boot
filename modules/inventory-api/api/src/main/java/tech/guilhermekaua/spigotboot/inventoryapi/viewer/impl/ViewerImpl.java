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
package tech.guilhermekaua.spigotboot.inventoryapi.viewer.impl;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.impl.InventoryEditorImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.ViewerContext;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.configuration.ViewerConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.property.ViewerPropertyMap;

import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"context", "placeholderApplier"})
public class ViewerImpl implements Viewer {

    private final ViewerContext context;
    private final PlaceholderApplier placeholderApplier;

    @EqualsAndHashCode.Include
    private final UUID uniqueId;
    private final String name;
    private final CustomInventory customInventory;

    private final ViewerConfiguration configuration = new ViewerConfigurationImpl();
    private final ViewerPropertyMap propertyMap = new ViewerPropertyMap();

    @Setter(AccessLevel.PRIVATE)
    protected InventoryEditor editor;

    @Override
    public Inventory createInventory() {
        Inventory inventory = Bukkit.createInventory(
                null,
                configuration.inventorySize(),
                ChatColor.translateAlternateColorCodes('&', configuration.titleInventory())
        );
        this.editor = new InventoryEditorImpl(inventory, placeholderApplier);
        return inventory;
    }

    @Override
    public void resetConfigurations() {
        this.configuration.titleInventory(this.customInventory.getTitle());
        this.configuration.inventorySize(this.customInventory.getSize());
        this.configuration.backInventory(null);
    }

    @Override
    public void updateTitle(String title) {
        getTitleUpdater().update(getPlayer(), title);
    }

    @Override
    public void close() {
        Player player = getPlayer();
        if (player == null || !player.isOnline()) {
            getRegistry().unregisterViewer(this);
            return;
        }

        Bukkit.getScheduler().runTask(getPlugin(), player::closeInventory);
    }

}
