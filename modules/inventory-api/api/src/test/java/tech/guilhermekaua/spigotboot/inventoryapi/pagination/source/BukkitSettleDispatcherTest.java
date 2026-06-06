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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitSettleDispatcherTest {

    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PageRequest requestFor(MockPlugin plugin) {
        Viewer viewer = mock(Viewer.class);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(null);
        return new PageRequest(1, 3, 0, viewer);
    }

    @Test
    void dispatch_onPrimaryThread_runsInline() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        AtomicBoolean ran = new AtomicBoolean();

        dispatcher.dispatch(requestFor(plugin), () -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void dispatch_whilePluginDisabling_dropsSettleInsteadOfThrowing() {
        // MockBukkit's scheduler does not validate plugin state, so the real server's
        // rejection is reproduced by stubbing the scheduler to throw what CraftBukkit
        // throws when a disabling plugin registers a task
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        PageRequest request = requestFor(plugin);
        AtomicBoolean ran = new AtomicBoolean();

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertDoesNotThrow(() -> dispatcher.dispatch(request, () -> ran.set(true)),
                    "a disabling plugin must not blow up the completing thread");
        }

        assertFalse(ran.get(), "the scheduler rejected the task; the settle is dropped");
    }
}
