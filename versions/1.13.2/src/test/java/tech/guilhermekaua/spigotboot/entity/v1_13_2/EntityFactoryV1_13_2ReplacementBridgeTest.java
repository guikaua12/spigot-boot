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
package tech.guilhermekaua.spigotboot.entity.v1_13_2;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFactoryV1_13_2ReplacementBridgeTest {

    @Test
    void resolveCurrentNativeHandle_shouldUseBukkitHandleAccessor() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        Object currentHandle = new Object();
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(currentHandle);

        assertSame(currentHandle, factory.resolveCurrentNativeHandle(entity));
    }

    @Test
    void allocateReplacementHandle_shouldAllocatePreparedReplacementType() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        SimpleReplacementHandle currentHandle = new SimpleReplacementHandle();

        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement =
                factory.prepareReplacement(entity, currentHandle);
        Object replacementHandle = factory.allocateReplacementHandle(preparedReplacement);

        assertInstanceOf(SimpleReplacementHandle.class, replacementHandle);
        assertNotSame(currentHandle, replacementHandle);
    }

    @Test
    void bindLifecycleToReplacement_shouldBindLifecycleAwareReplacementHandles() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        LifecycleAwareReplacementHandle currentHandle = new LifecycleAwareReplacementHandle();
        @SuppressWarnings("unchecked")
        NativeEntityLifecycle<Zombie> lifecycle = Mockito.mock(NativeEntityLifecycle.class);

        when(entity.getType()).thenReturn(EntityType.ZOMBIE);

        LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement =
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
    void resolveTrackerEntryHandle_shouldResolveLegacyTrackerEntryFromWorldTrackerState() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        LegacyLevel level = new LegacyLevel();
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(5, UUID.randomUUID(), level);
        TrackerEntry trackerEntry = new TrackerEntry(replacementHandle);

        level.tracker.trackedEntities.a(replacementHandle.id, trackerEntry);

        assertSame(trackerEntry, factory.resolveTrackerEntryHandle(replacementHandle));
    }

    @Test
    void rebindBukkitZombie_shouldDelegateToTheCraftWrapperHandleSetter() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        HandleAwareZombie entity = Mockito.mock(HandleAwareZombie.class);
        Object replacementHandle = new Object();

        factory.rebindBukkitZombie(entity, replacementHandle);

        verify(entity).setHandle(replacementHandle);
    }

    @Test
    void rebindLegacyBukkitBridge_shouldMoveTheBukkitWrapperReferenceToTheReplacementHandle() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        Entity bukkitEntity = Mockito.mock(Entity.class);
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(4, UUID.randomUUID(), new LegacyLevel());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(4, oldHandle.uniqueID, new LegacyLevel());
        oldHandle.bukkitEntity = bukkitEntity;

        factory.rebindLegacyBukkitBridge(bukkitEntity, oldHandle, replacementHandle);

        assertNull(oldHandle.bukkitEntity);
        assertSame(bukkitEntity, replacementHandle.bukkitEntity);
    }

    @Test
    void replaceWorldReferences_shouldRewriteLegacyWorldStateAndTrackerEntry() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        UUID uuid = UUID.randomUUID();
        LegacyLevel level = new LegacyLevel();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(12, uuid, level);
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(12, uuid, level);
        TrackerEntry trackerEntry = new TrackerEntry(oldHandle);

        level.entityList.add(oldHandle);
        level.entitiesById.a(oldHandle.id, oldHandle);
        level.entitiesByUUID.put(uuid, oldHandle);
        level.chunk.entitySlices[oldHandle.chunkY].add(oldHandle);
        level.tracker.trackedEntities.a(oldHandle.id, trackerEntry);

        factory.replaceWorldReferences(EntityPublicationFamily.LEGACY_WORLD_LISTENER, oldHandle, replacementHandle);

        assertFalse(level.entityList.contains(oldHandle));
        assertTrue(level.entityList.contains(replacementHandle));
        assertSame(replacementHandle, level.entitiesById.get(oldHandle.id));
        assertSame(replacementHandle, level.entitiesByUUID.get(uuid));
        assertFalse(level.chunk.entitySlices[oldHandle.chunkY].contains(oldHandle));
        assertTrue(level.chunk.entitySlices[oldHandle.chunkY].contains(replacementHandle));
        assertSame(replacementHandle, trackerEntry.tracker);
        assertSame(trackerEntry, factory.resolveTrackerEntryHandle(replacementHandle));
    }

    @Test
    void rewireLegacyVehicleAndPassengerReferences_shouldRewritePassengerOwnership() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(31, UUID.randomUUID(), new LegacyLevel());
        ReplacementPublicationHandle replacementHandle = new ReplacementPublicationHandle(31, oldHandle.uniqueID, new LegacyLevel());
        ReplacementPublicationHandle passenger = new ReplacementPublicationHandle(32, UUID.randomUUID(), new LegacyLevel());
        ReplacementPublicationHandle vehicle = new ReplacementPublicationHandle(33, UUID.randomUUID(), new LegacyLevel());

        oldHandle.passengers.add(passenger);
        oldHandle.vehicle = vehicle;
        passenger.vehicle = oldHandle;
        vehicle.passengers.add(oldHandle);

        factory.rewireLegacyVehicleAndPassengerReferences(oldHandle, replacementHandle);

        assertSame(replacementHandle, passenger.vehicle);
        assertFalse(vehicle.passengers.contains(oldHandle));
        assertTrue(vehicle.passengers.contains(replacementHandle));
    }

    @Test
    void markLegacyEntityRemoved_shouldFlagTheOldHandleAsRemoved() {
        EntityFactoryV1_13_2 factory = new EntityFactoryV1_13_2();
        ReplacementPublicationHandle oldHandle = new ReplacementPublicationHandle(40, UUID.randomUUID(), new LegacyLevel());

        factory.markLegacyEntityRemoved(oldHandle);

        assertTrue(oldHandle.dead);
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

    private static final class LegacyLevel {
        private final List<Object> entityList = new ArrayList<Object>();
        private final IntLookup entitiesById = new IntLookup();
        private final Map<Object, Object> entitiesByUUID = new LinkedHashMap<Object, Object>();
        private final LegacyTracker tracker = new LegacyTracker();
        private final LegacyChunk chunk = new LegacyChunk();

        public Object getChunkAt(int chunkX, int chunkZ) {
            return chunk;
        }
    }

    private static final class LegacyTracker {
        private final IntLookup trackedEntities = new IntLookup();
    }

    private static final class TrackerEntry {
        private Object tracker;

        private TrackerEntry(Object tracker) {
            this.tracker = tracker;
        }
    }

    private static final class IntLookup {
        private final Map<Integer, Object> values = new LinkedHashMap<Integer, Object>();

        public Object get(int id) {
            return values.get(Integer.valueOf(id));
        }

        public void a(int id, Object value) {
            values.put(Integer.valueOf(id), value);
        }
    }

    private static final class LegacyChunk {
        private final Collection<Object>[] entitySlices = createSections();

        private static Collection<Object>[] createSections() {
            @SuppressWarnings("unchecked")
            Collection<Object>[] sections = new Collection[16];
            for (int index = 0; index < sections.length; index++) {
                sections[index] = new LinkedHashSet<Object>();
            }
            return sections;
        }
    }

    private static class ReplacementPublicationHandle {
        private final int id;
        private final UUID uniqueID;
        private final Object world;
        private boolean inChunk = true;
        private int chunkX = 4;
        private int chunkY = 7;
        private int chunkZ = 9;
        private Object bukkitEntity;
        private List<Object> passengers = new ArrayList<Object>();
        private Object vehicle;
        private boolean dead;
        private boolean valid = true;

        private ReplacementPublicationHandle(int id, UUID uniqueID, Object world) {
            this.id = id;
            this.uniqueID = uniqueID;
            this.world = world;
        }

        public int getId() {
            return id;
        }

        public UUID getUniqueID() {
            return uniqueID;
        }
    }
}
