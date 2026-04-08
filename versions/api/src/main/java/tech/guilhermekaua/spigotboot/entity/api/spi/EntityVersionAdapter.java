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
package tech.guilhermekaua.spigotboot.entity.api.spi;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;

import java.util.Objects;

/**
 * Service-provider contract implemented by each version-specific native adapter.
 *
 * @since 2.0.2
 */
public interface EntityVersionAdapter {

    /**
     * Returns the lowest Minecraft version supported by this adapter.
     *
     * @return the lower bound
     */
    @NotNull MinecraftVersion minimumVersion();

    /**
     * Returns the highest Minecraft version supported by this adapter.
     *
     * @return the upper bound
     */
    @NotNull MinecraftVersion maximumVersion();

    /**
     * Returns whether this adapter supports the supplied version.
     *
     * @param version the version to inspect
     * @return {@code true} when the adapter supports the version
     */
    default boolean supports(@NotNull MinecraftVersion version) {
        Objects.requireNonNull(version, "version cannot be null");
        return version.isBetween(minimumVersion(), maximumVersion());
    }

    /**
     * Returns whether this adapter can spawn the supplied logical base type.
     *
     * @param baseType the logical base type
     * @return {@code true} when the adapter can spawn the type
     */
    boolean supports(@NotNull CustomEntityBaseType baseType);

    /**
     * Spawns a real native custom entity for the supplied definition.
     *
     * @param definition the logical definition to spawn
     * @param spawnRequest the spawn request
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live entity handle
     */
    <T extends Entity> @NotNull CustomEntityHandle<T> spawn(
            @NotNull CustomEntityDefinition<T> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );

    /**
     * Attaches the shared runtime lifecycle to an existing native-backed entity.
     *
     * @param entity the Bukkit entity to hook
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live controlled entity
     */
    <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    );
}
