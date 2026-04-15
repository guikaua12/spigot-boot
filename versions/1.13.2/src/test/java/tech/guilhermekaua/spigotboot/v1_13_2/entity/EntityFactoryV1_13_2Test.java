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
package tech.guilhermekaua.spigotboot.v1_13_2.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFactoryV1_13_2Test {

    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_13_2.entityCapabilities());
        assertNotNull(EntityFactoryV1_13_2.entityBindings());
    }

    @Test
    void shouldAdvertiseZombieOnlySupportThroughMetadataRegistry() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();

        assertTrue(factory.supports(CustomEntityBaseType.ZOMBIE));
        assertFalse(factory.supports(CustomEntityBaseType.COW));
    }

    @Test
    void createNativeEntity_shouldGenerateLifecycleAwareBridgeSubclassUsingTheCoordinateConstructor() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        SpawnFixture fixture = prepareFixture(factory);

        Object nativeEntity = factory.createNativeEntity(fixture.preparedSpawn, fixture.location);

        HookBridgeFreshSpawnHandle spawnedEntity = assertInstanceOf(HookBridgeFreshSpawnHandle.class, nativeEntity);
        assertInstanceOf(LifecycleAwareNativeEntity.class, nativeEntity);
        assertSame(fixture.levelHandle, spawnedEntity.world);
        assertEquals(1.25D, spawnedEntity.spawnX, 0.0D);
        assertEquals(2.5D, spawnedEntity.spawnY, 0.0D);
        assertEquals(3.75D, spawnedEntity.spawnZ, 0.0D);
        verify(fixture.probeEntity).remove();
    }

    @Test
    void bindLifecycleToNativeEntity_shouldBindTheGeneratedBridgeAndDispatchTickHooks() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        SpawnFixture fixture = prepareFixture(factory);
        Object nativeEntity = factory.createNativeEntity(fixture.preparedSpawn, fixture.location);
        Zombie bukkitEntity = Mockito.mock(Zombie.class);
        TestRuntimeLifecycle lifecycle = new TestRuntimeLifecycle();
        lifecycle.bind(bukkitEntity);

        factory.bindLifecycleToNativeEntity(nativeEntity, fixture.preparedSpawn, lifecycle);

        LifecycleAwareNativeEntity lifecycleAwareNativeEntity = assertInstanceOf(
                LifecycleAwareNativeEntity.class,
                nativeEntity
        );
        assertSame(lifecycle, lifecycleAwareNativeEntity.spigotBootGetLifecycle());

        ((HookBridgeFreshSpawnHandle) nativeEntity).movementTick();

        assertEquals(1, lifecycle.controllerTickCount());
        assertEquals(1, ((HookBridgeFreshSpawnHandle) nativeEntity).baseTickCount);
    }

    @Test
    void resolveNativeWorldHandle_shouldUseTheBukkitWorldHandleAccessor() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        HandleAwareWorld world = Mockito.mock(HandleAwareWorld.class);
        Object worldHandle = new Object();
        Location location = new Location(world, 0.0D, 64.0D, 0.0D);

        when(world.getHandle()).thenReturn(worldHandle);

        assertSame(worldHandle, factory.resolveNativeWorldHandle(location));
    }

    private static @NotNull SpawnFixture prepareFixture(@NotNull EntityFactoryV1_13_2 factory) {
        HandleAwareWorld world = Mockito.mock(HandleAwareWorld.class);
        HandleAwareZombie probeEntity = Mockito.mock(HandleAwareZombie.class);
        LevelHandle levelHandle = new LevelHandle();
        HookBridgeFreshSpawnHandle probeHandle = new HookBridgeFreshSpawnHandle(levelHandle);
        Location location = new Location(world, 1.25D, 2.5D, 3.75D, 90.0F, 45.0F);
        EntityTemplate<Zombie> template = EntityTemplate.builder(CustomEntityBaseType.ZOMBIE, Zombie.class).build();

        when(world.getHandle()).thenReturn(levelHandle);
        when(world.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE))).thenReturn(probeEntity);
        when(probeEntity.getHandle()).thenReturn(probeHandle);

        SpawnOptions spawnOptions = SpawnOptions.at(location);
        return new SpawnFixture(
                location,
                levelHandle,
                probeEntity,
                factory.prepareFreshSpawn(template, spawnOptions)
        );
    }

    private interface HandleAwareWorld extends World {
        Object getHandle();
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    public static class HookBridgeFreshSpawnHandle {
        private final Object world;
        private final double spawnX;
        private final double spawnY;
        private final double spawnZ;
        private int baseTickCount;

        public HookBridgeFreshSpawnHandle(Object world) {
            this(world, 0.0D, 0.0D, 0.0D);
        }

        public HookBridgeFreshSpawnHandle(Object world, double spawnX, double spawnY, double spawnZ) {
            this.world = world;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
        }

        public void movementTick() {
            baseTickCount++;
        }

        public Entity getBukkitEntity() {
            return null;
        }
    }

    private static final class LevelHandle {
    }

    private static final class SpawnFixture {
        private final Location location;
        private final LevelHandle levelHandle;
        private final HandleAwareZombie probeEntity;
        private final LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn;

        private SpawnFixture(
                @NotNull Location location,
                @NotNull LevelHandle levelHandle,
                @NotNull HandleAwareZombie probeEntity,
                @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn
        ) {
            this.location = location;
            this.levelHandle = levelHandle;
            this.probeEntity = probeEntity;
            this.preparedSpawn = preparedSpawn;
        }
    }

    private static final class TestRuntimeLifecycle extends AbstractRuntimeControlledEntity<Zombie> {
        private final int[] controllerTickCount;

        private TestRuntimeLifecycle() {
            this(new int[1]);
        }

        private TestRuntimeLifecycle(final int[] controllerTickCount) {
            super(
                    CustomEntityBaseType.ZOMBIE,
                    MinecraftVersion.of(1, 13, 2),
                    new EntityController<Zombie>() {
                        @Override
                        public void onTick(@NotNull EntityTickContext<Zombie> context) {
                            controllerTickCount[0]++;
                            context.base().invoke();
                        }
                    }
            );
            this.controllerTickCount = controllerTickCount;
        }

        private int controllerTickCount() {
            return controllerTickCount[0];
        }
    }
}
