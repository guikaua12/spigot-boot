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
package tech.guilhermekaua.spigotboot.entity.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.EntityTransportRequest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

class EntityTransportContractTest {

    @Test
    void shouldDeclareEverySemanticTransportOperationAndKeepItInsideRuntimePackages() {
        Set<String> operations = new HashSet<String>();
        for (Method method : EntityTransport.class.getDeclaredMethods()) {
            operations.add(method.getName());
        }

        assertEquals(
                new HashSet<String>(Arrays.asList(
                        "spawnForViewer",
                        "destroyForViewer",
                        "syncRelativeMove",
                        "syncAbsoluteMove",
                        "syncRotation",
                        "syncHeadRotation",
                        "syncVelocity",
                        "syncPassengersOrVehicle",
                        "sendInitialMetadataSnapshot",
                        "sendDirtyMetadataDelta",
                        "sendLivingInitialization"
                )),
                operations
        );
        assertEquals(
                "tech.guilhermekaua.spigotboot.entity.runtime.network.transport",
                EntityTransport.class.getPackage().getName()
        );
        assertFalse(referencesTransportType(ControlledEntity.class));
        assertFalse(referencesTransportType(EntityNetworkController.class));
    }

    @Test
    void shouldDispatchLateViewerSnapshotAndDestroyInExpectedOrder() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createLifecycle(transport);
        Player viewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(viewer);
        lifecycle.unregisterViewer(viewer);

        assertEquals(
                Arrays.asList(
                        "spawnForViewer:viewer",
                        "sendInitialMetadataSnapshot:viewer",
                        "sendLivingInitialization:viewer",
                        "syncVelocity:viewer",
                        "syncPassengersOrVehicle:viewer",
                        "syncHeadRotation:viewer",
                        "destroyForViewer:viewer"
                ),
                transport.operations
        );
        assertEquals(lifecycle.networkState().liveX(), lifecycle.networkState().syncedX(), 0.0D);
        assertEquals(lifecycle.networkState().liveYaw(), lifecycle.networkState().syncedYaw(), 0.0F);
        assertEquals(lifecycle.networkState().liveHeadYaw(), lifecycle.networkState().syncedHeadYaw(), 0.0F);
    }

    @Test
    void shouldPreferAbsoluteSyncOverRelativeSyncDuringTickDispatch() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createLifecycle(transport);
        Player viewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(viewer);
        transport.operations.clear();

        lifecycle.networkState().setSyncedPosition(0.0D, 0.0D, 0.0D);
        lifecycle.networkState().setSyncedRotation(0.0F, 0.0F);
        lifecycle.networkState().setSyncedVelocity(0.0D, 0.0D, 0.0D);
        lifecycle.networkState().setSyncedHeadYaw(0.0F);
        lifecycle.networkState().setTicksSinceAbsoluteSync(EntityNetworkController.ABSOLUTE_RESYNC_INTERVAL);

        lifecycle.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Zombie>, Void>() {
            @Override
            public Void invoke(@NotNull EntityTickContext<Zombie> context) {
                return null;
            }
        });

        assertEquals(
                Arrays.asList(
                        "syncPassengersOrVehicle:tracked",
                        "syncAbsoluteMove:tracked",
                        "syncRotation:tracked",
                        "syncVelocity:tracked",
                        "sendDirtyMetadataDelta:tracked",
                        "syncHeadRotation:tracked"
                ),
                transport.operations
        );
        assertEquals(0, lifecycle.networkState().ticksSinceAbsoluteSync());
    }

    @Test
    void shouldDispatchRelativeMovementAndMetadataDeltaInExpectedOrder() {
        RecordingTransport transport = new RecordingTransport();
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createLifecycle(transport);
        Player viewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(viewer);
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

    private static boolean referencesTransportType(@NotNull Class<?> type) {
        for (Method method : type.getMethods()) {
            if (isTransportType(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (isTransportType(parameterType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTransportType(@NotNull Class<?> type) {
        Package declaredPackage = type.getPackage();
        return declaredPackage != null
                && declaredPackage.getName().startsWith("tech.guilhermekaua.spigotboot.entity.runtime.network.transport");
    }

    private static RuntimeAttachedEntityLifecycle<Zombie> createLifecycle(EntityTransport transport) {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 21, 11),
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
        return lifecycle;
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
