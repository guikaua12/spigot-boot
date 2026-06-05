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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySettingsTest {

    @Test
    void settersAreFluentAndStoreValues() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();
        InventorySettings settings = new InventorySettings(configuration);

        assertSame(settings, settings.title("&aShop"));
        assertSame(settings, settings.rows(6));

        assertEquals("&aShop", settings.getTitle());
        assertEquals(6, settings.getRows());
    }

    @Test
    void rowsAcceptsChestRangeBounds() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertEquals(1, settings.rows(1).getRows());
        assertEquals(6, settings.rows(6).getRows());
    }

    @Test
    void rowsRejectsValuesOutsideChestRange() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertThrows(IllegalArgumentException.class, () -> settings.rows(0));
        assertThrows(IllegalArgumentException.class, () -> settings.rows(-1));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settings.rows(7));
        assertEquals("rows must be between 1 and 6, got 7", error.getMessage());
    }

    @Test
    void getRowsReturnsZeroWhenUnset() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertEquals(0, settings.getRows());
    }

    @Test
    void tickOptionsDelegateToWrappedConfiguration() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();
        InventorySettings settings = new InventorySettings(configuration);

        assertSame(settings, settings.tickUpdate(20));
        assertSame(settings, settings.tickAsync(true));

        assertEquals(20, configuration.tickUpdate());
        assertTrue(configuration.tickAsync());
        assertSame(configuration, settings.getConfiguration());
    }

    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new InventorySettings(null));
    }
}
