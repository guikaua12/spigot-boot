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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRegistryTest {

    @Test
    void registerAndFindRoundtrip() {
        InventoryRegistry registry = new InventoryRegistry();
        StubInventory stub = new StubInventory();

        registry.registerInventory(stub);

        assertTrue(registry.findInventory(StubInventory.class).isPresent());
        assertSame(stub, registry.findInventory(StubInventory.class).get());
    }

    @Test
    void findReturnsEmptyForUnknownClass() {
        InventoryRegistry registry = new InventoryRegistry();

        assertFalse(registry.findInventory(StubInventory.class).isPresent());
    }

    @Test
    void registerOverwritesByConcreteClassKey() {
        InventoryRegistry registry = new InventoryRegistry();
        StubInventory first = new StubInventory();
        StubInventory second = new StubInventory();

        registry.registerInventory(first);
        registry.registerInventory(second);

        assertEquals(1, registry.findAll().size());
        assertSame(second, registry.findInventory(StubInventory.class).get());
    }

    @Test
    void lombokStyleInventoryIsConstructedInjectedAndConfigured() throws Exception {
        DependencyManager dependencyManager = new DependencyManager();
        FakeService service = new FakeService();
        dependencyManager.registerDependency(service, null, false);

        Constructor<?> constructor = dependencyManager.findInjectConstructor(LombokStyleInventory.class);
        assertNotNull(constructor);

        Object[] arguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        LombokStyleInventory inventory = (LombokStyleInventory) constructor.newInstance(arguments);

        inventory.applyConfiguration();

        assertSame(service, inventory.getService());
        assertEquals("&aLombok", inventory.getTitle());
        assertEquals(54, inventory.getSize());
        assertEquals(20, inventory.getConfiguration().tickUpdate());
    }

    @Test
    void stubInventoryDerivesRowsFromSize() {
        assertEquals(1, new StubInventory().getRows());
    }

    private static final class FakeService {
    }

    @Getter // exposes getService() for the assertSame check in the regression test
    @RequiredArgsConstructor
    private static final class LombokStyleInventory extends CustomInventoryImpl {
        private final FakeService service;

        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aLombok").rows(6).tickUpdate(20);
        }
    }

    private static final class StubInventory implements CustomInventory {
        @Override
        public @NotNull String getTitle() { return "stub"; }

        @Override
        public int getSize() { return 9; }

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull <T extends InventoryConfiguration> T getConfiguration() { return (T) new InventoryConfiguration() {
            int ticks = 999999;
            boolean async = false;
            @Override public int tickUpdate() { return ticks; }
            @Override public InventoryConfiguration tickUpdate(int t) { ticks = t; return this; }
            @Override public boolean tickAsync() { return async; }
            @Override public InventoryConfiguration tickAsync(boolean a) { async = a; return this; }
        }; }

        @Override
        public void defaultOpenInventory(Player player, Viewer viewer, Consumer<Viewer> viewerConsumer) { }

        @Override
        public void updateInventory(@NotNull Player player) { }
    }
}
