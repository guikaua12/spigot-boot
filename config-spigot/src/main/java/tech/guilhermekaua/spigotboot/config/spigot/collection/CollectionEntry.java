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
package tech.guilhermekaua.spigotboot.config.spigot.collection;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigCollection;
import tech.guilhermekaua.spigotboot.config.annotation.NodeKey;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.binding.BindingResult;
import tech.guilhermekaua.spigotboot.config.collection.CollectionItemChange;
import tech.guilhermekaua.spigotboot.config.collection.ConfigNodeHash;
import tech.guilhermekaua.spigotboot.config.collection.EditResult;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Internal entry holding collection state for SpigotConfigManager.
 * <p>
 * Each CollectionEntry manages one folder-based collection.
 *
 * @param <T> the item type
 */
public final class CollectionEntry<T> {
    private static final String[] EXTENSIONS = {"yml", "yaml"};
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private final Class<T> itemType;
    private final String collectionName;
    private final Path folder;
    private final ConfigCollection annotation;
    private final YamlConfigLoader loader;
    private final Binder binder;
    private final Plugin plugin;
    private final Logger logger;

    private final DefaultConfigCollectionRef<T> ref;
    private final DefaultConfigCollectionEditor<T> editor;

    private final Map<String, ItemMeta> itemMetadata = new ConcurrentHashMap<>();

    /**
     * Creates a new collection entry.
     *
     * @param itemType   the item type class
     * @param annotation the @{@link ConfigCollection} annotation
     * @param plugin     the owning plugin
     * @param loader     the YAML loader
     * @param binder     the config binder
     */
    public CollectionEntry(
            @NotNull Class<T> itemType,
            @NotNull ConfigCollection annotation,
            @NotNull Plugin plugin,
            @NotNull YamlConfigLoader loader,
            @NotNull Binder binder) {
        this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
        this.annotation = Objects.requireNonNull(annotation, "annotation cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.loader = Objects.requireNonNull(loader, "loader cannot be null");
        this.binder = Objects.requireNonNull(binder, "binder cannot be null");
        this.logger = plugin.getLogger();

        String name = annotation.name();
        if (name.isEmpty()) {
            name = deriveNameFromFolder(annotation.folder());
        }
        this.collectionName = name;

        this.folder = plugin.getDataFolder().toPath().resolve(annotation.folder());

        this.editor = new DefaultConfigCollectionEditor<>(this);
        this.ref = new DefaultConfigCollectionRef<>(
                itemType,
                collectionName,
                editor,
                this::reloadAll,
                this::reloadItem
        );
    }

    private String deriveNameFromFolder(@NotNull String folderPath) {
        String normalized = folderPath.replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    public @NotNull Class<T> getItemType() {
        return itemType;
    }

    public @NotNull String getCollectionName() {
        return collectionName;
    }

    public @NotNull Path getFolder() {
        return folder;
    }

    public @NotNull DefaultConfigCollectionRef<T> getRef() {
        return ref;
    }

    public void initialize() {
        ensureFolderExists();
        copyDefaultsFromResources();
        loadAll();
    }

    private void ensureFolderExists() {
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                logger.info("Created collection folder: " + folder);
            }
        } catch (IOException e) {
            logger.warning("Failed to create collection folder: " + folder + " - " + e.getMessage());
        }
    }

    /**
     * Copies default files from resources if the folder is empty.
     */
    private void copyDefaultsFromResources() {
        String resourcePath = annotation.resource();
        if (resourcePath.isEmpty()) {
            return;
        }

        try {
            try (Stream<Path> files = Files.list(folder)) {
                if (files.findFirst().isPresent()) {
                    return;
                }
            }

            if (!resourcePath.endsWith("/")) {
                resourcePath += "/";
            }

            InputStream indexStream = plugin.getResource(resourcePath + "index.txt");
            if (indexStream != null) {
                try (Scanner scanner = new Scanner(indexStream)) {
                    while (scanner.hasNextLine()) {
                        String fileName = scanner.nextLine().trim();
                        if (!fileName.isEmpty()) {
                            copyResourceFile(resourcePath + fileName, fileName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to copy defaults from resources: " + e.getMessage());
        }
    }

    private void copyResourceFile(String resourcePath, String fileName) {
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is != null) {
                Path targetPath = folder.resolve(fileName);
                Files.copy(is, targetPath);
                logger.info("Copied default: " + fileName);
            }
        } catch (IOException e) {
            logger.warning("Failed to copy resource " + fileName + ": " + e.getMessage());
        }
    }

    public void loadAll() {
        Map<String, T> itemsById = new LinkedHashMap<>();
        List<T> orderedValues = new ArrayList<>();
        Map<String, ItemMeta> newMetadata = new LinkedHashMap<>();

        if (!Files.exists(folder)) {
            ref.setSnapshot(DefaultConfigCollectionSnapshot.empty(itemType, collectionName));
            return;
        }

        String excludePrefix = annotation.excludePrefix();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            List<Path> files = new ArrayList<>();
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                String fileName = path.getFileName().toString();

                if (!excludePrefix.isEmpty() && fileName.startsWith(excludePrefix)) {
                    continue;
                }

                if (!matchesPattern(fileName)) {
                    continue;
                }

                files.add(path);
            }

            files.sort(Comparator.comparing(p -> p.getFileName().toString()));

            for (Path path : files) {
                String id = getIdFromPath(path);
                T item = loadItem(path, id);
                if (item != null) {
                    itemsById.put(id, item);
                    orderedValues.add(item);
                    newMetadata.put(id, createItemMeta(path));
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to scan collection folder " + folder + ": " + e.getMessage());
        }

        String orderBy = annotation.orderBy();
        if (!orderBy.isEmpty() && !"filename".equals(orderBy)) {
            orderByField(orderedValues, orderBy);
        }

        List<T> enabledItems = computeEnabledItems(orderedValues);

        itemMetadata.clear();
        itemMetadata.putAll(newMetadata);

        ref.setSnapshot(new DefaultConfigCollectionSnapshot<>(
                itemType, collectionName, itemsById, orderedValues, enabledItems
        ));

        logger.info("Loaded " + itemsById.size() + " items in collection '" + collectionName + "'");
    }

    /**
     * Reloads all items and computes changes.
     */
    public void reloadAll() {
        DefaultConfigCollectionSnapshot<T> oldSnapshot =
                (DefaultConfigCollectionSnapshot<T>) ref.get();
        Map<String, T> oldItems = oldSnapshot.getItemsMap();
        Map<String, ItemMeta> oldMetadata = new HashMap<>(itemMetadata);

        loadAll();

        DefaultConfigCollectionSnapshot<T> newSnapshot =
                (DefaultConfigCollectionSnapshot<T>) ref.get();
        Map<String, T> newItems = newSnapshot.getItemsMap();

        List<CollectionItemChange<T>> changes = computeChanges(oldItems, newItems, oldMetadata);
        for (CollectionItemChange<T> change : changes) {
            ref.notifyListeners(change);
        }
    }

    public void reloadItem(@NotNull String id) {
        Path itemPath = resolveItemPath(id);
        DefaultConfigCollectionSnapshot<T> oldSnapshot =
                (DefaultConfigCollectionSnapshot<T>) ref.get();
        Map<String, T> oldItems = new LinkedHashMap<>(oldSnapshot.getItemsMap());
        T oldItem = oldItems.get(id);

        if (!Files.exists(itemPath)) {
            if (oldItem != null) {
                oldItems.remove(id);
                itemMetadata.remove(id);
                rebuildSnapshot(oldItems);
                ref.notifyListeners(CollectionItemChange.removed(
                        collectionName, itemType, id, oldItem
                ));
            }
            return;
        }

        T newItem = loadItem(itemPath, id);
        if (newItem == null) {
            return;
        }

        if (oldItem == null) {
            oldItems.put(id, newItem);
            itemMetadata.put(id, createItemMeta(itemPath));
            rebuildSnapshot(oldItems);
            ref.notifyListeners(CollectionItemChange.added(
                    collectionName, itemType, id, newItem
            ));
        } else {
            ItemMeta oldMeta = itemMetadata.get(id);
            ItemMeta newMeta = createItemMeta(itemPath);

            if (oldMeta == null || !oldMeta.hash.equals(newMeta.hash)) {
                oldItems.put(id, newItem);
                itemMetadata.put(id, newMeta);
                rebuildSnapshot(oldItems);
                ref.notifyListeners(CollectionItemChange.modified(
                        collectionName, itemType, id, oldItem, newItem
                ));
            }
        }
    }

    /**
     * Saves an item to disk.
     *
     * @param id    the item ID
     * @param value the item value
     * @return the edit result
     */
    public @NotNull EditResult<T> saveItem(@NotNull String id, @NotNull T value) {
        if (!isValidId(id)) {
            return EditResult.failure("Invalid item ID: '" + id + "'. " +
                    "ID must contain only letters, numbers, dots, underscores, and hyphens.");
        }

        Path itemPath = resolveItemPath(id);
        try {
            MutableConfigNode node = loader.createNode();
            binder.unbind(value, node);
            loader.save(node, ConfigSource.file(itemPath));

            T loadedItem = loadItem(itemPath, id);
            if (loadedItem == null) {
                return EditResult.failure("Failed to reload saved item");
            }

            DefaultConfigCollectionSnapshot<T> oldSnapshot =
                    (DefaultConfigCollectionSnapshot<T>) ref.get();
            Map<String, T> items = new LinkedHashMap<>(oldSnapshot.getItemsMap());
            boolean isNew = !items.containsKey(id);
            T oldItem = items.put(id, loadedItem);
            itemMetadata.put(id, createItemMeta(itemPath));
            rebuildSnapshot(items);

            if (isNew) {
                ref.notifyListeners(CollectionItemChange.added(
                        collectionName, itemType, id, loadedItem
                ));
            } else {
                ref.notifyListeners(CollectionItemChange.modified(
                        collectionName, itemType, id, oldItem, loadedItem
                ));
            }

            return EditResult.success(loadedItem);
        } catch (Exception e) {
            logger.warning("Failed to save item " + id + ": " + e.getMessage());
            return EditResult.failure("Failed to save: " + e.getMessage());
        }
    }

    /**
     * Deletes an item from disk.
     *
     * @param id the item ID
     * @return the edit result
     */
    public @NotNull EditResult<Void> deleteItem(@NotNull String id) {
        Path itemPath = resolveItemPath(id);

        DefaultConfigCollectionSnapshot<T> oldSnapshot =
                (DefaultConfigCollectionSnapshot<T>) ref.get();
        T oldItem = oldSnapshot.find(id);

        if (oldItem == null) {
            return EditResult.failure("Item not found: " + id);
        }

        try {
            Files.deleteIfExists(itemPath);

            Map<String, T> items = new LinkedHashMap<>(oldSnapshot.getItemsMap());
            items.remove(id);
            itemMetadata.remove(id);
            rebuildSnapshot(items);

            ref.notifyListeners(CollectionItemChange.removed(
                    collectionName, itemType, id, oldItem
            ));

            return EditResult.success();
        } catch (IOException e) {
            logger.warning("Failed to delete item " + id + ": " + e.getMessage());
            return EditResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    /**
     * Creates a copy of an item for safe mutation.
     *
     * @param item the item to copy
     * @return the copy
     */
    public @Nullable T copyItem(@NotNull T item) {
        try {
            MutableConfigNode node = loader.createNode();
            binder.unbind(item, node);
            BindingResult<T> result = binder.bind(node, itemType);
            if (result.hasErrors() || result.hasValidationErrors()) {
                return null;
            }
            return result.get();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesPattern(String fileName) {
        for (String extension : EXTENSIONS) {
            if (fileName.endsWith("." + extension)) {
                return true;
            }
        }

        return false;
    }

    private String getIdFromPath(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private Path resolveItemPath(String id) {
        Path ymlPath = folder.resolve(id + ".yml");
        Path yamlPath = folder.resolve(id + ".yaml");
        if (Files.exists(yamlPath)) {
            return yamlPath;
        }
        return ymlPath;
    }

    private boolean isValidId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(id).matches();
    }

    private @Nullable T loadItem(Path path, String id) {
        try {
            ConfigNode node = loader.load(ConfigSource.file(path));
            BindingResult<T> result = binder.bind(node, itemType);

            if (result.hasErrors()) {
                logger.warning("Binding errors loading " + path + ": " + result.errors());
                return null;
            }
            if (result.hasValidationErrors()) {
                logger.warning("Validation errors loading " + path + ": " + result.validationErrors());
                return null;
            }

            T item = result.get();
            injectId(item, id);
            return item;
        } catch (Exception e) {
            logger.warning("Failed to load item from " + path + ": " + e.getMessage());
            return null;
        }
    }

    private void injectId(T item, String id) {
        String idField = annotation.idField();
        if (!idField.isEmpty()) {
            setFieldValue(item, idField, id);
            return;
        }

        for (Field field : itemType.getDeclaredFields()) {
            if (field.isAnnotationPresent(NodeKey.class)) {
                field.setAccessible(true);
                try {
                    field.set(item, id);
                } catch (Exception e) {
                    logger.log(Level.FINE, "Failed to inject ID into @NodeKey field: " + field.getName(), e);
                }
                return;
            }
        }
    }

    private void setFieldValue(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to set field value: " + fieldName, e);
        }
    }

    private void orderByField(List<T> items, String fieldName) {
        try {
            Field field = itemType.getDeclaredField(fieldName);
            field.setAccessible(true);

            items.sort((a, b) -> {
                try {
                    Object va = field.get(a);
                    Object vb = field.get(b);
                    if (va instanceof Comparable && vb instanceof Comparable) {
                        @SuppressWarnings("unchecked")
                        Comparable<Object> ca = (Comparable<Object>) va;
                        return ca.compareTo(vb);
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Failed to compare field values for ordering", e);
                }
                return 0;
            });
        } catch (NoSuchFieldException e) {
            logger.log(Level.FINE, "Order-by field not found: " + fieldName, e);
        }
    }

    private @Nullable List<T> computeEnabledItems(List<T> items) {
        String enabledField = annotation.enabledField();
        if (enabledField.isEmpty()) {
            return null;
        }

        Field field;
        try {
            field = itemType.getDeclaredField(enabledField);
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            logger.log(Level.FINE, "Enabled field not found: " + enabledField, e);
            return null;
        }

        if (field.getType() != boolean.class && field.getType() != Boolean.class) {
            return null;
        }

        List<T> enabled = new ArrayList<>();
        for (T item : items) {
            try {
                Object value = field.get(item);
                if (Boolean.TRUE.equals(value)) {
                    enabled.add(item);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to read enabled field, assuming enabled", e);
                enabled.add(item);
            }
        }
        return enabled;
    }

    private ItemMeta createItemMeta(Path path) {
        try {
            ConfigNode node = loader.load(ConfigSource.file(path));
            String hash = ConfigNodeHash.sha256(node);
            return new ItemMeta(hash);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to create metadata for: " + path, e);
            return new ItemMeta("");
        }
    }

    private List<CollectionItemChange<T>> computeChanges(
            Map<String, T> oldItems,
            Map<String, T> newItems,
            Map<String, ItemMeta> oldMetadata) {
        List<CollectionItemChange<T>> changes = new ArrayList<>();

        for (Map.Entry<String, T> entry : oldItems.entrySet()) {
            if (!newItems.containsKey(entry.getKey())) {
                changes.add(CollectionItemChange.removed(
                        collectionName, itemType, entry.getKey(), entry.getValue()
                ));
            }
        }

        for (Map.Entry<String, T> entry : newItems.entrySet()) {
            String id = entry.getKey();
            T newItem = entry.getValue();
            T oldItem = oldItems.get(id);

            if (oldItem == null) {
                changes.add(CollectionItemChange.added(
                        collectionName, itemType, id, newItem
                ));
            } else {
                ItemMeta oldMeta = oldMetadata.get(id);
                ItemMeta newMeta = itemMetadata.get(id);
                if (oldMeta == null || newMeta == null || !oldMeta.hash.equals(newMeta.hash)) {
                    changes.add(CollectionItemChange.modified(
                            collectionName, itemType, id, oldItem, newItem
                    ));
                }
            }
        }

        return changes;
    }

    private void rebuildSnapshot(Map<String, T> items) {
        List<T> orderedValues = new ArrayList<>(items.values());

        String orderBy = annotation.orderBy();
        if (!orderBy.isEmpty() && !"filename".equals(orderBy)) {
            orderByField(orderedValues, orderBy);
        }

        List<T> enabledItems = computeEnabledItems(orderedValues);

        ref.setSnapshot(new DefaultConfigCollectionSnapshot<>(
                itemType, collectionName, items, orderedValues, enabledItems
        ));
    }

    private static final class ItemMeta {
        final String hash;

        public ItemMeta(String hash) {
            this.hash = hash;
        }
    }
}
