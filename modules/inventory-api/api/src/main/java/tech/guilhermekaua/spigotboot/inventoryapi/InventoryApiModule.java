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
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.InventoryRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.schedule.InventoryUpdateRunnable;

/**
 * Discovers every {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory}
 * -annotated {@link CustomInventory} subclass under the host plugin's base package, instantiates
 * each one through the dependency manager, and schedules its periodic update task.
 *
 * <p>The update task is skipped when {@code tickUpdate <= 0} and runs on the main server thread
 * unless the inventory opts into {@code tickAsync(true)}.
 *
 * <p>Replaces the upstream {@code InventoryManager.enable(plugin, inv1, inv2, ...)} static
 * bootstrap. Users no longer hand-list inventories — annotated subclasses self-register.
 */
public final class InventoryApiModule implements Module {

    @Inject
    private InventoryRegistry inventoryRegistry;

    @Inject
    private ViewerRegistry viewerRegistry;

    @Inject
    private Plugin plugin;

    @Override
    public void onInitialize(Context context) throws Exception {
        inventoryRegistry.initialize(context);

        BukkitScheduler scheduler = Bukkit.getScheduler();
        for (CustomInventory inventory : inventoryRegistry.findAll()) {
            InventoryConfiguration configuration = inventory.getConfiguration();
            int tickUpdate = configuration.tickUpdate();
            if (tickUpdate <= InventoryConfiguration.TICK_UPDATE_DISABLED) {
                continue;
            }

            InventoryUpdateRunnable task = new InventoryUpdateRunnable(viewerRegistry, inventory);
            if (configuration.tickAsync()) {
                scheduler.runTaskTimerAsynchronously(plugin, task, 0L, tickUpdate);
            } else {
                scheduler.runTaskTimer(plugin, task, 0L, tickUpdate);
            }
        }
    }
}
