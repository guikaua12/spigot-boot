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

import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores every custom inventory the framework discovered at boot, keyed by concrete class.
 * Replaces the upstream {@code InventoryController} static singleton with a DI-managed bean.
 */
@Component
public final class InventoryRegistry {

    private final Map<Class<? extends CustomInventory>, CustomInventory> inventoryMap = new ConcurrentHashMap<>();

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
