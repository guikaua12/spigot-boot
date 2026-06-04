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
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.InventoryRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.ViewerContext;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.impl.ViewerImpl;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * User-facing facade for the inventory module. Replaces the upstream {@code InventoryManager}
 * static methods with a single injectable bean.
 *
 * <p>Typical usage:
 * <pre>{@code
 * @Component
 * class MyMenuOpener {
 *     @Inject InventoryService inventories;
 *
 *     void show(Player p) {
 *         inventories.open(p, MyInventory.class);
 *     }
 * }
 * }</pre>
 */
@Service
public final class InventoryService {

    private final InventoryRegistry inventoryRegistry;
    private final ViewerRegistry viewerRegistry;
    private final PlaceholderApplier placeholderApplier;
    private final ViewerContext viewerContext;

    public InventoryService(
            InventoryRegistry inventoryRegistry,
            ViewerRegistry viewerRegistry,
            TitleUpdater titleUpdater,
            Plugin plugin,
            PlaceholderApplier placeholderApplier
    ) {
        this.inventoryRegistry = inventoryRegistry;
        this.viewerRegistry = viewerRegistry;
        this.placeholderApplier = placeholderApplier;
        this.viewerContext = new ViewerContext(viewerRegistry, titleUpdater, plugin);
    }

    /**
     * Opens the registered inventory of {@code type} for {@code player}.
     *
     * @return the freshly created {@link Viewer}, or {@code null} if no inventory of that type is
     * registered
     */
    public @Nullable Viewer open(@NotNull Player player, @NotNull Class<? extends CustomInventory> type) {
        return open(player, type, null);
    }

    /**
     * Opens the registered inventory of {@code type} for {@code player}, invoking
     * {@code initializer} against the freshly built {@link Viewer} before the inventory is
     * actually rendered.
     *
     * @return the freshly created {@link Viewer}, or {@code null} if no inventory of that type is
     * registered
     */
    public @Nullable Viewer open(
            @NotNull Player player,
            @NotNull Class<? extends CustomInventory> type,
            @Nullable Consumer<Viewer> initializer
    ) {
        Optional<CustomInventory> maybe = inventoryRegistry.findInventory(type);
        if (!maybe.isPresent()) {
            return null;
        }

        CustomInventory inventory = maybe.get();
        Viewer viewer = new ViewerImpl(
                viewerContext,
                placeholderApplier,
                player.getUniqueId(),
                player.getName(),
                inventory
        );

        inventory.defaultOpenInventory(player, viewer, initializer);
        return viewer;
    }

    /**
     * Closes the inventory currently open for the given player and removes their
     * {@link Viewer} from the registry. No-op if the player has no active viewer.
     */
    public void close(@NotNull Player player) {
        viewerRegistry.findViewer(player).ifPresent(Viewer::close);
    }

    /**
     * Triggers an immediate update for the player's open inventory, invoking the inventory's
     * {@code update(viewer, editor)} hook. No-op if the player has no active viewer.
     */
    public void update(@NotNull Player player) {
        viewerRegistry.findViewer(player)
                .ifPresent(viewer -> viewer.getCustomInventory().updateInventory(player));
    }

}
