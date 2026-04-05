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
package tech.guilhermekaua.spigotboot.entity.runtime;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.runtime.exception.CustomEntityDefinitionNotFoundException;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.registry.CustomEntityDefinitionRegistry;

import java.util.Collection;
import java.util.Objects;

/**
 * Plugin-facing runtime entry point for multi-version native custom entities.
 *
 * @since 2.0.2
 */
public final class VersionedEntityPlatform {
    private final MinecraftVersion minecraftVersion;
    private final EntityVersionAdapter adapter;
    private final CustomEntityDefinitionRegistry definitionRegistry;

    /**
     * Creates a new resolved platform.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param adapter the selected adapter
     */
    public VersionedEntityPlatform(@NotNull MinecraftVersion minecraftVersion, @NotNull EntityVersionAdapter adapter) {
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
        this.definitionRegistry = new CustomEntityDefinitionRegistry();
    }

    /**
     * Returns the resolved Minecraft version.
     *
     * @return the active Minecraft version
     */
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the active version adapter.
     *
     * @return the active adapter
     */
    public @NotNull EntityVersionAdapter adapter() {
        return adapter;
    }

    /**
     * Returns whether the active adapter supports the supplied logical base type.
     *
     * @param baseType the base type to inspect
     * @return {@code true} when the active adapter supports the type
     */
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return adapter.supports(baseType);
    }

    /**
     * Registers a custom entity definition for later spawning.
     *
     * @param definition the definition to register
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public <T extends LivingEntity> void registerDefinition(@NotNull CustomEntityDefinition<T> definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        if (!supports(definition.baseType())) {
            throw new IllegalArgumentException(
                    "The active adapter does not support base type '" + definition.baseType() + "'."
            );
        }
        definitionRegistry.register(definition);
    }

    /**
     * Registers each supplied definition.
     *
     * @param definitions the definitions to register
     */
    public void registerDefinitions(@NotNull Iterable<? extends CustomEntityDefinition<?>> definitions) {
        Objects.requireNonNull(definitions, "definitions cannot be null");
        for (CustomEntityDefinition<?> definition : definitions) {
            registerDefinition(definition);
        }
    }

    /**
     * Returns the registered definition for the supplied id, or {@code null} when it does not exist.
     *
     * @param id the logical definition id
     * @return the registered definition, or {@code null}
     */
    public @Nullable CustomEntityDefinition<?> definition(@NotNull CustomEntityId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return definitionRegistry.find(id);
    }

    /**
     * Returns every registered definition.
     *
     * @return the registered definitions
     */
    public @NotNull Collection<CustomEntityDefinition<?>> definitions() {
        return definitionRegistry.definitions();
    }

    /**
     * Spawns a registered definition by id.
     *
     * @param id the logical custom entity id
     * @param spawnRequest the spawn request
     * @return the spawned entity handle
     */
    public @NotNull CustomEntityHandle<?> spawn(
            @NotNull CustomEntityId id,
            @NotNull CustomEntitySpawnRequest spawnRequest
    ) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");

        CustomEntityDefinition<?> definition = definitionRegistry.find(id);
        if (definition == null) {
            throw new CustomEntityDefinitionNotFoundException(
                    "No custom entity definition has been registered for '" + id + "'."
            );
        }
        return spawnUnchecked(definition, spawnRequest);
    }

    /**
     * Spawns the supplied definition after verifying it is registered on this runtime.
     *
     * @param definition the registered definition
     * @param spawnRequest the spawn request
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the spawned entity handle
     */
    public <T extends LivingEntity> @NotNull CustomEntityHandle<T> spawn(
            @NotNull CustomEntityDefinition<T> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");

        CustomEntityDefinition<?> registeredDefinition = definitionRegistry.find(definition.id());
        if (registeredDefinition == null) {
            throw new CustomEntityDefinitionNotFoundException(
                    "Definition '" + definition.id() + "' is not registered on this platform."
            );
        }
        if (registeredDefinition != definition) {
            throw new IllegalArgumentException(
                    "A different definition is already registered for '" + definition.id() + "'."
            );
        }
        return spawnUnchecked(definition, spawnRequest);
    }

    @SuppressWarnings("unchecked")
    private <T extends LivingEntity> @NotNull CustomEntityHandle<T> spawnUnchecked(
            @NotNull CustomEntityDefinition<?> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest
    ) {
        CustomEntityDefinition<T> typedDefinition = (CustomEntityDefinition<T>) definition;
        RuntimeNativeEntityLifecycle<T> lifecycle = new RuntimeNativeEntityLifecycle<T>(
                typedDefinition,
                spawnRequest,
                minecraftVersion
        );
        return adapter.spawn(typedDefinition, spawnRequest, lifecycle);
    }
}
