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
package tech.guilhermekaua.spigotboot.v1_17_1.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EntityFactoryV1_17_1GoalAttachTest {

    @Test
    void spawnGoalSupport_shouldApplyBuilderProfileThroughTheSharedManagedSnapshot() {
        assertSpawnGoalSupportRoundTrip(
                CustomEntityBaseType.ZOMBIE,
                Zombie.class,
                Mockito.mock(HandleAwareZombie.class),
                "modern-goal-builder-1171-zombie"
        );
    }

    @Test
    void spawnGoalSupport_shouldApplyBuilderProfileThroughTheSharedManagedSnapshotForSupportedSkeleton() {
        assertSpawnGoalSupportRoundTrip(
                CustomEntityBaseType.SKELETON,
                Skeleton.class,
                Mockito.mock(HandleAwareSkeleton.class),
                "modern-goal-builder-1171-skeleton"
        );
    }

    @Test
    void attachedGoalMutations_shouldRemoveStableKeysPreserveUnknownEntriesAndRoundTripCustomGoals() {
        assertAttachedGoalMutationsRoundTrip(CustomEntityBaseType.ZOMBIE, Mockito.mock(HandleAwareZombie.class));
    }

    @Test
    void attachedGoalMutations_shouldRemoveStableKeysPreserveUnknownEntriesAndRoundTripCustomGoalsForSupportedSkeleton() {
        assertAttachedGoalMutationsRoundTrip(CustomEntityBaseType.SKELETON, Mockito.mock(HandleAwareSkeleton.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertSpawnGoalSupportRoundTrip(
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<? extends Entity> bukkitType,
            @NotNull HandleAwareEntity entity,
            @NotNull String entityId
    ) {
        EntityFactoryV1_17_1 factory = new EntityFactoryV1_17_1();
        EntityTemplate template = EntityTemplate.builder(
                        CustomEntityId.of("test", entityId),
                        baseType,
                        bukkitType
                )
                .controller(context -> new EntityController() {
                })
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 1)
                .addCustomGoal(GoalSelectorType.NORMAL, CustomGoalKey.of("test", "follow-owner"), 4)
                .build();

        RuntimeGoalMutationExecutor<?> executor = factory.createSpawnGoalMutationExecutor(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 17, 1)
        );
        ModernZombieHandle handle = new ModernZombieHandle();
        when(entity.getHandle()).thenReturn(handle);

        invokeApplyManagedGoalSnapshot(handle, executor.initialManagedGoals());

        GoalProfile<?> snapshot = factory.createAttachedGoalMutationExecutor(
                baseType,
                (Entity) entity,
                MinecraftVersion.of(1, 17, 1)
        ).initialManagedGoals();

        assertEquals(VanillaGoalKey.FLOAT, snapshot.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, snapshot.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
        assertEquals(CustomGoalKey.of("test", "follow-owner"), snapshot.customGoals(GoalSelectorType.NORMAL).get(0).key());
    }

    private <T extends Entity & HandleAwareEntity> void assertAttachedGoalMutationsRoundTrip(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity
    ) {
        EntityFactoryV1_17_1 factory = new EntityFactoryV1_17_1();
        ModernZombieHandle handle = new ModernZombieHandle();
        WrappedGoalHandle normalUnknown = handle.goalSelector.addGoal(8, new UnknownGoal());
        WrappedGoalHandle targetUnknown = handle.targetSelector.addGoal(9, new UnknownGoal());
        handle.goalSelector.addGoal(3, new LookAtPlayerGoal());
        handle.targetSelector.addGoal(1, new HurtByTargetGoal());

        when(entity.getHandle()).thenReturn(handle);

        RuntimeAttachedEntityLifecycle<T> lifecycle = new RuntimeAttachedEntityLifecycle<T>(
                baseType,
                MinecraftVersion.of(1, 17, 1),
                new EntityController<T>() {
                },
                tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver.noop(),
                tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver.noop(),
                factory.createAttachedGoalMutationExecutor(baseType, entity, MinecraftVersion.of(1, 17, 1))
        );
        lifecycle.bind(entity);
        lifecycle.bindHookBinder(new TickOnlyHookBinder<T>());

        assertEquals(1, lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(1, lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.TARGET).size());

        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "guard-owner");
        lifecycle.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.LOOK_AT_PLAYER);
        lifecycle.goalManager().removeVanilla(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET);
        lifecycle.goalManager().addCustom(GoalSelectorType.NORMAL, customGoalKey, 5);

        assertTrue(lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.TARGET).isEmpty());

        assertEquals(2, handle.goalSelector.availableGoals.size());
        assertEquals(2, handle.targetSelector.availableGoals.size());

        invokeApplyManagedGoalSnapshot(handle, lifecycle.goalManager().managedGoals());

        assertFalse(handle.goalSelector.containsGoalType(LookAtPlayerGoal.class));
        assertFalse(handle.targetSelector.containsGoalType(HurtByTargetGoal.class));

        GoalProfile<?> afterFirstFlush = factory.createAttachedGoalMutationExecutor(
                baseType,
                entity,
                MinecraftVersion.of(1, 17, 1)
        ).initialManagedGoals();
        assertTrue(afterFirstFlush.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(afterFirstFlush.vanillaGoals(GoalSelectorType.TARGET).isEmpty());
        assertEquals(customGoalKey, afterFirstFlush.customGoals(GoalSelectorType.NORMAL).get(0).key());
        assertTrue(handle.goalSelector.availableGoals.contains(normalUnknown));
        assertTrue(handle.targetSelector.availableGoals.contains(targetUnknown));
        assertEquals(2, handle.goalSelector.availableGoals.size());
        assertEquals(1, handle.targetSelector.availableGoals.size());

        lifecycle.goalManager().removeCustom(GoalSelectorType.NORMAL, customGoalKey);
        invokeApplyManagedGoalSnapshot(handle, lifecycle.goalManager().managedGoals());

        GoalProfile<?> afterSecondFlush = factory.createAttachedGoalMutationExecutor(
                baseType,
                entity,
                MinecraftVersion.of(1, 17, 1)
        ).initialManagedGoals();
        assertTrue(afterSecondFlush.customGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(handle.goalSelector.availableGoals.contains(normalUnknown));
        assertTrue(handle.targetSelector.availableGoals.contains(targetUnknown));
        assertEquals(1, handle.goalSelector.availableGoals.size());
        assertEquals(1, handle.targetSelector.availableGoals.size());
        assertFalse(handle.goalSelector.containsGoalType(LookAtPlayerGoal.class));
        assertFalse(handle.targetSelector.containsGoalType(HurtByTargetGoal.class));
    }

    private static void invokeApplyManagedGoalSnapshot(@NotNull Object nativeHandle, @NotNull GoalProfile<?> managedGoals) {
        try {
            Method method = EntityFactoryV1_17_1.class.getDeclaredMethod(
                    "applyManagedGoalSnapshot",
                    Object.class,
                    GoalProfile.class
            );
            method.setAccessible(true);
            method.invoke(null, nativeHandle, managedGoals);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private interface HandleAwareEntity {
        Object getHandle();
    }

    private interface HandleAwareZombie extends Zombie, HandleAwareEntity {
    }

    private interface HandleAwareSkeleton extends Skeleton, HandleAwareEntity {
    }

    private static final class ModernZombieHandle implements LifecycleAwareNativeEntity {
        private final SelectorHandle goalSelector = new SelectorHandle();
        private final SelectorHandle targetSelector = new SelectorHandle();
        private NativeEntityLifecycle<?> lifecycle;

        @Override
        public void spigotBootBindLifecycle(@NotNull NativeEntityLifecycle<?> lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public @Nullable NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return lifecycle;
        }

        @Override
        public @Nullable Object spigotBootInvokeBase(@NotNull String hookName, @Nullable Object[] arguments) {
            return null;
        }
    }

    private static final class SelectorHandle {
        private final List<Object> availableGoals = new ArrayList<Object>();

        private WrappedGoalHandle addGoal(int priority, @NotNull Object goal) {
            WrappedGoalHandle wrappedGoal = new WrappedGoalHandle(priority, goal);
            availableGoals.add(wrappedGoal);
            return wrappedGoal;
        }

        private boolean containsGoalType(@NotNull Class<?> goalType) {
            for (Object entry : availableGoals) {
                if (entry instanceof WrappedGoalHandle && goalType.isInstance(((WrappedGoalHandle) entry).getGoal())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class WrappedGoalHandle {
        private final int priority;
        private final Object goal;

        private WrappedGoalHandle(int priority, @NotNull Object goal) {
            this.priority = priority;
            this.goal = goal;
        }

        public int getPriority() {
            return priority;
        }

        public @NotNull Object getGoal() {
            return goal;
        }
    }

    private static final class LookAtPlayerGoal {
    }

    private static final class HurtByTargetGoal {
    }

    private static final class UnknownGoal {
    }

    private static final class TickOnlyHookBinder<T extends Entity> implements NativeHookBinder<T> {
        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            return Collections.emptyList();
        }

        @Override
        public @Nullable Object dispatch(
                @NotNull AbstractRuntimeControlledEntity<T> controlledEntity,
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                @NotNull String hookName,
                @Nullable Object[] arguments
        ) {
            if (!"tick".equals(hookName)) {
                return null;
            }
            controlledEntity.dispatchTick(new ContextualBaseInvoker<EntityTickContext<T>, Void>() {
                @Override
                public Void invoke(@NotNull EntityTickContext<T> context) {
                    nativeEntity.spigotBootInvokeBase(hookName, arguments);
                    return null;
                }
            });
            return null;
        }
    }
}
