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
package tech.guilhermekaua.spigotboot.config.spigot;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigCollection;
import tech.guilhermekaua.spigotboot.config.binding.Binder;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionRef;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reference.key.CollectionItemKey;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.reference.key.SingleConfigKey;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.reload.DefaultConfigRef;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.spigot.collection.CollectionEntry;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.config.spigot.reference.ConfigReferenceManager;
import tech.guilhermekaua.spigotboot.config.spigot.reference.SpigotConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;
import tech.guilhermekaua.spigotboot.core.validation.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spigot implementation of ConfigManager.
 */
public class SpigotConfigManager implements ConfigManager {

    private final Plugin plugin;
    private final YamlConfigLoader loader;
    private final TypeSerializerRegistry serializers;
    private final Binder binder;
    private final ConfigReferenceManager referenceManager;
    private final ConfigBindingCoordinator bindingCoordinator;

    private final Map<Class<?>, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> configsByName = new ConcurrentHashMap<>();

    private final Map<CollectionKey, CollectionEntry<?>> collections = new ConcurrentHashMap<>();

    /**
     * Tracks whether initializeAll() has been called
     */
    private volatile boolean initialized = false;

    /**
     * Creates a new config manager with default error handling.
     *
     * @param plugin the Bukkit plugin
     */
    public SpigotConfigManager(@NotNull Plugin plugin) {
        this(plugin, null);
    }

    /**
     * Creates a new config manager with optional custom error handling.
     *
     * @param plugin       the Bukkit plugin
     * @param errorHandler the optional custom error handler (null for default)
     */
    public SpigotConfigManager(@NotNull Plugin plugin, @Nullable ConfigReferenceErrorHandler errorHandler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.loader = new YamlConfigLoader();
        this.serializers = TypeSerializerRegistry.defaults();
        BukkitSerializers.registerAll(this.serializers);

        if (errorHandler != null) {
            this.referenceManager = new ConfigReferenceManager(this, errorHandler);
        } else {
            this.referenceManager = new ConfigReferenceManager(this, plugin.getLogger());
        }

        this.binder = Binder.builder()
                .serializers(serializers)
                .validator(Validator.create())
                .implicitDefaults(true)
                .useConstructorBinding(true)
                .nodePreprocessor(referenceManager.getPreprocessor())
                .build();

        this.bindingCoordinator = new ConfigBindingCoordinator(
                referenceManager,
                binder,
                plugin.getLogger(),
                new ConfigEntryAccessor() {
                    @Override
                    public @Nullable Class<?> getConfigClassByName(@NotNull String configName) {
                        return configsByName.get(configName);
                    }

                    @Override
                    public @Nullable ConfigNode getConfigNode(@NotNull Class<?> configClass) {
                        ConfigEntry<?> entry = configs.get(configClass);
                        return entry != null ? entry.getNode() : null;
                    }

                    @Override
                    public @Nullable NamingStrategy getNamingStrategy(@NotNull Class<?> configClass) {
                        ConfigEntry<?> entry = configs.get(configClass);
                        return entry != null ? entry.getNamingStrategy() : null;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void setConfigInstance(@NotNull Class<?> configClass, @NotNull Object instance) {
                        ConfigEntry<Object> entry = (ConfigEntry<Object>) configs.get(configClass);
                        if (entry != null) {
                            entry.setInstance(instance);
                        }
                    }

                    @Override
                    public @Nullable CollectionEntry<?> getCollectionEntryByName(@NotNull String collectionName) {
                        return SpigotConfigManager.this.getCollectionEntryByName(collectionName);
                    }
                }
        );
    }

    /**
     * Gets the serializer registry for registering custom serializers.
     *
     * @return the serializer registry
     */
    @Override
    public @NotNull TypeSerializerRegistry getSerializerRegistry() {
        return serializers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull T get(@NotNull Class<T> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        ConfigEntry<T> entry = (ConfigEntry<T>) configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }
        return entry.getInstance();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull ConfigRef<T> getRef(@NotNull Class<T> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        ConfigEntry<T> entry = (ConfigEntry<T>) configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }
        return entry.getRef();
    }

    // ==================== Collection API ====================

    @Override
    public <T> @NotNull Collection<T> getCollection(@NotNull Class<T> configClass) {
        return getCollection(configClass, findSingleCollectionName(configClass));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull Collection<T> getCollection(@NotNull Class<T> configClass, @NotNull String collectionName) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");

        CollectionEntry<T> entry = (CollectionEntry<T>) collections.get(new CollectionKey(configClass, collectionName));
        if (entry == null) {
            throw new ConfigException("Collection not registered: " + configClass.getName() + " with name '" + collectionName + "'");
        }
        return entry.getRef().get().values();
    }

    @Override
    public <T> @NotNull ConfigCollectionRef<T> getCollectionRef(@NotNull Class<T> configClass) {
        return getCollectionRef(configClass, findSingleCollectionName(configClass));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull ConfigCollectionRef<T> getCollectionRef(@NotNull Class<T> configClass, @NotNull String collectionName) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");

        CollectionEntry<T> entry = (CollectionEntry<T>) collections.get(new CollectionKey(configClass, collectionName));
        if (entry == null) {
            throw new ConfigException("Collection not registered: " + configClass.getName() + " with name '" + collectionName + "'");
        }
        return entry.getRef();
    }

    /**
     * Registers a config collection.
     * <p>
     * This only registers the collection definition.
     * Actual loading and binding happens in {@link #initializeAll()}.
     *
     * @param itemType   the item type class
     * @param annotation the @ConfigCollection annotation
     * @param <T>        the item type
     */
    public <T> void registerCollection(@NotNull Class<T> itemType, @NotNull ConfigCollection annotation) {
        Objects.requireNonNull(itemType, "itemType cannot be null");
        Objects.requireNonNull(annotation, "annotation cannot be null");

        CollectionEntry<T> entry = new CollectionEntry<>(itemType, annotation, plugin, loader, binder, annotation.naming());

        validateConfigName(entry.getCollectionName(), itemType.getName());

        entry.prepareFolder();

        CollectionKey key = new CollectionKey(itemType, entry.getCollectionName());
        collections.put(key, entry);

        plugin.getLogger().info("Registered collection definition: " + entry.getCollectionName() +
                " (" + itemType.getSimpleName() + ") from " + entry.getFolder());
    }

    /**
     * Gets a collection entry by item type and name.
     *
     * @param itemType       the item type
     * @param collectionName the collection name
     * @param <T>            the item type
     * @return the entry, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable CollectionEntry<T> getCollectionEntry(@NotNull Class<T> itemType, @NotNull String collectionName) {
        return (CollectionEntry<T>) collections.get(new CollectionKey(itemType, collectionName));
    }

    /**
     * Gets all collection names for an item type.
     *
     * @param itemType the item type
     * @return the set of collection names
     */
    public @NotNull Set<String> getCollectionNames(@NotNull Class<?> itemType) {
        Set<String> names = new LinkedHashSet<>();
        for (CollectionKey key : collections.keySet()) {
            if (key.itemType.equals(itemType)) {
                names.add(key.collectionName);
            }
        }
        return names;
    }

    private <T> String findSingleCollectionName(Class<T> itemType) {
        List<String> names = new ArrayList<>();
        for (CollectionKey key : collections.keySet()) {
            if (key.itemType.equals(itemType)) {
                names.add(key.collectionName);
            }
        }

        if (names.isEmpty()) {
            throw new ConfigException("No collection registered for type: " + itemType.getName());
        }
        if (names.size() > 1) {
            throw new ConfigException("Multiple collections exist for type " + itemType.getName() +
                    ": " + names + ". Use the overload with collectionName parameter, or add @ConfigRefName on the injection point.");
        }
        return names.get(0);
    }

    /**
     * Initializes all registered configs and collections in topological order.
     * <p>
     * This method must be called after all {@link #register(Class)} and
     * {@link #registerCollection(Class, ConfigCollection)} calls are complete.
     * It delegates to {@link #reloadAll()} after setting the initialized flag.
     *
     * @throws ConfigException if a cycle is detected in references
     */
    public void initializeAll() {
        if (initialized) {
            throw new ConfigException("initializeAll() called more than once");
        }

        initialized = true;
        reloadAll();
        plugin.getLogger().info("Config initialization complete.");
    }

    /**
     * Checks if initialization has been completed.
     *
     * @return true if {@link #initializeAll()} has been called
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Gets the reference manager for advanced operations.
     *
     * @return the reference manager
     */
    public @NotNull ConfigReferenceManager getReferenceManager() {
        return referenceManager;
    }

    // ==================== Standard config methods ====================

    @Override
    public @Nullable Object get(@NotNull String path) {
        return get(path, Object.class);
    }

    @Override
    public <T> @Nullable T get(@NotNull String path, @NotNull Class<T> type) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String configName;
        String nodePath;


        int colonIndex = path.indexOf(':');
        if (colonIndex > 0) {
            configName = path.substring(0, colonIndex);
            nodePath = path.substring(colonIndex + 1);
        } else {
            if (configsByName.isEmpty()) {
                return null;
            }
            configName = configsByName.keySet().iterator().next();
            nodePath = path;
        }

        Class<?> configClass = configsByName.get(configName);
        if (configClass == null) {
            return null;
        }

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null || entry.getNode() == null) {
            return null;
        }

        String[] parts = nodePath.split("\\.");
        Object[] pathSegments = new Object[parts.length];
        System.arraycopy(parts, 0, pathSegments, 0, parts.length);

        ConfigNode node = entry.getNode().node(pathSegments);
        return node.get(type);
    }

    @Override
    public @NotNull String getString(@NotNull String path, @NotNull String defaultValue) {
        String result = get(path, String.class);
        return result != null ? result : defaultValue;
    }

    @Override
    public int getInt(@NotNull String path, int defaultValue) {
        Integer result = get(path, Integer.class);
        return result != null ? result : defaultValue;
    }

    @Override
    public long getLong(@NotNull String path, long defaultValue) {
        Long result = get(path, Long.class);
        return result != null ? result : defaultValue;
    }

    @Override
    public boolean getBoolean(@NotNull String path, boolean defaultValue) {
        Boolean result = get(path, Boolean.class);
        return result != null ? result : defaultValue;
    }

    @Override
    public double getDouble(@NotNull String path, double defaultValue) {
        Double result = get(path, Double.class);
        return result != null ? result : defaultValue;
    }

    @Override
    public @NotNull List<String> getStringList(@NotNull String path) {
        Object result = get(path);
        if (result instanceof List) {
            List<String> strings = new ArrayList<>();
            for (Object item : (List<?>) result) {
                strings.add(String.valueOf(item));
            }
            return strings;
        }
        return Collections.emptyList();
    }

    @Override
    public <T> void register(@NotNull Class<T> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");

        Config annotation = configClass.getAnnotation(Config.class);
        if (annotation == null) {
            throw new ConfigException("Class is not annotated with @Config: " + configClass.getName());
        }

        String filePath = annotation.value();
        if (filePath.isEmpty()) {
            filePath = configClass.getSimpleName().toLowerCase() + ".yml";
        }

        Path path = plugin.getDataFolder().toPath().resolve(filePath);
        register(configClass, ConfigSource.file(path));
    }

    @Override
    public <T> void register(@NotNull Class<T> configClass, @NotNull ConfigSource source) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        Config annotation = configClass.getAnnotation(Config.class);
        if (annotation == null) {
            throw new ConfigException("Class is not annotated with @Config: " + configClass.getName());
        }

        String configName = annotation.name();
        if (configName.isEmpty()) {
            configName = configClass.getSimpleName().toLowerCase();
        }
        final String finalConfigName = configName;

        validateConfigName(finalConfigName, configClass.getName());

        if (annotation.generateDefaults() && !source.exists()) {
            copyDefaultFromResources(configClass, annotation, source);
        }

        NamingStrategy namingStrategy = annotation.naming();

        ConfigNode node = loader.load(source);

        DefaultConfigRef<T> ref = new DefaultConfigRef<>(configClass, null, () -> {
            ConfigNode reloadedNode = loader.load(source);
            ReferenceKey sourceKey = ReferenceKey.singleConfig(finalConfigName);
            referenceManager.setCurrentSourceKey(sourceKey);
            try {
                return binder.bind(reloadedNode, configClass, namingStrategy).get();
            } finally {
                referenceManager.clearCurrentSourceKey();
            }
        });

        ConfigEntry<T> entry = new ConfigEntry<>(configClass, source, namingStrategy, null, ref, node, finalConfigName);
        configs.put(configClass, entry);
        configsByName.put(finalConfigName, configClass);

        plugin.getLogger().info("Registered config definition: " + configClass.getSimpleName() + " (name=" + finalConfigName + ")");
    }

    private void validateConfigName(@NotNull String name, @NotNull String contextInfo) {
        if (name.contains(".")) {
            throw new ConfigException("Config name '" + name + "' cannot contain '.'. Context: " + contextInfo);
        }
        if (name.contains(":")) {
            throw new ConfigException("Config name '" + name + "' cannot contain ':'. Context: " + contextInfo);
        }
    }

    private void copyDefaultFromResources(Class<?> configClass, Config annotation, ConfigSource target) {
        String resourcePath = annotation.resource();
        if (resourcePath.isEmpty()) {
            resourcePath = annotation.value();
            if (resourcePath.isEmpty()) {
                resourcePath = configClass.getSimpleName().toLowerCase() + ".yml";
            }
        }

        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is != null) {
                Path targetPath = target.path();
                if (targetPath != null) {
                    Path parent = targetPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(is, targetPath);
                    plugin.getLogger().info("Created default config from resources: " + target.name());
                }
            } else {
                generateDefaultsToSource(configClass, target, annotation.naming());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy default config: " + e.getMessage());
        }
    }

    private void generateDefaultsToSource(Class<?> configClass, ConfigSource target, NamingStrategy namingStrategy) {
        try {
            Path targetPath = target.path();
            if (targetPath != null) {
                Path parent = targetPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
            }

            Object defaultInstance = configClass.getDeclaredConstructor().newInstance();
            MutableConfigNode node = loader.createNode();
            binder.unbind(defaultInstance, node, namingStrategy);
            loader.save(node, target);
            plugin.getLogger().info("Generated default config from class: " + configClass.getSimpleName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to generate default config from class: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reload(@NotNull Class<?> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }

        ReferenceKey key = ReferenceKey.singleConfig(entry.getConfigName());
        reloadKeyWithPropagation(key);
        plugin.getLogger().info("Reloaded config: " + configClass.getSimpleName());
    }

    @Override
    public void reloadCollectionItem(@NotNull Class<?> configClass, @NotNull String itemId) {
        reloadCollectionItem(configClass, findSingleCollectionName(configClass), itemId);
    }

    @Override
    public void reloadCollectionItem(@NotNull Class<?> configClass, @NotNull String collectionName, @NotNull String itemId) {
        Objects.requireNonNull(configClass, "configClass cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");

        CollectionEntry<?> entry = collections.get(new CollectionKey(configClass, collectionName));
        if (entry == null) {
            throw new ConfigException("Collection not registered: " + configClass.getName() + " with name '" + collectionName + "'");
        }

        ReferenceKey key = ReferenceKey.collectionItem(collectionName, itemId);
        reloadKeyWithPropagation(key);
        plugin.getLogger().info("Reloaded collection item: " + collectionName + "." + itemId);
    }

    /**
     * Reloads a single key and all its transitive dependents.
     * <p>
     * This performs:
     * <ol>
     *   <li>Reload the raw node from disk</li>
     *   <li>Rescan for references and update graph edges</li>
     *   <li>Compute impacted set (this key + all transitive dependents)</li>
     *   <li>Rebind impacted keys in topological order</li>
     * </ol>
     *
     * @param key the key to reload
     */
    private void reloadKeyWithPropagation(@NotNull ReferenceKey key) {
        ConfigNode newNode = reloadRawNode(key);
        if (newNode == null) {
            return;
        }

        referenceManager.updateDependenciesForKey(key, newNode);

        Set<ReferenceKey> impacted = referenceManager.getImpactedKeys(key);

        List<ReferenceKey> reloadOrder;
        try {
            reloadOrder = referenceManager.getLoadOrderForSubset(impacted);
        } catch (CycleDetectedException e) {
            throw new ConfigException("Circular reference detected during reload: " + e.formatCycle(), e);
        }

        for (ReferenceKey impactedKey : reloadOrder) {
            bindingCoordinator.bindKey(impactedKey, true);
        }
    }

    /**
     * Reloads the raw node for a key from disk.
     *
     * @param key the reference key
     * @return the new raw node, or null if not found
     */
    @SuppressWarnings("unchecked")
    private @Nullable ConfigNode reloadRawNode(@NotNull ReferenceKey key) {
        if (key.isSingleConfig()) {
            SingleConfigKey singleKey = (SingleConfigKey) key;
            String configName = singleKey.getConfigName();
            Class<?> configClass = configsByName.get(configName);
            if (configClass == null) {
                return null;
            }

            ConfigEntry<Object> entry = (ConfigEntry<Object>) configs.get(configClass);
            if (entry == null) {
                return null;
            }

            ConfigNode newNode = loader.load(entry.getSource());
            entry.setNode(newNode);
            return newNode;
        } else {
            CollectionItemKey itemKey = (CollectionItemKey) key;
            String collectionName = itemKey.getCollectionName();
            String itemId = itemKey.getItemId();

            CollectionEntry<?> collEntry = getCollectionEntryByName(collectionName);
            if (collEntry == null) {
                return null;
            }

            collEntry.reloadItemRawNode(itemId);
            return collEntry.getItemNode(itemId);
        }
    }

    @Override
    public void reloadAll() {
        if (!initialized) {
            plugin.getLogger().warning("reloadAll() called before initialization - calling initializeAll() instead");
            initializeAll();
            return;
        }

        plugin.getLogger().info("Loading all configs with reference resolution...");

        referenceManager.resetDependencies();

        for (ConfigEntry<?> entry : configs.values()) {
            ConfigNode newNode = loader.load(entry.getSource());
            entry.setNode(newNode);

            ReferenceKey key = ReferenceKey.singleConfig(entry.getConfigName());
            referenceManager.scanAndRegister(key, newNode);
        }

        for (CollectionEntry<?> collEntry : collections.values()) {
            collEntry.loadRawNodesOnly();

            String collectionName = collEntry.getCollectionName();
            for (String itemId : collEntry.getItemIds()) {
                ReferenceKey key = ReferenceKey.collectionItem(collectionName, itemId);
                ConfigNode node = collEntry.getItemNode(itemId);
                if (node != null) {
                    referenceManager.scanAndRegister(key, node);
                }
            }
        }

        List<ReferenceKey> loadOrder;
        try {
            loadOrder = referenceManager.getLoadOrder();
        } catch (CycleDetectedException e) {
            throw new ConfigException("Circular config reference detected: " + e.formatCycle(), e);
        }


        for (ReferenceKey key : loadOrder) {
            bindingCoordinator.bindKey(key, true);
        }

        for (CollectionEntry<?> collEntry : collections.values()) {
            String collectionName = collEntry.getCollectionName();
            List<String> itemOrder = new ArrayList<>();
            for (ReferenceKey key : loadOrder) {
                if (key instanceof CollectionItemKey) {
                    CollectionItemKey itemKey = (CollectionItemKey) key;
                    if (itemKey.getCollectionName().equals(collectionName)) {
                        itemOrder.add(itemKey.getItemId());
                    }
                }
            }
            collEntry.bindFromLoadedNodes(itemOrder);
        }

        plugin.getLogger().info("Load complete.");
    }

    @Override
    public void save(@NotNull Class<?> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }

        MutableConfigNode node = loader.createNode();
        binder.unbind(entry.getInstance(), node, entry.getNamingStrategy());
        loader.save(node, entry.getSource());
        plugin.getLogger().info("Saved config: " + configClass.getSimpleName());
    }

    @Override
    public void generateDefaults(@NotNull Class<?> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }

        try {
            Object defaultInstance = configClass.getDeclaredConstructor().newInstance();
            MutableConfigNode node = loader.createNode();
            binder.unbind(defaultInstance, node, entry.getNamingStrategy());
            loader.save(node, entry.getSource());
            plugin.getLogger().info("Generated defaults for: " + configClass.getSimpleName());
        } catch (Exception e) {
            throw new ConfigException("Failed to generate defaults for " + configClass.getName(), e);
        }
    }

    @Override
    public boolean isRegistered(@NotNull Class<?> configClass) {
        return configs.containsKey(configClass);
    }

    @Override
    public @NotNull Set<Class<?>> getRegisteredConfigs() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    // ==================== Reference Lookup Support ====================

    /**
     * Gets the raw config node for a config by name.
     * <p>
     * Used by {@link tech.guilhermekaua.spigotboot.config.spigot.reference.SpigotConfigReferenceLookup}.
     *
     * @param configName the config name
     * @return the config node, or null if not found
     */
    public @Nullable ConfigNode getConfigNode(@NotNull String configName) {
        Objects.requireNonNull(configName, "configName cannot be null");

        Class<?> configClass = configsByName.get(configName);
        if (configClass == null) {
            return null;
        }

        ConfigEntry<?> entry = configs.get(configClass);
        return entry != null ? entry.getNode() : null;
    }

    /**
     * Gets all registered config names.
     *
     * @return set of config names
     */
    public @NotNull Set<String> getConfigNames() {
        return Collections.unmodifiableSet(configsByName.keySet());
    }

    /**
     * Gets a collection entry by name only (without item type).
     * <p>
     * Used by {@link SpigotConfigReferenceLookup}.
     *
     * @param collectionName the collection name
     * @return the collection entry, or null if not found
     */
    public @Nullable CollectionEntry<?> getCollectionEntryByName(@NotNull String collectionName) {
        Objects.requireNonNull(collectionName, "collectionName cannot be null");

        for (Map.Entry<CollectionKey, CollectionEntry<?>> entry : collections.entrySet()) {
            if (entry.getKey().getCollectionName().equals(collectionName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Gets all collection names across all item types.
     *
     * @return set of all collection names
     */
    public @NotNull Set<String> getAllCollectionNames() {
        Set<String> names = new LinkedHashSet<>();
        for (CollectionKey key : collections.keySet()) {
            names.add(key.getCollectionName());
        }
        return Collections.unmodifiableSet(names);
    }

    // ==================== Internal Classes ====================

    /**
     * Internal entry holding config state.
     */
    private static class ConfigEntry<T> {
        private final Class<T> configClass;
        private final ConfigSource source;
        private final NamingStrategy namingStrategy;
        private final String configName;
        private volatile T instance;
        private final DefaultConfigRef<T> ref;
        private volatile ConfigNode node;

        ConfigEntry(Class<T> configClass, ConfigSource source, NamingStrategy namingStrategy,
                    T instance, DefaultConfigRef<T> ref, ConfigNode node, String configName) {
            this.configClass = configClass;
            this.source = source;
            this.namingStrategy = namingStrategy;
            this.configName = configName;
            this.instance = instance;
            this.ref = ref;
            this.node = node;
        }

        T getInstance() {
            return instance;
        }

        ConfigRef<T> getRef() {
            return ref;
        }

        ConfigSource getSource() {
            return source;
        }

        NamingStrategy getNamingStrategy() {
            return namingStrategy;
        }

        String getConfigName() {
            return configName;
        }

        ConfigNode getNode() {
            return node;
        }

        void update(T newInstance, ConfigNode newNode) {
            this.instance = newInstance;
            this.node = newNode;
            this.ref.update(newInstance);
        }

        void setInstance(T newInstance) {
            this.instance = newInstance;
            this.ref.update(newInstance);
        }

        void setNode(ConfigNode newNode) {
            this.node = newNode;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    private static class CollectionKey {
        private final Class<?> itemType;
        private final String collectionName;
    }
}
