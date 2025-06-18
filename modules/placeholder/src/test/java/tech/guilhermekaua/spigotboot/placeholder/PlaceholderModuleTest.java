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
package tech.guilhermekaua.spigotboot.placeholder;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.core.ApxPlugin;
import tech.guilhermekaua.spigotboot.placeholder.registry.PlaceholderRegistry;

import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class PlaceholderModuleTest {

    private ServerMock server;
    private ApxPlugin plugin;
    private PlaceholderRegistry registry;
    private Logger logger;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(ApxPlugin.class);

        registry = mock(PlaceholderRegistry.class);

        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testInitialize_PlaceholderApiPresent() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        module.initialize();

        verify(logger).info("Initializing Placeholder Module...");
        verify(registry).initialize();
        verify(plugin).addDisableEntry(any());
    }

    @Test
    void testInitialize_PlaceholderApiAbsent() throws Exception {
        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        module.initialize();

        verify(logger).info("Initializing Placeholder Module...");
        verify(logger).info("PlaceholderAPI not found, skipping registration of placeholders.");
        verify(registry, never()).initialize();
        verify(plugin, never()).addDisableEntry(any());
    }

    @Test
    void testDisableEntry_UnregistersIfPluginEnabled() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        module.initialize();
        verify(plugin).addDisableEntry(captor.capture());

        Runnable disableEntry = captor.getValue();
        disableEntry.run();

        verify(logger).info("Unregistering PlaceholderAPI expansion...");
        verify(registry).unregister();
    }

    @Test
    void testDisableEntry_DoesNotUnregisterIfPluginDisabled() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        module.initialize();
        verify(plugin).addDisableEntry(captor.capture());

        server.getPluginManager().disablePlugin(papi);

        Runnable disableEntry = captor.getValue();
        disableEntry.run();

        verify(logger).info("Unregistering PlaceholderAPI expansion...");
        verify(registry, never()).unregister();
    }
}

