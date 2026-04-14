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

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFactoryV1_19_2ReplacementBridgeTest {

    @Test
    void resolveCurrentNativeHandle_shouldUseBukkitHandleAccessor() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        Object currentHandle = new Object();
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(currentHandle);

        assertSame(currentHandle, factory.resolveCurrentNativeHandle(entity));
    }

    @Test
    void allocateReplacementHandle_shouldCreateLifecycleAwareBridgeSubclassForSupportedEntities() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        HookBridgeReplacementHandle currentHandle = new HookBridgeReplacementHandle();

        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement =
                factory.prepareReplacement(entity, currentHandle);
        Object replacementHandle = factory.allocateReplacementHandle(preparedReplacement);

        assertInstanceOf(HookBridgeReplacementHandle.class, replacementHandle);
        assertInstanceOf(LifecycleAwareNativeEntity.class, replacementHandle);
        assertNotSame(currentHandle, replacementHandle);
    }

    @Test
    void bindLifecycleToReplacement_shouldBindTheGeneratedBridgeAndDispatchTickHooks() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        Zombie bukkitEntity = Mockito.mock(Zombie.class);
        HookBridgeReplacementHandle currentHandle = new HookBridgeReplacementHandle();
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement =
                factory.prepareReplacement(entity, currentHandle);
        Object replacementHandle = factory.allocateReplacementHandle(preparedReplacement);
        TestRuntimeLifecycle lifecycle = new TestRuntimeLifecycle();
        lifecycle.bind(bukkitEntity);

        factory.bindLifecycleToReplacement(
                replacementHandle,
                preparedReplacement,
                lifecycle
        );

        LifecycleAwareNativeEntity lifecycleAwareHandle = assertInstanceOf(
                LifecycleAwareNativeEntity.class,
                replacementHandle
        );
        assertSame(lifecycle, lifecycleAwareHandle.spigotBootGetLifecycle());

        ((HookBridgeReplacementHandle) replacementHandle).tick();

        assertEquals(1, lifecycle.controllerTickCount);
        assertEquals(1, ((HookBridgeReplacementHandle) replacementHandle).baseTickCount);
    }

    @Test
    void resolveReplacementTrackingHandles_shouldFallbackToTrackerFieldWhenMethodAccessorsAreMissing() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        TrackerEntryState serverEntity = new TrackerEntryState("state");
        TrackerHandle tracker = new TrackerHandle(new Object(), serverEntity);
        TrackerFieldCarrier replacementHandle = new TrackerFieldCarrier(tracker);

        PaperReplacementStrategy_1_21_plus.TrackingHandles trackingHandles =
                factory.resolveReplacementTrackingHandles(replacementHandle);

        assertSame(tracker, trackingHandles.trackerEntryHandle());
        assertSame(serverEntity, trackingHandles.trackerStateHandle());
    }

    @Test
    void rebindBukkitZombie_shouldDelegateToTheCraftWrapperHandleSetter() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        Object replacementHandle = new Object();

        factory.rebindBukkitZombie(entity, replacementHandle);

        verify(entity).setHandle(replacementHandle);
    }

    @Test
    void rebindModernBukkitBridge_shouldMoveTheBukkitWrapperReferenceToTheReplacementHandle() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        Entity bukkitEntity = Mockito.mock(Entity.class);
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(4, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(4, oldHandle.uuid, new Object());
        oldHandle.bukkitEntity = bukkitEntity;

        factory.rebindModernBukkitBridge(bukkitEntity, oldHandle, replacementHandle);

        assertNull(oldHandle.bukkitEntity);
        assertSame(bukkitEntity, replacementHandle.bukkitEntity);
    }

    @Test
    void replaceWorldReferences_shouldRewriteSectionManagerState() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        UUID uuid = UUID.randomUUID();
        SectionManagerLevel level = new SectionManagerLevel();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(12, uuid, level);
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(12, uuid, level);
        TrackerEntryState serverEntity = new TrackerEntryState(oldHandle);
        TrackerHandle tracker = new TrackerHandle(oldHandle, serverEntity);
        oldHandle.tracker = tracker;

        level.entityManager.visibleEntityStorage.byId.put(Integer.valueOf(oldHandle.id), oldHandle);
        level.entityManager.visibleEntityStorage.byUUID.put(uuid, oldHandle);
        level.entityTickList.add(oldHandle);
        level.navigatingMobs.add(oldHandle);

        factory.replaceWorldReferences(EntityPublicationFamily.SECTION_MANAGER, oldHandle, replacementHandle);

        assertSame(replacementHandle, level.entityManager.visibleEntityStorage.byId.get(Integer.valueOf(oldHandle.id)));
        assertSame(replacementHandle, level.entityManager.visibleEntityStorage.byUUID.get(uuid));
        assertFalse(level.entityTickList.contains(oldHandle));
        assertTrue(level.entityTickList.contains(replacementHandle));
        assertFalse(level.navigatingMobs.contains(oldHandle));
        assertTrue(level.navigatingMobs.contains(replacementHandle));
        assertSame(replacementHandle, tracker.c);
        assertSame(replacementHandle, tracker.b.d);
        assertSame(tracker, replacementHandle.tracker);
        assertNull(oldHandle.tracker);
    }

    @Test
    void replaceWorldReferences_shouldRewritePaperChunkSystemState() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        UUID uuid = UUID.randomUUID();
        PaperChunkSystemLevel level = new PaperChunkSystemLevel();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(21, uuid, level);
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(21, uuid, level);
        TrackerEntryState serverEntity = new TrackerEntryState(oldHandle);
        TrackerHandle tracker = new TrackerHandle(oldHandle, serverEntity);
        oldHandle.tracker = tracker;

        level.entityLookup.entityById.put(Integer.valueOf(oldHandle.id), oldHandle);
        level.entityLookup.entityByUUID.put(uuid, oldHandle);
        level.entityLookup.accessibleEntities.add(oldHandle);
        level.entityLookup.chunk.addEntity(oldHandle, Integer.valueOf(oldHandle.sectionY));
        level.entityTickList.add(oldHandle);
        level.navigatingMobs.add(oldHandle);

        factory.replaceWorldReferences(EntityPublicationFamily.PAPER_CHUNK_SYSTEM, oldHandle, replacementHandle);

        assertSame(replacementHandle, level.entityLookup.entityById.get(Integer.valueOf(oldHandle.id)));
        assertSame(replacementHandle, level.entityLookup.entityByUUID.get(uuid));
        assertFalse(level.entityLookup.accessibleEntities.contains(oldHandle));
        assertTrue(level.entityLookup.accessibleEntities.contains(replacementHandle));
        assertFalse(level.entityLookup.chunk.contains(oldHandle));
        assertTrue(level.entityLookup.chunk.contains(replacementHandle));
        assertFalse(level.entityTickList.contains(oldHandle));
        assertTrue(level.entityTickList.contains(replacementHandle));
        assertFalse(level.navigatingMobs.contains(oldHandle));
        assertTrue(level.navigatingMobs.contains(replacementHandle));
        assertSame(replacementHandle, tracker.c);
        assertSame(replacementHandle, tracker.b.d);
        assertSame(tracker, replacementHandle.tracker);
        assertNull(oldHandle.tracker);
    }

    @Test
    void rewireModernVehicleAndPassengerReferences_shouldRewritePassengerOwnership() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(31, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(31, oldHandle.uuid, new Object());
        ReplacementPublicationHandle passenger = new ReplacementPublicationHandle(32, UUID.randomUUID(), new Object());
        ReplacementPublicationHandle vehicle = new ReplacementPublicationHandle(33, UUID.randomUUID(), new Object());

        oldHandle.au = new ArrayList<Object>();
        oldHandle.au.add(passenger);
        oldHandle.av = vehicle;
        passenger.av = oldHandle;
        vehicle.au = new ArrayList<Object>();
        vehicle.au.add(oldHandle);

        factory.rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);

        assertSame(replacementHandle, passenger.av);
        assertFalse(vehicle.au.contains(oldHandle));
        assertTrue(vehicle.au.contains(replacementHandle));
    }

    private interface HandleAwareEntity extends Entity {
        Object getHandle();
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();

        void setHandle(Object handle);
    }

    public static class HookBridgeReplacementHandle {
        private int baseTickCount;

        public HookBridgeReplacementHandle() {
        }

        public void tick() {
            baseTickCount++;
        }
    }

    private static final class TrackerFieldCarrier {
        private final Object tracker;

        private TrackerFieldCarrier(Object tracker) {
            this.tracker = tracker;
        }
    }

    private static final class TrackerHandle {
        private final TrackerEntryState b;
        private Object c;

        private TrackerHandle(Object entity, TrackerEntryState serverEntity) {
            this.c = entity;
            this.b = serverEntity;
        }
    }

    private static final class TrackerEntryState {
        private Object d;
        private final Object marker;

        private TrackerEntryState(Object marker) {
            this.d = marker;
            this.marker = marker;
        }
    }

    private static class ReplacementPublicationHandle {
        private final int id;
        private final UUID uuid;
        private final Object level;
        private final int sectionX = 4;
        private final int sectionY = 7;
        private final int sectionZ = 9;
        private Object tracker;
        private Object bukkitEntity;
        private Object levelCallback;
        private List<Object> au = new ArrayList<Object>();
        private Object av;

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

        public void setLevelCallback(Object callback) {
            this.levelCallback = callback;
        }
    }

    private static final class SectionManagerLevel {
        private final SectionManager entityManager = new SectionManager();
        private final ManagedEntityCollection entityTickList = new ManagedEntityCollection();
        private final Set<Object> navigatingMobs = new LinkedHashSet<Object>();
    }

    private static final class SectionManager {
        private final VisibleEntityStorage visibleEntityStorage = new VisibleEntityStorage();
    }

    private static final class VisibleEntityStorage {
        private final Map<Object, Object> byId = new LinkedHashMap<Object, Object>();
        private final Map<Object, Object> byUUID = new LinkedHashMap<Object, Object>();
    }

    private static final class PaperChunkSystemLevel {
        private final PaperEntityLookup entityLookup = new PaperEntityLookup();
        private final ManagedEntityCollection entityTickList = new ManagedEntityCollection();
        private final Set<Object> navigatingMobs = new LinkedHashSet<Object>();

        public PaperEntityLookup getEntityLookup() {
            return entityLookup;
        }
    }

    private static final class PaperEntityLookup {
        private final Map<Object, Object> entityById = new LinkedHashMap<Object, Object>();
        private final Map<Object, Object> entityByUUID = new LinkedHashMap<Object, Object>();
        private final ManagedEntityCollection accessibleEntities = new ManagedEntityCollection();
        private final ChunkSlices chunk = new ChunkSlices();

        public Object getChunk(int chunkX, int chunkZ) {
            return chunk;
        }

        public Object getOrCreateChunk(int chunkX, int chunkZ) {
            return chunk;
        }
    }

    private static final class ChunkSlices {
        private final Set<Object> values = new LinkedHashSet<Object>();

        public boolean addEntity(Object entity, int sectionY) {
            return values.add(entity);
        }

        public boolean removeEntity(Object entity, int sectionY) {
            return values.remove(entity);
        }

        public boolean contains(Object entity) {
            return values.contains(entity);
        }
    }

    private static final class ManagedEntityCollection {
        private final Set<Object> values = new LinkedHashSet<Object>();

        public void add(Object entity) {
            values.add(entity);
        }

        public void remove(Object entity) {
            values.remove(entity);
        }

        public boolean contains(Object entity) {
            return values.contains(entity);
        }
    }

    private static final class TestRuntimeLifecycle extends AbstractRuntimeControlledEntity<Zombie> {
        private int controllerTickCount;

        private TestRuntimeLifecycle() {
            super(
                    CustomEntityBaseType.ZOMBIE,
                    MinecraftVersion.of(1, 19, 2),
                    new EntityController<Zombie>() {
                    }
            );
            setController(new EntityController<Zombie>() {
                @Override
                public void onTick(@NotNull EntityTickContext<Zombie> context) {
                    controllerTickCount++;
                    context.base().invoke();
                }
            });
        }
    }
}
