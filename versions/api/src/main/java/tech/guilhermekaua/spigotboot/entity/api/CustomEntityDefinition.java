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

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable logical definition for a native custom entity.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public abstract class CustomEntityDefinition<T extends LivingEntity> {
    private final CustomEntityId id;
    private final CustomEntityBaseType baseType;
    private final Class<T> bukkitType;
    private final EntityControllerFactory<T> controllerFactory;
    private final CustomEntityInitializer<T> initializer;

    protected CustomEntityDefinition(
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
}
