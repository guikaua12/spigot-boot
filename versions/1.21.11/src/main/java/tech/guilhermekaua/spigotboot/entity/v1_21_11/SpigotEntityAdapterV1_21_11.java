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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntity;
import tech.guilhermekaua.spigotboot.entity.api.EntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.capability.EntityCapabilities;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKeys;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKeys;
import tech.guilhermekaua.spigotboot.entity.v1_21_11.zombie.CustomZombieV1_21_11;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Entity adapter for Minecraft 1.21.11 on Paper.
 *
 * <p>Spawns entities via the Bukkit API and backs goal management through NMS reflection
 * against Mojang-mapped Paper classes. Pathfinding uses Paper's stable {@code Pathfinder} API.
 *
 * @since 2.0.2
 */
public final class SpigotEntityAdapterV1_21_11 implements EntityVersionAdapter {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);
    private static final EntityCapabilities CAPABILITIES = new FixedEntityCapabilities(createGoalMatrix());

    /**
     * Creates the adapter.
     */
    public SpigotEntityAdapterV1_21_11() {
    }

    @Override
    public @NotNull MinecraftVersion minimumVersion() {
        return VERSION;
    }

    @Override
    public @NotNull MinecraftVersion maximumVersion() {
        return VERSION;
    }

    @Override
    public @NotNull EntityCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull CustomEntity createEntity(@NotNull EntityTypeKey entityType, @NotNull EntitySpawnRequest spawnRequest) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");

        World world = resolveWorld(spawnRequest.worldName());
        Location location = new Location(world, spawnRequest.x(), spawnRequest.y(), spawnRequest.z(),
                spawnRequest.yaw(), spawnRequest.pitch());

        if (EntityTypeKeys.ZOMBIE.equals(entityType)) {
            Zombie bukkit = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
            return new CustomZombieV1_21_11(bukkit);
        }

        throw new UnsupportedOperationException(
                "Entity type '" + entityType + "' is not yet supported by the 1.21.11 adapter.");
    }

    private static World resolveWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World '" + worldName + "' does not exist.");
        }
        return world;
    }

    private static Map<EntityTypeKey, Set<EntityGoalKey>> createGoalMatrix() {
        Map<EntityTypeKey, Set<EntityGoalKey>> goalMatrix = new LinkedHashMap<EntityTypeKey, Set<EntityGoalKey>>();
        goalMatrix.put(EntityTypeKeys.ZOMBIE, setOf(
                EntityGoalKeys.BREAK_DOOR,
                EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW,
                EntityGoalKeys.FLOAT,
                EntityGoalKeys.LOOK_AT_PLAYER,
                EntityGoalKeys.MELEE_ATTACK,
                EntityGoalKeys.RANDOM_LOOK_AROUND,
                EntityGoalKeys.USE_ITEM
        ));
        goalMatrix.put(EntityTypeKeys.SKELETON, setOf(
                EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW,
                EntityGoalKeys.FLOAT,
                EntityGoalKeys.LOOK_AT_PLAYER,
                EntityGoalKeys.RANGED_ATTACK,
                EntityGoalKeys.RANDOM_LOOK_AROUND,
                EntityGoalKeys.USE_ITEM
        ));
        goalMatrix.put(EntityTypeKeys.WOLF, setOf(
                EntityGoalKeys.AVOID_ENTITY,
                EntityGoalKeys.FLOAT,
                EntityGoalKeys.FOLLOW_OWNER,
                EntityGoalKeys.LOOK_AT_PLAYER,
                EntityGoalKeys.PANIC,
                EntityGoalKeys.TEMPT
        ));
        goalMatrix.put(EntityTypeKeys.VILLAGER, setOf(
                EntityGoalKeys.AVOID_ENTITY,
                EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW,
                EntityGoalKeys.FLOAT,
                EntityGoalKeys.LOOK_AT_PLAYER,
                EntityGoalKeys.OPEN_DOOR,
                EntityGoalKeys.PANIC,
                EntityGoalKeys.RANDOM_LOOK_AROUND,
                EntityGoalKeys.USE_ITEM
        ));
        return Collections.unmodifiableMap(goalMatrix);
    }

    private static Set<EntityGoalKey> setOf(EntityGoalKey... goals) {
        return Collections.unmodifiableSet(new LinkedHashSet<EntityGoalKey>(Arrays.asList(goals)));
    }

    private static final class FixedEntityCapabilities implements EntityCapabilities {
        private static final Set<EntityGoalKey> DEFAULT_GOALS = setOf(
                EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW,
                EntityGoalKeys.FLOAT,
                EntityGoalKeys.LOOK_AT_PLAYER,
                EntityGoalKeys.RANDOM_LOOK_AROUND,
                EntityGoalKeys.USE_ITEM
        );

        private final Map<EntityTypeKey, Set<EntityGoalKey>> goalMatrix;

        private FixedEntityCapabilities(Map<EntityTypeKey, Set<EntityGoalKey>> goalMatrix) {
            this.goalMatrix = goalMatrix;
        }

        @Override
        public @NotNull Set<EntityGoalKey> supportedGoals(@NotNull EntityTypeKey entityType) {
            Objects.requireNonNull(entityType, "entityType cannot be null");
            Set<EntityGoalKey> goals = goalMatrix.get(entityType);
            return goals == null ? DEFAULT_GOALS : goals;
        }
    }
}
