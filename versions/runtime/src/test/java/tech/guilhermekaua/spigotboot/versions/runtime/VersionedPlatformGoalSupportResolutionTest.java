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
package tech.guilhermekaua.spigotboot.versions.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityGoalSupportFamily;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class VersionedPlatformGoalSupportResolutionTest {

    @Test
    void shouldResolveGoalSupportFromOptionalAdapterProviderWithoutPublicApiConditionals() {
        GoalSupportAwareAdapter adapter = new GoalSupportAwareAdapter();
        VersionedPlatform platform = new VersionedPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        EntityTemplate<Zombie> template = createTemplate();

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 2.0D, 64.0D, 2.0D))
        );

        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.getHandle()).thenReturn(new Object());
        ControlledEntity<HandleAwareZombie> attachedEntity = platform.get(entity);

        assertSame(EntityGoalSupportFamily.LATEST_1_21_X, platform.goalSupport().family());
        assertTrue(platform.goalSupport().providerAvailable());
        assertEquals(1, adapter.spawnExecutorCreations);
        assertEquals(1, adapter.attachExecutorCreations);
        assertEquals(
                VanillaGoalKey.FLOAT,
                spawnedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).get(0).key()
        );
        assertEquals(
                VanillaGoalKey.LOOK_AT_PLAYER,
                attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).get(0).key()
        );
    }

    @Test
    void shouldFallbackToUnspecifiedBundleAndNoopExecutorsWhenProviderIsAbsent() {
        NoGoalSupportAdapter adapter = new NoGoalSupportAdapter();
        VersionedPlatform platform = new VersionedPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        EntityTemplate<Zombie> template = createTemplate();

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 5.0D, 70.0D, 5.0D))
        );

        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.getHandle()).thenReturn(new Object());
        ControlledEntity<HandleAwareZombie> attachedEntity = platform.get(entity);

        assertSame(EntityGoalSupportFamily.UNSPECIFIED, platform.goalSupport().family());
        assertEquals(0, adapter.spawnExecutorCreations);
        assertEquals(0, adapter.attachExecutorCreations);
        assertTrue(spawnedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(attachedEntity.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
    }

    private static EntityTemplate<Zombie> createTemplate() {
        return EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "goal-support-resolution"),
                        CustomEntityBaseType.ZOMBIE,
                        Zombie.class
                )
                .controller(context -> new EntityController<Zombie>() {
                })
                .build();
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private static class NoGoalSupportAdapter implements VersionAdapter {
        int spawnExecutorCreations;
        int attachExecutorCreations;

        @Override
        public MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            T entity = Mockito.mock(template.bukkitType());
            when(entity.getType()).thenReturn(EntityType.ZOMBIE);
            lifecycle.bind(entity);
            lifecycle.onSpawn();
            return (SpawnedEntity<T>) lifecycle.handle();
        }

        @Override
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            lifecycle.bind(entity);
            return lifecycle.handle();
        }
    }

    private static final class GoalSupportAwareAdapter extends NoGoalSupportAdapter implements VersionGoalSupportProvider {
        private static final VersionGoalSupportMetadata GOAL_SUPPORT_METADATA = new VersionGoalSupportMetadata(
                EnumSet.of(VanillaGoalKey.FLOAT, VanillaGoalKey.LOOK_AT_PLAYER),
                true,
                true,
                true
        );

        @Override
        public VersionGoalSupportMetadata entityGoalSupportMetadata() {
            return GOAL_SUPPORT_METADATA;
        }

        @Override
        public <T extends Entity> RuntimeGoalMutationExecutor<T> createSpawnGoalMutationExecutor(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                MinecraftVersion minecraftVersion
        ) {
            this.spawnExecutorCreations++;
            return RuntimeGoalMutationExecutor.noop(GoalProfile.<T>builder(template.bukkitType())
                    .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                    .build());
        }

        @Override
        public <T extends Entity> RuntimeGoalMutationExecutor<T> createAttachedGoalMutationExecutor(
                CustomEntityBaseType baseType,
                T entity,
                MinecraftVersion minecraftVersion
        ) {
            this.attachExecutorCreations++;
            @SuppressWarnings("unchecked")
            Class<T> entityType = (Class<T>) entity.getClass().asSubclass(Entity.class);
            return RuntimeGoalMutationExecutor.noop(GoalProfile.<T>builder(entityType)
                    .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.LOOK_AT_PLAYER, 4)
                    .build());
        }
    }
}
