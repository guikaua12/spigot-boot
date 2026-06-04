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
package tech.guilhermekaua.spigotboot.inventoryapi.registry;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.discovery.InventoryDiscoveryService;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores every custom inventory the framework discovered at boot, keyed by concrete class.
 * Replaces the upstream {@code InventoryController} static singleton with a DI-managed bean.
 */
@Component
public final class InventoryRegistry {
    private static final Logger LOGGER = Logger.getLogger(InventoryRegistry.class.getName());

    private final Map<Class<? extends CustomInventory>, CustomInventory> inventoryMap = new ConcurrentHashMap<>();

    /**
     * Discovers {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory}-annotated
     * classes, instantiates each through the dependency manager, and stores the instance for
     * {@code InventoryService} lookups.
     */
    public void initialize(Context context) {
        InventoryDiscoveryService discoveryService = context.getBean(InventoryDiscoveryService.class);
        if (discoveryService == null) {
            throw new IllegalStateException("InventoryDiscoveryService is not available.");
        }

        String basePackage = context.getPlugin().getMainClass().getPackage().getName();
        Set<Class<? extends CustomInventory>> classes = discoveryService.discoverFromPackage(basePackage);
        DependencyManager dependencyManager = context.getDependencyManager();

        int registered = 0;
        for (Class<? extends CustomInventory> inventoryClass : classes) {
            try {
                registerDiscoveredInventory(inventoryClass, dependencyManager);
                registered++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to register inventory " + inventoryClass.getName(), ex);
            }
        }

        LOGGER.log(Level.INFO, "Registered {0} custom inventories.", registered);
    }

    private void registerDiscoveredInventory(
            Class<? extends CustomInventory> inventoryClass,
            DependencyManager dependencyManager
    ) throws Exception {
        Constructor<?> constructor = dependencyManager.findInjectConstructor(inventoryClass);
        if (constructor == null) {
            throw new IllegalStateException(
                    "No injectable constructor found for inventory: " + inventoryClass.getName()
            );
        }

        Object[] constructorArguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        Object rawInstance = constructor.newInstance(constructorArguments);

        BeanDefinition definition = new BeanDefinition(
                inventoryClass,
                inventoryClass,
                inventoryClass.getName() + "#inventory",
                false,
                null,
                null
        );
        CustomInventory inventory = (CustomInventory) dependencyManager.initializeBean(definition, rawInstance);
        injectSuperclassDependencies(dependencyManager, inventoryClass, inventory);

        dependencyManager.registerDependency(
                inventory,
                BeanUtils.getQualifier(inventoryClass),
                BeanUtils.getIsPrimary(inventoryClass)
        );
        registerInventory(inventory);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectSuperclassDependencies(
            DependencyManager dependencyManager,
            Class<? extends CustomInventory> inventoryClass,
            CustomInventory inventoryInstance
    ) {
        for (Class type = inventoryClass.getSuperclass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            dependencyManager.injectDependencies(type, inventoryInstance);
        }
    }

    public <T extends CustomInventory> T registerInventory(T inventory) {
        this.inventoryMap.put(inventory.getClass(), inventory);
        return inventory;
    }

    public Optional<CustomInventory> findInventory(Class<? extends CustomInventory> clazz) {
        return Optional.ofNullable(this.inventoryMap.get(clazz));
    }

    public Collection<CustomInventory> findAll() {
        return Collections.unmodifiableCollection(this.inventoryMap.values());
    }

}
