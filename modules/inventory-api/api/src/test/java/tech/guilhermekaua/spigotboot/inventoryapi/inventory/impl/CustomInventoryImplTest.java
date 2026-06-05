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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomInventoryImplTest {

    @Test
    void applyConfigurationPopulatesTitleSizeAndTickConfig() {
        ConfiguredInventory inventory = new ConfiguredInventory();

        inventory.applyConfiguration();

        assertEquals("&aShop", inventory.getTitle());
        assertEquals(54, inventory.getSize());
        assertEquals(20, inventory.getConfiguration().tickUpdate());
    }

    @Test
    void applyConfigurationIsIdempotent() {
        ConfiguredInventory inventory = new ConfiguredInventory();

        inventory.applyConfiguration();
        inventory.applyConfiguration();

        assertEquals(1, inventory.configureCalls);
    }

    @Test
    void applyConfigurationThrowsWhenTitleMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MissingTitleInventory().applyConfiguration());

        assertTrue(error.getMessage().contains(MissingTitleInventory.class.getName()));
    }

    @Test
    void applyConfigurationThrowsWhenSizeNotPositive() {
        assertThrows(IllegalStateException.class,
                () -> new MissingSizeInventory().applyConfiguration());
    }

    private static final class ConfiguredInventory extends CustomInventoryImpl {
        private int configureCalls;

        @Override
        protected void configure(@NotNull InventorySettings settings) {
            configureCalls++;
            settings.title("&aShop").size(54).tickUpdate(20);
        }
    }

    private static final class MissingTitleInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.size(54);
        }
    }

    private static final class MissingSizeInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aShop");
        }
    }
}
