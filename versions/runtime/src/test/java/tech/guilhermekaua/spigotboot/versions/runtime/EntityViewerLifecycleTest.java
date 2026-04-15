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
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkState;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EntityViewerLifecycleTest {
    private static final MinecraftVersion MINECRAFT_VERSION = MinecraftVersion.of(1, 21, 11);

    @Test
    void shouldRefreshBindStateForSpawnedAndAttachedLifecycles() {
        BindRecordingNetworkController spawnedNetworkController = new BindRecordingNetworkController();
        MutableZombieHandle spawnedHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> spawned = createSpawnedLifecycle(new RecordingTransport(), spawnedHandle, spawnedNetworkController);

        assertEquals(12.0D, spawnedNetworkController.liveX, 0.0D);
        assertEquals(65.0D, spawnedNetworkController.liveY, 0.0D);
        assertEquals(-4.0D, spawnedNetworkController.liveZ, 0.0D);
        assertEquals(90.0F, spawnedNetworkController.liveYaw, 0.0F);
        assertEquals(1.5D, spawnedNetworkController.liveVelocityX, 0.0D);

        BindRecordingNetworkController attachedNetworkController = new BindRecordingNetworkController();
        MutableZombieHandle attachedHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> attached = createAttachedLifecycle(new RecordingTransport(), attachedHandle, attachedNetworkController);

        assertEquals(12.0D, attachedNetworkController.liveX, 0.0D);
        assertEquals(65.0D, attachedNetworkController.liveY, 0.0D);
        assertEquals(-4.0D, attachedNetworkController.liveZ, 0.0D);
        assertEquals(90.0F, attachedNetworkController.liveYaw, 0.0F);
        assertEquals(1.5D, attachedNetworkController.liveVelocityX, 0.0D);
        assertTrue(spawned.networkState().viewers().isEmpty());
        assertTrue(attached.networkState().viewers().isEmpty());
    }

    @Test
    void shouldGiveLateViewerFullSnapshotAndKeepPendingDeltaParityForSpawnedAndAttachedLifecycles() {
        assertLateViewerSnapshotParity(true);
        assertLateViewerSnapshotParity(false);
    }

    @Test
    void shouldDestroyEachViewerExactlyOnceAcrossRemoveAndUnbindForSpawnedAndAttachedLifecycles() {
        assertDestroyParity(true);
        assertDestroyParity(false);
    }

    private static void assertLateViewerSnapshotParity(boolean spawnedLifecycle) {
        RecordingTransport transport = new RecordingTransport();
        MutableZombieHandle zombieHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> lifecycle = spawnedLifecycle
                ? createSpawnedLifecycle(transport, zombieHandle, new BindRecordingNetworkController())
                : createAttachedLifecycle(transport, zombieHandle, new BindRecordingNetworkController());
        Player firstViewer = Mockito.mock(Player.class);
        Player lateViewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(firstViewer);
        assertEquals(
                Arrays.asList(
                        "spawnForViewer",
                        "sendInitialMetadataSnapshot",
                        "sendLivingInitialization",
                        "syncVelocity",
                        "syncPassengersOrVehicle",
                        "syncHeadRotation"
                ),
                transport.operationsForViewer(firstViewer)
        );
        assertEquals(12.0D, lifecycle.networkState().syncedX(), 0.0D);

        zombieHandle.location = new Location(Mockito.mock(World.class), 17.0D, 66.0D, 3.0D, 135.0F, 15.0F);
        zombieHandle.velocity = new Vector(-0.5D, 0.1D, 0.75D);
        transport.clear();

        lifecycle.registerViewer(lateViewer);

        assertEquals(
                Arrays.asList(
                        "spawnForViewer",
                        "sendInitialMetadataSnapshot",
                        "sendLivingInitialization",
                        "syncVelocity",
                        "syncPassengersOrVehicle",
                        "syncHeadRotation"
                ),
                transport.operationsForViewer(lateViewer)
        );
        assertEquals(17.0D, lifecycle.networkState().liveX(), 0.0D);
        assertEquals(12.0D, lifecycle.networkState().syncedX(), 0.0D);
        assertNotEquals(lifecycle.networkState().liveX(), lifecycle.networkState().syncedX(), 0.0D);

        transport.clear();
        lifecycle.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull EntityTickContext<Zombie> context) {
                return null;
            }
        });

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
        assertEquals(17.0D, lifecycle.networkState().syncedX(), 0.0D);
    }

    private static void assertDestroyParity(boolean spawnedLifecycle) {
        RecordingTransport transport = new RecordingTransport();
        MutableZombieHandle zombieHandle = new MutableZombieHandle();
        AbstractRuntimeControlledEntity<Zombie> lifecycle = spawnedLifecycle
                ? createSpawnedLifecycle(transport, zombieHandle, new BindRecordingNetworkController())
                : createAttachedLifecycle(transport, zombieHandle, new BindRecordingNetworkController());
        Player firstViewer = Mockito.mock(Player.class);
        Player secondViewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(firstViewer);
        lifecycle.registerViewer(secondViewer);
        transport.clear();

        lifecycle.unregisterViewer(firstViewer);
        lifecycle.dispatchRemove(new ContextualBaseInvoker<tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext<Zombie> context) {
                return null;
            }
        });
        lifecycle.dispatchRemove(new ContextualBaseInvoker<tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext<Zombie> context) {
                return null;
            }
        });
        lifecycle.unregisterViewer(secondViewer);

        assertEquals(1, transport.count("destroyForViewer", firstViewer));
        assertEquals(1, transport.count("destroyForViewer", secondViewer));
        assertEquals(2, transport.count("destroyForViewer"));
        assertTrue(lifecycle.networkState().viewers().isEmpty());
    }

    private static AbstractRuntimeControlledEntity<Zombie> createSpawnedLifecycle(
            RecordingTransport transport,
            MutableZombieHandle zombieHandle,
            BindRecordingNetworkController networkController
    ) {
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "viewer-lifecycle"),
                        CustomEntityBaseType.ZOMBIE
                )
                .controller(context -> new EntityController<Zombie>() {
                })
                .networkController(context -> networkController)
                .build();
        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                template,
                SpawnOptions.at(zombieHandle.location),
                MINECRAFT_VERSION,
                transport
        );
        lifecycle.bind(zombieHandle.bukkitEntity);
        lifecycle.onSpawn();
        return lifecycle;
    }

    private static AbstractRuntimeControlledEntity<Zombie> createAttachedLifecycle(
            RecordingTransport transport,
            MutableZombieHandle zombieHandle,
            BindRecordingNetworkController networkController
    ) {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MINECRAFT_VERSION,
                new EntityController<Zombie>() {
                },
                transport
        );
        lifecycle.bind(zombieHandle.bukkitEntity);
        lifecycle.setNetworkController(networkController);
        return lifecycle;
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

    private static final class BindRecordingNetworkController extends EntityNetworkController<Zombie> {
        private double liveX;
        private double liveY;
        private double liveZ;
        private float liveYaw;
        private double liveVelocityX;

        @Override
        public void onBind(@NotNull ControlledEntity<Zombie> entity, @NotNull EntityNetworkState state) {
            this.liveX = state.liveX();
            this.liveY = state.liveY();
            this.liveZ = state.liveZ();
            this.liveYaw = state.liveYaw();
            this.liveVelocityX = state.liveVelocityX();
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

        private int count(@NotNull String operation, @NotNull Player viewer) {
            int count = 0;
            for (TransportEvent event : events) {
                if (!event.trackedViewers && event.viewer == viewer && operation.equals(event.operation)) {
                    count++;
                }
            }
            return count;
        }

        private int count(@NotNull String operation) {
            int count = 0;
            for (TransportEvent event : events) {
                if (operation.equals(event.operation)) {
                    count++;
                }
            }
            return count;
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
