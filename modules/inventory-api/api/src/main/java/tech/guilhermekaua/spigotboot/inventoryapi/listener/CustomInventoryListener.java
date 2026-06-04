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
package tech.guilhermekaua.spigotboot.inventoryapi.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.event.impl.CustomInventoryClickEvent;
import tech.guilhermekaua.spigotboot.inventoryapi.event.impl.CustomInventoryCloseEvent;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Bridges Bukkit's {@link InventoryClickEvent} and {@link InventoryCloseEvent} into the
 * inventory-api's own {@link CustomInventoryClickEvent} / {@link CustomInventoryCloseEvent},
 * dispatching click handlers registered on the active viewer.
 *
 * <p>Auto-registered with Bukkit by spigot-boot's {@code BukkitListenerAutoRegistrar} when
 * the context becomes ready — no manual {@code registerEvents} call required.
 */
@Component
@RequiredArgsConstructor
public final class CustomInventoryListener implements Listener {

    private final ViewerRegistry viewerRegistry;

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();

        Viewer viewer = viewerRegistry.unregisterViewer(player);
        if (viewer != null) {
            CustomInventoryCloseEvent closeEvent = new CustomInventoryCloseEvent(viewer, event);
            Bukkit.getPluginManager().callEvent(closeEvent);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        Player player = (Player) event.getWhoClicked();

        viewerRegistry.findViewer(player).ifPresent(viewer -> {
            event.setCancelled(true);

            CustomInventoryClickEvent clickEvent = new CustomInventoryClickEvent(viewer, event);
            Bukkit.getPluginManager().callEvent(clickEvent);

            if (clickedInventory.getType().equals(InventoryType.PLAYER)) return;

            InventoryEditor editor = viewer.getEditor();
            ItemCallback itemCallback = editor.getItemCallback(event.getRawSlot());
            if (itemCallback == null) return;

            Consumer<CustomInventoryClickEvent> callback = itemCallback.getClickCallback(event.getClick());
            if (callback != null) {
                callback.accept(clickEvent);
            }
        });
    }

}
