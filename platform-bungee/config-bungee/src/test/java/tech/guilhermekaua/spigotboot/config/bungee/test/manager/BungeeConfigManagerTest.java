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
package tech.guilhermekaua.spigotboot.config.bungee.test.manager;

import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BungeeConfigManagerTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private BungeeConfigManager configManager;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(BungeeConfigManagerTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new BungeeConfigManager(plugin);
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

    @Test
    void register_WhenSameClassRegisteredTwice_ThrowsConfigException() {
        configManager.register(TestConfig.class);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.register(TestConfig.class)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(TestConfig.class.getName()));

        assertTrue(configManager.isRegistered(TestConfig.class));
        assertEquals(1, configManager.getRegisteredConfigs().size());
    }

    @Test
    void register_WhenDifferentClassesShareSameConfigName_ThrowsConfigExceptionAndKeepsOriginalMapping() throws IOException {
        writeYaml("collision-one.yml", "value: one\n");
        writeYaml("collision-two.yml", "value: two\n");

        configManager.register(CollisionConfigOne.class);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.register(CollisionConfigTwo.class)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("collision"));

        assertTrue(configManager.isRegistered(CollisionConfigOne.class));
        assertFalse(configManager.isRegistered(CollisionConfigTwo.class));
        assertEquals(1, configManager.getRegisteredConfigs().size());

        String value = configManager.get("collision:value", String.class);
        assertEquals("one", value);
    }

    @Test
    void registerFolderConfig_WhenSameTypeAndNameRegisteredTwice_ThrowsConfigExceptionAndKeepsOriginalEntry() {
        FolderConfig annotation = DuplicateKeyFolderItem.class.getAnnotation(FolderConfig.class);
        assertNotNull(annotation);

        configManager.registerFolderConfig(DuplicateKeyFolderItem.class, annotation);

        Object originalEntry = configManager.getFolderConfigEntry(DuplicateKeyFolderItem.class, "shared_key");
        assertNotNull(originalEntry);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.registerFolderConfig(DuplicateKeyFolderItem.class, annotation)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("shared_key"));
        assertTrue(message.contains(DuplicateKeyFolderItem.class.getName()));

        Object currentEntry = configManager.getFolderConfigEntry(DuplicateKeyFolderItem.class, "shared_key");
        assertSame(originalEntry, currentEntry);
        assertEquals(1, configManager.getFolderConfigNames(DuplicateKeyFolderItem.class).size());
    }

    @Test
    void registerFolderConfig_WhenDifferentTypesShareSameName_ThrowsConfigExceptionAndKeepsOriginalEntry() {
        FolderConfig firstAnnotation = SharedNameFolderItemOne.class.getAnnotation(FolderConfig.class);
        FolderConfig secondAnnotation = SharedNameFolderItemTwo.class.getAnnotation(FolderConfig.class);
        assertNotNull(firstAnnotation);
        assertNotNull(secondAnnotation);

        configManager.registerFolderConfig(SharedNameFolderItemOne.class, firstAnnotation);

        var originalByName = configManager.getFolderConfigEntryByName("shared_name");
        assertNotNull(originalByName);
        assertEquals(SharedNameFolderItemOne.class, originalByName.getItemType());

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> configManager.registerFolderConfig(SharedNameFolderItemTwo.class, secondAnnotation)
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("shared_name"));
        assertTrue(message.contains(SharedNameFolderItemOne.class.getName()));
        assertTrue(message.contains(SharedNameFolderItemTwo.class.getName()));

        var currentByName = configManager.getFolderConfigEntryByName("shared_name");
        assertNotNull(currentByName);
        assertEquals(SharedNameFolderItemOne.class, currentByName.getItemType());
        assertNull(configManager.getFolderConfigEntry(SharedNameFolderItemTwo.class, "shared_name"));
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

    @Config(value = "collision-one.yml", name = "collision", generateDefaults = false)
    public static class CollisionConfigOne {
        private String value;

        public CollisionConfigOne() {
        }
    }

    @Config(value = "collision-two.yml", name = "collision", generateDefaults = false)
    public static class CollisionConfigTwo {
        private String value;

        public CollisionConfigTwo() {
        }
    }

    @FolderConfig(name = "shared_key", folder = "shared-key-folder")
    public static class DuplicateKeyFolderItem {
        private String value;

        public DuplicateKeyFolderItem() {
        }
    }

    @FolderConfig(name = "shared_name", folder = "shared-name-folder-one")
    public static class SharedNameFolderItemOne {
        private String value;

        public SharedNameFolderItemOne() {
        }
    }

    @FolderConfig(name = "shared_name", folder = "shared-name-folder-two")
    public static class SharedNameFolderItemTwo {
        private String value;

        public SharedNameFolderItemTwo() {
        }
    }
}
