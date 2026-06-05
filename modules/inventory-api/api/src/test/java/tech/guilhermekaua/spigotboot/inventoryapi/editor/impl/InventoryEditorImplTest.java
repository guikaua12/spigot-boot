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
package tech.guilhermekaua.spigotboot.inventoryapi.editor.impl;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryEditorImplTest {

    private static final int INVENTORY_SIZE = 9;

    private ServerMock server;
    private MockPlugin plugin;
    private Player player;
    private InventoryEditorImpl editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
        player = server.addPlayer("TestPlayer");

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, "test");
        editor = new InventoryEditorImpl(inventory, new NoopPlaceholderApplier(), player);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setItem_outOfBoundsSlot_throwsIllegalArgumentException() {
        InventoryItem item = InventoryItem.of(new ItemStack(Material.DIAMOND));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> editor.setItem(36, item)
        );

        assertEquals("slot 36 is out of bounds for inventory size " + INVENTORY_SIZE, exception.getMessage());
    }

    @Test
    void setEmptyItem_negativeSlot_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> editor.setEmptyItem(-1)
        );

        assertEquals("slot -1 is out of bounds for inventory size " + INVENTORY_SIZE, exception.getMessage());
    }

    @Test
    void setItem_validSlot_succeeds() {
        InventoryItem item = InventoryItem.of(new ItemStack(Material.DIAMOND));

        assertDoesNotThrow(() -> editor.setItem(0, item));
        assertEquals(Material.DIAMOND, editor.getItemStack(0).getType());
        assertEquals(Material.DIAMOND, editor.getInventory().getItem(0).getType());
    }
}
