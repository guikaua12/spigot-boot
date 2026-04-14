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
package tech.guilhermekaua.spigotboot.entity.v1_17_1;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFactoryV1_17_1Test {

    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_17_1.entityCapabilities());
        assertNotNull(EntityFactoryV1_17_1.entityBindings());
    }

    @Test
    void shouldAdvertiseZombieOnlySupportThroughMetadataRegistry() {
        EntityFactoryV1_17_1 factory = new EntityFactoryV1_17_1();

        assertTrue(factory.supports(CustomEntityBaseType.ZOMBIE));
        assertFalse(factory.supports(CustomEntityBaseType.COW));
    }

    @Test
    void instantiateNativeEntity_shouldPreferCoordinateConstructorWhenAvailable() {
        StubLevel level = new StubLevel();
        Location location = new Location(null, 1.25D, 2.5D, 3.75D, 0.0F, 0.0F);

        CoordinateConstructorEntity entity = assertDoesNotThrow(() -> (CoordinateConstructorEntity) invokeStatic(
                "instantiateNativeEntity",
                new Class<?>[]{Class.class, Object.class, Object.class, Location.class},
                CoordinateConstructorEntity.class,
                new StubEntityType(),
                level,
                location
        ));

        assertSame(level, entity.level);
        assertEquals(1.25D, entity.spawnX, 0.0D);
        assertEquals(2.5D, entity.spawnY, 0.0D);
        assertEquals(3.75D, entity.spawnZ, 0.0D);
    }

    @Test
    void applySpawnLocation_shouldFallbackToSetPosAndRotationsWhenMoveToIsUnavailable() {
        RotationOnlyEntity entity = new RotationOnlyEntity(new StubLevel());
        Location location = new Location(null, 4.0D, 5.0D, 6.0D, 90.0F, 45.0F);

        assertDoesNotThrow(() -> invokeStatic(
                "applySpawnLocation",
                new Class<?>[]{Object.class, Location.class},
                entity,
                location
        ));

        assertEquals(4.0D, entity.x, 0.0D);
        assertEquals(5.0D, entity.y, 0.0D);
        assertEquals(6.0D, entity.z, 0.0D);
        assertEquals(90.0F, entity.yRot, 0.0F);
        assertEquals(45.0F, entity.xRot, 0.0F);
    }

    @Test
    void resolveTrackingHandles_shouldExposeTrackedEntityStateWhenTrackerMapContainsHandle() {
        EntityFactoryV1_17_1 factory = new EntityFactoryV1_17_1();
        Object trackedEntity = new StubTrackedEntity("server-state");
        StubPlayerChunkMap playerChunkMap = new StubPlayerChunkMap();
        playerChunkMap.trackedEntities.put(Integer.valueOf(27), trackedEntity);

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(
                new StubTrackedEntityCarrier(new StubLevel(new StubChunkProvider(playerChunkMap)), 27)
        );

        assertSame(trackedEntity, trackingHandles.trackerEntryHandle());
        assertEquals("server-state", trackingHandles.trackerStateHandle());
    }

    @Test
    void resolveTrackingHandles_shouldFallbackToUntrackedStateWhenTrackerIsMissing() {
        EntityFactoryV1_17_1 factory = new EntityFactoryV1_17_1();

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(
                new StubTrackedEntityCarrier(new StubLevel(new StubChunkProvider(new StubPlayerChunkMap())), 11)
        );

        assertNull(trackingHandles.trackerEntryHandle());
        assertNull(trackingHandles.trackerStateHandle());
    }

    @Test
    void hookBinder_shouldGenerateLifecycleAwareSubclassForSupportedNativeType() throws ReflectiveOperationException {
        EntityHookBinderV1_17_1 hookBinder = new EntityHookBinderV1_17_1();
        GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();
        Class<?> generatedType = classFactory.createSubclass(
                HookableEntity.class,
                "test_1_17_1_hookable_" + System.nanoTime(),
                hookBinder.hookSpecs(HookableEntity.class)
        );
        Object generatedEntity = generatedType.getDeclaredConstructor().newInstance();

        classFactory.installInterceptor(generatedEntity, hookBinder.hookSpecs(HookableEntity.class));
        ((HookableEntity) generatedEntity).tick();

        assertTrue(LifecycleAwareNativeEntity.class.isAssignableFrom(generatedType));
        assertEquals(1, ((HookableEntity) generatedEntity).tickCount());
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = EntityFactoryV1_17_1.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class StubLevel {
        private final StubChunkProvider chunkProvider;

        private StubLevel() {
            this(new StubChunkProvider(new StubPlayerChunkMap()));
        }

        private StubLevel(StubChunkProvider chunkProvider) {
            this.chunkProvider = chunkProvider;
        }

        public StubChunkProvider getChunkProvider() {
            return chunkProvider;
        }
    }

    private static final class StubChunkProvider {
        private final StubPlayerChunkMap playerChunkMap;

        private StubChunkProvider(StubPlayerChunkMap playerChunkMap) {
            this.playerChunkMap = playerChunkMap;
        }
    }

    private static final class StubPlayerChunkMap {
        private final Map<Integer, Object> trackedEntities = new LinkedHashMap<Integer, Object>();
    }

    private static final class StubEntityType {
    }

    private static final class CoordinateConstructorEntity {
        private final StubLevel level;
        private final double spawnX;
        private final double spawnY;
        private final double spawnZ;

        private CoordinateConstructorEntity(StubLevel level, double spawnX, double spawnY, double spawnZ) {
            this.level = level;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
        }
    }

    private static final class RotationOnlyEntity {
        private final StubLevel level;
        private double x;
        private double y;
        private double z;
        private float yRot;
        private float xRot;

        private RotationOnlyEntity(StubLevel level) {
            this.level = level;
        }

        public void setPos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void setYRot(float yRot) {
            this.yRot = yRot;
        }

        public void setXRot(float xRot) {
            this.xRot = xRot;
        }
    }

    private static final class StubTrackedEntityCarrier {
        private final StubLevel level;
        private final int id;

        private StubTrackedEntityCarrier(StubLevel level, int id) {
            this.level = level;
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    private static final class StubTrackedEntity {
        private final Object serverEntity;

        private StubTrackedEntity(Object serverEntity) {
            this.serverEntity = serverEntity;
        }
    }

    public static class HookableEntity {
        private int tickCount;

        public void tick() {
            tickCount++;
        }

        public int tickCount() {
            return tickCount;
        }
    }
}
