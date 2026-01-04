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
import tech.guilhermekaua.spigotboot.config.binding.BindingResult;
import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionRef;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.reload.DefaultConfigRef;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.spigot.collection.CollectionEntry;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.config.spigot.serialization.BukkitSerializers;
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

    private final Map<Class<?>, ConfigEntry<?>> configs = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> configsByName = new ConcurrentHashMap<>();

    private final Map<CollectionKey, CollectionEntry<?>> collections = new ConcurrentHashMap<>();

    public SpigotConfigManager(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.loader = new YamlConfigLoader();
        this.serializers = TypeSerializerRegistry.defaults();
        BukkitSerializers.registerAll(this.serializers);

        this.binder = Binder.builder()
                .serializers(serializers)
                .validator(Validator.create())
                .namingStrategy(NamingStrategy.SNAKE_CASE)
                .implicitDefaults(true)
                .useConstructorBinding(true)
                .build();
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
     *
     * @param itemType   the item type class
     * @param annotation the @ConfigCollection annotation
     * @param <T>        the item type
     */
    public <T> void registerCollection(@NotNull Class<T> itemType, @NotNull ConfigCollection annotation) {
        Objects.requireNonNull(itemType, "itemType cannot be null");
        Objects.requireNonNull(annotation, "annotation cannot be null");

        CollectionEntry<T> entry = new CollectionEntry<>(itemType, annotation, plugin, loader, binder);
        entry.initialize();

        CollectionKey key = new CollectionKey(itemType, entry.getCollectionName());
        collections.put(key, entry);

        plugin.getLogger().info("Registered collection: " + entry.getCollectionName() +
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

        if (annotation.generateDefaults() && !source.exists()) {
            copyDefaultFromResources(configClass, annotation, source);
        }

        ConfigNode node = loader.load(source);
        BindingResult<T> result = binder.bind(node, configClass);

        T instance = result.get();

        DefaultConfigRef<T> ref = new DefaultConfigRef<>(configClass, instance, () -> {
            ConfigNode reloadedNode = loader.load(source);
            return binder.bind(reloadedNode, configClass).get();
        });

        ConfigEntry<T> entry = new ConfigEntry<>(configClass, source, instance, ref, node);
        configs.put(configClass, entry);

        String name = annotation.name();
        if (name.isEmpty()) {
            name = configClass.getSimpleName().toLowerCase();
        }
        configsByName.put(name, configClass);

        plugin.getLogger().info("Registered config: " + configClass.getSimpleName() + " from " + source.name());
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
                generateDefaultsToSource(configClass, target);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to copy default config: " + e.getMessage());
        }
    }

    private void generateDefaultsToSource(Class<?> configClass, ConfigSource target) {
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
            binder.unbind(defaultInstance, node);
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

        ConfigNode node = loader.load(entry.getSource());
        BindingResult<?> result = binder.bind(node, configClass);
        Object newInstance = result.get();

        ((ConfigEntry<Object>) entry).update(newInstance, node);
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

        entry.reloadItem(itemId);
    }

    @Override
    public void reloadAll() {
        for (Class<?> configClass : configs.keySet()) {
            try {
                reload(configClass);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload " + configClass.getSimpleName() + ": " + e.getMessage());
            }
        }

        for (CollectionEntry<?> entry : collections.values()) {
            try {
                entry.reloadAll();
                plugin.getLogger().info("Reloaded collection: " + entry.getCollectionName());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload collection " + entry.getCollectionName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void save(@NotNull Class<?> configClass) {
        Objects.requireNonNull(configClass, "configClass cannot be null");

        ConfigEntry<?> entry = configs.get(configClass);
        if (entry == null) {
            throw new ConfigException("Config not registered: " + configClass.getName());
        }

        MutableConfigNode node = loader.createNode();
        binder.unbind(entry.getInstance(), node);
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
            binder.unbind(defaultInstance, node);
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

    /**
     * Internal entry holding config state.
     */
    private static class ConfigEntry<T> {
        private final Class<T> configClass;
        private final ConfigSource source;
        private volatile T instance;
        private final DefaultConfigRef<T> ref;
        private volatile ConfigNode node;

        ConfigEntry(Class<T> configClass, ConfigSource source, T instance, DefaultConfigRef<T> ref, ConfigNode node) {
            this.configClass = configClass;
            this.source = source;
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

        ConfigNode getNode() {
            return node;
        }

        void update(T newInstance, ConfigNode newNode) {
            this.instance = newInstance;
            this.node = newNode;
            this.ref.update(newInstance);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    private static class CollectionKey {
        private final Class<?> itemType;
        private final String collectionName;
    }
}
