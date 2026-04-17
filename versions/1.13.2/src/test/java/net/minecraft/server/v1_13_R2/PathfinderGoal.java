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
package net.minecraft.server.v1_13_R2;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.v1_13_2.entity.SpigotVersionAdapterV1_13_2;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public abstract class PathfinderGoal {
    public PathfinderGoal() {
    }

    public abstract boolean a();

    public boolean b() {
        return false;
    }

    public boolean f() {
        return false;
    }

    public void c() {
    }

    public void d() {
    }

    public void e() {
    }

    public void a(int priority) {
    }

    public int h() {
        return 0;
    }
}

class Entity {
}

class EntityLiving extends Entity {
}

class EntityHuman extends EntityLiving {
}

class EntityInsentient extends EntityLiving {
}

class EntityCreature extends EntityInsentient {
}

class EntityZombie extends EntityCreature {
    final PathfinderGoalSelector goalSelector = new PathfinderGoalSelector();
    final PathfinderGoalSelector targetSelector = new PathfinderGoalSelector();
}

class PathfinderGoalSelector {
    final Set<PathfinderGoalSelectorItem> b = new LinkedHashSet<PathfinderGoalSelectorItem>();
    final Set<PathfinderGoalSelectorItem> c = new LinkedHashSet<PathfinderGoalSelectorItem>();

    public void a(int priority, PathfinderGoal goal) {
        b.add(new PathfinderGoalSelectorItem(priority, goal));
    }

    public void a(PathfinderGoal goal) {
        remove(goal, b);
        remove(goal, c);
    }

    private void remove(PathfinderGoal goal, Set<PathfinderGoalSelectorItem> entries) {
        Iterator<PathfinderGoalSelectorItem> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().a == goal) {
                iterator.remove();
            }
        }
    }
}

class PathfinderGoalFloat extends PathfinderGoal {
    PathfinderGoalFloat(EntityInsentient entity) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalMeleeAttack extends PathfinderGoal {
    PathfinderGoalMeleeAttack(EntityCreature entity, double speed, boolean pauseWhenMobIdle) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalRandomStrollLand extends PathfinderGoal {
    PathfinderGoalRandomStrollLand(EntityCreature entity, double speed) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalLookAtPlayer extends PathfinderGoal {
    PathfinderGoalLookAtPlayer(EntityInsentient entity, Class<? extends Entity> targetType, float distance) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalRandomLookaround extends PathfinderGoal {
    PathfinderGoalRandomLookaround(EntityInsentient entity) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalHurtByTarget extends PathfinderGoal {
    PathfinderGoalHurtByTarget(EntityCreature entity, boolean alertsAllies, Class<?>... ignoredReinforcements) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalNearestAttackableTarget<T extends EntityLiving> extends PathfinderGoal {
    PathfinderGoalNearestAttackableTarget(EntityCreature entity, Class<T> targetType, boolean mustSee) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalBreakDoor extends PathfinderGoal {
    PathfinderGoalBreakDoor(EntityInsentient entity) {
    }

    @Override
    public boolean a() {
        return false;
    }
}

class PathfinderGoalSelectorItem {
    final PathfinderGoal a;
    final int b;
    boolean c;

    PathfinderGoalSelectorItem(int priority, PathfinderGoal goal) {
        this.a = goal;
        this.b = priority;
    }
}

class LegacyGoalAttachV1_13_2Test {

    @Test
    void spawnedGoalManagerShouldAddAndRemoveLegacyVanillaAndCustomGoalsThroughProviderExecutor() {
        SpigotVersionAdapterV1_13_2 adapter = new SpigotVersionAdapterV1_13_2();
        VersionGoalSupportProvider provider = assertInstanceOf(VersionGoalSupportProvider.class, adapter);
        HandleAwareZombie entity = bukkitZombie(new EntityZombie());
        SpawnOptions spawnOptions = SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D));
        EntityTemplate<HandleAwareZombie> template = EntityTemplate.<HandleAwareZombie>builder(
                        CustomEntityBaseType.ZOMBIE,
                        HandleAwareZombie.class
                )
                .controller(context -> new EntityController<HandleAwareZombie>() {
                })
                .build();
        RuntimeGoalMutationExecutor<HandleAwareZombie> executor = provider.createSpawnGoalMutationExecutor(
                template,
                spawnOptions,
                MinecraftVersion.of(1, 13, 2)
        );
        RuntimeNativeEntityLifecycle<HandleAwareZombie> lifecycle = new RuntimeNativeEntityLifecycle<HandleAwareZombie>(
                template,
                spawnOptions,
                MinecraftVersion.of(1, 13, 2),
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.noop(),
                executor
        );
        lifecycle.bind(entity);
        lifecycle.bindHookBinder(new TickOnlyHookBinder<HandleAwareZombie>());

        lifecycle.goalManager().addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0);
        lifecycle.goalManager().addCustom(GoalSelectorType.TARGET, CustomGoalKey.of("test", "guard-owner"), 2);

        assertTrue(entity.getHandle().goalSelector.b.isEmpty());
        assertTrue(entity.getHandle().targetSelector.b.isEmpty());

        lifecycle.onNativeHook(LogicalEntityHook.TICK.methodName(), new RecordingNativeEntity(), new Object[0]);

        assertEquals(1, entity.getHandle().goalSelector.b.size());
        assertEquals(PathfinderGoalFloat.class, entity.getHandle().goalSelector.b.iterator().next().a.getClass());
        assertEquals(1, entity.getHandle().targetSelector.b.size());
        assertEquals("test:guard-owner", customGoalKey(entity.getHandle().targetSelector.b.iterator().next().a));

        lifecycle.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT);
        lifecycle.goalManager().removeCustom(GoalSelectorType.TARGET, CustomGoalKey.of("test", "guard-owner"));

        lifecycle.onNativeHook(LogicalEntityHook.TICK.methodName(), new RecordingNativeEntity(), new Object[0]);

        assertTrue(entity.getHandle().goalSelector.b.isEmpty());
        assertTrue(entity.getHandle().targetSelector.b.isEmpty());
    }

    @Test
    void attachedGoalManagerShouldSnapshotRecognizedLegacyGoalsAndPreserveUnmanagedEntries() {
        EntityZombie handle = new EntityZombie();
        handle.goalSelector.a(0, new PathfinderGoalFloat(handle));
        handle.goalSelector.a(3, new PathfinderGoalBreakDoor(handle));
        handle.targetSelector.a(1, new PathfinderGoalHurtByTarget(handle, true, new Class[0]));

        SpigotVersionAdapterV1_13_2 adapter = new SpigotVersionAdapterV1_13_2();
        VersionGoalSupportProvider provider = assertInstanceOf(VersionGoalSupportProvider.class, adapter);
        HandleAwareZombie entity = bukkitZombie(handle);
        RuntimeAttachedEntityLifecycle<HandleAwareZombie> lifecycle = new RuntimeAttachedEntityLifecycle<HandleAwareZombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 13, 2),
                new EntityController<HandleAwareZombie>() {
                },
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.noop(),
                provider.createAttachedGoalMutationExecutor(
                        CustomEntityBaseType.ZOMBIE,
                        entity,
                        MinecraftVersion.of(1, 13, 2)
                )
        );
        lifecycle.bind(entity);
        lifecycle.bindHookBinder(new TickOnlyHookBinder<HandleAwareZombie>());

        assertEquals(
                VanillaGoalKey.FLOAT,
                lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).get(0).key()
        );
        assertEquals(
                VanillaGoalKey.HURT_BY_TARGET,
                lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.TARGET).get(0).key()
        );

        lifecycle.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT);

        assertEquals(2, entity.getHandle().goalSelector.b.size());

        lifecycle.onNativeHook(LogicalEntityHook.TICK.methodName(), new RecordingNativeEntity(), new Object[0]);

        assertEquals(1, entity.getHandle().goalSelector.b.size());
        assertEquals(PathfinderGoalBreakDoor.class, entity.getHandle().goalSelector.b.iterator().next().a.getClass());
        assertEquals(1, entity.getHandle().targetSelector.b.size());
    }

    private static HandleAwareZombie bukkitZombie(EntityZombie handle) {
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getHandle()).thenReturn(handle);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.isValid()).thenReturn(true);
        return entity;
    }

    private static String customGoalKey(PathfinderGoal goal) {
        try {
            Method method = goal.getClass().getDeclaredMethod("spigotBootCustomGoalKey");
            method.setAccessible(true);
            return (String) method.invoke(goal);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private interface HandleAwareZombie extends Zombie {
        EntityZombie getHandle();
    }

    private static final class RecordingNativeEntity implements LifecycleAwareNativeEntity {
        @Override
        public void spigotBootBindLifecycle(NativeEntityLifecycle<?> lifecycle) {
        }

        @Override
        public NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return null;
        }

        @Override
        public Object spigotBootInvokeBase(String hookName, Object[] arguments) {
            return null;
        }
    }

    private static final class TickOnlyHookBinder<T extends org.bukkit.entity.Entity> implements NativeHookBinder<T> {
        @Override
        public Collection<GeneratedNativeHookSpec> hookSpecs(Class<?> nativeType) {
            return new ArrayList<GeneratedNativeHookSpec>();
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
                        return null;
                    }
                });
            }
            return null;
        }
    }
}
