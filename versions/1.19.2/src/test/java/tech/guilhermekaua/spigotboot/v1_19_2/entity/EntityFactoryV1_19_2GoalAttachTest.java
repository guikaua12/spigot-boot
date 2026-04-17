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
package tech.guilhermekaua.spigotboot.v1_19_2.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EntityFactoryV1_19_2GoalAttachTest {

    @Test
    void spawnGoalSupport_shouldApplyBuilderProfileThroughTheSharedManagedSnapshot() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "modern-goal-builder-1192"),
                        CustomEntityBaseType.ZOMBIE,
                        Zombie.class
                )
                .controller(context -> new EntityController<Zombie>() {
                })
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 1)
                .addCustomGoal(GoalSelectorType.NORMAL, CustomGoalKey.of("test", "follow-owner"), 4)
                .build();

        RuntimeGoalMutationExecutor<Zombie> executor = factory.createSpawnGoalMutationExecutor(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 19, 2)
        );
        ModernZombieHandle handle = new ModernZombieHandle();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getHandle()).thenReturn(handle);

        invokeApplyManagedGoalSnapshot(handle, executor.initialManagedGoals());

        GoalProfile<HandleAwareZombie> snapshot = factory.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                entity,
                MinecraftVersion.of(1, 19, 2)
        ).initialManagedGoals();

        assertEquals(VanillaGoalKey.FLOAT, snapshot.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, snapshot.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
        assertEquals(CustomGoalKey.of("test", "follow-owner"), snapshot.customGoals(GoalSelectorType.NORMAL).get(0).key());
    }

    @Test
    void attachedGoalMutations_shouldRemoveStableKeysPreserveUnknownEntriesAndRoundTripCustomGoals() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        ModernZombieHandle handle = new ModernZombieHandle();
        WrappedGoalHandle normalUnknown = handle.goalSelector.addGoal(8, new UnknownGoal());
        WrappedGoalHandle targetUnknown = handle.targetSelector.addGoal(9, new UnknownGoal());
        handle.goalSelector.addGoal(3, new LookAtPlayerGoal());
        handle.targetSelector.addGoal(1, new HurtByTargetGoal());

        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getHandle()).thenReturn(handle);

        RuntimeAttachedEntityLifecycle<HandleAwareZombie> lifecycle = new RuntimeAttachedEntityLifecycle<HandleAwareZombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 19, 2),
                new EntityController<HandleAwareZombie>() {
                },
                tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver.noop(),
                tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver.noop(),
                factory.createAttachedGoalMutationExecutor(CustomEntityBaseType.ZOMBIE, entity, MinecraftVersion.of(1, 19, 2))
        );
        lifecycle.bind(entity);
        lifecycle.bindHookBinder(new TickOnlyHookBinder<HandleAwareZombie>());

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

        GoalProfile<HandleAwareZombie> afterFirstFlush = factory.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                entity,
                MinecraftVersion.of(1, 19, 2)
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

        GoalProfile<HandleAwareZombie> afterSecondFlush = factory.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                entity,
                MinecraftVersion.of(1, 19, 2)
        ).initialManagedGoals();
        assertTrue(afterSecondFlush.customGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(handle.goalSelector.availableGoals.contains(normalUnknown));
        assertTrue(handle.targetSelector.availableGoals.contains(targetUnknown));
        assertEquals(1, handle.goalSelector.availableGoals.size());
        assertEquals(1, handle.targetSelector.availableGoals.size());
        assertFalse(handle.goalSelector.containsGoalType(LookAtPlayerGoal.class));
        assertFalse(handle.targetSelector.containsGoalType(HurtByTargetGoal.class));
    }

    @Test
    void createAttachedGoalMutationExecutor_shouldRejectPreservedCowManagedSnapshot() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        HandleAwareCow entity = Mockito.mock(HandleAwareCow.class);

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> factory.createAttachedGoalMutationExecutor(
                        CustomEntityBaseType.COW,
                        entity,
                        MinecraftVersion.of(1, 19, 2)
                )
        );

        assertEquals(
                "Minecraft 1.19.2-1.20.6 does not support spawn and attach for base type 'COW'.",
                exception.getMessage()
        );
    }

    @Test
    void attach_shouldRejectPreservedCowBeforeReplacementHandling() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        Cow entity = Mockito.mock(Cow.class);
        @SuppressWarnings("unchecked")
        NativeEntityLifecycle<Cow> lifecycle = Mockito.mock(NativeEntityLifecycle.class);

        when(entity.getType()).thenReturn(org.bukkit.entity.EntityType.COW);

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> factory.attach(entity, lifecycle)
        );

        assertEquals(
                "Minecraft 1.19.2-1.20.6 does not support spawn and attach for base type 'COW'.",
                exception.getMessage()
        );
    }

    private static void invokeApplyManagedGoalSnapshot(@NotNull Object nativeHandle, @NotNull GoalProfile<?> managedGoals) {
        try {
            Method method = EntityFactoryV1_19_2.class.getDeclaredMethod(
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

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private interface HandleAwareCow extends Cow {
        Object getHandle();
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
