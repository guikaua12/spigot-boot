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
package tech.guilhermekaua.spigotboot.versions.runtime.model;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;

/**
 * Optional runtime-facing provider implemented by exact-version adapters that expose managed-goal support.
 *
 * @since 2.0.2
 */
public interface VersionGoalSupportProvider {

    /**
     * Returns the immutable managed-goal support metadata for the implementing adapter.
     *
     * @return the managed-goal support metadata
     */
    @NotNull VersionGoalSupportMetadata entityGoalSupportMetadata();

    /**
     * Creates the runtime goal mutation executor seam used for freshly spawned entities.
     *
     * @param template the immutable template being spawned
     * @param spawnOptions the immutable spawn options
     * @param minecraftVersion the resolved Minecraft version
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the version-owned runtime goal mutation executor
     */
    <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnGoalMutationExecutor(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    );

    /**
     * Creates the runtime goal mutation executor seam used for attached existing entities.
     *
     * @param baseType the logical base type resolved for the entity
     * @param entity the Bukkit entity being attached
     * @param minecraftVersion the resolved Minecraft version
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the version-owned runtime goal mutation executor
     */
    <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedGoalMutationExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    );
}
