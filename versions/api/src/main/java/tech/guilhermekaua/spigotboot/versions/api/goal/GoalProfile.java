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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable managed goal snapshot for one Bukkit entity type.
 *
 * <p>Adding the same managed key again inside the same selector replaces the previously managed entry
 * while preserving the selector-local uniqueness contract.</p>
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class GoalProfile<T extends Entity> {
    private final Class<T> entityType;
    private final List<VanillaGoalSpec> normalVanillaGoals;
    private final List<VanillaGoalSpec> targetVanillaGoals;
    private final List<CustomGoalSpec> normalCustomGoals;
    private final List<CustomGoalSpec> targetCustomGoals;

    GoalProfile(
            @NotNull Class<T> entityType,
            @NotNull List<VanillaGoalSpec> normalVanillaGoals,
            @NotNull List<VanillaGoalSpec> targetVanillaGoals,
            @NotNull List<CustomGoalSpec> normalCustomGoals,
            @NotNull List<CustomGoalSpec> targetCustomGoals
    ) {
        this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        this.normalVanillaGoals = immutableCopy(normalVanillaGoals, "normalVanillaGoals cannot be null");
        this.targetVanillaGoals = immutableCopy(targetVanillaGoals, "targetVanillaGoals cannot be null");
        this.normalCustomGoals = immutableCopy(normalCustomGoals, "normalCustomGoals cannot be null");
        this.targetCustomGoals = immutableCopy(targetCustomGoals, "targetCustomGoals cannot be null");
    }

    /**
     * Creates a new builder for the supplied Bukkit entity type.
     *
     * @param entityType the Bukkit entity type
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the builder
     */
    public static <T extends Entity> @NotNull Builder<T> builder(@NotNull Class<T> entityType) {
        return new Builder<T>(entityType);
    }

    /**
     * Returns the Bukkit entity type targeted by this goal profile.
     *
     * @return the Bukkit entity type
     */
    public @NotNull Class<T> entityType() {
        return entityType;
    }

    /**
     * Returns the immutable managed vanilla goals for the supplied selector.
     *
     * @param selectorType the selector to inspect
     * @return the immutable managed vanilla goals for the selector
     */
    public @NotNull List<VanillaGoalSpec> vanillaGoals(@NotNull GoalSelectorType selectorType) {
        Objects.requireNonNull(selectorType, "selectorType cannot be null");
        return selectorType == GoalSelectorType.NORMAL ? normalVanillaGoals : targetVanillaGoals;
    }

    /**
     * Returns the immutable managed custom goals for the supplied selector.
     *
     * @param selectorType the selector to inspect
     * @return the immutable managed custom goals for the selector
     */
    public @NotNull List<CustomGoalSpec> customGoals(@NotNull GoalSelectorType selectorType) {
        Objects.requireNonNull(selectorType, "selectorType cannot be null");
        return selectorType == GoalSelectorType.NORMAL ? normalCustomGoals : targetCustomGoals;
    }

    private static <S> @NotNull List<S> immutableCopy(@NotNull List<S> source, @NotNull String message) {
        Objects.requireNonNull(source, message);
        return Collections.unmodifiableList(new ArrayList<S>(source));
    }

    /**
     * Builds immutable managed goal profiles.
     *
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public static final class Builder<T extends Entity> {
        private final Class<T> entityType;
        private final Map<VanillaGoalKey, VanillaGoalSpec> normalVanillaGoals =
                new LinkedHashMap<VanillaGoalKey, VanillaGoalSpec>();
        private final Map<VanillaGoalKey, VanillaGoalSpec> targetVanillaGoals =
                new LinkedHashMap<VanillaGoalKey, VanillaGoalSpec>();
        private final Map<CustomGoalKey, CustomGoalSpec> normalCustomGoals =
                new LinkedHashMap<CustomGoalKey, CustomGoalSpec>();
        private final Map<CustomGoalKey, CustomGoalSpec> targetCustomGoals =
                new LinkedHashMap<CustomGoalKey, CustomGoalSpec>();

        private Builder(@NotNull Class<T> entityType) {
            this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        }

        /**
         * Adds or replaces one managed vanilla goal entry.
         *
         * @param goalSpec the goal specification
         * @return the builder
         */
        public @NotNull Builder<T> add(@NotNull VanillaGoalSpec goalSpec) {
            VanillaGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
            vanillaGoals(resolvedGoalSpec.selectorType()).put(resolvedGoalSpec.key(), resolvedGoalSpec);
            return this;
        }

        /**
         * Adds or replaces one managed custom goal entry.
         *
         * @param goalSpec the goal specification
         * @return the builder
         */
        public @NotNull Builder<T> add(@NotNull CustomGoalSpec goalSpec) {
            CustomGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
            customGoals(resolvedGoalSpec.selectorType()).put(resolvedGoalSpec.key(), resolvedGoalSpec);
            return this;
        }

        /**
         * Adds or replaces one managed vanilla goal entry.
         *
         * @param selectorType the selector that owns the goal
         * @param key the managed vanilla goal key
         * @param priority the selector priority
         * @return the builder
         */
        public @NotNull Builder<T> addVanilla(
                @NotNull GoalSelectorType selectorType,
                @NotNull VanillaGoalKey key,
                int priority
        ) {
            return add(VanillaGoalSpec.of(selectorType, key, priority));
        }

        /**
         * Adds or replaces one managed custom goal entry.
         *
         * @param selectorType the selector that owns the goal
         * @param key the managed custom goal key
         * @param priority the selector priority
         * @return the builder
         */
        public @NotNull Builder<T> addCustom(
                @NotNull GoalSelectorType selectorType,
                @NotNull CustomGoalKey key,
                int priority
        ) {
            return add(CustomGoalSpec.of(selectorType, key, priority));
        }

        /**
         * Creates the immutable goal profile.
         *
         * @return the immutable goal profile
         */
        public @NotNull GoalProfile<T> build() {
            return new GoalProfile<T>(
                    entityType,
                    new ArrayList<VanillaGoalSpec>(normalVanillaGoals.values()),
                    new ArrayList<VanillaGoalSpec>(targetVanillaGoals.values()),
                    new ArrayList<CustomGoalSpec>(normalCustomGoals.values()),
                    new ArrayList<CustomGoalSpec>(targetCustomGoals.values())
            );
        }

        private @NotNull Map<VanillaGoalKey, VanillaGoalSpec> vanillaGoals(@NotNull GoalSelectorType selectorType) {
            Objects.requireNonNull(selectorType, "selectorType cannot be null");
            return selectorType == GoalSelectorType.NORMAL ? normalVanillaGoals : targetVanillaGoals;
        }

        private @NotNull Map<CustomGoalKey, CustomGoalSpec> customGoals(@NotNull GoalSelectorType selectorType) {
            Objects.requireNonNull(selectorType, "selectorType cannot be null");
            return selectorType == GoalSelectorType.NORMAL ? normalCustomGoals : targetCustomGoals;
        }
    }
}
