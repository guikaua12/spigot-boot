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
package tech.guilhermekaua.spigotboot.entity.runtime.network.transport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionTransportProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.ActiveEffectsSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EquipmentSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingAttribute;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingAttributeSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherDelta;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherItem;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherPayload;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityTrackerHookFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityTransportFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class ModernPacketTransportV1_14PlusTest {

    @Test
    void shouldResolveOneSharedBackendPerModernFamilyAndOnlySplitProbeDrivenPaperOverlays() {
        EntityNetworkMetadataContract metadataContract = EntityNetworkMetadataContract.of("modern-test");

        EntityTransport modern14 = EntityTransportResolver.resolve(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 16, 5), RuntimeServerFlavor.SPIGOT, false, false, false),
                bundle(EntityTransportFamily.MODERN_1_14_TO_1_16_5),
                new TransportOnlyAdapter(new RecordingSupport("modern-1_14-shared", EntityTransportFamily.MODERN_1_14_TO_1_16_5)),
                metadataContract
        );
        EntityTransport modern17 = EntityTransportResolver.resolve(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 17, 1), RuntimeServerFlavor.SPIGOT, true, false, false),
                bundle(EntityTransportFamily.MODERN_1_17_TO_1_18_2),
                new TransportOnlyAdapter(new RecordingSupport("modern-1_17-shared", EntityTransportFamily.MODERN_1_17_TO_1_18_2)),
                metadataContract
        );
        EntityTransport modern19Spigot = EntityTransportResolver.resolve(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 19, 2), RuntimeServerFlavor.SPIGOT, true, false, false),
                bundle(EntityTransportFamily.MODERN_1_19_2_TO_1_20_6),
                new OverlayTransportAdapter(
                        new RecordingSupport("modern-1_19-spigot", EntityTransportFamily.MODERN_1_19_2_TO_1_20_6),
                        new RecordingSupport("modern-1_19-paper", EntityTransportFamily.MODERN_1_19_2_TO_1_20_6)
                ),
                metadataContract
        );
        EntityTransport modern19Paper = EntityTransportResolver.resolve(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 19, 2), RuntimeServerFlavor.PAPER, true, true, false),
                bundle(EntityTransportFamily.MODERN_1_19_2_TO_1_20_6),
                new OverlayTransportAdapter(
                        new RecordingSupport("modern-1_19-spigot", EntityTransportFamily.MODERN_1_19_2_TO_1_20_6),
                        new RecordingSupport("modern-1_19-paper", EntityTransportFamily.MODERN_1_19_2_TO_1_20_6)
                ),
                metadataContract
        );
        EntityTransport latest21Paper = EntityTransportResolver.resolve(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.PAPER, true, false, true),
                bundle(EntityTransportFamily.LATEST_1_21_X),
                new OverlayTransportAdapter(
                        new RecordingSupport("latest-1_21-spigot", EntityTransportFamily.LATEST_1_21_X),
                        new RecordingSupport("latest-1_21-paper", EntityTransportFamily.LATEST_1_21_X)
                ),
                metadataContract
        );

        assertInstanceOf(ModernEntityTransportV1_14To1_16_5.class, modern14);
        assertInstanceOf(ModernEntityTransportV1_17To1_18_2.class, modern17);
        assertInstanceOf(ModernEntityTransportV1_19_2To1_20_6.class, modern19Spigot);
        assertInstanceOf(ModernEntityTransportV1_19_2To1_20_6.class, modern19Paper);
        assertInstanceOf(LatestEntityTransportV1_21_X.class, latest21Paper);
        assertEquals("modern-1_14-shared", ((AbstractModernEntityTransport) modern14).support().id());
        assertEquals("modern-1_17-shared", ((AbstractModernEntityTransport) modern17).support().id());
        assertEquals("modern-1_19-spigot", ((AbstractModernEntityTransport) modern19Spigot).support().id());
        assertEquals("modern-1_19-paper", ((AbstractModernEntityTransport) modern19Paper).support().id());
        assertEquals("latest-1_21-paper", ((AbstractModernEntityTransport) latest21Paper).support().id());
    }

    @Test
    void shouldRouteViewerSnapshotsAndTrackedTicksThroughTheSharedModernBackend() {
        RecordingSupport support = new RecordingSupport("modern-1_17-shared", EntityTransportFamily.MODERN_1_17_TO_1_18_2);
        EntityTransport transport = new ModernEntityTransportV1_17To1_18_2(
                support,
                EntityNetworkMetadataContract.of("modern-test")
        );
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createLifecycle(transport);
        Player viewer = Mockito.mock(Player.class);

        lifecycle.registerViewer(viewer);
        support.operations.clear();
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
                        "tracked:passengers:1",
                        "tracked:relative",
                        "tracked:rotation",
                        "tracked:velocity",
                        "tracked:metadata-dirty:1",
                        "tracked:head:45.0"
                ),
                support.operations
        );
    }

    private static EntityNetworkRuntimeBundle bundle(@NotNull EntityTransportFamily family) {
        return new EntityNetworkRuntimeBundle(
                EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                EntityPublicationFamily.SECTION_MANAGER,
                family,
                family == EntityTransportFamily.MODERN_1_14_TO_1_16_5
                        ? EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5
                        : family == EntityTransportFamily.LATEST_1_21_X
                        ? EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X
                        : EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6
        );
    }

    private static RuntimeAttachedEntityLifecycle<Zombie> createLifecycle(EntityTransport transport) {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 17, 1),
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

    private static class TransportOnlyAdapter implements EntityVersionAdapter,
            EntityVersionTransportProvider,
            EntityVersionNetworkMetadataProvider,
            EntityVersionMetadataProvider {
        private final ModernTransportSupport support;

        private TransportOnlyAdapter(@NotNull ModernTransportSupport support) {
            this.support = support;
        }

        @Override
        public @NotNull ModernTransportSupport entityTransportSupport(@NotNull EntityRuntimeProfile runtimeProfile) {
            return support;
        }

        @Override
        public @NotNull EntityNetworkMetadataContract entityNetworkMetadataContract() {
            return EntityNetworkMetadataContract.of("modern-test");
        }

        @Override
        public @NotNull EntityVersionCapabilities entityCapabilities() {
            return EntityVersionCapabilities.unspecified();
        }

        @Override
        public @NotNull EntityVersionBindings entityBindings() {
            return EntityVersionBindings.unspecified();
        }

        @Override
        public @NotNull MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 14, 0);
        }

        @Override
        public @NotNull MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public boolean supports(@NotNull CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> @NotNull ControlledEntity<T> attach(
                @NotNull T entity,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class OverlayTransportAdapter extends TransportOnlyAdapter {
        private final ModernTransportSupport spigotSupport;
        private final ModernTransportSupport paperSupport;

        private OverlayTransportAdapter(@NotNull ModernTransportSupport spigotSupport, @NotNull ModernTransportSupport paperSupport) {
            super(spigotSupport);
            this.spigotSupport = spigotSupport;
            this.paperSupport = paperSupport;
        }

        @Override
        public @NotNull ModernTransportSupport entityTransportSupport(@NotNull EntityRuntimeProfile runtimeProfile) {
            return runtimeProfile.paperChunkSystemAvailable() || runtimeProfile.paperMoonriseChunkSystemAvailable()
                    ? paperSupport
                    : spigotSupport;
        }
    }

    private static final class RecordingSupport implements ModernTransportSupport {
        private final String id;
        private final EntityTransportFamily family;
        private final List<String> operations = new ArrayList<String>();

        private RecordingSupport(@NotNull String id, @NotNull EntityTransportFamily family) {
            this.id = id;
            this.family = family;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public @NotNull EntityTransportFamily family() {
            return family;
        }

        @Override
        public @NotNull EntityNetworkMetadataSource metadataSource(@NotNull EntityTransportRequest request) {
            return new EntityNetworkMetadataSource() {
                @Override
                public @NotNull WatcherPayload watcherPayload() {
                    return new WatcherPayload(Collections.singletonList(new WatcherItem(0, "raw", "initial")));
                }

                @Override
                public @NotNull WatcherDelta dirtyWatcherDelta() {
                    return new WatcherDelta(Collections.singletonList(new WatcherItem(0, "raw", "dirty")));
                }

                @Override
                public @NotNull LivingEntityMetadata livingMetadata() {
                    return new LivingEntityMetadata(
                            new LivingAttributeSnapshot(Collections.singletonList(new LivingAttribute("max_health", 20.0D))),
                            EquipmentSnapshot.empty(),
                            ActiveEffectsSnapshot.empty()
                    );
                }

                @Override
                public @NotNull HeadRotation headRotation() {
                    return HeadRotation.of(45.0F);
                }

                @Override
                public @NotNull PassengerVehicleState passengerVehicleState() {
                    return new PassengerVehicleState(null, Collections.singletonList(Integer.valueOf(77)));
                }
            };
        }

        @Override
        public Object createSpawnPacket(@NotNull EntityTransportRequest request) {
            return "spawn";
        }

        @Override
        public Object createDestroyPacket(@NotNull EntityTransportRequest request) {
            return "destroy";
        }

        @Override
        public Object createRelativeMovePacket(@NotNull EntityTransportRequest request) {
            return "relative";
        }

        @Override
        public Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request) {
            return "absolute";
        }

        @Override
        public Object createRotationPacket(@NotNull EntityTransportRequest request) {
            return "rotation";
        }

        @Override
        public Object createHeadRotationPacket(@NotNull EntityTransportRequest request, @NotNull HeadRotation headRotation) {
            return "head:" + headRotation.yaw();
        }

        @Override
        public Object createVelocityPacket(@NotNull EntityTransportRequest request) {
            return "velocity";
        }

        @Override
        public @NotNull List<Object> createPassengerVehiclePackets(
                @NotNull EntityTransportRequest request,
                @NotNull PassengerVehicleState state
        ) {
            return Collections.<Object>singletonList("passengers:" + state.passengerEntityIds().size());
        }

        @Override
        public Object createMetadataPacket(
                @NotNull EntityTransportRequest request,
                @NotNull List<WatcherItem> items,
                boolean initialSnapshot
        ) {
            return (initialSnapshot ? "metadata-init:" : "metadata-dirty:") + items.size();
        }

        @Override
        public @NotNull List<Object> createLivingInitializationPackets(
                @NotNull EntityTransportRequest request,
                @NotNull LivingEntityMetadata livingMetadata
        ) {
            return Collections.<Object>singletonList("living:" + livingMetadata.attributes().attributes().size());
        }

        @Override
        public void sendPacket(@NotNull Player viewer, @NotNull Object packet) {
            operations.add("viewer:" + packet);
        }

        @Override
        public void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet) {
            operations.add("tracked:" + packet);
        }
    }
}
