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
package tech.guilhermekaua.spigotboot.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionRef;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Main entry point for configuration management.
 */
public interface ConfigManager {

    /**
     * Gets a loaded config by class.
     *
     * @param configClass the config class
     * @param <T>         the config type
     * @return the config instance
     */
    <T> @NotNull T get(@NotNull Class<T> configClass);

    /**
     * Gets a live reference to a config.
     *
     * @param configClass the config class
     * @param <T>         the config type
     * @return the config reference
     */
    <T> @NotNull ConfigRef<T> getRef(@NotNull Class<T> configClass);

    /**
     * Gets a config collection (folder-based).
     * <p>
     * If multiple collections exist for this item type, throws an exception
     * instructing to use {@link #getCollection(Class, String)} instead.
     *
     * @param configClass the config class
     * @param <T>         the config type
     * @return the collection of config instances
     */
    <T> @NotNull Collection<T> getCollection(@NotNull Class<T> configClass);

    /**
     * Gets a config collection by item type and collection name.
     *
     * @param configClass    the config class
     * @param collectionName the collection name
     * @param <T>            the config type
     * @return the collection of config instances
     */
    <T> @NotNull Collection<T> getCollection(@NotNull Class<T> configClass, @NotNull String collectionName);

    /**
     * Gets a live config collection reference with edit capabilities.
     * <p>
     * If multiple collections exist for this item type, throws an exception
     * instructing to use {@link #getCollectionRef(Class, String)} instead.
     *
     * @param configClass the config class
     * @param <T>         the config type
     * @return the collection reference
     */
    <T> @NotNull ConfigCollectionRef<T> getCollectionRef(@NotNull Class<T> configClass);

    /**
     * Gets a live config collection reference by item type and collection name.
     *
     * @param configClass    the config class
     * @param collectionName the collection name
     * @param <T>            the config type
     * @return the collection reference
     */
    <T> @NotNull ConfigCollectionRef<T> getCollectionRef(@NotNull Class<T> configClass, @NotNull String collectionName);

    /**
     * Gets a value by path.
     * <p>
     * Format: "configName:path.to.value" or just "path.to.value"
     *
     * @param path the path
     * @return the value, or null if not found
     */
    @Nullable Object get(@NotNull String path);

    /**
     * Gets a typed value by path.
     *
     * @param path the path
     * @param type the target type
     * @param <T>  the type
     * @return the value, or null if not found
     */
    <T> @Nullable T get(@NotNull String path, @NotNull Class<T> type);

    /**
     * Gets a string value with default.
     *
     * @param path         the path
     * @param defaultValue the default value
     * @return the value or default
     */
    @NotNull String getString(@NotNull String path, @NotNull String defaultValue);

    /**
     * Gets an int value with default.
     *
     * @param path         the path
     * @param defaultValue the default value
     * @return the value or default
     */
    int getInt(@NotNull String path, int defaultValue);

    /**
     * Gets a long value with default.
     *
     * @param path         the path
     * @param defaultValue the default value
     * @return the value or default
     */
    long getLong(@NotNull String path, long defaultValue);

    /**
     * Gets a boolean value with default.
     *
     * @param path         the path
     * @param defaultValue the default value
     * @return the value or default
     */
    boolean getBoolean(@NotNull String path, boolean defaultValue);

    /**
     * Gets a double value with default.
     *
     * @param path         the path
     * @param defaultValue the default value
     * @return the value or default
     */
    double getDouble(@NotNull String path, double defaultValue);

    /**
     * Gets a string list.
     *
     * @param path the path
     * @return the list, or empty list if not found
     */
    @NotNull List<String> getStringList(@NotNull String path);

    /**
     * Registers a config class for management.
     *
     * @param configClass the config class
     * @param <T>         the config type
     */
    <T> void register(@NotNull Class<T> configClass);

    /**
     * Registers a config class with a custom source.
     *
     * @param configClass the config class
     * @param source      the config source
     * @param <T>         the config type
     */
    <T> void register(@NotNull Class<T> configClass, @NotNull ConfigSource source);

    /**
     * Reloads a specific config.
     *
     * @param configClass the config class
     */
    void reload(@NotNull Class<?> configClass);

    /**
     * Reloads a specific file in a collection.
     * <p>
     * If multiple collections exist for this item type, throws an exception
     * instructing to use {@link #reloadCollectionItem(Class, String, String)} instead.
     *
     * @param configClass the config class
     * @param itemId      the item ID (filename without extension)
     */
    void reloadCollectionItem(@NotNull Class<?> configClass, @NotNull String itemId);

    /**
     * Reloads a specific file in a collection by name.
     *
     * @param configClass    the config class
     * @param collectionName the collection name
     * @param itemId         the item ID (filename without extension)
     */
    void reloadCollectionItem(@NotNull Class<?> configClass, @NotNull String collectionName, @NotNull String itemId);

    /**
     * Reloads all managed configs.
     */
    void reloadAll();

    /**
     * Saves a config (writes current values to file).
     *
     * @param configClass the config class
     */
    void save(@NotNull Class<?> configClass);

    /**
     * Generates a default config file with comments.
     *
     * @param configClass the config class
     */
    void generateDefaults(@NotNull Class<?> configClass);

    /**
     * Checks if a config class is registered.
     *
     * @param configClass the config class
     * @return true if registered
     */
    boolean isRegistered(@NotNull Class<?> configClass);

    /**
     * Gets all registered config classes.
     *
     * @return the set of registered classes
     */
    @NotNull Set<Class<?>> getRegisteredConfigs();

    /**
     * Gets the serializer registry for registering custom serializers.
     *
     * @return the serializer registry
     */
    @NotNull TypeSerializerRegistry getSerializerRegistry();
}
