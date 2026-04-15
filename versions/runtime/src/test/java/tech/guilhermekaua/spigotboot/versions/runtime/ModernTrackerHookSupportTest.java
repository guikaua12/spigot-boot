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
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkState;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportRequest;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.ModernTrackerHook;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class ModernTrackerHookSupportTest {
    private static final MinecraftVersion MINECRAFT_VERSION = MinecraftVersion.of(1, 21, 11);

    @Test
    void shouldRoutePairingTransitionsAndStateTicksThroughTheRuntimeTransportPipeline() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle(transport);
        FakeTrackerStateHandle trackerStateHandle = new FakeTrackerStateHandle();
        PaperTrackingBindingStrategy_1_21_plus strategy = new PaperTrackingBindingStrategy_1_21_plus(
                "paper-entry-and-state",
                true,
                true,
                true,
                true
        );
        ModernTrackerHook hook = strategy.bindReplacementTracking(
                new FakeReplacementSupport(new Object(), trackerStateHandle, false),
                new Object(),
                lifecycle
        );
        Player viewer = Mockito.mock(Player.class);

        hook.addPairing(viewer);
        lifecycle.networkState().setSyncedPosition(11.0D, 65.0D, -4.0D);
        lifecycle.networkState().setSyncedRotation(0.0F, 0.0F);
        lifecycle.networkState().setSyncedVelocity(0.0D, 0.0D, 0.0D);
        lifecycle.networkState().setSyncedHeadYaw(0.0F);
        lifecycle.networkState().setTicksSinceAbsoluteSync(0);
        hook.onTick();
        hook.removePairing(viewer);

        assertEquals(
                Arrays.asList(
                        "spawnForViewer:viewer",
                        "sendInitialMetadataSnapshot:viewer",
                        "sendLivingInitialization:viewer",
                        "syncVelocity:viewer",
                        "syncPassengersOrVehicle:viewer",
                        "syncHeadRotation:viewer",
                        "syncPassengersOrVehicle:tracked",
                        "syncRelativeMove:tracked",
                        "syncRotation:tracked",
                        "syncVelocity:tracked",
                        "sendDirtyMetadataDelta:tracked",
                        "syncHeadRotation:tracked",
                        "destroyForViewer:viewer"
                ),
                transport.operations
        );
        assertSame(trackerStateHandle, lifecycle.networkState().trackerStateHandle());
        assertEquals(1, trackerStateHandle.timeSinceLocationSync);
        assertEquals(1, trackerStateHandle.tickCounter);
    }

    @Test
    void shouldPreserveBroadcastConsumerAndPassengerVehicleStateFromTheTrackerStateHandle() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle(transport);
        List<Object> broadcastPackets = new ArrayList<Object>();
        FakeTrackerStateHandle trackerStateHandle = new FakeTrackerStateHandle();
        trackerStateHandle.broadcastMethod = new Consumer<Object>() {
            @Override
            public void accept(Object packet) {
                broadcastPackets.add(packet);
            }
        };
        trackerStateHandle.opt_passengers.add("passenger-a");
        trackerStateHandle.opt_passengers.add("passenger-b");
        trackerStateHandle.opt_vehicle = "vehicle-a";
        PaperTrackingBindingStrategy_1_21_plus strategy = new PaperTrackingBindingStrategy_1_21_plus(
                "paper-entry-and-state",
                true,
                true,
                true,
                true
        );

        ModernTrackerHook hook = strategy.bindReplacementTracking(
                new FakeReplacementSupport(new Object(), trackerStateHandle, false),
                new Object(),
                lifecycle
        );
        hook.broadcast("packet-a");

        assertEquals(Arrays.asList((Object) "packet-a"), broadcastPackets);
        assertEquals(Arrays.asList((Object) "passenger-a", "passenger-b"), hook.passengerSnapshot());
        assertEquals("vehicle-a", hook.vehicleSnapshot());
        assertSame(trackerStateHandle.broadcastMethod, hook.originalBroadcastConsumer());
    }

    @Test
    void shouldLetInstalledTrackerHooksOwnNetworkTickDispatch() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle(transport);
        FakeTrackerStateHandle trackerStateHandle = new FakeTrackerStateHandle();
        PaperTrackingBindingStrategy_1_21_plus strategy = new PaperTrackingBindingStrategy_1_21_plus(
                "paper-entry-and-state",
                true,
                true,
                true,
                true
        );
        ModernTrackerHook hook = strategy.bindReplacementTracking(
                new FakeReplacementSupport(new Object(), trackerStateHandle, true),
                new Object(),
                lifecycle
        );
        Player viewer = Mockito.mock(Player.class);

        hook.addPairing(viewer);
        transport.operations.clear();
        lifecycle.networkState().setSyncedPosition(11.0D, 65.0D, -4.0D);
        lifecycle.networkState().setSyncedRotation(0.0F, 0.0F);
        lifecycle.networkState().setSyncedVelocity(0.0D, 0.0D, 0.0D);
        lifecycle.networkState().setSyncedHeadYaw(0.0F);
        lifecycle.networkState().setTicksSinceAbsoluteSync(0);

        lifecycle.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull EntityTickContext<Zombie> context) {
                return null;
            }
        });
        assertFalse(transport.operations.contains("syncPassengersOrVehicle:tracked"));

        hook.onTick();

        assertEquals(
                Arrays.asList(
                        "syncPassengersOrVehicle:tracked",
                        "syncRelativeMove:tracked",
                        "syncRotation:tracked",
                        "syncVelocity:tracked",
                        "sendDirtyMetadataDelta:tracked",
                        "syncHeadRotation:tracked"
                ),
                transport.operations
        );
    }

    private static RuntimeAttachedEntityLifecycle<Zombie> createAttachedLifecycle(EntityTransport transport) {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MINECRAFT_VERSION,
                new EntityController<Zombie>() {
                },
                transport
        );

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getLocation()).thenReturn(
                new Location(Mockito.mock(World.class), 12.0D, 65.0D, -4.0D, 90.0F, 10.0F)
        );
        when(zombie.getVelocity()).thenReturn(new Vector(1.5D, 0.0D, -0.5D));
        lifecycle.bind(zombie);
        lifecycle.setNetworkController(new EntityNetworkController<Zombie>() {
        });
        return lifecycle;
    }

    @SuppressWarnings("unused")
    private static final class FakeTrackerStateHandle {
        private int tickCounter;
        private int timeSinceLocationSync;
        private Consumer<Object> broadcastMethod;
        private List<Object> opt_passengers = new ArrayList<Object>();
        private Object opt_vehicle;
    }

    private static final class FakeReplacementSupport implements PaperTrackingBindingStrategy_1_21_plus.ReplacementSupport {
        private final Object trackerEntryHandle;
        private final Object trackerStateHandle;
        private final boolean installHook;

        private FakeReplacementSupport(Object trackerEntryHandle, Object trackerStateHandle, boolean installHook) {
            this.trackerEntryHandle = trackerEntryHandle;
            this.trackerStateHandle = trackerStateHandle;
            this.installHook = installHook;
        }

        @Override
        public @NotNull PaperTrackingBindingStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                @NotNull Object replacementHandle
        ) {
            return new PaperTrackingBindingStrategy_1_21_plus.TrackingHandles(trackerEntryHandle, trackerStateHandle);
        }

        @Override
        public boolean installReplacementTrackingHook(@NotNull Object replacementHandle, @NotNull ModernTrackerHook trackerHook) {
            return installHook;
        }
    }

    private static final class RecordingTransport implements EntityTransport {
        private final List<String> operations = new ArrayList<String>();

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
            operations.add(operation + ":" + (request.targetsTrackedViewers() ? "tracked" : "viewer"));
        }
    }
}
