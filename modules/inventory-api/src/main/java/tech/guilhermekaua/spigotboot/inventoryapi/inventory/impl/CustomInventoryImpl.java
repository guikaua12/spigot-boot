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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Base class for user-defined inventories. Subclasses override {@link #firstOpen},
 * {@link #configureInventory}, {@link #update} and {@link #configureViewer} to populate items
 * and react to renders.
 *
 * <p>The {@code viewerRegistry} field is filled by the framework after construction via the
 * dependency manager's hierarchy walk, so subclass authors never see it.
 */
@Getter
@RequiredArgsConstructor
public abstract class CustomInventoryImpl implements CustomInventory {
    private final String title;
    private final int size;

    private final InventoryConfiguration configuration = new InventoryConfigurationImpl();

    @Inject
    private ViewerRegistry viewerRegistry;

    @Override
    public final <T extends InventoryConfiguration> void configuration(@NotNull Consumer<T> consumer) {
        T configuration = this.getConfiguration();
        consumer.accept(configuration);
    }

    @Override
    public void updateInventory(@NotNull Player player) {
        viewerRegistry.findViewer(player).ifPresent(viewer -> {
            if (viewer.getCustomInventory().getClass().isInstance(this)) {
                InventoryEditor editor = viewer.getEditor();
                update(viewer, editor);
                editor.updateAllItemStacks();

                player.updateInventory();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends InventoryConfiguration> @NotNull T getConfiguration() {
        return (T) configuration;
    }

    @Override
    public void defaultOpenInventory(Player player, Viewer viewer, Consumer<Viewer> viewerConsumer) {
        if (viewerConsumer != null) {
            viewerConsumer.accept(viewer);
        }

        viewer.resetConfigurations();
        this.configureViewer(viewer);

        Inventory inventory = viewer.createInventory();
        InventoryEditor editor = viewer.getEditor();

        player.openInventory(inventory);

        firstOpen(viewer, editor);
        configureInventory(viewer, editor);
        update(viewer, editor);

        viewerRegistry.registerViewer(viewer);
    }

    protected void configureViewer(@NotNull Viewer viewer) {
        // empty default — override to seed per-viewer configuration before the inventory opens
    }

    protected void configureInventory(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override to lay out static items on first open
    }

    protected void update(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override to refresh dynamic items on each tick or click
    }

    protected void firstOpen(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override for one-shot setup that runs once when the player opens the GUI
    }
}
