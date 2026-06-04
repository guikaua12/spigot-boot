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
package tech.guilhermekaua.spigotboot.inventoryapi.registry.discovery;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDiscoveryServiceTest {

    @Test
    void emptyResultForPackageWithNoAnnotatedClasses() {
        InventoryDiscoveryService service = new InventoryDiscoveryService();

        Set<Class<? extends CustomInventory>> result = service.discoverFromPackage(
                "tech.guilhermekaua.spigotboot.inventoryapi.nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void resultIsAlwaysNonNull() {
        InventoryDiscoveryService service = new InventoryDiscoveryService();

        Set<Class<? extends CustomInventory>> result = service.discoverFromPackage(
                "com.example.does.not.exist");

        assertTrue(result.isEmpty(), "service should return empty set, not null");
    }
}
