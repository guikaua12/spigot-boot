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

import java.util.Objects;

/**
 * Immutable logical definition for a native custom entity.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class CustomEntityDefinition<T extends Entity> {
    private final CustomEntityId id;
    private final CustomEntityBaseType baseType;
    private final Class<T> bukkitType;
    private final EntityControllerFactory<T> controllerFactory;
    private final CustomEntityInitializer<T> initializer;

    private CustomEntityDefinition(
            @NotNull CustomEntityId id,
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<T> bukkitType,
            @NotNull EntityControllerFactory<T> controllerFactory,
            @NotNull CustomEntityInitializer<T> initializer
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
        this.bukkitType = Objects.requireNonNull(bukkitType, "bukkitType cannot be null");
        this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null");
        this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
    }

    /**
     * Returns the logical custom entity id.
     *
     * @return the logical id
     */
    public @NotNull CustomEntityId id() {
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
     * Returns the Bukkit entity type exposed by this definition.
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
    public @NotNull EntityControllerFactory<T> controllerFactory() {
        return controllerFactory;
    }

    /**
     * Returns the initializer invoked before {@link EntityController#onSpawn(CustomEntityContext)}.
     *
     * @return the initializer
     */
    public @NotNull CustomEntityInitializer<T> initializer() {
        return initializer;
    }

    /**
     * Creates a new builder for the supplied logical base type.
     *
     * @param id the logical custom entity id
     * @param baseType the logical vanilla base type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityId id,
            @NotNull CustomEntityBaseType baseType
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return new Builder<T>(id, baseType, resolveBukkitType(baseType));
    }

    /**
     * Creates a new builder for the supplied Bukkit entity type.
     *
     * @param id the logical custom entity id
     * @param entityType the Bukkit entity type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(
            @NotNull CustomEntityId id,
            @NotNull EntityType entityType
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(entityType, "entityType cannot be null");

        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entityType);
        if (baseType == null) {
            throw new IllegalArgumentException("Unsupported Bukkit entity type '" + entityType.name() + "'.");
        }

        return new Builder<T>(id, baseType, resolveBukkitType(baseType, entityType));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> resolveBukkitType(@NotNull CustomEntityBaseType baseType) {
        Class<? extends Entity> bukkitType = baseType.bukkitTypeOrNull();
        if (bukkitType == null) {
            return (Class<T>) Entity.class;
        }
        return (Class<T>) bukkitType;
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
     * Builds immutable custom entity definitions.
     *
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public static final class Builder<T extends Entity> {
        private final CustomEntityId id;
        private final CustomEntityBaseType baseType;
        private final Class<T> bukkitType;
        private EntityControllerFactory<T> controllerFactory;
        private CustomEntityInitializer<T> initializer = CustomEntityInitializer.noop();

        private Builder(
                @NotNull CustomEntityId id,
                @NotNull CustomEntityBaseType baseType,
                @NotNull Class<T> bukkitType
        ) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
            this.bukkitType = Objects.requireNonNull(bukkitType, "bukkitType cannot be null");
        }

        /**
         * Sets the controller factory used to create one controller instance per spawn.
         *
         * @param controllerFactory the controller factory
         * @return the builder
         */
        public @NotNull Builder<T> controllerFactory(@NotNull EntityControllerFactory<T> controllerFactory) {
            this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null");
            return this;
        }

        /**
         * Sets the initializer that configures the Bukkit entity view after spawn.
         *
         * @param initializer the initializer
         * @return the builder
         */
        public @NotNull Builder<T> initializer(@NotNull CustomEntityInitializer<T> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
            return this;
        }

        /**
         * Creates the immutable definition.
         *
         * @return the immutable definition
         */
        public @NotNull CustomEntityDefinition<T> build() {
            if (controllerFactory == null) {
                throw new IllegalStateException("controllerFactory cannot be null");
            }
            return new CustomEntityDefinition<T>(id, baseType, bukkitType, controllerFactory, initializer);
        }
    }
}
