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
package tech.guilhermekaua.spigotboot.config.spigot.test.manager;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SpigotConfigManagerValueAccessTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() throws IOException {
        Logger logger = Logger.getLogger(SpigotConfigManagerValueAccessTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"),
                "name: hello\nport: 25565\nenabled: true\nratio: 1.5\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
    }

    @Test
    void getConfigName_returnsRegisteredName() {
        assertEquals("app", configManager.getConfigName(AppConfig.class));
    }

    @Test
    void getConfigName_whenUnregistered_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configManager.getConfigName(UnregisteredConfig.class));
        assertTrue(ex.getMessage().contains(UnregisteredConfig.class.getName()));
    }

    @Test
    void deserializeAt_readsScalarsOfEachType() {
        assertEquals("hello", configManager.deserializeAt("app:name", String.class));
        assertEquals(Integer.valueOf(25565), configManager.deserializeAt("app:port", Integer.class));
        assertEquals(Integer.valueOf(25565), configManager.deserializeAt("app:port", int.class));
        assertEquals(Boolean.TRUE, configManager.deserializeAt("app:enabled", Boolean.class));
        assertEquals(Double.valueOf(1.5), configManager.deserializeAt("app:ratio", Double.class));
    }

    @Test
    void deserializeAt_whenKeyAbsent_returnsNull() {
        assertNull(configManager.deserializeAt("app:missing", String.class));
    }

    @Test
    void deserializeAt_whenTypeUnsupported_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> configManager.deserializeAt("app:name", List.class));
        assertTrue(ex.getMessage().contains(List.class.getName()));
    }

    @Test
    void coerceDefault_coercesToTargetType() {
        assertEquals(Integer.valueOf(42), configManager.coerceDefault("42", Integer.class));
        assertEquals("", configManager.coerceDefault("", String.class));
    }

    @Test
    void coerceDefault_whenUnparseable_throws() {
        assertThrows(ConfigException.class, () -> configManager.coerceDefault("abc", Integer.class));
    }

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        private boolean enabled;
        private double ratio;

        public AppConfig() {
        }
    }

    @Config(value = "unregistered.yml", name = "unregistered", generateDefaults = false)
    public static class UnregisteredConfig {
        private String value;

        public UnregisteredConfig() {
        }
    }
}
