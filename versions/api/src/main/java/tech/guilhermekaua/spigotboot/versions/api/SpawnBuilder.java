/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent one-off spawn customizer used by {@code VersionedEntityPlatform.spawn(...)}.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class SpawnBuilder<T extends Entity> {
    private final CustomEntityId templateId;
    private final CustomEntityBaseType baseType;
    private final Class<T> bukkitType;
    private final Location location;
    private final Map<String, Object> data = new LinkedHashMap<String, Object>();

    private SpawnControllerFactory<T> controllerFactory;
    private SpawnNetworkControllerFactory<T> networkControllerFactory;
    private EntityInitializer<T> initializer;

    SpawnBuilder(@NotNull EntityTemplate<T> template, @NotNull Location location) {
        Objects.requireNonNull(template, "template cannot be null");
        this.templateId = template.id();
        this.baseType = template.baseType();
        this.bukkitType = template.bukkitType();
        this.controllerFactory = template.controllerFactory();
        this.networkControllerFactory = template.networkControllerFactory();
        this.initializer = template.initializer();
        this.location = Objects.requireNonNull(location, "location cannot be null").clone();
        if (this.location.getWorld() == null) {
            throw new IllegalArgumentException("location world cannot be null");
        }
    }

    /**
     * Stores arbitrary spawn metadata.
     *
     * @param key the key to store
     * @param value the value to store
     * @return this builder
     */
    public @NotNull SpawnBuilder<T> data(@NotNull String key, @NotNull Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        data.put(key, value);
        return this;
    }

    /**
     * Sets the initializer applied before spawn callbacks run.
     *
     * @param initializer the initializer
     * @return this builder
     */
    public @NotNull SpawnBuilder<T> initialize(@NotNull EntityInitializer<T> initializer) {
        this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
        return this;
    }

    /**
     * Sets the controller factory used for this spawn.
     *
     * @param controllerFactory the controller factory
     * @return this builder
     */
    public @NotNull SpawnBuilder<T> controller(@NotNull SpawnControllerFactory<T> controllerFactory) {
        this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null");
        return this;
    }

    /**
     * Sets the network controller factory used for this spawn.
     *
     * @param networkControllerFactory the network controller factory
     * @return this builder
     */
    public @NotNull SpawnBuilder<T> networkController(@NotNull SpawnNetworkControllerFactory<T> networkControllerFactory) {
        this.networkControllerFactory = Objects.requireNonNull(
                networkControllerFactory,
                "networkControllerFactory cannot be null"
        );
        return this;
    }

    /**
     * Returns the effective immutable template represented by the current builder state.
     *
     * @return the effective template
     */
    public @NotNull EntityTemplate<T> template() {
        return new EntityTemplate<T>(
                templateId,
                baseType,
                bukkitType,
                controllerFactory,
                networkControllerFactory,
                initializer
        );
    }

    /**
     * Returns the immutable spawn options represented by the current builder state.
     *
     * @return the immutable spawn options
     */
    public @NotNull SpawnOptions spawnOptions() {
        SpawnOptions.Builder builder = SpawnOptions.builder(location);
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            builder.data(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    public static <T extends Entity> @NotNull SpawnBuilder<T> fromTemplate(
            @NotNull EntityTemplate<T> template,
            @NotNull Location location
    ) {
        return new SpawnBuilder<T>(template, location);
    }

    public static <T extends Entity> @NotNull SpawnBuilder<T> oneOff(
            @Nullable CustomEntityId templateId,
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType,
            @NotNull Location location
    ) {
        return new SpawnBuilder<T>(
                new EntityTemplate<T>(
                        templateId,
                        baseType,
                        bukkitType,
                        SpawnControllerFactory.passThrough(),
                        SpawnNetworkControllerFactory.passThrough(),
                        EntityInitializer.noop()
                ),
                location
        );
    }
}
