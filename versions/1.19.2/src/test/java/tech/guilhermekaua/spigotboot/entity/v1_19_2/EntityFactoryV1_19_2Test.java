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
package tech.guilhermekaua.spigotboot.entity.v1_19_2;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFactoryV1_19_2Test {

    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_19_2.entityCapabilities());
        assertNotNull(EntityFactoryV1_19_2.entityBindings());
    }

    @Test
    void shouldAdvertiseZombieSupportWithoutClaimingCowSupport() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();

        assertTrue(factory.supports(CustomEntityBaseType.ZOMBIE));
        assertFalse(factory.supports(CustomEntityBaseType.COW));
    }

    @Test
    void resolveNativeEntityType_shouldFallbackToObfuscatedAccessorWhenGetTypeIsUnavailable() {
        StubEntityType entityType = new StubEntityType();

        Object resolved = assertDoesNotThrow(() -> invokeStatic(
                "resolveNativeEntityType",
                new Class<?>[]{Object.class},
                new ObfuscatedEntityTypeCarrier(entityType)
        ));

        assertSame(entityType, resolved);
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
    void resolveTrackingHandles_shouldExposeTrackedEntityStateWhenAccessorExists() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        Object trackedEntity = new StubTrackedEntity("server-state");

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(
                new StubTrackedEntityCarrier(trackedEntity)
        );

        assertSame(trackedEntity, trackingHandles.trackerEntryHandle());
        assertEquals("server-state", trackingHandles.trackerStateHandle());
    }

    @Test
    void resolveTrackingHandles_shouldFallbackToUntrackedStateWhenAccessorIsMissing() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(new Object());

        assertNull(trackingHandles.trackerEntryHandle());
        assertNull(trackingHandles.trackerStateHandle());
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = EntityFactoryV1_19_2.class.getDeclaredMethod(methodName, parameterTypes);
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
    }

    private static final class StubEntityType {
    }

    private static final class ObfuscatedEntityTypeCarrier {
        private final StubEntityType entityType;

        private ObfuscatedEntityTypeCarrier(StubEntityType entityType) {
            this.entityType = entityType;
        }

        public StubEntityType ad() {
            return entityType;
        }
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
        private final Object trackedEntity;

        private StubTrackedEntityCarrier(Object trackedEntity) {
            this.trackedEntity = trackedEntity;
        }

        public Object moonrise$getTrackedEntity() {
            return trackedEntity;
        }
    }

    private static final class StubTrackedEntity {
        private final Object serverEntity;

        private StubTrackedEntity(Object serverEntity) {
            this.serverEntity = serverEntity;
        }
    }
}
