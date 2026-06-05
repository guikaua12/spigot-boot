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

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Base class for user-defined inventories. Subclasses implement {@link #configure} to declare the
 * title, size and tick configuration, and override {@link #firstOpen}, {@link #configureInventory},
 * {@link #update} and {@link #configureViewer} to populate items and react to renders.
 *
 * <p>The no-arg constructor leaves the subclass constructor free for dependency injection (for
 * example Lombok's {@code @RequiredArgsConstructor}). The framework calls {@link #applyConfiguration}
 * once, after construction and dependency injection, so {@link #configure} may reference injected
 * collaborators.
 *
 * <p>The {@code viewerRegistry} field is filled by the framework after construction when the
 * inventory is registered in {@link tech.guilhermekaua.spigotboot.inventoryapi.registry.InventoryRegistry},
 * so subclass authors never see it.
 */
@Getter
public abstract class CustomInventoryImpl implements CustomInventory {

    private final InventoryConfiguration configuration = new InventoryConfigurationImpl();

    private String title;
    private int size;

    @Getter(AccessLevel.NONE)
    private boolean configured;

    @Inject
    private ViewerRegistry viewerRegistry;

    /**
     * Populates this inventory's title, size and tick configuration by invoking {@link #configure}.
     *
     * <p>Called once by the framework after construction and dependency injection. Idempotent — a
     * second call is a no-op.
     *
     * @throws IllegalStateException if {@link #configure} leaves the title unset or the size
     *                               non-positive
     */
    public final void applyConfiguration() {
        if (configured) {
            return;
        }

        InventorySettings settings = new InventorySettings(configuration);
        configure(settings);

        this.title = settings.getTitle();
        this.size = settings.getSize();

        if (this.title == null) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a title.");
        }
        if (this.size <= 0) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a positive size.");
        }

        this.configured = true;
    }

    /**
     * Declares this inventory's title, size and tick configuration. Invoked once after construction
     * and dependency injection.
     *
     * @param settings the settings to populate, not null
     */
    protected abstract void configure(@NotNull InventorySettings settings);

    @Override
    public void updateInventory(@NotNull Player player) {
        viewerRegistry.findViewer(player).ifPresent(viewer -> {
            if (viewer.getCustomInventory() == this) {
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
        viewer.resetConfigurations();
        this.configureViewer(viewer);

        if (viewerConsumer != null) {
            viewerConsumer.accept(viewer);
        }

        Inventory inventory = viewer.createInventory();
        viewerRegistry.registerViewer(viewer);
        InventoryEditor editor = viewer.getEditor();

        player.openInventory(inventory);

        firstOpen(viewer, editor);
        configureInventory(viewer, editor);
        update(viewer, editor);
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
