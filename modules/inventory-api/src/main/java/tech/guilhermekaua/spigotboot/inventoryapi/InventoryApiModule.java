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
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.InventoryRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.discovery.InventoryDiscoveryService;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.schedule.InventoryUpdateRunnable;

import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers every {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory}
 * -annotated {@link CustomInventory} subclass under the host plugin's base package, instantiates
 * each one through the dependency manager, and schedules its periodic update task.
 *
 * <p>Replaces the upstream {@code InventoryManager.enable(plugin, inv1, inv2, ...)} static
 * bootstrap. Users no longer hand-list inventories — annotated subclasses self-register.
 */
public final class InventoryApiModule implements Module {
    private static final Logger LOGGER = Logger.getLogger(InventoryApiModule.class.getName());

    @Inject
    private InventoryDiscoveryService discoveryService;

    @Inject
    private InventoryRegistry inventoryRegistry;

    @Inject
    private ViewerRegistry viewerRegistry;

    @Inject
    private DependencyManager dependencyManager;

    @Inject
    private Plugin plugin;

    @Override
    public void onInitialize(Context context) throws Exception {
        String basePackage = context.getPlugin().getMainClass().getPackage().getName();

        Set<Class<? extends CustomInventory>> classes = discoveryService.discoverFromPackage(basePackage);
        BukkitScheduler scheduler = Bukkit.getScheduler();

        int registered = 0;
        for (Class<? extends CustomInventory> cls : classes) {
            try {
                CustomInventory inventory = instantiateInventory(cls);
                inventoryRegistry.registerInventory(inventory);

                int tickUpdate = inventory.getConfiguration().tickUpdate();
                scheduler.runTaskTimerAsynchronously(
                        plugin,
                        new InventoryUpdateRunnable(viewerRegistry, inventory),
                        0L,
                        tickUpdate
                );
                registered++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to register inventory " + cls.getName(), ex);
            }
        }

        LOGGER.log(Level.INFO, "Registered {0} custom inventories.", registered);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CustomInventory instantiateInventory(Class<? extends CustomInventory> cls) throws Exception {
        Constructor<?> ctor = dependencyManager.findInjectConstructor(cls);
        if (ctor == null) {
            throw new IllegalStateException(
                    "No injectable constructor found for inventory: " + cls.getName()
            );
        }

        Object[] ctorArgs = dependencyManager.resolveArguments(ctor);
        ctor.setAccessible(true);
        CustomInventory instance = (CustomInventory) ctor.newInstance(ctorArgs);

        // walk the class hierarchy so @Inject fields declared on superclasses (notably
        // CustomInventoryImpl#viewerRegistry) are populated; DependencyManager#fieldInject only
        // visits the class's own declared fields.
        Class<?> walker = cls;
        while (walker != null && walker != Object.class) {
            dependencyManager.injectDependencies((Class) walker, instance);
            walker = walker.getSuperclass();
        }

        return instance;
    }
}
