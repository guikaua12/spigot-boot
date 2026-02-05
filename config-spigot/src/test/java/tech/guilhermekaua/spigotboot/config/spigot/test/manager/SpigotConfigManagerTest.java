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

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SpigotConfigManagerTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(SpigotConfigManagerTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
    }

    @Test
    void get_WhenConfigIsNotRegistered_ThrowsConfigException() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.get(TestConfig.class)
        );

        assertTrue(exception.getMessage().contains("Config not registered"));
        assertTrue(exception.getMessage().contains(TestConfig.class.getName()));
    }

    @Test
    void get_WhenConfigIsRegisteredButNotInitialized_ThrowsConfigException() {
        configManager.register(TestConfig.class);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.get(TestConfig.class)
        );

        assertTrue(exception.getMessage().contains("Config not loaded"));
        assertTrue(exception.getMessage().contains(TestConfig.class.getName()));
    }

    @Test
    void get_WhenConfigIsInitialized_ReturnsInstance() {
        configManager.register(TestConfig.class);
        configManager.initializeAll();

        TestConfig config = configManager.get(TestConfig.class);

        assertNotNull(config);
    }

    @Config(value = "test-config.yml", generateDefaults = false)
    public static class TestConfig {
        private String name = "default-name";

        public TestConfig() {
        }
    }
}
