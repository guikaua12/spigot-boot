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
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalManager;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationResult;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared runtime-owned managed-goal orchestrator reused by spawned and attached lifecycle bridges.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class RuntimeGoalManager<T extends Entity> implements GoalManager<T> {
    private final AbstractRuntimeControlledEntity<T> controlledEntity;
    private final RuntimeGoalMutationExecutor<T> executor;

    private GoalProfile<T> managedGoals;
    private final List<RuntimeGoalMutation> queuedMutations = new ArrayList<RuntimeGoalMutation>();

    public RuntimeGoalManager(
            @NotNull AbstractRuntimeControlledEntity<T> controlledEntity,
            @NotNull RuntimeGoalMutationExecutor<T> executor
    ) {
        this.controlledEntity = Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.managedGoals = Objects.requireNonNull(executor.initialManagedGoals(), "initialManagedGoals cannot be null");
    }

    @Override
    public synchronized @NotNull GoalOperationResult addVanilla(@NotNull VanillaGoalSpec goalSpec) {
        VanillaGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
        boolean replacedExistingEntry = containsVanilla(resolvedGoalSpec.selectorType(), resolvedGoalSpec.key());
        GoalProfile.Builder<T> builder = copyBuilder(managedGoals);
        builder.add(resolvedGoalSpec);
        managedGoals = builder.build();
        queuedMutations.add(RuntimeGoalMutation.addVanilla(resolvedGoalSpec));
        scheduleFlush();
        return GoalOperationResult.added(resolvedGoalSpec.selectorType(), resolvedGoalSpec.key(), replacedExistingEntry);
    }

    @Override
    public synchronized @NotNull GoalOperationResult removeVanilla(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key
    ) {
        GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        VanillaGoalKey resolvedKey = Objects.requireNonNull(key, "key cannot be null");
        int removedEntries = containsVanilla(resolvedSelectorType, resolvedKey) ? 1 : 0;
        if (removedEntries > 0) {
            GoalProfile.Builder<T> builder = GoalProfile.builder(managedGoals.entityType());
            rebuildWithoutVanilla(builder, resolvedSelectorType, resolvedKey);
            managedGoals = builder.build();
        }
        queuedMutations.add(RuntimeGoalMutation.removeVanilla(resolvedSelectorType, resolvedKey));
        scheduleFlush();
        return GoalOperationResult.removed(resolvedSelectorType, resolvedKey, removedEntries);
    }

    @Override
    public synchronized @NotNull GoalOperationResult addCustom(@NotNull CustomGoalSpec goalSpec) {
        CustomGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
        boolean replacedExistingEntry = containsCustom(resolvedGoalSpec.selectorType(), resolvedGoalSpec.key());
        GoalProfile.Builder<T> builder = copyBuilder(managedGoals);
        builder.add(resolvedGoalSpec);
        managedGoals = builder.build();
        queuedMutations.add(RuntimeGoalMutation.addCustom(resolvedGoalSpec));
        scheduleFlush();
        return GoalOperationResult.added(resolvedGoalSpec.selectorType(), resolvedGoalSpec.key(), replacedExistingEntry);
    }

    @Override
    public synchronized @NotNull GoalOperationResult removeCustom(
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key
    ) {
        GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        CustomGoalKey resolvedKey = Objects.requireNonNull(key, "key cannot be null");
        int removedEntries = containsCustom(resolvedSelectorType, resolvedKey) ? 1 : 0;
        if (removedEntries > 0) {
            GoalProfile.Builder<T> builder = GoalProfile.builder(managedGoals.entityType());
            rebuildWithoutCustom(builder, resolvedSelectorType, resolvedKey);
            managedGoals = builder.build();
        }
        queuedMutations.add(RuntimeGoalMutation.removeCustom(resolvedSelectorType, resolvedKey));
        scheduleFlush();
        return GoalOperationResult.removed(resolvedSelectorType, resolvedKey, removedEntries);
    }

    @Override
    public synchronized int clear(@NotNull GoalSelectorType selectorType) {
        GoalSelectorType resolvedSelectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        int removedEntries = managedGoals.vanillaGoals(resolvedSelectorType).size()
                + managedGoals.customGoals(resolvedSelectorType).size();
        if (removedEntries > 0) {
            GoalProfile.Builder<T> builder = GoalProfile.builder(managedGoals.entityType());
            rebuildWithoutSelector(builder, resolvedSelectorType);
            managedGoals = builder.build();
        }
        queuedMutations.add(RuntimeGoalMutation.clear(resolvedSelectorType));
        scheduleFlush();
        return removedEntries;
    }

    @Override
    public synchronized @NotNull GoalProfile<T> managedGoals() {
        return managedGoals;
    }

    private synchronized void flushQueuedMutations() {
        if (queuedMutations.isEmpty()) {
            return;
        }

        List<RuntimeGoalMutation> mutations = new ArrayList<RuntimeGoalMutation>(queuedMutations);
        queuedMutations.clear();
        if (controlledEntity.isRemoved()) {
            return;
        }
        executor.execute(new RuntimeGoalMutationBatch<T>(controlledEntity, managedGoals, mutations));
    }

    private void scheduleFlush() {
        controlledEntity.scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                flushQueuedMutations();
            }
        });
    }

    private boolean containsVanilla(@NotNull GoalSelectorType selectorType, @NotNull VanillaGoalKey key) {
        for (VanillaGoalSpec goalSpec : managedGoals.vanillaGoals(selectorType)) {
            if (goalSpec.key() == key) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCustom(@NotNull GoalSelectorType selectorType, @NotNull CustomGoalKey key) {
        for (CustomGoalSpec goalSpec : managedGoals.customGoals(selectorType)) {
            if (goalSpec.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private @NotNull GoalProfile.Builder<T> copyBuilder(@NotNull GoalProfile<T> source) {
        GoalProfile.Builder<T> builder = GoalProfile.builder(source.entityType());
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            for (VanillaGoalSpec goalSpec : source.vanillaGoals(selectorType)) {
                builder.add(goalSpec);
            }
            for (CustomGoalSpec goalSpec : source.customGoals(selectorType)) {
                builder.add(goalSpec);
            }
        }
        return builder;
    }

    private void rebuildWithoutVanilla(
            @NotNull GoalProfile.Builder<T> builder,
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key
    ) {
        GoalProfile<T> snapshot = managedGoals;
        rebuildWithoutSelector(builder, selectorType);
        for (VanillaGoalSpec goalSpec : snapshot.vanillaGoals(selectorType)) {
            if (goalSpec.key() != key) {
                builder.add(goalSpec);
            }
        }
        for (CustomGoalSpec goalSpec : snapshot.customGoals(selectorType)) {
            builder.add(goalSpec);
        }
    }

    private void rebuildWithoutCustom(
            @NotNull GoalProfile.Builder<T> builder,
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key
    ) {
        GoalProfile<T> snapshot = managedGoals;
        rebuildWithoutSelector(builder, selectorType);
        for (VanillaGoalSpec goalSpec : snapshot.vanillaGoals(selectorType)) {
            builder.add(goalSpec);
        }
        for (CustomGoalSpec goalSpec : snapshot.customGoals(selectorType)) {
            if (!goalSpec.key().equals(key)) {
                builder.add(goalSpec);
            }
        }
    }

    private void rebuildWithoutSelector(
            @NotNull GoalProfile.Builder<T> builder,
            @NotNull GoalSelectorType selectorType
    ) {
        GoalProfile<T> snapshot = managedGoals;
        for (GoalSelectorType candidate : GoalSelectorType.values()) {
            if (candidate == selectorType) {
                continue;
            }
            for (VanillaGoalSpec goalSpec : snapshot.vanillaGoals(candidate)) {
                builder.add(goalSpec);
            }
            for (CustomGoalSpec goalSpec : snapshot.customGoals(candidate)) {
                builder.add(goalSpec);
            }
        }
    }
}
