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
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkState;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportRequest;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyTrackingBindingStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerEntryHandleBridge;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerEntryHook;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerHookBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerHookSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerViewabilitySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class LegacyTrackerHookSupportTest {
    private static final MinecraftVersion MINECRAFT_VERSION = MinecraftVersion.of(1, 8, 8);

    @Test
    void shouldBindLegacyHookThroughTrackingStrategySupportBridge() {
        RecordingTransport transport = new RecordingTransport();
        MutableZombieHandle zombieHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> lifecycle = createLifecycle(transport, zombieHandle);
        TestTrackerEntryHandle trackerEntryHandle = new TestTrackerEntryHandle();
        LegacyTrackingBindingStrategy_1_8_to_1_12 strategy =
                new LegacyTrackingBindingStrategy_1_8_to_1_12("legacy-entry-only", true, false, true, false);

        strategy.bindFreshSpawnTracking(new BoundSupport(trackerEntryHandle), new Object(), lifecycle);

        assertSame(trackerEntryHandle, lifecycle.networkState().trackerEntryHandle());
        assertNull(lifecycle.networkState().trackerStateHandle());
        assertNotNull(trackerEntryHandle.boundHook());
    }

    @Test
    void shouldRouteLegacyTrackerTickAndViewerCallbacksThroughRuntimeTransportPipeline() {
        RecordingTransport transport = new RecordingTransport();
        MutableZombieHandle zombieHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> lifecycle = createLifecycle(transport, zombieHandle);
        TestTrackerEntryHandle trackerEntryHandle = new TestTrackerEntryHandle();
        Player viewer = Mockito.mock(Player.class);

        trackerEntryHandle.setSnapshot(viewer, visibleSnapshot());
        LegacyTrackerHookBackend.bind(lifecycle, trackerEntryHandle, new BridgeBackedLegacyTrackerHookSupport("test"));

        trackerEntryHandle.fireViewerUpdate(viewer);
        assertEquals(
                Arrays.asList(
                        "spawnForViewer",
                        "sendInitialMetadataSnapshot",
                        "sendLivingInitialization",
                        "syncVelocity",
                        "syncPassengersOrVehicle",
                        "syncHeadRotation"
                ),
                transport.operationsForViewer(viewer)
        );

        zombieHandle.location = new Location(Mockito.mock(World.class), 18.0D, 65.0D, 2.0D, 135.0F, 5.0F);
        zombieHandle.velocity = new Vector(-0.25D, 0.0D, 0.75D);
        transport.clear();

        lifecycle.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull EntityTickContext<Zombie> context) {
                return null;
            }
        });

        assertTrue(transport.operationsForTrackedViewers().isEmpty());

        trackerEntryHandle.fireTrack();

        assertEquals(
                Arrays.asList(
                        "syncPassengersOrVehicle",
                        "syncRelativeMove",
                        "syncRotation",
                        "syncVelocity",
                        "sendDirtyMetadataDelta",
                        "syncHeadRotation"
                ),
                transport.operationsForTrackedViewers()
        );

        transport.clear();
        trackerEntryHandle.fireViewerRemoved(viewer);
        assertEquals(Arrays.asList("destroyForViewer"), transport.operationsForViewer(viewer));
        assertTrue(lifecycle.networkState().viewers().isEmpty());
    }

    @Test
    void shouldKeepLegacyViewabilityLogicInsideSharedBackend() {
        RecordingTransport transport = new RecordingTransport();
        MutableZombieHandle zombieHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> lifecycle = createLifecycle(transport, zombieHandle);
        TestTrackerEntryHandle trackerEntryHandle = new TestTrackerEntryHandle();
        Player passengerViewer = Mockito.mock(Player.class);
        Player respawnBlindViewer = Mockito.mock(Player.class);
        Player selfViewer = Mockito.mock(Player.class);

        LegacyTrackerHookBackend.bind(lifecycle, trackerEntryHandle, new BridgeBackedLegacyTrackerHookSupport("test"));

        trackerEntryHandle.setSnapshot(passengerViewer, new LegacyTrackerViewabilitySnapshot(
                false,
                false,
                true,
                false,
                true,
                true,
                true,
                true
        ));
        trackerEntryHandle.fireViewerUpdate(passengerViewer);
        assertTrue(lifecycle.networkState().viewers().contains(passengerViewer));

        transport.clear();
        trackerEntryHandle.setSnapshot(passengerViewer, new LegacyTrackerViewabilitySnapshot(
                false,
                true,
                true,
                true,
                false,
                true,
                true,
                true
        ));
        trackerEntryHandle.fireViewerUpdate(passengerViewer);
        assertFalse(lifecycle.networkState().viewers().contains(passengerViewer));
        assertEquals(Arrays.asList("destroyForViewer"), transport.operationsForViewer(passengerViewer));

        trackerEntryHandle.setSnapshot(respawnBlindViewer, new LegacyTrackerViewabilitySnapshot(
                false,
                true,
                true,
                true,
                false,
                true,
                true,
                true
        ));
        trackerEntryHandle.fireViewerUpdate(respawnBlindViewer);
        assertFalse(lifecycle.networkState().viewers().contains(respawnBlindViewer));

        trackerEntryHandle.setSnapshot(selfViewer, new LegacyTrackerViewabilitySnapshot(
                true,
                false,
                true,
                true,
                false,
                true,
                true,
                true
        ));
        trackerEntryHandle.fireViewerUpdate(selfViewer);
        assertFalse(lifecycle.networkState().viewers().contains(selfViewer));
    }

    private static AbstractRuntimeControlledEntity<Zombie> createLifecycle(
            RecordingTransport transport,
            MutableZombieHandle zombieHandle
    ) {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MINECRAFT_VERSION,
                new tech.guilhermekaua.spigotboot.versions.api.EntityController<Zombie>() {
                },
                transport
        );
        lifecycle.bind(zombieHandle.bukkitEntity);
        return lifecycle;
    }

    private static LegacyTrackerViewabilitySnapshot visibleSnapshot() {
        return new LegacyTrackerViewabilitySnapshot(
                false,
                false,
                true,
                true,
                false,
                true,
                true,
                true
        );
    }

    private static final class BoundSupport implements LegacyTrackingBindingStrategy_1_8_to_1_12.FreshSupport {
        private final TestTrackerEntryHandle trackerEntryHandle;

        private BoundSupport(@NotNull TestTrackerEntryHandle trackerEntryHandle) {
            this.trackerEntryHandle = trackerEntryHandle;
        }

        @Override
        public Object resolveTrackerEntryHandle(@NotNull Object trackedHandle) {
            return trackerEntryHandle;
        }

        @Override
        public LegacyTrackerHookSupport legacyTrackerHookSupport() {
            return new BridgeBackedLegacyTrackerHookSupport("test");
        }
    }

    private static final class BridgeBackedLegacyTrackerHookSupport implements LegacyTrackerHookSupport {
        private final String overlayId;

        private BridgeBackedLegacyTrackerHookSupport(@NotNull String overlayId) {
            this.overlayId = overlayId;
        }

        @Override
        public @NotNull String overlayId() {
            return overlayId;
        }

        @Override
        public void installHook(@NotNull Object trackerEntryHandle, @NotNull LegacyTrackerEntryHook hook) {
            if (trackerEntryHandle instanceof LegacyTrackerEntryHandleBridge) {
                ((LegacyTrackerEntryHandleBridge) trackerEntryHandle).bindLegacyTrackerEntryHook(hook);
            }
        }

        @Override
        public Player resolveViewer(@NotNull Object rawViewer) {
            return rawViewer instanceof Player ? (Player) rawViewer : null;
        }

        @Override
        public @NotNull LegacyTrackerViewabilitySnapshot describeViewability(
                @NotNull Object trackerEntryHandle,
                @NotNull Object rawViewer
        ) {
            if (trackerEntryHandle instanceof LegacyTrackerEntryHandleBridge) {
                return ((LegacyTrackerEntryHandleBridge) trackerEntryHandle).describeLegacyViewability(rawViewer);
            }
            return LegacyTrackerViewabilitySnapshot.hidden();
        }
    }

    private static final class TestTrackerEntryHandle implements LegacyTrackerEntryHandleBridge {
        private final Map<Object, LegacyTrackerViewabilitySnapshot> snapshots =
                new LinkedHashMap<Object, LegacyTrackerViewabilitySnapshot>();
        private LegacyTrackerEntryHook boundHook;

        @Override
        public void bindLegacyTrackerEntryHook(@NotNull LegacyTrackerEntryHook hook) {
            this.boundHook = hook;
        }

        @Override
        public @NotNull LegacyTrackerViewabilitySnapshot describeLegacyViewability(@NotNull Object rawViewer) {
            LegacyTrackerViewabilitySnapshot snapshot = snapshots.get(rawViewer);
            return snapshot != null ? snapshot : LegacyTrackerViewabilitySnapshot.hidden();
        }

        private void setSnapshot(@NotNull Object rawViewer, @NotNull LegacyTrackerViewabilitySnapshot snapshot) {
            snapshots.put(rawViewer, snapshot);
        }

        private void fireTrack() {
            boundHook.onTrack();
        }

        private void fireViewerUpdate(@NotNull Object rawViewer) {
            boundHook.onViewerUpdate(rawViewer);
        }

        private void fireViewerRemoved(@NotNull Object rawViewer) {
            boundHook.onViewerRemoved(rawViewer);
        }

        private LegacyTrackerEntryHook boundHook() {
            return boundHook;
        }
    }

    private static final class MutableZombieHandle {
        private Location location = new Location(Mockito.mock(World.class), 12.0D, 65.0D, -4.0D, 90.0F, 10.0F);
        private Vector velocity = new Vector(1.5D, 0.0D, -0.5D);
        private final Zombie bukkitEntity;

        private MutableZombieHandle() {
            this.bukkitEntity = Mockito.mock(Zombie.class);
            when(bukkitEntity.isValid()).thenReturn(true);
            when(bukkitEntity.getLocation()).thenAnswer(invocation -> location);
            when(bukkitEntity.getVelocity()).thenAnswer(invocation -> velocity);
        }
    }

    private static final class RecordingTransport implements EntityTransport {
        private final List<TransportEvent> events = new ArrayList<TransportEvent>();

        @Override
        public void spawnForViewer(@NotNull EntityTransportRequest request) {
            record("spawnForViewer", request);
        }

        @Override
        public void destroyForViewer(@NotNull EntityTransportRequest request) {
            record("destroyForViewer", request);
        }

        @Override
        public void syncRelativeMove(@NotNull EntityTransportRequest request) {
            record("syncRelativeMove", request);
        }

        @Override
        public void syncAbsoluteMove(@NotNull EntityTransportRequest request) {
            record("syncAbsoluteMove", request);
        }

        @Override
        public void syncRotation(@NotNull EntityTransportRequest request) {
            record("syncRotation", request);
        }

        @Override
        public void syncHeadRotation(@NotNull EntityTransportRequest request) {
            record("syncHeadRotation", request);
        }

        @Override
        public void syncVelocity(@NotNull EntityTransportRequest request) {
            record("syncVelocity", request);
        }

        @Override
        public void syncPassengersOrVehicle(@NotNull EntityTransportRequest request) {
            record("syncPassengersOrVehicle", request);
        }

        @Override
        public void sendInitialMetadataSnapshot(@NotNull EntityTransportRequest request) {
            record("sendInitialMetadataSnapshot", request);
        }

        @Override
        public void sendDirtyMetadataDelta(@NotNull EntityTransportRequest request) {
            record("sendDirtyMetadataDelta", request);
        }

        @Override
        public void sendLivingInitialization(@NotNull EntityTransportRequest request) {
            record("sendLivingInitialization", request);
        }

        private void record(@NotNull String operation, @NotNull EntityTransportRequest request) {
            events.add(new TransportEvent(operation, request.targetsTrackedViewers(), request.viewerOrNull()));
        }

        private void clear() {
            events.clear();
        }

        private @NotNull List<String> operationsForViewer(@NotNull Player viewer) {
            List<String> operations = new ArrayList<String>();
            for (TransportEvent event : events) {
                if (!event.trackedViewers && event.viewer == viewer) {
                    operations.add(event.operation);
                }
            }
            return operations;
        }

        private @NotNull List<String> operationsForTrackedViewers() {
            List<String> operations = new ArrayList<String>();
            for (TransportEvent event : events) {
                if (event.trackedViewers) {
                    operations.add(event.operation);
                }
            }
            return operations;
        }
    }

    private static final class TransportEvent {
        private final String operation;
        private final boolean trackedViewers;
        private final Player viewer;

        private TransportEvent(@NotNull String operation, boolean trackedViewers, Player viewer) {
            this.operation = operation;
            this.trackedViewers = trackedViewers;
            this.viewer = viewer;
        }
    }
}
