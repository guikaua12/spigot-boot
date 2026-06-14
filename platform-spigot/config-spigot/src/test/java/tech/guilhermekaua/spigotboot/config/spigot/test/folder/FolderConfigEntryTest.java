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
package tech.guilhermekaua.spigotboot.config.spigot.test.folder;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
import tech.guilhermekaua.spigotboot.config.annotation.NodeKey;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.folder.EditResult;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigChangeListener;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.folder.ItemChangeType;
import tech.guilhermekaua.spigotboot.config.spigot.folder.FolderConfigEntry;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FolderConfigEntryTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private Logger logger;
    private YamlConfigLoader loader;
    private Binder binder;
    private Path itemsFolder;

    // ========== Test Fixtures ==========

    public static class TestItem {
        private String name;
        private int priority;

        public TestItem() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    public static class TestItemWithNodeKey {
        @NodeKey
        private String id;
        private String name;

        public TestItemWithNodeKey() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TestItemWithIdField {
        private String itemId;
        private String name;

        public TestItemWithIdField() {
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TestItemWithEnabled {
        private String name;
        private boolean enabled;
        private int priority;

        public TestItemWithEnabled() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private FolderConfig createAnnotation(
            String name,
            String folder,
            String idField,
            String orderBy,
            String enabledField,
            String resource,
            String excludePrefix) {
        Map<String, Object> values = Map.of(
                "name", name,
                "folder", folder,
                "idField", idField,
                "orderBy", orderBy,
                "enabledField", enabledField,
                "resource", resource,
                "excludePrefix", excludePrefix
        );

        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("annotationType".equals(methodName)) {
                return FolderConfig.class;
            }
            if ("toString".equals(methodName)) {
                return "@FolderConfig(folder=" + folder + ")";
            }
            if ("hashCode".equals(methodName)) {
                return values.hashCode();
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            return values.get(methodName);
        };

        return (FolderConfig) Proxy.newProxyInstance(
                FolderConfig.class.getClassLoader(),
                new Class<?>[]{FolderConfig.class},
                handler
        );
    }

    private FolderConfig createDefaultAnnotation() {
        return createAnnotation("", "items", "", "filename", "", "", "_");
    }

    private Path createTestFile(String name, String content) throws IOException {
        Files.createDirectories(itemsFolder);
        Path file = itemsFolder.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger(FolderConfigEntryTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        itemsFolder = tempDir.resolve("items");
        loader = new YamlConfigLoader();
        binder = Binder.create();
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_WithValidParameters_CreatesEntry() {
        FolderConfig annotation = createDefaultAnnotation();

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertNotNull(entry);
        assertEquals(TestItem.class, entry.getItemType());
        assertEquals("items", entry.getFolderConfigName());
        assertEquals(itemsFolder, entry.getFolder());
    }

    @Test
    void constructor_WithNullItemType_ThrowsNPE() {
        FolderConfig annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new FolderConfigEntry<>(null, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE));

        assertTrue(exception.getMessage().contains("itemType"));
    }

    @Test
    void constructor_WithNullAnnotation_ThrowsNPE() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new FolderConfigEntry<>(TestItem.class, null, plugin, loader, binder, NamingStrategy.SNAKE_CASE));

        assertTrue(exception.getMessage().contains("annotation"));
    }

    @Test
    void constructor_WithNullPlugin_ThrowsNPE() {
        FolderConfig annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new FolderConfigEntry<>(TestItem.class, annotation, null, loader, binder, NamingStrategy.SNAKE_CASE));

        assertTrue(exception.getMessage().contains("plugin"));
    }

    @Test
    void constructor_WithNullLoader_ThrowsNPE() {
        FolderConfig annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new FolderConfigEntry<>(TestItem.class, annotation, plugin, null, binder, NamingStrategy.SNAKE_CASE));

        assertTrue(exception.getMessage().contains("loader"));
    }

    @Test
    void constructor_WithNullBinder_ThrowsNPE() {
        FolderConfig annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new FolderConfigEntry<>(TestItem.class, annotation, plugin, loader, null, NamingStrategy.SNAKE_CASE));

        assertTrue(exception.getMessage().contains("binder"));
    }

    @Test
    void constructor_WithEmptyName_DerivesNameFromFolder() {
        FolderConfig annotation = createAnnotation("", "boosters", "", "filename", "", "", "_");

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertEquals("boosters", entry.getFolderConfigName());
    }

    @Test
    void constructor_WithExplicitName_UsesProvidedName() {
        FolderConfig annotation = createAnnotation("my-collection", "items", "", "filename", "", "", "_");

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertEquals("my-collection", entry.getFolderConfigName());
    }

    @Test
    void constructor_WithNestedFolder_DerivesNameFromLastSegment() {
        FolderConfig annotation = createAnnotation("", "configs/items/boosters", "", "filename", "", "", "_");

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertEquals("boosters", entry.getFolderConfigName());
    }

    @Test
    void constructor_WithTrailingSlash_IgnoresSlash() {
        FolderConfig annotation = createAnnotation("", "items/", "", "filename", "", "", "_");

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertEquals("items", entry.getFolderConfigName());
    }

    @Test
    void constructor_WithBackslashPath_NormalizesToForwardSlash() {
        FolderConfig annotation = createAnnotation("", "configs\\items", "", "filename", "", "", "_");

        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        assertEquals("items", entry.getFolderConfigName());
    }

    // ========== Initialize Tests ==========

    @Test
    void initialize_FolderDoesNotExist_CreatesFolderAndLoadsEmpty() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        entry.initialize();

        assertTrue(Files.exists(itemsFolder));
        assertTrue(entry.getRef().get().isEmpty());
    }

    @Test
    void initialize_FolderExists_LoadsItemsWithoutRecreating() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Test\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        entry.initialize();

        assertTrue(Files.exists(itemsFolder));
        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void initialize_EmptyFolder_ReturnsEmptySnapshot() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        entry.initialize();

        assertTrue(entry.getRef().get().isEmpty());
        assertEquals(0, entry.getRef().get().size());
    }

    // ========== LoadAll Tests ==========

    @Test
    void loadAll_WithYmlFiles_LoadsAllItems() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("item2.yml", "name: Item2\npriority: 2");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertTrue(entry.getRef().get().contains("item1"));
        assertTrue(entry.getRef().get().contains("item2"));
    }

    @Test
    void loadAll_WithYamlFiles_LoadsAllItems() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yaml", "name: Item1\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
        assertTrue(entry.getRef().get().contains("item1"));
    }

    @Test
    void loadAll_IgnoresNonYamlFiles() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("readme.txt", "This is not a config");
        createTestFile("data.json", "{}");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void loadAll_WithExcludePrefix_SkipsMatchingFiles() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("_template.yml", "name: Template\npriority: 0");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
        assertTrue(entry.getRef().get().contains("item1"));
        assertFalse(entry.getRef().get().contains("_template"));
    }

    @Test
    void loadAll_WithEmptyExcludePrefix_LoadsAll() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("_template.yml", "name: Template\npriority: 0");

        FolderConfig annotation = createAnnotation("", "items", "", "filename", "", "", "");
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
    }

    @Test
    void loadAll_SkipsDirectories() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        Files.createDirectory(itemsFolder.resolve("subdir"));

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void loadAll_FolderDoesNotExist_SetsEmptySnapshot() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        entry.loadAll();

        assertTrue(entry.getRef().get().isEmpty());
    }

    @Test
    void loadAll_OrdersByFilename_DefaultBehavior() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("c-item.yml", "name: C\npriority: 3");
        createTestFile("a-item.yml", "name: A\npriority: 1");
        createTestFile("b-item.yml", "name: B\npriority: 2");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<TestItem> values = new ArrayList<>(entry.getRef().get().values());
        assertEquals(3, values.size());
        assertEquals("A", values.get(0).getName());
        assertEquals("B", values.get(1).getName());
        assertEquals("C", values.get(2).getName());
    }

    @Test
    void loadAll_OrdersByField_WhenOrderBySpecified() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 3");
        createTestFile("item2.yml", "name: Item2\npriority: 1");
        createTestFile("item3.yml", "name: Item3\npriority: 2");

        FolderConfig annotation = createAnnotation("", "items", "", "priority", "", "", "_");
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<TestItem> values = new ArrayList<>(entry.getRef().get().values());
        assertEquals(3, values.size());
        assertEquals(1, values.get(0).getPriority());
        assertEquals(2, values.get(1).getPriority());
        assertEquals(3, values.get(2).getPriority());
    }

    // ========== ID Injection Tests ==========

    @Test
    void loadItem_WithNodeKeyField_InjectsId() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("my-item.yml", "name: Test");

        FolderConfig annotation = createAnnotation("", "items", "", "filename", "", "", "_");
        FolderConfigEntry<TestItemWithNodeKey> entry = new FolderConfigEntry<>(
                TestItemWithNodeKey.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItemWithNodeKey loaded = entry.getRef().get().find("my-item");
        assertNotNull(loaded);
        assertEquals("my-item", loaded.getId());
    }

    @Test
    void loadItem_WithExplicitIdField_InjectsId() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("my-item.yml", "name: Test");

        FolderConfig annotation = createAnnotation("", "items", "itemId", "filename", "", "", "_");
        FolderConfigEntry<TestItemWithIdField> entry = new FolderConfigEntry<>(
                TestItemWithIdField.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItemWithIdField loaded = entry.getRef().get().find("my-item");
        assertNotNull(loaded);
        assertEquals("my-item", loaded.getItemId());
    }

    // ========== Enabled Filtering Tests ==========

    @Test
    void loadAll_WithEnabledField_FiltersDisabledItems() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("enabled-item.yml", "name: Enabled\nenabled: true\npriority: 1");
        createTestFile("disabled-item.yml", "name: Disabled\nenabled: false\npriority: 2");

        FolderConfig annotation = createAnnotation("", "items", "", "filename", "enabled", "", "_");
        FolderConfigEntry<TestItemWithEnabled> entry = new FolderConfigEntry<>(
                TestItemWithEnabled.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertEquals(1, entry.getRef().get().enabled().size());
    }

    @Test
    void loadAll_WithEmptyEnabledField_ReturnsAllAsEnabled() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\nenabled: true\npriority: 1");
        createTestFile("item2.yml", "name: Item2\nenabled: false\npriority: 2");

        FolderConfig annotation = createAnnotation("", "items", "", "filename", "", "", "_");
        FolderConfigEntry<TestItemWithEnabled> entry = new FolderConfigEntry<>(
                TestItemWithEnabled.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertEquals(2, entry.getRef().get().enabled().size());
    }

    // ========== Reload Tests ==========

    @Test
    void reloadAll_NewFileAdded_NotifiesAddedChange() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        createTestFile("item2.yml", "name: Item2\npriority: 2");
        entry.reloadAll();

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.ADDED, changes.get(0).getType());
        assertEquals("item2", changes.get(0).getId());
    }

    @Test
    void reloadAll_FileRemoved_NotifiesRemovedChange() throws IOException {
        Files.createDirectories(itemsFolder);
        Path item1Path = createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("item2.yml", "name: Item2\npriority: 2");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        Files.delete(item1Path);
        entry.reloadAll();

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.REMOVED, changes.get(0).getType());
        assertEquals("item1", changes.get(0).getId());
    }

    @Test
    void reloadItem_ItemDoesNotExist_AddsItem() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        createTestFile("new-item.yml", "name: NewItem\npriority: 1");
        entry.reloadItem("new-item");

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.ADDED, changes.get(0).getType());
        assertEquals("new-item", changes.get(0).getId());
    }

    @Test
    void reloadItem_ItemRemoved_RemovesAndNotifies() throws IOException {
        Files.createDirectories(itemsFolder);
        Path itemPath = createTestFile("item1.yml", "name: Item1\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        Files.delete(itemPath);
        entry.reloadItem("item1");

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.REMOVED, changes.get(0).getType());
        assertEquals("item1", changes.get(0).getId());
        assertNull(entry.getRef().get().find("item1"));
    }

    // ========== SaveItem Tests ==========

    @Test
    void saveItem_ValidId_SavesAndReturnsSuccess() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        EditResult<TestItem> result = entry.saveItem("new-item", item);

        assertTrue(result.isSuccess());
        assertNotNull(result.getValue());
        assertTrue(Files.exists(itemsFolder.resolve("new-item.yml")));
    }

    @Test
    void saveItem_NewItem_NotifiesAdded() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        entry.saveItem("new-item", item);

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.ADDED, changes.get(0).getType());
    }

    @Test
    void saveItem_NullId_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem(null, item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_EmptyId_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithSlash_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo/bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithBackslash_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo\\bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithDoubleDot_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo..bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithSpecialChars_ReturnsFailure() {
        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo@bar!", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_ValidIdWithDot_Succeeds() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        EditResult<TestItem> result = entry.saveItem("config.v1", item);

        assertTrue(result.isSuccess());
    }

    @Test
    void saveItem_ValidIdWithUnderscore_Succeeds() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        EditResult<TestItem> result = entry.saveItem("my_config", item);

        assertTrue(result.isSuccess());
    }

    @Test
    void saveItem_ValidIdWithHyphen_Succeeds() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        EditResult<TestItem> result = entry.saveItem("my-config", item);

        assertTrue(result.isSuccess());
    }

    // ========== DeleteItem Tests ==========

    @Test
    void deleteItem_ExistingItem_DeletesAndReturnsSuccess() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        EditResult<Void> result = entry.deleteItem("item1");

        assertTrue(result.isSuccess());
        assertNull(entry.getRef().get().find("item1"));
    }

    @Test
    void deleteItem_ExistingItem_NotifiesRemoved() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        entry.deleteItem("item1");

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.REMOVED, changes.get(0).getType());
        assertEquals("item1", changes.get(0).getId());
    }

    @Test
    void deleteItem_NonExistentItem_ReturnsFailure() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        EditResult<Void> result = entry.deleteItem("nonexistent");

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Item not found"));
    }

    // ========== CopyItem Tests ==========

    @Test
    void copyItem_ValidItem_ReturnsDeepCopy() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        TestItem original = new TestItem();
        original.setName("Original");
        original.setPriority(10);

        TestItem result = entry.copyItem(original);

        assertNotNull(result);
        assertNotSame(original, result);
        assertEquals(original.getName(), result.getName());
        assertEquals(original.getPriority(), result.getPriority());
    }

    // ========== Path Resolution Tests ==========

    @Test
    void resolveItemPath_YamlExists_PrefersYaml() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item.yml", "name: Yml\npriority: 1");
        createTestFile("item.yaml", "name: Yaml\npriority: 2");

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        assertTrue(entry.getRef().get().contains("item"));
    }

    // ========== Listener Tests ==========

    @Test
    void addListener_ReceivesNotifications() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> receivedChanges = new ArrayList<>();
        FolderConfigChangeListener<TestItem> listener = receivedChanges::add;
        entry.getRef().addListener(listener);

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        entry.saveItem("new-item", item);

        assertEquals(1, receivedChanges.size());
    }

    @Test
    void removeListener_StopsReceivingNotifications() throws IOException {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);
        entry.loadAll();

        List<FolderConfigItemChange<TestItem>> receivedChanges = new ArrayList<>();
        FolderConfigChangeListener<TestItem> listener = receivedChanges::add;
        entry.getRef().addListener(listener);
        entry.getRef().removeListener(listener);

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        entry.saveItem("new-item", item);

        assertTrue(receivedChanges.isEmpty());
    }

    // ========== Resource-Copy Containment Guard Tests ==========

    private void invokeCopyResourceFile(FolderConfigEntry<?> entry, String resourcePath, String fileName)
            throws Exception {
        Method method = FolderConfigEntry.class
                .getDeclaredMethod("copyResourceFile", String.class, String.class);
        method.setAccessible(true);
        method.invoke(entry, resourcePath, fileName);
    }

    @Test
    void copyResourceFile_BenignFileName_WritesInsideFolder() throws Exception {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        lenient().when(plugin.getResource("items/default.yml")).thenReturn(
                new ByteArrayInputStream("name: Default\npriority: 1".getBytes(StandardCharsets.UTF_8)));

        invokeCopyResourceFile(entry, "items/default.yml", "default.yml");

        assertTrue(Files.exists(itemsFolder.resolve("default.yml")));
    }

    @Test
    void copyResourceFile_FileNameEscapesFolder_SkipsAndDoesNotWriteOutside() throws Exception {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        lenient().when(plugin.getResource("items/evil.yml")).thenReturn(
                new ByteArrayInputStream("name: Evil".getBytes(StandardCharsets.UTF_8)));

        // resolves to tempDir/escape.yml, i.e. outside the items folder
        invokeCopyResourceFile(entry, "items/evil.yml", "../escape.yml");

        assertFalse(Files.exists(tempDir.resolve("escape.yml")),
                "guard must not write resources outside the target folder");
        try (var entries = Files.list(itemsFolder)) {
            assertTrue(entries.findFirst().isEmpty(), "skipped resource must not write anything");
        }
    }

    @Test
    void copyResourceFile_DeepEscapeFileName_SkipsAndDoesNotWriteOutside() throws Exception {
        Files.createDirectories(itemsFolder);

        FolderConfig annotation = createDefaultAnnotation();
        FolderConfigEntry<TestItem> entry = new FolderConfigEntry<>(
                TestItem.class, annotation, plugin, loader, binder, NamingStrategy.SNAKE_CASE);

        lenient().when(plugin.getResource("items/evil.yml")).thenReturn(
                new ByteArrayInputStream("name: Evil".getBytes(StandardCharsets.UTF_8)));

        String uniqueName = "deep-escape-" + UUID.randomUUID() + ".yml";
        Path outside = tempDir.getParent().resolve(uniqueName);
        Files.deleteIfExists(outside);

        invokeCopyResourceFile(entry, "items/evil.yml", "../../" + uniqueName);

        assertFalse(Files.exists(outside),
                "guard must not write resources outside the target folder via '..' segments");
    }
}
