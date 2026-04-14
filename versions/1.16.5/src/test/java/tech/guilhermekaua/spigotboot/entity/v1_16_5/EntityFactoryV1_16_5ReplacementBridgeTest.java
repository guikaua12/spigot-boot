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
package tech.guilhermekaua.spigotboot.entity.v1_16_5;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFactoryV1_16_5ReplacementBridgeTest {

    @Test
    void resolveCurrentNativeHandle_shouldUseBukkitHandleAccessor() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        Object currentHandle = new Object();
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(currentHandle);

        assertSame(currentHandle, factory.resolveCurrentNativeHandle(entity));
    }

    @Test
    void allocateReplacementHandle_shouldAllocatePreparedReplacementType() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        SimpleReplacementHandle currentHandle = new SimpleReplacementHandle();

        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement =
                factory.prepareReplacement(entity, currentHandle);
        Object replacementHandle = factory.allocateReplacementHandle(preparedReplacement);

        assertInstanceOf(SimpleReplacementHandle.class, replacementHandle);
        assertNotSame(currentHandle, replacementHandle);
    }

    @Test
    void bindLifecycleToReplacement_shouldBindLifecycleAwareReplacementHandles() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        LifecycleAwareReplacementHandle currentHandle = new LifecycleAwareReplacementHandle();
        TestRuntimeLifecycle lifecycle = new TestRuntimeLifecycle();

        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement =
                factory.prepareReplacement(entity, currentHandle);
        Object replacementHandle = factory.allocateReplacementHandle(preparedReplacement);

        factory.bindLifecycleToReplacement(replacementHandle, preparedReplacement, lifecycle);

        LifecycleAwareNativeEntity lifecycleAwareNativeEntity = assertInstanceOf(
                LifecycleAwareNativeEntity.class,
                replacementHandle
        );
        assertSame(lifecycle, lifecycleAwareNativeEntity.spigotBootGetLifecycle());
    }

    @Test
    void resolveReplacementTrackingHandles_shouldResolveChunkTrackerEntryAndState() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        UUID uuid = UUID.randomUUID();
        EntitiesByUuidLevel level = new EntitiesByUuidLevel();
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(5, uuid, level);
        TrackerStateHandle serverEntity = new TrackerStateHandle(replacementHandle);
        TrackerHandle tracker = new TrackerHandle(replacementHandle, serverEntity);

        level.chunkSource.chunkMap.entityMap.put(Integer.valueOf(replacementHandle.id), tracker);

        PaperReplacementStrategy_1_21_plus.TrackingHandles trackingHandles =
                factory.resolveReplacementTrackingHandles(replacementHandle);

        assertSame(tracker, trackingHandles.trackerEntryHandle());
        assertSame(serverEntity, trackingHandles.trackerStateHandle());
    }

    @Test
    void rebindBukkitZombie_shouldDelegateToTheCraftWrapperHandleSetter() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        Object replacementHandle = new Object();

        factory.rebindBukkitZombie(entity, replacementHandle);

        verify(entity).setHandle(replacementHandle);
    }

    @Test
    void rebindModernBukkitBridge_shouldMoveTheBukkitWrapperReferenceToTheReplacementHandle() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        Entity bukkitEntity = Mockito.mock(Entity.class);
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(4, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(4, oldHandle.uuid, new Object());
        oldHandle.bukkitEntity = bukkitEntity;

        factory.rebindModernBukkitBridge(bukkitEntity, oldHandle, replacementHandle);

        assertNull(oldHandle.bukkitEntity);
        assertSame(bukkitEntity, replacementHandle.bukkitEntity);
    }

    @Test
    void replaceWorldReferences_shouldRewriteEntitiesByUuidWorldState() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        UUID uuid = UUID.randomUUID();
        EntitiesByUuidLevel level = new EntitiesByUuidLevel();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(12, uuid, level);
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(12, uuid, level);
        TrackerStateHandle serverEntity = new TrackerStateHandle(oldHandle);
        TrackerHandle tracker = new TrackerHandle(oldHandle, serverEntity);

        level.entitiesById.put(Integer.valueOf(oldHandle.id), oldHandle);
        level.entitiesByUuid.put(uuid, oldHandle);
        level.chunkSource.chunkMap.entityMap.put(Integer.valueOf(oldHandle.id), tracker);
        level.chunkSource.chunk.entitySections[oldHandle.yChunk].add(oldHandle);

        factory.replaceWorldReferences(EntityPublicationFamily.ENTITIES_BY_UUID, oldHandle, replacementHandle);

        assertSame(replacementHandle, level.entitiesById.get(Integer.valueOf(oldHandle.id)));
        assertSame(replacementHandle, level.entitiesByUuid.get(uuid));
        assertFalse(level.chunkSource.chunk.entitySections[oldHandle.yChunk].contains(oldHandle));
        assertTrue(level.chunkSource.chunk.entitySections[oldHandle.yChunk].contains(replacementHandle));
        assertSame(replacementHandle, tracker.entity);
        assertSame(replacementHandle, tracker.serverEntity.entity);
    }

    @Test
    void rewireModernVehicleAndPassengerReferences_shouldRewritePassengerOwnership() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(31, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(31, oldHandle.uuid, new Object());
        ReplacementPublicationHandle passenger = new ReplacementPublicationHandle(32, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle vehicle = new ReplacementPublicationHandle(33, UUID.randomUUID(), new Object());

        oldHandle.passengers = new ArrayList<Object>();
        oldHandle.passengers.add(passenger);
        oldHandle.vehicle = vehicle;
        passenger.vehicle = oldHandle;
        vehicle.passengers = new ArrayList<Object>();
        vehicle.passengers.add(oldHandle);

        factory.rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);

        assertSame(replacementHandle, passenger.vehicle);
        assertFalse(vehicle.passengers.contains(oldHandle));
        assertTrue(vehicle.passengers.contains(replacementHandle));
    }

    @Test
    void markModernEntityRemoved_shouldFlagTheOldHandleAsRemoved() {
        EntityFactoryV1_16_5 factory = new EntityFactoryV1_16_5();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(40, UUID.randomUUID(), new Object());

        factory.markModernEntityRemoved(oldHandle);

        assertTrue(oldHandle.removed);
        assertFalse(oldHandle.valid);
    }

    private interface HandleAwareEntity extends Entity {
        Object getHandle();
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();

        void setHandle(Object handle);
    }

    public static class SimpleReplacementHandle {
        public SimpleReplacementHandle() {
        }
    }

    public static class LifecycleAwareReplacementHandle implements LifecycleAwareNativeEntity {
        private NativeEntityLifecycle<?> lifecycle;

        public LifecycleAwareReplacementHandle() {
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
    }

    private static final class TrackerHandle {
        private final TrackerStateHandle serverEntity;
        private Object entity;

        private TrackerHandle(Object entity, TrackerStateHandle serverEntity) {
            this.entity = entity;
            this.serverEntity = serverEntity;
        }
    }

    private static final class TrackerStateHandle {
        private Object entity;

        private TrackerStateHandle(Object entity) {
            this.entity = entity;
        }
    }

    private static class ReplacementPublicationHandle {
        private final int id;
        private final UUID uuid;
        private final Object level;
        private boolean inChunk = true;
        private int xChunk = 4;
        private int yChunk = 7;
        private int zChunk = 9;
        private Object bukkitEntity;
        private List<Object> passengers = new ArrayList<Object>();
        private Object vehicle;
        private boolean removed;
        private boolean valid = true;

        private ReplacementPublicationHandle(int id, UUID uuid, Object level) {
            this.id = id;
            this.uuid = uuid;
            this.level = level;
        }

        public int getId() {
            return id;
        }

        public UUID getUUID() {
            return uuid;
        }

        public Object getBukkitEntity() {
            return bukkitEntity;
        }
    }

    private static final class EntitiesByUuidLevel {
        private final Map<Object, Object> entitiesById = new LinkedHashMap<Object, Object>();
        private final Map<Object, Object> entitiesByUuid = new LinkedHashMap<Object, Object>();
        private final ChunkSourceHandle chunkSource = new ChunkSourceHandle();
    }

    private static final class ChunkSourceHandle {
        private final ChunkMapHandle chunkMap = new ChunkMapHandle();
        private final ChunkHandle chunk = new ChunkHandle();

        public Object getChunkNow(int chunkX, int chunkZ) {
            return chunk;
        }
    }

    private static final class ChunkMapHandle {
        private final Map<Object, Object> entityMap = new LinkedHashMap<Object, Object>();
    }

    private static final class ChunkHandle {
        private final Collection<Object>[] entitySections = createSections();

        private static Collection<Object>[] createSections() {
            @SuppressWarnings("unchecked")
            Collection<Object>[] sections = new Collection[16];
            for (int index = 0; index < sections.length; index++) {
                sections[index] = new LinkedHashSet<Object>();
            }
            return sections;
        }
    }

    private static final class TestRuntimeLifecycle extends AbstractRuntimeControlledEntity<Zombie> {
        private TestRuntimeLifecycle() {
            super(
                    CustomEntityBaseType.ZOMBIE,
                    MinecraftVersion.of(1, 16, 5),
                    new EntityController<Zombie>() {
                        @Override
                        public void onTick(@NotNull EntityTickContext<Zombie> context) {
                            context.base().invoke();
                        }
                    }
            );
        }
    }
}
