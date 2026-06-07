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
package tech.guilhermekaua.spigotboot.config.spigot.folder;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
import tech.guilhermekaua.spigotboot.config.annotation.NodeKey;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.binding.BindingResult;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.folder.ConfigNodeHash;
import tech.guilhermekaua.spigotboot.config.folder.EditResult;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigItemChange;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.core.scanner.ResourceScanUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Internal entry holding folder config state for SpigotConfigManager.
 * <p>
 * Each {@link FolderConfigEntry} manages one folder-based folder config.
 *
 * @param <T> the item type
 */
public final class FolderConfigEntry<T> {
    private static final String[] EXTENSIONS = {"yml", "yaml"};
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private final Class<T> itemType;
    private final String folderConfigName;
    private final Path folder;
    private final FolderConfig annotation;
    private final YamlConfigLoader loader;
    private final Binder binder;
    private final NamingStrategy namingStrategy;
    private final Plugin plugin;
    private final Logger logger;

    private final DefaultFolderConfigRef<T> ref;
    private final DefaultFolderConfigEditor<T> editor;

    private final Map<String, ItemMeta> itemMetadata = new ConcurrentHashMap<>();
    private final Map<String, ConfigNode> itemNodes = new ConcurrentHashMap<>();

    /**
     * Creates a new folder config entry.
     *
     * @param itemType       the item type class
     * @param annotation     the @{@link FolderConfig} annotation
     * @param plugin         the owning plugin
     * @param loader         the YAML loader
     * @param binder         the config binder
     * @param namingStrategy the naming strategy for field to config key conversion
     */
    public FolderConfigEntry(
            @NotNull Class<T> itemType,
            @NotNull FolderConfig annotation,
            @NotNull Plugin plugin,
            @NotNull YamlConfigLoader loader,
            @NotNull Binder binder,
            @NotNull NamingStrategy namingStrategy) {
        this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
        this.annotation = Objects.requireNonNull(annotation, "annotation cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.loader = Objects.requireNonNull(loader, "loader cannot be null");
        this.binder = Objects.requireNonNull(binder, "binder cannot be null");
        this.namingStrategy = Objects.requireNonNull(namingStrategy, "namingStrategy cannot be null");
        this.logger = plugin.getLogger();

        String name = annotation.name();
        if (name.isEmpty()) {
            name = deriveNameFromFolder(annotation.folder());
        }
        this.folderConfigName = name;

        this.folder = plugin.getDataFolder().toPath().resolve(annotation.folder());

        this.editor = new DefaultFolderConfigEditor<>(this);
        this.ref = new DefaultFolderConfigRef<>(
                itemType,
                folderConfigName,
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

    public @NotNull String getFolderConfigName() {
        return folderConfigName;
    }

    public @NotNull Path getFolder() {
        return folder;
    }

    public @NotNull DefaultFolderConfigRef<T> getRef() {
        return ref;
    }

    /**
     * Gets the raw config node for a specific item.
     *
     * @param itemId the item ID
     * @return the raw config node, or null if item not found
     */
    public @Nullable ConfigNode getItemNode(@NotNull String itemId) {
        return itemNodes.get(itemId);
    }

    /**
     * Gets all item IDs that have raw nodes.
     *
     * @return set of item IDs
     */
    public @NotNull Set<String> getItemIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(itemNodes.keySet()));
    }

    public void initialize() {
        prepareFolder();
        loadAll();
    }

    public void prepareFolder() {
        ensureFolderExists();
        copyDefaultsFromResources();
    }

    /**
     * Loads all items from the folder with filename ordering.
     */
    public void loadAll() {
        if (!Files.exists(folder)) {
            ref.setSnapshot(DefaultFolderConfigSnapshot.empty(itemType, folderConfigName));
            return;
        }

        Map<String, ConfigNode> rawNodes = scanAndLoadRawNodes();
        List<String> bindOrder = sortedKeys(rawNodes);

        Map<String, T> itemsById = new LinkedHashMap<>();
        Map<String, ItemMeta> newMetadata = new LinkedHashMap<>();

        for (String id : bindOrder) {
            ConfigNode node = rawNodes.get(id);
            if (node == null) {
                continue;
            }
            T item = bindItem(id, node);
            if (item != null) {
                itemsById.put(id, item);
                newMetadata.put(id, createItemMeta(node));
            }
        }

        finalizeSnapshot(itemsById, newMetadata);
    }

    /**
     * Loads raw nodes from disk without binding them.
     * <p>
     * Used when reference scanning needs to happen across all folder configs
     * before determining binding order. After calling this, use
     * {@link #getItemIds()} and {@link #getItemNode(String)} to access the nodes,
     * then call {@link #bindFromLoadedNodes(List)} to bind them.
     *
     * @return map of item ID to raw config node
     */
    public @NotNull Map<String, ConfigNode> loadRawNodesOnly() {
        if (!Files.exists(folder)) {
            return Collections.emptyMap();
        }
        return scanAndLoadRawNodes();
    }

    /**
     * Binds items from already-loaded raw nodes in the specified order.
     * <p>
     * Raw nodes must have been loaded via {@link #loadRawNodesOnly()} first.
     * If no raw nodes are loaded, this creates an empty snapshot.
     *
     * @param bindOrder the order in which to bind items
     */
    public void bindFromLoadedNodes(@NotNull List<String> bindOrder) {
        Objects.requireNonNull(bindOrder, "bindOrder cannot be null");

        Map<String, T> itemsById = new LinkedHashMap<>();
        Map<String, ItemMeta> newMetadata = new LinkedHashMap<>();

        Set<String> bound = new HashSet<>();

        for (String id : bindOrder) {
            if (!bound.add(id)) {
                continue;
            }
            ConfigNode node = itemNodes.get(id);
            if (node == null) {
                continue;
            }
            T item = bindItem(id, node);
            if (item != null) {
                itemsById.put(id, item);
                newMetadata.put(id, createItemMeta(node));
            }
        }

        List<String> remainingIds = new ArrayList<>(itemNodes.keySet());
        remainingIds.removeAll(bound);
        remainingIds.sort(String::compareTo);

        for (String id : remainingIds) {
            ConfigNode node = itemNodes.get(id);
            if (node == null) {
                continue;
            }
            T item = bindItem(id, node);
            if (item != null) {
                itemsById.put(id, item);
                newMetadata.put(id, createItemMeta(node));
            }
        }

        finalizeSnapshot(itemsById, newMetadata);
    }

    private @NotNull Map<String, ConfigNode> scanAndLoadRawNodes() {
        Map<String, ConfigNode> rawNodes = new LinkedHashMap<>();

        itemNodes.clear();

        if (!Files.exists(folder)) {
            return rawNodes;
        }

        String excludePrefix = annotation.excludePrefix();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            List<Path> files = collectMatchingFiles(stream, excludePrefix);
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));

            for (Path path : files) {
                String id = getIdFromPath(path);
                try {
                    ConfigNode node = loader.load(ConfigSource.file(path));
                    rawNodes.put(id, node);
                    itemNodes.put(id, node);
                } catch (Exception e) {
                    logger.warning("Failed to load: " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to scan folder " + folder + ": " + e.getMessage());
        }

        return rawNodes;
    }

    private @NotNull List<Path> collectMatchingFiles(
            @NotNull DirectoryStream<Path> stream,
            @NotNull String excludePrefix) {
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
        return files;
    }

    private @NotNull List<String> sortedKeys(@NotNull Map<String, ConfigNode> rawNodes) {
        List<String> keys = new ArrayList<>(rawNodes.keySet());
        keys.sort(String::compareTo);
        return keys;
    }

    private void finalizeSnapshot(
            @NotNull Map<String, T> itemsById,
            @NotNull Map<String, ItemMeta> newMetadata) {

        List<T> orderedValues = new ArrayList<>(itemsById.values());

        String orderBy = annotation.orderBy();
        if (!orderBy.isEmpty() && !"filename".equals(orderBy)) {
            orderByField(orderedValues, orderBy);
        }

        List<T> enabledItems = computeEnabledItems(orderedValues);

        itemMetadata.clear();
        itemMetadata.putAll(newMetadata);

        ref.setSnapshot(new DefaultFolderConfigSnapshot<>(
                itemType, folderConfigName, itemsById, orderedValues, enabledItems
        ));

        logger.fine("Loaded " + itemsById.size() + " items in '" + folderConfigName + "'");
    }

    public @Nullable T bindItem(@NotNull String id, @NotNull ConfigNode node) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(node, "node cannot be null");

        try {
            BindingResult<T> result = binder.bind(node, itemType, namingStrategy);

            if (result.hasErrors()) {
                logger.warning("Binding errors for " + id + ": " + result.errors());
                return null;
            }
            if (result.hasValidationErrors()) {
                logger.warning("Validation errors for " + id + ": " + result.validationErrors());
                return null;
            }

            T item = result.get();
            injectId(item, id);
            return item;
        } catch (Exception e) {
            logger.warning("Failed to bind item " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Reloads the raw node for a single item from disk.
     * <p>
     * Used during reload propagation to update the raw node before rebinding.
     *
     * @param itemId the item ID
     */
    public void reloadItemRawNode(@NotNull String itemId) {
        Path itemPath = resolveItemPath(itemId);
        if (!Files.exists(itemPath)) {
            itemNodes.remove(itemId);
            return;
        }

        try {
            ConfigNode node = loader.load(ConfigSource.file(itemPath));
            itemNodes.put(itemId, node);
        } catch (Exception e) {
            logger.warning("Failed to reload raw node for " + itemId + ": " + e.getMessage());
        }
    }

    /**
     * Rebinds a single item from its raw node and updates the snapshot.
     * <p>
     * The raw node must already be loaded. Used during reload propagation
     * when only specific items need rebinding.
     *
     * @param itemId the item ID to rebind
     */
    public void rebindItem(@NotNull String itemId) {
        ConfigNode node = itemNodes.get(itemId);
        if (node == null) {
            removeItemFromSnapshot(itemId);
            return;
        }

        T item = bindItem(itemId, node);
        if (item != null) {
            updateItemInSnapshot(itemId, item);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateItemInSnapshot(@NotNull String itemId, @NotNull T item) {
        DefaultFolderConfigSnapshot<T> oldSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
        if (oldSnapshot == null) {
            oldSnapshot = DefaultFolderConfigSnapshot.empty(itemType, folderConfigName);
        }
        Map<String, T> items = new LinkedHashMap<>(oldSnapshot.getItemsMap());
        T oldItem = items.put(itemId, item);
        itemMetadata.put(itemId, createItemMetaFromRegisteredNode(itemId));
        rebuildSnapshot(items);

        if (oldItem == null) {
            notifyItemAdded(itemId, item);
        } else {
            notifyItemModified(itemId, oldItem, item);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeItemFromSnapshot(@NotNull String itemId) {
        DefaultFolderConfigSnapshot<T> oldSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
        if (oldSnapshot == null) {
            oldSnapshot = DefaultFolderConfigSnapshot.empty(itemType, folderConfigName);
        }
        Map<String, T> items = new LinkedHashMap<>(oldSnapshot.getItemsMap());
        T oldItem = items.remove(itemId);
        if (oldItem != null) {
            itemMetadata.remove(itemId);
            itemNodes.remove(itemId);
            rebuildSnapshot(items);
            notifyItemRemoved(itemId, oldItem);
        }
    }

    private void notifyItemAdded(@NotNull String id, @NotNull T item) {
        ref.notifyListeners(FolderConfigItemChange.added(folderConfigName, itemType, id, item));
    }

    private void notifyItemModified(@NotNull String id, @NotNull T oldItem, @NotNull T newItem) {
        ref.notifyListeners(FolderConfigItemChange.modified(folderConfigName, itemType, id, oldItem, newItem));
    }

    private void notifyItemRemoved(@NotNull String id, @NotNull T oldItem) {
        ref.notifyListeners(FolderConfigItemChange.removed(folderConfigName, itemType, id, oldItem));
    }

    private ItemMeta createItemMetaFromRegisteredNode(String id) {
        try {
            ConfigNode node = itemNodes.get(id);
            if (node == null) {
                return new ItemMeta("");
            }

            return createItemMeta(node);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to create metadata for: " + id, e);
            return new ItemMeta("");
        }
    }

    private void ensureFolderExists() {
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                logger.info("Created folder config folder: " + folder);
            }
        } catch (IOException e) {
            logger.warning("Failed to create folder config folder: " + folder + " - " + e.getMessage());
        }
    }

    /**
     * Copies default YAML files from resources if the folder is empty.
     * Automatically discovers all .yml/.yaml files in the resource path.
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

            String normalizedPath = ResourceScanUtils.normalizePath(resourcePath);

            // Object-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
            // getClass() must resolve via the java.* ignore (see root pom)
            Object pluginObject = plugin;
            URL jarUrl = pluginObject.getClass().getProtectionDomain()
                    .getCodeSource().getLocation();

            if (jarUrl != null && jarUrl.getPath().endsWith(".jar")) {
                copyFromJar(jarUrl, normalizedPath);
            } else {
                copyFromFilesystem(normalizedPath);
            }
        } catch (IOException e) {
            logger.warning("Failed to copy defaults from resources: " + e.getMessage());
        }
    }

    private void copyFromJar(URL jarUrl, String resourcePath) {
        try {
            File jarFile = new File(jarUrl.toURI());
            try (JarFile jar = new JarFile(jarFile)) {
                List<String> entries = ResourceScanUtils.scanJar(jar, resourcePath,
                        entry -> ResourceScanUtils.hasExtension(entry.getName(), EXTENSIONS));

                for (String entryName : entries) {
                    String fileName = entryName.substring(resourcePath.length());
                    copyResourceFile(entryName, fileName);
                }
            }
        } catch (URISyntaxException | IOException e) {
            logger.warning("Failed to scan JAR for resources: " + e.getMessage());
        }
    }

    private void copyFromFilesystem(String resourcePath) {
        // Object-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
        // getClass() must resolve via the java.* ignore (see root pom)
        Object pluginObject = plugin;
        URL resourceUrl = pluginObject.getClass().getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            return;
        }

        try {
            Path resourceDir = Paths.get(resourceUrl.toURI());
            List<Path> files = ResourceScanUtils.scanDirectory(resourceDir,
                    p -> ResourceScanUtils.hasExtension(p.getFileName().toString(), EXTENSIONS));

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                copyResourceFile(resourcePath + fileName, fileName);
            }
        } catch (URISyntaxException | IOException e) {
            logger.warning("Failed to scan filesystem for resources: " + e.getMessage());
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

    /**
     * Reloads all items and computes changes.
     */
    public void reloadAll() {
        DefaultFolderConfigSnapshot<T> oldSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
        Map<String, T> oldItems = oldSnapshot.getItemsMap();
        Map<String, ItemMeta> oldMetadata = new HashMap<>(itemMetadata);

        loadAll();

        DefaultFolderConfigSnapshot<T> newSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
        Map<String, T> newItems = newSnapshot.getItemsMap();

        List<FolderConfigItemChange<T>> changes = computeChanges(oldItems, newItems, oldMetadata);
        for (FolderConfigItemChange<T> change : changes) {
            ref.notifyListeners(change);
        }
    }

    public void reloadItem(@NotNull String id) {
        Path itemPath = resolveItemPath(id);
        DefaultFolderConfigSnapshot<T> oldSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
        Map<String, T> oldItems = new LinkedHashMap<>(oldSnapshot.getItemsMap());
        T oldItem = oldItems.get(id);

        if (!Files.exists(itemPath)) {
            if (oldItem != null) {
                oldItems.remove(id);
                itemMetadata.remove(id);
                rebuildSnapshot(oldItems);
                notifyItemRemoved(id, oldItem);
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
            notifyItemAdded(id, newItem);
        } else {
            ItemMeta oldMeta = itemMetadata.get(id);
            ItemMeta newMeta = createItemMeta(itemPath);

            if (oldMeta == null || !oldMeta.hash.equals(newMeta.hash)) {
                oldItems.put(id, newItem);
                itemMetadata.put(id, newMeta);
                rebuildSnapshot(oldItems);
                notifyItemModified(id, oldItem, newItem);
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
            binder.unbind(value, node, namingStrategy);
            loader.save(node, ConfigSource.file(itemPath));

            T loadedItem = loadItem(itemPath, id);
            if (loadedItem == null) {
                return EditResult.failure("Failed to reload saved item");
            }

            DefaultFolderConfigSnapshot<T> oldSnapshot =
                    (DefaultFolderConfigSnapshot<T>) ref.get();
            Map<String, T> items = new LinkedHashMap<>(oldSnapshot.getItemsMap());
            boolean isNew = !items.containsKey(id);
            T oldItem = items.put(id, loadedItem);
            itemMetadata.put(id, createItemMeta(itemPath));
            rebuildSnapshot(items);

            if (isNew) {
                notifyItemAdded(id, loadedItem);
            } else {
                notifyItemModified(id, oldItem, loadedItem);
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

        DefaultFolderConfigSnapshot<T> oldSnapshot =
                (DefaultFolderConfigSnapshot<T>) ref.get();
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

            notifyItemRemoved(id, oldItem);

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
            binder.unbind(item, node, namingStrategy);
            BindingResult<T> result = binder.bind(node, itemType, namingStrategy);
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

            T item = bindItem(id, node);

            itemNodes.put(id, node);

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

        Field field = findAnnotatedField(itemType, NodeKey.class);
        if (field != null) {
            field.setAccessible(true);
            try {
                field.set(item, id);
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to inject ID into @NodeKey field: " + field.getName(), e);
            }
        }
    }

    private void setFieldValue(Object obj, String fieldName, Object value) {
        Field field = findField(obj.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            try {
                field.set(obj, value);
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to set field value: " + fieldName, e);
            }
        } else {
            logger.log(Level.FINE, "Field not found: " + fieldName);
        }
    }

    private void orderByField(List<T> items, String fieldName) {
        Field field = findField(itemType, fieldName);
        if (field == null) {
            logger.log(Level.FINE, "Order-by field not found: " + fieldName);
            return;
        }
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
    }

    private @Nullable List<T> computeEnabledItems(List<T> items) {
        String enabledField = annotation.enabledField();
        if (enabledField.isEmpty()) {
            return null;
        }

        Field field = findField(itemType, enabledField);
        if (field == null) {
            logger.log(Level.FINE, "Enabled field not found: " + enabledField);
            return null;
        }
        field.setAccessible(true);

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

            return createItemMeta(node);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to create metadata for: " + path, e);
            return new ItemMeta("");
        }
    }

    private ItemMeta createItemMeta(ConfigNode node) {
        try {
            String hash = ConfigNodeHash.sha256(node);
            return new ItemMeta(hash);
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to create metadata for: " + node.path().asString(), e);
            return new ItemMeta("");
        }
    }

    private List<FolderConfigItemChange<T>> computeChanges(
            Map<String, T> oldItems,
            Map<String, T> newItems,
            Map<String, ItemMeta> oldMetadata) {
        List<FolderConfigItemChange<T>> changes = new ArrayList<>();

        for (Map.Entry<String, T> entry : oldItems.entrySet()) {
            if (!newItems.containsKey(entry.getKey())) {
                changes.add(FolderConfigItemChange.removed(
                        folderConfigName, itemType, entry.getKey(), entry.getValue()
                ));
            }
        }

        for (Map.Entry<String, T> entry : newItems.entrySet()) {
            String id = entry.getKey();
            T newItem = entry.getValue();
            T oldItem = oldItems.get(id);

            if (oldItem == null) {
                changes.add(FolderConfigItemChange.added(
                        folderConfigName, itemType, id, newItem
                ));
            } else {
                ItemMeta oldMeta = oldMetadata.get(id);
                ItemMeta newMeta = itemMetadata.get(id);
                if (oldMeta == null || newMeta == null || !oldMeta.hash.equals(newMeta.hash)) {
                    changes.add(FolderConfigItemChange.modified(
                            folderConfigName, itemType, id, oldItem, newItem
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

        ref.setSnapshot(new DefaultFolderConfigSnapshot<>(
                itemType, folderConfigName, items, orderedValues, enabledItems
        ));
    }

    /**
     * Finds a field by name, traversing the class hierarchy.
     *
     * @param clazz     the class to search
     * @param fieldName the name of the field to find
     * @return the Field if found, or null if not found in this class or any superclass
     */
    private @Nullable Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Finds a field by annotation type, traversing the class hierarchy.
     *
     * @param clazz          the class to search
     * @param annotationType the annotation type to look for
     * @return the first Field annotated with the given annotation type, or null if none found
     */
    private @Nullable Field findAnnotatedField(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotationType) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationType)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static final class ItemMeta {
        final String hash;

        public ItemMeta(String hash) {
            this.hash = hash;
        }
    }
}
