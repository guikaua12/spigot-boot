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
package tech.guilhermekaua.spigotboot.versions.runtime.selection;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;

import java.util.Objects;

/**
 * Immutable composition root for the runtime-selected managed-goal support seam.
 *
 * @since 2.0.2
 */
public final class EntityGoalSupportBundle {
    private final EntityGoalSupportFamily family;
    private final VersionGoalSupportMetadata metadata;
    private final VersionGoalSupportProvider provider;

    /**
     * Creates a new managed-goal support bundle.
     *
     * @param family the selected managed-goal support family
     * @param metadata the resolved managed-goal support metadata
     * @param provider the optional managed-goal support provider
     */
    public EntityGoalSupportBundle(
            @NotNull EntityGoalSupportFamily family,
            @NotNull VersionGoalSupportMetadata metadata,
            @Nullable VersionGoalSupportProvider provider
    ) {
        this.family = Objects.requireNonNull(family, "family cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        this.provider = provider;
    }

    /**
     * Returns the selected managed-goal support family.
     *
     * @return the selected managed-goal support family
     */
    public @NotNull EntityGoalSupportFamily family() {
        return family;
    }

    /**
     * Returns the resolved managed-goal support metadata.
     *
     * @return the resolved managed-goal support metadata
     */
    public @NotNull VersionGoalSupportMetadata metadata() {
        return metadata;
    }

    /**
     * Returns whether this bundle is backed by a concrete provider.
     *
     * @return {@code true} when a provider was resolved
     */
    public boolean providerAvailable() {
        return provider != null;
    }

    /**
     * Creates the runtime goal executor seam used for freshly spawned entities.
     *
     * @param template the immutable template being spawned
     * @param spawnOptions the immutable spawn options
     * @param minecraftVersion the resolved Minecraft version
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the resolved runtime goal executor seam
     */
    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnExecutor(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        EntityTemplate<T> resolvedTemplate = Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");

        if (provider != null && metadata.spawnedExecutorFactoryAvailable()) {
            return Objects.requireNonNull(
                    provider.createSpawnGoalMutationExecutor(resolvedTemplate, spawnOptions, minecraftVersion),
                    "provider createSpawnGoalMutationExecutor cannot return null"
            );
        }
        return RuntimeGoalMutationExecutor.noop(resolvedTemplate.goalProfile());
    }

    /**
     * Creates the runtime goal executor seam used for attached existing entities.
     *
     * @param baseType the logical base type resolved for the entity
     * @param entity the Bukkit entity being attached
     * @param minecraftVersion the resolved Minecraft version
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the resolved runtime goal executor seam
     */
    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        T resolvedEntity = Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");

        if (provider != null && metadata.attachedExecutorFactoryAvailable()) {
            return Objects.requireNonNull(
                    provider.createAttachedGoalMutationExecutor(baseType, resolvedEntity, minecraftVersion),
                    "provider createAttachedGoalMutationExecutor cannot return null"
            );
        }
        return RuntimeGoalMutationExecutor.noop(emptyManagedGoals(resolvedEntity));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull GoalProfile<T> emptyManagedGoals(@NotNull T entity) {
        Class<T> entityType = (Class<T>) entity.getClass().asSubclass(Entity.class);
        return GoalProfile.builder(entityType).build();
    }
}
