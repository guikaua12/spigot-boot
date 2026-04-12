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
 * Legacy definition wrapper kept for migration from the old custom-entity API.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 * @deprecated use {@link EntityTemplate}
 */
@Deprecated
public final class CustomEntityDefinition<T extends Entity> {
    private final EntityTemplate<T> template;

    private CustomEntityDefinition(@NotNull EntityTemplate<T> template) {
        this.template = Objects.requireNonNull(template, "template cannot be null");
        if (template.id() == null) {
            throw new IllegalArgumentException("Legacy custom entity definitions require a non-null id.");
        }
    }

    /**
     * Returns the logical custom entity id.
     *
     * @return the logical id
     */
    public @NotNull CustomEntityId id() {
        CustomEntityId templateId = template.id();
        if (templateId == null) {
            throw new IllegalStateException("This legacy definition does not have an id.");
        }
        return templateId;
    }

    /**
     * Returns the logical vanilla base type.
     *
     * @return the logical base type
     */
    public @NotNull CustomEntityBaseType baseType() {
        return template.baseType();
    }

    /**
     * Returns the Bukkit entity type exposed by this definition.
     *
     * @return the Bukkit entity type
     */
    public @NotNull Class<T> bukkitType() {
        return template.bukkitType();
    }

    /**
     * Returns the controller factory used to create one controller instance per spawn.
     *
     * @return the controller factory
     */
    public @NotNull EntityControllerFactory<T> controllerFactory() {
        return new EntityControllerFactory<T>() {
            @Override
            public @NotNull EntityController<T> create(@NotNull CustomEntitySpawnContext<T> context) {
                return template.controllerFactory().create(context);
            }
        };
    }

    /**
     * Returns the initializer invoked before {@link EntityController#onSpawn(CustomEntityContext)}.
     *
     * @return the initializer
     */
    public @NotNull CustomEntityInitializer<T> initializer() {
        return new CustomEntityInitializer<T>() {
            @Override
            public void initialize(@NotNull CustomEntityContext<T> context) {
                template.initializer().initialize(context);
            }
        };
    }

    /**
     * Returns the new template view for this legacy definition.
     *
     * @return the template
     */
    public @NotNull EntityTemplate<T> toTemplate() {
        return template;
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
        return new Builder<T>(EntityTemplate.builder(id, baseType));
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
        return new Builder<T>(EntityTemplate.builder(id, entityType));
    }

    public static <T extends Entity> @NotNull CustomEntityDefinition<T> fromTemplate(@NotNull EntityTemplate<T> template) {
        return new CustomEntityDefinition<T>(template);
    }

    /**
     * Builds immutable custom entity definitions.
     *
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public static final class Builder<T extends Entity> {
        private final EntityTemplate.Builder<T> delegate;
        private boolean controllerFactorySet;

        private Builder(@NotNull EntityTemplate.Builder<T> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        }

        /**
         * Sets the controller factory used to create one controller instance per spawn.
         *
         * @param controllerFactory the controller factory
         * @return the builder
         */
        public @NotNull Builder<T> controllerFactory(@NotNull EntityControllerFactory<T> controllerFactory) {
            delegate.controller(Objects.requireNonNull(controllerFactory, "controllerFactory cannot be null"));
            controllerFactorySet = true;
            return this;
        }

        /**
         * Sets the initializer that configures the Bukkit entity view after spawn.
         *
         * @param initializer the initializer
         * @return the builder
         */
        public @NotNull Builder<T> initializer(@NotNull CustomEntityInitializer<T> initializer) {
            delegate.initialize(Objects.requireNonNull(initializer, "initializer cannot be null"));
            return this;
        }

        /**
         * Creates the immutable definition.
         *
         * @return the immutable definition
         */
        public @NotNull CustomEntityDefinition<T> build() {
            if (!controllerFactorySet) {
                throw new IllegalStateException("controllerFactory cannot be null");
            }
            return new CustomEntityDefinition<T>(delegate.build());
        }
    }
}
