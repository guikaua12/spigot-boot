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

    @Test
    void getByPath_WhenSingleConfigWithoutPrefix_ReturnsValue() throws IOException {
        writeYaml("single.yml", "value: single\n");
        configManager.register(SingleNamedConfig.class);

        String value = configManager.get("value", String.class);

        assertEquals("single", value);
    }

    @Test
    void getByPath_WhenMultipleConfigsWithPrefix_ReturnsValueFromRequestedConfig() throws IOException {
        writeYaml("main.yml", "value: main\n");
        writeYaml("messages.yml", "value: messages\n");
        configManager.register(MainNamedConfig.class);
        configManager.register(MessagesNamedConfig.class);

        String value = configManager.get("messages:value", String.class);

        assertEquals("messages", value);
    }

    @Test
    void getByPath_WhenMultipleConfigsWithoutPrefix_ThrowsAmbiguousLookupException() throws IOException {
        writeYaml("main.yml", "value: main\n");
        writeYaml("messages.yml", "value: messages\n");
        configManager.register(MainNamedConfig.class);
        configManager.register(MessagesNamedConfig.class);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> configManager.get("value", String.class)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("ambiguous"));
        assertTrue(message.contains("main"));
        assertTrue(message.contains("messages"));
    }

    private void writeYaml(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }

    @Config(value = "test-config.yml", generateDefaults = false)
    public static class TestConfig {
        private String name = "default-name";

        public TestConfig() {
        }
    }

    @Config(value = "single.yml", name = "single", generateDefaults = false)
    public static class SingleNamedConfig {
        private String value;

        public SingleNamedConfig() {
        }
    }

    @Config(value = "main.yml", name = "main", generateDefaults = false)
    public static class MainNamedConfig {
        private String value;

        public MainNamedConfig() {
        }
    }

    @Config(value = "messages.yml", name = "messages", generateDefaults = false)
    public static class MessagesNamedConfig {
        private String value;

        public MessagesNamedConfig() {
        }
    }
}
