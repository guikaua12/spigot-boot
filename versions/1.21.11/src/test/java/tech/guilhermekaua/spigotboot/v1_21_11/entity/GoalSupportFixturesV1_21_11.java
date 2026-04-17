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
package tech.guilhermekaua.spigotboot.v1_21_11.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

final class GoalSupportFixturesV1_21_11 {
    private GoalSupportFixturesV1_21_11() {
    }

    static HandleAwareZombie createEntity(@NotNull StubMobHandle handle) {
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.isValid()).thenReturn(true);
        when(entity.getHandle()).thenReturn(handle);
        return entity;
    }

    static @NotNull TickOnlyHookBinder<HandleAwareZombie> tickOnlyHookBinder() {
        return new TickOnlyHookBinder<HandleAwareZombie>();
    }

    interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    static final class StubMobHandle implements LifecycleAwareNativeEntity {
        final StubSelector goalSelector = new StubSelector();
        final StubSelector targetSelector = new StubSelector();
        private NativeEntityLifecycle<?> lifecycle;

        StubWrappedGoal addGoal(@NotNull GoalSelectorType selectorType, int priority, @NotNull Object goal) {
            return selector(selectorType).addGoal(priority, goal);
        }

        boolean containsGoalType(@NotNull GoalSelectorType selectorType, @NotNull Class<?> goalType) {
            return selector(selectorType).containsGoalType(goalType);
        }

        boolean containsUnknownEntry(@NotNull GoalSelectorType selectorType, @NotNull StubWrappedGoal entry) {
            return selector(selectorType).availableGoals.contains(entry);
        }

        int size(@NotNull GoalSelectorType selectorType) {
            return selector(selectorType).availableGoals.size();
        }

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

        private @NotNull StubSelector selector(@NotNull GoalSelectorType selectorType) {
            return selectorType == GoalSelectorType.NORMAL ? goalSelector : targetSelector;
        }
    }

    static final class StubSelector {
        final List<Object> availableGoals = new ArrayList<Object>();

        StubWrappedGoal addGoal(int priority, @NotNull Object goal) {
            StubWrappedGoal wrappedGoal = new StubWrappedGoal(priority, goal);
            availableGoals.add(wrappedGoal);
            return wrappedGoal;
        }

        boolean containsGoalType(@NotNull Class<?> goalType) {
            for (Object entry : availableGoals) {
                if (entry instanceof StubWrappedGoal && goalType.isInstance(((StubWrappedGoal) entry).getGoal())) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class StubWrappedGoal {
        private final int priority;
        private final Object goal;

        private StubWrappedGoal(int priority, @NotNull Object goal) {
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

    static final class LookAtPlayerGoal {
    }

    static final class HurtByTargetGoal {
    }

    static final class WaterAvoidingRandomStrollGoal {
    }

    static final class UnknownGoal {
    }

    static final class ManagedCustomGoalProbe {
        private final CustomGoalKey key;

        ManagedCustomGoalProbe(@NotNull CustomGoalKey key) {
            this.key = key;
        }

        @NotNull CustomGoalKey key() {
            return key;
        }
    }

    static final class TickOnlyHookBinder<T extends Entity> implements NativeHookBinder<T> {
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
            if (!LogicalEntityHook.TICK.methodName().equals(hookName)) {
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

    static boolean containsCustomGoal(@NotNull GoalSupportFixturesV1_21_11.StubMobHandle handle, @NotNull CustomGoalKey key) {
        for (Object entry : handle.goalSelector.availableGoals) {
            if (entry instanceof StubWrappedGoal) {
                Object goal = ((StubWrappedGoal) entry).getGoal();
                if (goal instanceof ManagedCustomGoalProbe && key.equals(((ManagedCustomGoalProbe) goal).key())) {
                    return true;
                }
            }
        }
        for (Object entry : handle.targetSelector.availableGoals) {
            if (entry instanceof StubWrappedGoal) {
                Object goal = ((StubWrappedGoal) entry).getGoal();
                if (goal instanceof ManagedCustomGoalProbe && key.equals(((ManagedCustomGoalProbe) goal).key())) {
                    return true;
                }
            }
        }
        return false;
    }

    static @Nullable CustomGoalKey extractCustomKey(@NotNull Object entry) {
        if (entry instanceof StubWrappedGoal) {
            Object goal = ((StubWrappedGoal) entry).getGoal();
            try {
                java.lang.reflect.Method method = goal.getClass().getDeclaredMethod("key");
                method.setAccessible(true);
                Object key = method.invoke(goal);
                if (key instanceof CustomGoalKey) {
                    return (CustomGoalKey) key;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    static @Nullable VanillaGoalKey extractVanillaKey(@NotNull Object entry) {
        if (entry instanceof StubWrappedGoal) {
            Object goal = ((StubWrappedGoal) entry).getGoal();
            try {
                java.lang.reflect.Method method = goal.getClass().getDeclaredMethod("key");
                method.setAccessible(true);
                Object key = method.invoke(goal);
                if (key instanceof VanillaGoalKey) {
                    return (VanillaGoalKey) key;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
