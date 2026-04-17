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
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable runtime batch of managed goal mutations scheduled for one safe-tick execution window.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class RuntimeGoalMutationBatch<T extends Entity> {
    private final ControlledEntity<T> controlledEntity;
    private final GoalProfile<T> managedGoals;
    private final List<RuntimeGoalMutation> mutations;

    RuntimeGoalMutationBatch(
            @NotNull ControlledEntity<T> controlledEntity,
            @NotNull GoalProfile<T> managedGoals,
            @NotNull List<RuntimeGoalMutation> mutations
    ) {
        this.controlledEntity = Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        this.managedGoals = Objects.requireNonNull(managedGoals, "managedGoals cannot be null");
        Objects.requireNonNull(mutations, "mutations cannot be null");
        this.mutations = Collections.unmodifiableList(new ArrayList<RuntimeGoalMutation>(mutations));
    }

    /**
     * Returns the controlled entity that owns the batch.
     *
     * @return the controlled entity that owns the batch
     */
    public @NotNull ControlledEntity<T> controlledEntity() {
        return controlledEntity;
    }

    /**
     * Returns the deterministic managed snapshot after the queued mutations were accepted.
     *
     * @return the deterministic managed snapshot
     */
    public @NotNull GoalProfile<T> managedGoals() {
        return managedGoals;
    }

    /**
     * Returns the queued managed mutations in execution order.
     *
     * @return the queued managed mutations
     */
    public @NotNull List<RuntimeGoalMutation> mutations() {
        return mutations;
    }
}
