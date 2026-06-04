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

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerRegistryTest {

    @Test
    void registerAndFindByPlayer() {
        UUID id = UUID.randomUUID();
        ViewerRegistry registry = new ViewerRegistry();
        Viewer viewer = mockViewer(id);

        registry.registerViewer(viewer);

        Player player = mockPlayer(id);
        assertTrue(registry.findViewer(player).isPresent());
        assertSame(viewer, registry.findViewer(player).get());
    }

    @Test
    void unregisterByPlayerRemovesEntry() {
        UUID id = UUID.randomUUID();
        ViewerRegistry registry = new ViewerRegistry();
        registry.registerViewer(mockViewer(id));

        Viewer removed = registry.unregisterViewer(mockPlayer(id));
        assertNotNull(removed);
        assertFalse(registry.findViewer(mockPlayer(id)).isPresent());
    }

    @Test
    void unregisterByViewerRemovesEntry() {
        UUID id = UUID.randomUUID();
        ViewerRegistry registry = new ViewerRegistry();
        Viewer viewer = mockViewer(id);
        registry.registerViewer(viewer);

        Viewer removed = registry.unregisterViewer(viewer);
        assertSame(viewer, removed);
        assertEquals(0, registry.findAll().size());
    }

    @Test
    void findReturnsEmptyForUnknownPlayer() {
        ViewerRegistry registry = new ViewerRegistry();
        assertFalse(registry.findViewer(mockPlayer(UUID.randomUUID())).isPresent());
    }

    @Test
    void findAllReturnsLiveSnapshot() {
        ViewerRegistry registry = new ViewerRegistry();
        registry.registerViewer(mockViewer(UUID.randomUUID()));
        registry.registerViewer(mockViewer(UUID.randomUUID()));

        assertEquals(2, registry.findAll().size());
    }

    private static Viewer mockViewer(UUID id) {
        Viewer viewer = Mockito.mock(Viewer.class);
        Mockito.when(viewer.getUniqueId()).thenReturn(id);
        return viewer;
    }

    private static Player mockPlayer(UUID id) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(id);
        return player;
    }
}
