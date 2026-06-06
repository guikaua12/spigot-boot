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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.Bukkit;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

/**
 * Default {@link SettleDispatcher}: applies asynchronously completed page loads on the main
 * server thread, mirroring how click-triggered updates always run there. Inventories that opted
 * into {@link InventoryConfiguration#tickAsync()} settle directly on the completing thread, the
 * same trust the async tick task already extends to them.
 */
public final class BukkitSettleDispatcher implements SettleDispatcher {

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        Viewer viewer = request.getViewer();
        if (viewer == null || Bukkit.isPrimaryThread() || tickAsync(viewer)) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(viewer.getPlugin(), task);
    }

    private static boolean tickAsync(Viewer viewer) {
        CustomInventory customInventory = viewer.getCustomInventory();
        if (customInventory == null) {
            return false;
        }
        InventoryConfiguration configuration = customInventory.getConfiguration();
        return configuration != null && configuration.tickAsync();
    }
}
