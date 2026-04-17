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
package tech.guilhermekaua.spigotboot.versions.runtime.goal;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;

import java.util.Objects;

/**
 * Internal runtime seam that will later bridge shared goal state to version-specific selector backends.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface RuntimeGoalMutationExecutor<T extends Entity> {

    /**
     * Returns the initial recognized managed snapshot owned by the runtime handle.
     *
     * @return the initial managed snapshot
     */
    @NotNull GoalProfile<T> initialManagedGoals();

    /**
     * Applies one queued batch on the next runtime-safe tick boundary.
     *
     * @param batch the queued managed mutation batch
     */
    void execute(@NotNull RuntimeGoalMutationBatch<T> batch);

    /**
     * Creates a backend placeholder that carries an initial managed snapshot but performs no selector writes yet.
     *
     * @param initialManagedGoals the initial managed snapshot
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the placeholder executor
     */
    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> noop(@NotNull GoalProfile<T> initialManagedGoals) {
        GoalProfile<T> resolvedInitialManagedGoals = Objects.requireNonNull(
                initialManagedGoals,
                "initialManagedGoals cannot be null"
        );
        return new RuntimeGoalMutationExecutor<T>() {
            @Override
            public @NotNull GoalProfile<T> initialManagedGoals() {
                return resolvedInitialManagedGoals;
            }

            @Override
            public void execute(@NotNull RuntimeGoalMutationBatch<T> batch) {
                Objects.requireNonNull(batch, "batch cannot be null");
            }
        };
    }
}
