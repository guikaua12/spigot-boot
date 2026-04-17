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
package tech.guilhermekaua.spigotboot.versions.runtime.lifecycle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalManager;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationResult;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalManager;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationBatch;

import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ControlledEntityGoalManagerRuntimeContractTest {

    @Test
    void shouldExposeSharedGoalManagerContractFromSpawnedAndAttachedRuntimeHandles() {
        ControlledEntity<Zombie> spawnedEntity = new RuntimeNativeEntityLifecycle<Zombie>(
                EntityTemplate.<Zombie>builder(
                                CustomEntityId.of("test", "goal-manager-runtime"),
                                CustomEntityBaseType.ZOMBIE
                        )
                        .controller(context -> new EntityController<Zombie>() {
                        })
                        .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                        .build(),
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 21, 11)
        );
        ControlledEntity<Zombie> attachedEntity = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 21, 11),
                new EntityController<Zombie>() {
                }
        );

        assertTrue(spawnedEntity.goalManager() instanceof RuntimeGoalManager);
        assertTrue(attachedEntity.goalManager() instanceof RuntimeGoalManager);
        assertSame(spawnedEntity.goalManager().getClass(), attachedEntity.goalManager().getClass());

        assertManagedTemplateGoals(spawnedEntity.goalManager());
        assertEmptyManagedContract(attachedEntity.goalManager());
    }

    @Test
    void shouldAddOneNormalAndOneTargetZombieGoalThenRemoveBothByStableKey() {
        RecordingGoalExecutor executor = new RecordingGoalExecutor(GoalProfile.<Zombie>builder(Zombie.class).build());
        RuntimeAttachedEntityLifecycle<Zombie> attachedEntity = createAttachedEntity(executor);
        CustomGoalKey guardOwnerKey = CustomGoalKey.of("test", "guard-owner");

        GoalOperationResult addVanillaResult = attachedEntity.goalManager().addVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.FLOAT,
                1
        );
        GoalOperationResult addCustomResult = attachedEntity.goalManager().addCustom(
                GoalSelectorType.TARGET,
                guardOwnerKey,
                2
        );

        assertFalse(addVanillaResult.replacedExistingEntry());
        assertFalse(addCustomResult.replacedExistingEntry());
        assertEquals(1, attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(1, attachedEntity.goalManager().managedGoals().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(0, executor.executions);
        assertTrue(executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(executor.appliedManagedGoals.customGoals(GoalSelectorType.TARGET).isEmpty());

        attachedEntity.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(1, executor.executions);
        assertEquals(2, executor.lastBatch.mutations().size());
        assertEquals(VanillaGoalKey.FLOAT, executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(guardOwnerKey, executor.appliedManagedGoals.customGoals(GoalSelectorType.TARGET).get(0).key());

        GoalOperationResult removeVanillaResult = attachedEntity.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.FLOAT
        );
        GoalOperationResult removeCustomResult = attachedEntity.goalManager().removeCustom(
                GoalSelectorType.TARGET,
                guardOwnerKey
        );

        assertEquals(1, removeVanillaResult.removedEntries());
        assertEquals(1, removeCustomResult.removedEntries());
        assertTrue(attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(attachedEntity.goalManager().managedGoals().customGoals(GoalSelectorType.TARGET).isEmpty());

        attachedEntity.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(2, executor.executions);
        assertEquals(2, executor.lastBatch.mutations().size());
        assertTrue(executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(executor.appliedManagedGoals.customGoals(GoalSelectorType.TARGET).isEmpty());
    }

    @Test
    void shouldReplaceDuplicateManagedEntriesAndReturnZeroWhenRemovingAgain() {
        RecordingGoalExecutor executor = new RecordingGoalExecutor(GoalProfile.<Zombie>builder(Zombie.class).build());
        RuntimeAttachedEntityLifecycle<Zombie> attachedEntity = createAttachedEntity(executor);

        GoalOperationResult firstAddResult = attachedEntity.goalManager().addVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER,
                2
        );
        GoalOperationResult secondAddResult = attachedEntity.goalManager().addVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER,
                7
        );

        assertFalse(firstAddResult.replacedExistingEntry());
        assertTrue(secondAddResult.replacedExistingEntry());
        assertEquals(1, attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(7, attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());

        attachedEntity.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(1, executor.executions);
        assertEquals(7, executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());

        GoalOperationResult firstRemoveResult = attachedEntity.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );
        GoalOperationResult secondRemoveResult = attachedEntity.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );

        assertEquals(1, firstRemoveResult.removedEntries());
        assertEquals(0, secondRemoveResult.removedEntries());
        assertTrue(attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(7, executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());

        attachedEntity.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(2, executor.executions);
        assertTrue(executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
    }

    @Test
    void shouldUpdateManagedSnapshotImmediatelyButApplyAttachedMutationsOnNextSafeTick() {
        RecordingGoalExecutor executor = new RecordingGoalExecutor(
                GoalProfile.<Zombie>builder(Zombie.class)
                        .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.LOOK_AT_PLAYER, 6)
                        .build()
        );
        RuntimeAttachedEntityLifecycle<Zombie> attachedEntity = createAttachedEntity(executor);

        GoalOperationResult removeResult = attachedEntity.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );
        GoalOperationResult addResult = attachedEntity.goalManager().addCustom(
                GoalSelectorType.TARGET,
                CustomGoalKey.of("test", "guard-owner"),
                2
        );

        assertEquals(1, removeResult.removedEntries());
        assertFalse(addResult.replacedExistingEntry());
        assertTrue(attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(1, attachedEntity.goalManager().managedGoals().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(0, executor.executions);
        assertEquals(1, executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(VanillaGoalKey.LOOK_AT_PLAYER, executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertTrue(executor.appliedManagedGoals.customGoals(GoalSelectorType.TARGET).isEmpty());

        attachedEntity.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(1, executor.executions);
        assertEquals(2, executor.lastBatch.mutations().size());
        assertTrue(executor.appliedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(1, executor.appliedManagedGoals.customGoals(GoalSelectorType.TARGET).size());
    }

    private static RuntimeAttachedEntityLifecycle<Zombie> createAttachedEntity(RecordingGoalExecutor executor) {
        RuntimeAttachedEntityLifecycle<Zombie> attachedEntity = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 21, 11),
                new EntityController<Zombie>() {
                },
                tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver.noop(),
                tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver.noop(),
                executor
        );
        attachedEntity.bindHookBinder(new TickOnlyHookBinder<Zombie>());
        return attachedEntity;
    }

    private static void assertEmptyManagedContract(GoalManager<Zombie> goalManager) {
        assertNotNull(goalManager);
        assertTrue(goalManager.managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(goalManager.managedGoals().vanillaGoals(GoalSelectorType.TARGET).isEmpty());
        assertTrue(goalManager.managedGoals().customGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(goalManager.managedGoals().customGoals(GoalSelectorType.TARGET).isEmpty());
    }

    private static void assertManagedTemplateGoals(GoalManager<Zombie> goalManager) {
        assertNotNull(goalManager);
        assertSame(Zombie.class, goalManager.managedGoals().entityType());
        assertEquals(1, goalManager.managedGoals().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(VanillaGoalKey.FLOAT, goalManager.managedGoals().vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
    }

    private static final class RecordingGoalExecutor implements RuntimeGoalMutationExecutor<Zombie> {
        private final GoalProfile<Zombie> initialManagedGoals;
        private GoalProfile<Zombie> appliedManagedGoals;
        private int executions;
        private RuntimeGoalMutationBatch<Zombie> lastBatch;

        private RecordingGoalExecutor(GoalProfile<Zombie> initialManagedGoals) {
            this.initialManagedGoals = initialManagedGoals;
            this.appliedManagedGoals = initialManagedGoals;
        }

        @Override
        public GoalProfile<Zombie> initialManagedGoals() {
            return initialManagedGoals;
        }

        @Override
        public void execute(RuntimeGoalMutationBatch<Zombie> batch) {
            this.executions++;
            this.lastBatch = batch;
            this.appliedManagedGoals = batch.managedGoals();
        }
    }

    private static final class RecordingNativeEntity implements LifecycleAwareNativeEntity {
        private Object lastBaseResult;

        @Override
        public void spigotBootBindLifecycle(tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle<?> lifecycle) {
        }

        @Override
        public tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return null;
        }

        @Override
        public Object spigotBootInvokeBase(String hookName, Object[] arguments) {
            this.lastBaseResult = hookName;
            return null;
        }
    }

    private static final class TickOnlyHookBinder<T extends org.bukkit.entity.Entity>
            implements NativeHookBinder<T> {

        @Override
        public Collection<GeneratedNativeHookSpec> hookSpecs(Class<?> nativeType) {
            return Collections.emptyList();
        }

        @Override
        public Object dispatch(
                AbstractRuntimeControlledEntity<T> lifecycle,
                LifecycleAwareNativeEntity nativeEntity,
                String hookName,
                Object[] arguments
        ) {
            if (LogicalEntityHook.TICK.methodName().equals(hookName)) {
                lifecycle.dispatchTick(new ContextualBaseInvoker<tech.guilhermekaua.spigotboot.versions.api.EntityTickContext<T>, Void>() {
                    @Override
                    public Void invoke(tech.guilhermekaua.spigotboot.versions.api.EntityTickContext<T> context) {
                        nativeEntity.spigotBootInvokeBase(hookName, arguments);
                        return null;
                    }
                });
            }
            return null;
        }
    }
}
