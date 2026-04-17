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
package tech.guilhermekaua.spigotboot.versions.api.goal;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;

import java.util.Objects;

/**
 * Live managed-goal contract for one controlled entity.
 *
 * <p>Callers must invoke this manager from the Bukkit main thread only. Implementations may defer the
 * underlying selector mutation until the next safe tick boundary, so successful mutations become visible
 * through the real runtime selector on that boundary rather than immediately inside the current callback.</p>
 *
 * <p>Inspection methods expose only the managed recognized set owned by Spigot Boot. They never expose raw
 * selector handles, unknown runtime entries, or direct version-specific internals.</p>
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface GoalManager<T extends Entity> {

    /**
     * Adds or replaces one managed vanilla goal entry.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned result is deterministic for the
     * requested managed key immediately.</p>
     *
     * @param goalSpec the managed goal specification
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    @NotNull GoalOperationResult addVanilla(@NotNull VanillaGoalSpec goalSpec);

    /**
     * Adds or replaces one managed vanilla goal entry.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned result is deterministic for the
     * requested managed key immediately.</p>
     *
     * @param selectorType the selector that owns the goal
     * @param key the managed vanilla goal key
     * @param priority the selector priority
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    default @NotNull GoalOperationResult addVanilla(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            int priority
    ) {
        return addVanilla(VanillaGoalSpec.of(selectorType, key, priority));
    }

    /**
     * Removes managed vanilla goals for one stable key inside one selector.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned removal count is deterministic for
     * the recognized managed set immediately.</p>
     *
     * @param selectorType the selector that owns the managed entry
     * @param key the managed vanilla goal key
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    @NotNull GoalOperationResult removeVanilla(@NotNull GoalSelectorType selectorType, @NotNull VanillaGoalKey key);

    /**
     * Adds or replaces one managed custom goal entry.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned result is deterministic for the
     * requested managed key immediately.</p>
     *
     * @param goalSpec the managed goal specification
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    @NotNull GoalOperationResult addCustom(@NotNull CustomGoalSpec goalSpec);

    /**
     * Adds or replaces one managed custom goal entry.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned result is deterministic for the
     * requested managed key immediately.</p>
     *
     * @param selectorType the selector that owns the goal
     * @param key the managed custom goal key
     * @param priority the selector priority
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    default @NotNull GoalOperationResult addCustom(
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            int priority
    ) {
        return addCustom(CustomGoalSpec.of(selectorType, key, priority));
    }

    /**
     * Removes managed custom goals for one stable key inside one selector.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned removal count is deterministic for
     * the recognized managed set immediately.</p>
     *
     * @param selectorType the selector that owns the managed entry
     * @param key the managed custom goal key
     * @return the deterministic managed mutation result
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    @NotNull GoalOperationResult removeCustom(@NotNull GoalSelectorType selectorType, @NotNull CustomGoalKey key);

    /**
     * Clears the managed recognized set for one selector.
     *
     * <p>Callers must invoke this method from the main thread. Implementations may apply the underlying
     * selector mutation on the next safe tick boundary, but the returned count is deterministic for the
     * recognized managed set immediately.</p>
     *
     * @param selectorType the selector to clear
     * @return how many managed recognized entries were cleared from the selector
     * @throws GoalOperationException when the mutation fails or is unsupported
     */
    int clear(@NotNull GoalSelectorType selectorType);

    /**
     * Returns the deterministic managed recognized set for this entity.
     *
     * <p>The returned snapshot never includes arbitrary unknown selector contents. Implementations may return
     * an empty managed set when no recognized runtime manager is available yet.</p>
     *
     * @return the managed recognized set
     */
    @NotNull GoalProfile<T> managedGoals();

    /**
     * Creates a safe placeholder manager for one controlled entity.
     *
     * @param controlledEntity the controlled entity that exposes the manager
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the placeholder manager
     */
    static <T extends Entity> @NotNull GoalManager<T> unsupported(@NotNull ControlledEntity<T> controlledEntity) {
        Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        return unsupported(resolveEntityType(controlledEntity), resolveMinecraftVersion(controlledEntity));
    }

    /**
     * Creates a safe placeholder manager for one controlled entity type and runtime version.
     *
     * @param entityType the Bukkit entity type involved, or {@code null} when unknown
     * @param minecraftVersion the Minecraft version involved, or {@code null} when unknown
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the placeholder manager
     */
    static <T extends Entity> @NotNull GoalManager<T> unsupported(
            @Nullable Class<T> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        return new UnsupportedGoalManager<T>(entityType, minecraftVersion);
    }

    static <T extends Entity> @Nullable Class<T> resolveEntityType(@NotNull ControlledEntity<T> controlledEntity) {
        if (controlledEntity instanceof SpawnedEntity) {
            return ((SpawnedEntity<T>) controlledEntity).template().bukkitType();
        }

        try {
            return castEntityType(controlledEntity.bukkitEntity().getClass());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static @Nullable MinecraftVersion resolveMinecraftVersion(@NotNull ControlledEntity<?> controlledEntity) {
        try {
            return controlledEntity.minecraftVersion();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Entity> @NotNull Class<T> castEntityType(@NotNull Class<?> entityType) {
        return (Class<T>) entityType.asSubclass(Entity.class);
    }

    final class UnsupportedGoalManager<T extends Entity> implements GoalManager<T> {
        private final Class<T> entityType;
        private final MinecraftVersion minecraftVersion;
        private final GoalProfile<T> managedGoals;

        private UnsupportedGoalManager(@Nullable Class<T> entityType, @Nullable MinecraftVersion minecraftVersion) {
            this.entityType = entityType != null ? entityType : castEntityType(Entity.class);
            this.minecraftVersion = minecraftVersion;
            this.managedGoals = GoalProfile.<T>builder(this.entityType).build();
        }

        @Override
        public @NotNull GoalOperationResult addVanilla(@NotNull VanillaGoalSpec goalSpec) {
            VanillaGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
            throw new UnsupportedGoalOperationException(
                    "Live vanilla goal mutation is not available for this controlled entity yet.",
                    resolvedGoalSpec.selectorType(),
                    resolvedGoalSpec.key(),
                    entityType,
                    minecraftVersion
            );
        }

        @Override
        public @NotNull GoalOperationResult removeVanilla(
                @NotNull GoalSelectorType selectorType,
                @NotNull VanillaGoalKey key
        ) {
            GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
            VanillaGoalKey resolvedKey = Objects.requireNonNull(key, "key cannot be null");
            throw new UnsupportedGoalOperationException(
                    "Live vanilla goal mutation is not available for this controlled entity yet.",
                    resolvedSelectorType,
                    resolvedKey,
                    entityType,
                    minecraftVersion
            );
        }

        @Override
        public @NotNull GoalOperationResult addCustom(@NotNull CustomGoalSpec goalSpec) {
            CustomGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
            throw new UnsupportedGoalOperationException(
                    "Live custom goal mutation is not available for this controlled entity yet.",
                    resolvedGoalSpec.selectorType(),
                    resolvedGoalSpec.key(),
                    entityType,
                    minecraftVersion
            );
        }

        @Override
        public @NotNull GoalOperationResult removeCustom(
                @NotNull GoalSelectorType selectorType,
                @NotNull CustomGoalKey key
        ) {
            GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
            CustomGoalKey resolvedKey = Objects.requireNonNull(key, "key cannot be null");
            throw new UnsupportedGoalOperationException(
                    "Live custom goal mutation is not available for this controlled entity yet.",
                    resolvedSelectorType,
                    resolvedKey,
                    entityType,
                    minecraftVersion
            );
        }

        @Override
        public int clear(@NotNull GoalSelectorType selectorType) {
            GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
            throw new UnsupportedGoalOperationException(
                    "Clearing managed goals is not available for this controlled entity yet.",
                    resolvedSelectorType,
                    entityType,
                    minecraftVersion
            );
        }

        @Override
        public @NotNull GoalProfile<T> managedGoals() {
            return managedGoals;
        }
    }
}
