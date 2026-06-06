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
package tech.guilhermekaua.spigotboot.inventoryapi.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomInventoryListenerCloseTest {

    private ViewerRegistry viewerRegistry;
    private CustomInventoryListener listener;
    private PlayerMock player;
    private Inventory viewerInventory;
    private Inventory unrelatedInventory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewerRegistry = new ViewerRegistry();
        listener = new CustomInventoryListener(viewerRegistry);
        player = MockBukkit.getMock().addPlayer("tester");
        viewerInventory = Bukkit.createInventory(null, 27);
        unrelatedInventory = Bukkit.createInventory(null, 27);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Viewer registerViewer(Inventory inventory) {
        Viewer viewer = mock(Viewer.class);
        when(viewer.getUniqueId()).thenReturn(player.getUniqueId());
        lenient().when(viewer.getInventory()).thenReturn(inventory);
        viewerRegistry.registerViewer(viewer);
        return viewer;
    }

    private InventoryCloseEvent closeEventFor(Inventory inventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        return new InventoryCloseEvent(view);
    }

    @Test
    void close_ofUnrelatedInventory_keepsViewerRegistered() {
        registerViewer(viewerInventory);

        listener.onInventoryClose(closeEventFor(unrelatedInventory));

        assertTrue(viewerRegistry.findViewer(player).isPresent(),
                "closing a different container must not unregister the viewer");
    }

    @Test
    void close_ofViewerInventory_unregistersViewer() {
        registerViewer(viewerInventory);

        listener.onInventoryClose(closeEventFor(viewerInventory));

        assertFalse(viewerRegistry.findViewer(player).isPresent());
    }

    @Test
    void close_withNoRegisteredViewer_doesNothing() {
        listener.onInventoryClose(closeEventFor(viewerInventory));

        assertFalse(viewerRegistry.findViewer(player).isPresent());
    }
}
