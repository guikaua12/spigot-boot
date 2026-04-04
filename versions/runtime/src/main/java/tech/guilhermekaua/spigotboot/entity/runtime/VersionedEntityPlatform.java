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
package tech.guilhermekaua.spigotboot.entity.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntity;
import tech.guilhermekaua.spigotboot.entity.api.EntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.capability.EntityCapabilities;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;

import java.util.Objects;
import java.util.Set;

/**
 * Holds the resolved adapter for the active server version.
 *
 * @since 2.0.2
 */
public final class VersionedEntityPlatform {
    private final MinecraftVersion minecraftVersion;
    private final EntityVersionAdapter adapter;

    /**
     * Creates a new resolved platform.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param adapter the selected adapter
     */
    public VersionedEntityPlatform(@NotNull MinecraftVersion minecraftVersion, @NotNull EntityVersionAdapter adapter) {
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
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
     * Returns the selected version adapter.
     *
     * @return the selected adapter
     */
    public @NotNull EntityVersionAdapter adapter() {
        return adapter;
    }

    /**
     * Returns the capabilities exposed by the selected adapter.
     *
     * @return the selected capability model
     */
    public @NotNull EntityCapabilities capabilities() {
        return adapter.capabilities();
    }

    /**
     * Returns the supported goals for the supplied entity type.
     *
     * @param entityType the logical entity type
     * @return the supported goals
     */
    public @NotNull Set<EntityGoalKey> supportedGoals(@NotNull EntityTypeKey entityType) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        return adapter.capabilities().supportedGoals(entityType);
    }

    /**
     * Checks whether a goal is supported for the supplied entity type.
     *
     * @param entityType the logical entity type
     * @param goalKey the goal to inspect
     * @return {@code true} when the goal is supported
     */
    public boolean supportsGoal(@NotNull EntityTypeKey entityType, @NotNull EntityGoalKey goalKey) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(goalKey, "goalKey cannot be null");
        return adapter.capabilities().supportsGoal(entityType, goalKey);
    }

    /**
     * Creates a custom entity from a version-agnostic spawn request.
     *
     * @param entityType the logical entity type
     * @param spawnRequest the version-agnostic spawn request
     * @return the created entity abstraction
     */
    public @NotNull CustomEntity createEntity(@NotNull EntityTypeKey entityType, @NotNull EntitySpawnRequest spawnRequest) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");
        return adapter.createEntity(entityType, spawnRequest);
    }

    /**
     * Creates a custom entity from a Bukkit location.
     *
     * @param entityType the logical entity type
     * @param location the Bukkit location
     * @return the created entity abstraction
     * @throws IllegalArgumentException when the location world is null
     */
    public @NotNull CustomEntity createEntity(@NotNull EntityTypeKey entityType, @NotNull Location location) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(location, "location cannot be null");

        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location world cannot be null");
        }

        EntitySpawnRequest spawnRequest = EntitySpawnRequest.of(
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
        return createEntity(entityType, spawnRequest);
    }
}
