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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryConfigurationImplTest {

    @Test
    void defaultsAreUpdatesDisabledAndSync() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();

        assertEquals(InventoryConfiguration.TICK_UPDATE_DISABLED, configuration.tickUpdate());
        assertFalse(configuration.tickAsync(), "periodic updates must default to the main thread");
    }

    @Test
    void tickAsyncIsFluentlyMutable() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();

        assertSame(configuration, configuration.tickAsync(true));
        assertTrue(configuration.tickAsync());

        configuration.tickAsync(false);
        assertFalse(configuration.tickAsync());
    }

    @Test
    void tickUpdateIsFluentlyMutable() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();

        assertSame(configuration, configuration.tickUpdate(20));
        assertEquals(20, configuration.tickUpdate());
    }
}
