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
package tech.guilhermekaua.spigotboot.config.spigot.test.collection;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigCollection;
import tech.guilhermekaua.spigotboot.config.annotation.NodeKey;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.collection.CollectionChangeListener;
import tech.guilhermekaua.spigotboot.config.collection.CollectionItemChange;
import tech.guilhermekaua.spigotboot.config.collection.EditResult;
import tech.guilhermekaua.spigotboot.config.collection.ItemChangeType;
import tech.guilhermekaua.spigotboot.config.spigot.collection.CollectionEntry;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CollectionEntryTest {

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
    private ConfigCollection createAnnotation(
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
                return ConfigCollection.class;
            }
            if ("toString".equals(methodName)) {
                return "@ConfigCollection(folder=" + folder + ")";
            }
            if ("hashCode".equals(methodName)) {
                return values.hashCode();
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            return values.get(methodName);
        };

        return (ConfigCollection) Proxy.newProxyInstance(
                ConfigCollection.class.getClassLoader(),
                new Class<?>[]{ConfigCollection.class},
                handler
        );
    }

    private ConfigCollection createDefaultAnnotation() {
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
        logger = Logger.getLogger(CollectionEntryTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        itemsFolder = tempDir.resolve("items");
        loader = new YamlConfigLoader();
        binder = Binder.create();
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_WithValidParameters_CreatesEntry() {
        ConfigCollection annotation = createDefaultAnnotation();

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertNotNull(entry);
        assertEquals(TestItem.class, entry.getItemType());
        assertEquals("items", entry.getCollectionName());
        assertEquals(itemsFolder, entry.getFolder());
    }

    @Test
    void constructor_WithNullItemType_ThrowsNPE() {
        ConfigCollection annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new CollectionEntry<>(null, annotation, plugin, loader, binder));

        assertTrue(exception.getMessage().contains("itemType"));
    }

    @Test
    void constructor_WithNullAnnotation_ThrowsNPE() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new CollectionEntry<>(TestItem.class, null, plugin, loader, binder));

        assertTrue(exception.getMessage().contains("annotation"));
    }

    @Test
    void constructor_WithNullPlugin_ThrowsNPE() {
        ConfigCollection annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new CollectionEntry<>(TestItem.class, annotation, null, loader, binder));

        assertTrue(exception.getMessage().contains("plugin"));
    }

    @Test
    void constructor_WithNullLoader_ThrowsNPE() {
        ConfigCollection annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new CollectionEntry<>(TestItem.class, annotation, plugin, null, binder));

        assertTrue(exception.getMessage().contains("loader"));
    }

    @Test
    void constructor_WithNullBinder_ThrowsNPE() {
        ConfigCollection annotation = createDefaultAnnotation();

        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                new CollectionEntry<>(TestItem.class, annotation, plugin, loader, null));

        assertTrue(exception.getMessage().contains("binder"));
    }

    @Test
    void constructor_WithEmptyName_DerivesNameFromFolder() {
        ConfigCollection annotation = createAnnotation("", "boosters", "", "filename", "", "", "_");

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertEquals("boosters", entry.getCollectionName());
    }

    @Test
    void constructor_WithExplicitName_UsesProvidedName() {
        ConfigCollection annotation = createAnnotation("my-collection", "items", "", "filename", "", "", "_");

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertEquals("my-collection", entry.getCollectionName());
    }

    @Test
    void constructor_WithNestedFolder_DerivesNameFromLastSegment() {
        ConfigCollection annotation = createAnnotation("", "configs/items/boosters", "", "filename", "", "", "_");

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertEquals("boosters", entry.getCollectionName());
    }

    @Test
    void constructor_WithTrailingSlash_IgnoresSlash() {
        ConfigCollection annotation = createAnnotation("", "items/", "", "filename", "", "", "_");

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertEquals("items", entry.getCollectionName());
    }

    @Test
    void constructor_WithBackslashPath_NormalizesToForwardSlash() {
        ConfigCollection annotation = createAnnotation("", "configs\\items", "", "filename", "", "", "_");

        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        assertEquals("items", entry.getCollectionName());
    }

    // ========== Initialize Tests ==========

    @Test
    void initialize_FolderDoesNotExist_CreatesFolderAndLoadsEmpty() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        entry.initialize();

        assertTrue(Files.exists(itemsFolder));
        assertTrue(entry.getRef().get().isEmpty());
    }

    @Test
    void initialize_FolderExists_LoadsItemsWithoutRecreating() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Test\npriority: 1");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        entry.initialize();

        assertTrue(Files.exists(itemsFolder));
        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void initialize_EmptyFolder_ReturnsEmptySnapshot() throws IOException {
        Files.createDirectories(itemsFolder);

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertTrue(entry.getRef().get().contains("item1"));
        assertTrue(entry.getRef().get().contains("item2"));
    }

    @Test
    void loadAll_WithYamlFiles_LoadsAllItems() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yaml", "name: Item1\npriority: 1");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void loadAll_WithExcludePrefix_SkipsMatchingFiles() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        createTestFile("_template.yml", "name: Template\npriority: 0");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createAnnotation("", "items", "", "filename", "", "", "");
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
    }

    @Test
    void loadAll_SkipsDirectories() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");
        Files.createDirectory(itemsFolder.resolve("subdir"));

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(1, entry.getRef().get().size());
    }

    @Test
    void loadAll_FolderDoesNotExist_SetsEmptySnapshot() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        entry.loadAll();

        assertTrue(entry.getRef().get().isEmpty());
    }

    @Test
    void loadAll_OrdersByFilename_DefaultBehavior() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("c-item.yml", "name: C\npriority: 3");
        createTestFile("a-item.yml", "name: A\npriority: 1");
        createTestFile("b-item.yml", "name: B\npriority: 2");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createAnnotation("", "items", "", "priority", "", "", "_");
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createAnnotation("", "items", "", "filename", "", "", "_");
        CollectionEntry<TestItemWithNodeKey> entry = new CollectionEntry<>(
                TestItemWithNodeKey.class, annotation, plugin, loader, binder);
        entry.loadAll();

        TestItemWithNodeKey loaded = entry.getRef().get().find("my-item");
        assertNotNull(loaded);
        assertEquals("my-item", loaded.getId());
    }

    @Test
    void loadItem_WithExplicitIdField_InjectsId() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("my-item.yml", "name: Test");

        ConfigCollection annotation = createAnnotation("", "items", "itemId", "filename", "", "", "_");
        CollectionEntry<TestItemWithIdField> entry = new CollectionEntry<>(
                TestItemWithIdField.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createAnnotation("", "items", "", "filename", "enabled", "", "_");
        CollectionEntry<TestItemWithEnabled> entry = new CollectionEntry<>(
                TestItemWithEnabled.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertEquals(1, entry.getRef().get().enabled().size());
    }

    @Test
    void loadAll_WithEmptyEnabledField_ReturnsAllAsEnabled() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\nenabled: true\npriority: 1");
        createTestFile("item2.yml", "name: Item2\nenabled: false\npriority: 2");

        ConfigCollection annotation = createAnnotation("", "items", "", "filename", "", "", "_");
        CollectionEntry<TestItemWithEnabled> entry = new CollectionEntry<>(
                TestItemWithEnabled.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertEquals(2, entry.getRef().get().size());
        assertEquals(2, entry.getRef().get().enabled().size());
    }

    // ========== Reload Tests ==========

    @Test
    void reloadAll_NewFileAdded_NotifiesAddedChange() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
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
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem(null, item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_EmptyId_ReturnsFailure() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithSlash_ReturnsFailure() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo/bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithBackslash_ReturnsFailure() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo\\bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithDoubleDot_ReturnsFailure() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo..bar", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_IdWithSpecialChars_ReturnsFailure() {
        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

        TestItem item = new TestItem();

        EditResult<TestItem> result = entry.saveItem("foo@bar!", item);

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Invalid item ID"));
    }

    @Test
    void saveItem_ValidIdWithDot_Succeeds() throws IOException {
        Files.createDirectories(itemsFolder);

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        EditResult<Void> result = entry.deleteItem("item1");

        assertTrue(result.isSuccess());
        assertNull(entry.getRef().get().find("item1"));
    }

    @Test
    void deleteItem_ExistingItem_NotifiesRemoved() throws IOException {
        Files.createDirectories(itemsFolder);
        createTestFile("item1.yml", "name: Item1\npriority: 1");

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> changes = new ArrayList<>();
        entry.getRef().addListener(changes::add);

        entry.deleteItem("item1");

        assertEquals(1, changes.size());
        assertEquals(ItemChangeType.REMOVED, changes.get(0).getType());
        assertEquals("item1", changes.get(0).getId());
    }

    @Test
    void deleteItem_NonExistentItem_ReturnsFailure() throws IOException {
        Files.createDirectories(itemsFolder);

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        EditResult<Void> result = entry.deleteItem("nonexistent");

        assertTrue(result.isFailure());
        assertTrue(result.getErrorMessage().contains("Item not found"));
    }

    // ========== CopyItem Tests ==========

    @Test
    void copyItem_ValidItem_ReturnsDeepCopy() throws IOException {
        Files.createDirectories(itemsFolder);

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);

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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        assertTrue(entry.getRef().get().contains("item"));
    }

    // ========== Listener Tests ==========

    @Test
    void addListener_ReceivesNotifications() throws IOException {
        Files.createDirectories(itemsFolder);

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> receivedChanges = new ArrayList<>();
        CollectionChangeListener<TestItem> listener = receivedChanges::add;
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

        ConfigCollection annotation = createDefaultAnnotation();
        CollectionEntry<TestItem> entry = new CollectionEntry<>(
                TestItem.class, annotation, plugin, loader, binder);
        entry.loadAll();

        List<CollectionItemChange<TestItem>> receivedChanges = new ArrayList<>();
        CollectionChangeListener<TestItem> listener = receivedChanges::add;
        entry.getRef().addListener(listener);
        entry.getRef().removeListener(listener);

        TestItem item = new TestItem();
        item.setName("Test");
        item.setPriority(5);

        entry.saveItem("new-item", item);

        assertTrue(receivedChanges.isEmpty());
    }
}
