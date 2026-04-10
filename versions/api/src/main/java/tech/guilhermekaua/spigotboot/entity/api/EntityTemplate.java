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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable reusable blueprint for spawned controlled entities.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class EntityTemplate<T extends Entity> {
    private final CustomEntityId id;
    private final CustomEntityBaseType baseType;
    private final Class<T> bukkitType;
    private final SpawnControllerFactory<T> controllerFactory;
    private final EntityInitializer<T> initializer;

    EntityTemplate(
            @Nullable CustomEntityId id,
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType,
            @NotNull SpawnControllerFactory<T> controllerFactory,
            @NotNull EntityInitializer<T> initializer
    ) {
        this.id = id;
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
        this.bukkitType = Objects.requireNonNull(bukkitType, "bukkitType cannot be null");
        this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null");
        this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
    }

    /**
     * Returns the registered template id, or {@code null} for one-off templates.
     *
     * @return the template id, or {@code null}
     */
    public @Nullable CustomEntityId id() {
        return id;
    }

    /**
     * Returns the logical vanilla base type.
     *
     * @return the logical base type
     */
    public @NotNull CustomEntityBaseType baseType() {
        return baseType;
    }

    /**
     * Returns the Bukkit entity type exposed to plugin code.
     *
     * @return the Bukkit entity type
     */
    public @NotNull Class<T> bukkitType() {
        return bukkitType;
    }

    /**
     * Returns the controller factory used to create one controller instance per spawn.
     *
     * @return the controller factory
     */
    public @NotNull SpawnControllerFactory<T> controllerFactory() {
        return controllerFactory;
    }

    /**
     * Returns the initializer applied before spawn callbacks run.
     *
     * @return the initializer
     */
    public @NotNull EntityInitializer<T> initializer() {
        return initializer;
    }

    /**
     * Creates a new builder for the supplied logical base type.
     *
     * @param baseType the logical vanilla base type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return new Builder<T>(null, baseType, resolveBukkitType(baseType));
    }

    /**
     * Creates a new builder for the supplied logical base type and explicit Bukkit type.
     *
     * @param baseType the logical vanilla base type
     * @param bukkitType the Bukkit type exposed to plugin code
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return new Builder<T>(null, baseType, Objects.requireNonNull(bukkitType, "bukkitType cannot be null"));
    }

    /**
     * Creates a new builder for the supplied logical base type with a registered id.
     *
     * @param id the template id
     * @param baseType the logical vanilla base type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityId id,
            @NotNull CustomEntityBaseType baseType
    ) {
        Builder<T> builder = builder(baseType);
        return builder.id(id);
    }

    /**
     * Creates a new builder for the supplied logical base type and explicit Bukkit type with a registered id.
     *
     * @param id the template id
     * @param baseType the logical vanilla base type
     * @param bukkitType the Bukkit type exposed to plugin code
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityId id,
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType
    ) {
        Builder<T> builder = builder(baseType, bukkitType);
        return builder.id(id);
    }

    /**
     * Creates a new builder for the supplied Bukkit entity type.
     *
     * @param entityType the Bukkit entity type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(@NotNull EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType cannot be null");

        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entityType);
        if (baseType == null) {
            throw new IllegalArgumentException("Unsupported Bukkit entity type '" + entityType.name() + "'.");
        }

        return new Builder<T>(null, baseType, resolveBukkitType(baseType, entityType));
    }

    /**
     * Creates a new builder for the supplied Bukkit entity type with a registered id.
     *
     * @param id the template id
     * @param entityType the Bukkit entity type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityId id,
            @NotNull EntityType entityType
    ) {
        Builder<T> builder = builder(entityType);
        return builder.id(id);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> resolveBukkitType(@NotNull CustomEntityBaseType baseType) {
        Class<? extends Entity> resolvedType = baseType.bukkitTypeOrNull();
        if (resolvedType == null) {
            return (Class<T>) Entity.class;
        }
        return (Class<T>) resolvedType;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> resolveBukkitType(
            @NotNull CustomEntityBaseType baseType,
            @NotNull EntityType entityType
    ) {
        if (entityType.getEntityClass() != null) {
            return (Class<T>) entityType.getEntityClass();
        }
        return resolveBukkitType(baseType);
    }

    /**
     * Builds immutable entity templates.
     *
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public static final class Builder<T extends Entity> {
        private CustomEntityId id;
        private final CustomEntityBaseType baseType;
        private final Class<T> bukkitType;
        private SpawnControllerFactory<T> controllerFactory = SpawnControllerFactory.passThrough();
        private EntityInitializer<T> initializer = EntityInitializer.noop();

        private Builder(
                @Nullable CustomEntityId id,
                @NotNull CustomEntityBaseType baseType,
                @NotNull Class<T> bukkitType
        ) {
            this.id = id;
            this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
            this.bukkitType = Objects.requireNonNull(bukkitType, "bukkitType cannot be null");
        }

        /**
         * Sets the registered template id.
         *
         * @param id the template id
         * @return the builder
         */
        public @NotNull Builder<T> id(@NotNull CustomEntityId id) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            return this;
        }

        /**
         * Sets the registered template id.
         *
         * @param namespace the id namespace
         * @param value the id value
         * @return the builder
         */
        public @NotNull Builder<T> id(@NotNull String namespace, @NotNull String value) {
            return id(CustomEntityId.of(namespace, value));
        }

        /**
         * Sets the controller factory used to create one controller instance per spawn.
         *
         * @param controllerFactory the controller factory
         * @return the builder
         */
        public @NotNull Builder<T> controller(@NotNull SpawnControllerFactory<T> controllerFactory) {
            this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null");
            return this;
        }

        /**
         * Sets the initializer that configures the Bukkit entity view after spawn.
         *
         * @param initializer the initializer
         * @return the builder
         */
        public @NotNull Builder<T> initialize(@NotNull EntityInitializer<T> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
            return this;
        }

        /**
         * Creates the immutable template.
         *
         * @return the immutable template
         */
        public @NotNull EntityTemplate<T> build() {
            return new EntityTemplate<T>(id, baseType, bukkitType, controllerFactory, initializer);
        }
    }
}
