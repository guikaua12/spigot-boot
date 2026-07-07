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
package tech.guilhermekaua.spigotboot.core.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeBootPluginTest {

    private Plugin mockPlugin() {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getName()).thenReturn("TestPlugin");
        when(plugin.getDescription()).thenReturn(description);
        return plugin;
    }

    @Test
    void mapsNameFromDescription() {
        Plugin plugin = mockPlugin();
        assertEquals("TestPlugin", new BungeeBootPlugin(plugin).getName());
    }

    @Test
    void delegatesLoggerDataFolderAndResource() {
        Plugin plugin = mockPlugin();
        Logger logger = Logger.getLogger("bungee-test");
        File dataFolder = new File("plugins/TestPlugin");
        InputStream resource = new ByteArrayInputStream(new byte[0]);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getResourceAsStream("config.yml")).thenReturn(resource);

        BungeeBootPlugin bootPlugin = new BungeeBootPlugin(plugin);

        assertSame(logger, bootPlugin.getLogger());
        assertSame(dataFolder, bootPlugin.getDataFolder());
        assertSame(resource, bootPlugin.getResource("config.yml"));
    }

    @Test
    void exposesNativePluginAndMainClass() {
        Plugin plugin = mockPlugin();
        BungeeBootPlugin bootPlugin = new BungeeBootPlugin(plugin);

        assertSame(plugin, bootPlugin.getNativePlugin());
        // a Mockito mock is not a spigot-boot proxy and its classloader name does not contain "mockbukkit",
        // so ProxyUtils.isProxy() returns false and getRealClass() returns mock.getClass() directly.
        assertEquals(plugin.getClass(), bootPlugin.getMainClass());
        assertSame(plugin.getClass().getClassLoader(), bootPlugin.getClassLoader());
    }
}
