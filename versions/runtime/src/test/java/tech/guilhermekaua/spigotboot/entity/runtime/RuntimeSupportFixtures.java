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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionLegacyTransportProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionTransportProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherItem;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.EntityTransportRequest;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.LegacyTransportSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.ModernTransportSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityTransportFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.tracker.legacy.LegacyTrackerEntryHook;
import tech.guilhermekaua.spigotboot.entity.runtime.tracker.legacy.LegacyTrackerHookSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.tracker.legacy.LegacyTrackerViewabilitySnapshot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuntimeSupportFixtures {
    private static final LegacyTrackerHookSupport LEGACY_TRACKER_HOOK_SUPPORT = new NoOpLegacyTrackerHookSupport();
    private static final LegacyTransportSupport LEGACY_TRANSPORT_SUPPORT = new NoOpLegacyTransportSupport();

    private RuntimeSupportFixtures() {
    }

    public static @NotNull EntityVersionCapabilities legacyCapabilities() {
        return new EntityVersionCapabilities(
                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                false,
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                EntityWorldRegistrationMode.REFERENCE_REWRITE
        );
    }

    public static @NotNull EntityVersionCapabilities modernCapabilities() {
        return new EntityVersionCapabilities(
                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                true,
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                EntityWorldRegistrationMode.REFERENCE_REWRITE
        );
    }

    public static @NotNull EntityVersionBindings legacyBindings() {
        return new EntityVersionBindings(
                new EntityFreshSpawnBinding(
                        Arrays.asList(
                                NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                NativeEntityConstructorShape.LEVEL_ONLY
                        ),
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        true,
                        false
                ),
                new EntityReplacementBinding(
                        EntityWorldRegistrationMode.REFERENCE_REWRITE,
                        true,
                        false
                )
        );
    }

    public static @NotNull EntityVersionBindings modernBindings() {
        return new EntityVersionBindings(
                new EntityFreshSpawnBinding(
                        Arrays.asList(
                                NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                                NativeEntityConstructorShape.LEVEL_ONLY
                        ),
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        true,
                        true
                ),
                new EntityReplacementBinding(
                        EntityWorldRegistrationMode.REFERENCE_REWRITE,
                        true,
                        true
                )
        );
    }

    public static @NotNull EntityTransportFamily transportFamily(@NotNull MinecraftVersion version) {
        if (version.compareTo(MinecraftVersion.of(1, 14, 0)) < 0) {
            return EntityTransportFamily.LEGACY_1_8_TO_1_13_2;
        }
        if (version.compareTo(MinecraftVersion.of(1, 17, 0)) < 0) {
            return EntityTransportFamily.MODERN_1_14_TO_1_16_5;
        }
        if (version.compareTo(MinecraftVersion.of(1, 19, 2)) < 0) {
            return EntityTransportFamily.MODERN_1_17_TO_1_18_2;
        }
        if (version.compareTo(MinecraftVersion.of(1, 21, 0)) < 0) {
            return EntityTransportFamily.MODERN_1_19_2_TO_1_20_6;
        }
        return EntityTransportFamily.LATEST_1_21_X;
    }

    public static @NotNull EntityNetworkMetadataContract metadataContract(@NotNull MinecraftVersion version) {
        if (version.compareTo(MinecraftVersion.of(1, 13, 0)) < 0) {
            return EntityNetworkMetadataContract.of("legacy-datawatcher-test");
        }
        if (version.compareTo(MinecraftVersion.of(1, 14, 0)) < 0) {
            return EntityNetworkMetadataContract.of("transitional-datawatcher-test");
        }
        if (version.compareTo(MinecraftVersion.of(1, 17, 0)) < 0) {
            return EntityNetworkMetadataContract.of(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5.id());
        }
        if (version.compareTo(MinecraftVersion.of(1, 21, 0)) < 0) {
            return EntityNetworkMetadataContract.of(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6.id());
        }
        return EntityNetworkMetadataContract.of(EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X.id());
    }

    public static @NotNull ModernTransportSupport modernTransportSupport(@NotNull MinecraftVersion version) {
        return new NoOpModernTransportSupport(transportFamily(version));
    }

    public static @NotNull LegacyTransportSupport legacyTransportSupport() {
        return LEGACY_TRANSPORT_SUPPORT;
    }

    public static @NotNull LegacyTrackerHookSupport legacyTrackerHookSupport() {
        return LEGACY_TRACKER_HOOK_SUPPORT;
    }

    public static class MetadataOnlyAdapter implements EntityVersionAdapter, EntityVersionMetadataProvider {
        private final MinecraftVersion minimumVersion;
        private final MinecraftVersion maximumVersion;
        private final EntityVersionCapabilities capabilities;
        private final EntityVersionBindings bindings;

        public MetadataOnlyAdapter(
                @NotNull MinecraftVersion minimumVersion,
                @NotNull MinecraftVersion maximumVersion,
                @NotNull EntityVersionCapabilities capabilities,
                @NotNull EntityVersionBindings bindings
        ) {
            this.minimumVersion = minimumVersion;
            this.maximumVersion = maximumVersion;
            this.capabilities = capabilities;
            this.bindings = bindings;
        }

        @Override
        public @NotNull MinecraftVersion minimumVersion() {
            return minimumVersion;
        }

        @Override
        public @NotNull MinecraftVersion maximumVersion() {
            return maximumVersion;
        }

        @Override
        public @NotNull EntityVersionCapabilities entityCapabilities() {
            return capabilities;
        }

        @Override
        public @NotNull EntityVersionBindings entityBindings() {
            return bindings;
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
            throw unsupported();
        }

        @Override
        public <T extends Entity> @NotNull ControlledEntity<T> attach(
                @NotNull T entity,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw unsupported();
        }

        protected final @NotNull UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("RuntimeSupportFixtures test adapter does not execute spawn or attach.");
        }
    }

    public static class SupportedMetadataAdapter extends MetadataOnlyAdapter
            implements EntityVersionNetworkMetadataProvider,
            EntityVersionTransportProvider,
            EntityVersionLegacyTransportProvider,
            PaperFreshSpawnStrategy_1_21_plus.Provider,
            LegacyFreshSpawnStrategy_1_8_to_1_12.Provider,
            PaperReplacementStrategy_1_21_plus.Provider,
            LegacyReplacementStrategy_1_8_to_1_12.Provider {
        private final EntityNetworkMetadataContract networkMetadataContract;

        public SupportedMetadataAdapter(
                @NotNull MinecraftVersion minimumVersion,
                @NotNull MinecraftVersion maximumVersion,
                @NotNull EntityVersionCapabilities capabilities,
                @NotNull EntityVersionBindings bindings,
                @NotNull EntityNetworkMetadataContract networkMetadataContract
        ) {
            super(minimumVersion, maximumVersion, capabilities, bindings);
            this.networkMetadataContract = networkMetadataContract;
        }

        @Override
        public @NotNull EntityNetworkMetadataContract entityNetworkMetadataContract() {
            return networkMetadataContract;
        }

        @Override
        public @NotNull ModernTransportSupport entityTransportSupport(@NotNull tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile runtimeProfile) {
            return modernTransportSupport(runtimeProfile.minecraftVersion());
        }

        @Override
        public @NotNull LegacyTransportSupport legacyTransportSupport() {
            return RuntimeSupportFixtures.legacyTransportSupport();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport() {
            return NoOpPaperFreshSpawnSupport.INSTANCE;
        }

        @Override
        public @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.Support legacyFreshSpawnSupport() {
            return NoOpLegacyFreshSpawnSupport.INSTANCE;
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
            return NoOpPaperReplacementSupport.INSTANCE;
        }

        @Override
        public @NotNull LegacyReplacementStrategy_1_8_to_1_12.Support legacyReplacementSupport() {
            return NoOpLegacyReplacementSupport.INSTANCE;
        }
    }

    private static final class NoOpLegacyTrackerHookSupport implements LegacyTrackerHookSupport {
        @Override
        public @NotNull String overlayId() {
            return "test-legacy-tracker-hook";
        }

        @Override
        public void installHook(@NotNull Object trackerEntryHandle, @NotNull LegacyTrackerEntryHook hook) {
        }

        @Override
        public @Nullable Player resolveViewer(@NotNull Object rawViewer) {
            return null;
        }

        @Override
        public @NotNull LegacyTrackerViewabilitySnapshot describeViewability(
                @NotNull Object trackerEntryHandle,
                @NotNull Object rawViewer
        ) {
            return new LegacyTrackerViewabilitySnapshot(false, false, false, false, false, true, true, true);
        }
    }

    private static final class NoOpModernTransportSupport implements ModernTransportSupport {
        private final EntityTransportFamily family;

        private NoOpModernTransportSupport(@NotNull EntityTransportFamily family) {
            this.family = family;
        }

        @Override
        public @NotNull String id() {
            return family.id() + "-test";
        }

        @Override
        public @NotNull EntityTransportFamily family() {
            return family;
        }

        @Override
        public @NotNull EntityNetworkMetadataSource metadataSource(@NotNull EntityTransportRequest request) {
            return new EntityNetworkMetadataSource() {
            };
        }

        @Override
        public @Nullable Object createSpawnPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createDestroyPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createRelativeMovePacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createRotationPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createHeadRotationPacket(
                @NotNull EntityTransportRequest request,
                @NotNull HeadRotation headRotation
        ) {
            return null;
        }

        @Override
        public @Nullable Object createVelocityPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @NotNull List<Object> createPassengerVehiclePackets(
                @NotNull EntityTransportRequest request,
                @NotNull PassengerVehicleState state
        ) {
            return Collections.emptyList();
        }

        @Override
        public @Nullable Object createMetadataPacket(
                @NotNull EntityTransportRequest request,
                @NotNull List<WatcherItem> items,
                boolean initialSnapshot
        ) {
            return null;
        }

        @Override
        public @NotNull List<Object> createLivingInitializationPackets(
                @NotNull EntityTransportRequest request,
                @NotNull LivingEntityMetadata livingMetadata
        ) {
            return Collections.emptyList();
        }

        @Override
        public void sendPacket(@NotNull Player viewer, @NotNull Object packet) {
        }

        @Override
        public void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet) {
        }
    }

    private static final class NoOpLegacyTransportSupport implements LegacyTransportSupport {
        @Override
        public @NotNull String id() {
            return "legacy-transport-test";
        }

        @Override
        public @NotNull EntityNetworkMetadataSource metadataSource(@NotNull EntityTransportRequest request) {
            return new EntityNetworkMetadataSource() {
            };
        }

        @Override
        public @Nullable Object createSpawnPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createDestroyPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createRelativeMovePacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createRotationPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @Nullable Object createHeadRotationPacket(
                @NotNull EntityTransportRequest request,
                @NotNull HeadRotation headRotation
        ) {
            return null;
        }

        @Override
        public @Nullable Object createVelocityPacket(@NotNull EntityTransportRequest request) {
            return null;
        }

        @Override
        public @NotNull List<Object> createPassengerVehiclePackets(
                @NotNull EntityTransportRequest request,
                @NotNull PassengerVehicleState state
        ) {
            return Collections.emptyList();
        }

        @Override
        public @Nullable Object createMetadataPacket(
                @NotNull EntityTransportRequest request,
                @NotNull List<WatcherItem> items,
                boolean initialSnapshot
        ) {
            return null;
        }

        @Override
        public @NotNull List<Object> createLivingInitializationPackets(
                @NotNull EntityTransportRequest request,
                @NotNull LivingEntityMetadata livingMetadata
        ) {
            return Collections.emptyList();
        }

        @Override
        public void sendPacket(@NotNull Player viewer, @NotNull Object packet) {
        }

        @Override
        public void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet) {
        }
    }

    private static final class NoOpPaperFreshSpawnSupport implements PaperFreshSpawnStrategy_1_21_plus.Support {
        private static final NoOpPaperFreshSpawnSupport INSTANCE = new NoOpPaperFreshSpawnSupport();

        @Override
        public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Object createNativeEntity(
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull Location location
        ) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
            throw unsupported();
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            throw unsupported();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(@NotNull Object nativeEntity) {
            return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(null, null);
        }
    }

    private static final class NoOpPaperReplacementSupport implements PaperReplacementStrategy_1_21_plus.Support {
        private static final NoOpPaperReplacementSupport INSTANCE = new NoOpPaperReplacementSupport();

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
        ) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw unsupported();
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public void rebindModernBukkitBridge(
                @NotNull Entity entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }

        @Override
        public void replaceModernWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public void rewireModernVehicleAndPassengerReferences(
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }

        @Override
        public void refreshModernBukkitWrappers(@NotNull Entity entity) {
            throw unsupported();
        }

        @Override
        public void markModernEntityRemoved(@NotNull Object currentNativeHandle) {
            throw unsupported();
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                @NotNull Object replacementHandle
        ) {
            return new PaperReplacementStrategy_1_21_plus.TrackingHandles(null, null);
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }
    }

    private static final class NoOpLegacyFreshSpawnSupport implements LegacyFreshSpawnStrategy_1_8_to_1_12.Support {
        private static final NoOpLegacyFreshSpawnSupport INSTANCE = new NoOpLegacyFreshSpawnSupport();

        @Override
        public <T extends Entity> LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Object createNativeEntity(
                @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                @NotNull Location location
        ) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
            throw unsupported();
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            throw unsupported();
        }

        @Override
        public @Nullable Object resolveTrackerEntryHandle(@NotNull Object nativeEntity) {
            return null;
        }

        @Override
        public @NotNull LegacyTrackerHookSupport legacyTrackerHookSupport() {
            return RuntimeSupportFixtures.legacyTrackerHookSupport();
        }
    }

    private static final class NoOpLegacyReplacementSupport implements LegacyReplacementStrategy_1_8_to_1_12.Support {
        private static final NoOpLegacyReplacementSupport INSTANCE = new NoOpLegacyReplacementSupport();

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
        ) {
            throw unsupported();
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw unsupported();
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public void rebindLegacyBukkitBridge(
                @NotNull Entity entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }

        @Override
        public void replaceLegacyWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public void rewireLegacyVehicleAndPassengerReferences(
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }

        @Override
        public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
            throw unsupported();
        }

        @Override
        public void markLegacyEntityRemoved(@NotNull Object currentNativeHandle) {
            throw unsupported();
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            throw unsupported();
        }

        @Override
        public @Nullable Object resolveTrackerEntryHandle(@NotNull Object replacementHandle) {
            return null;
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            throw unsupported();
        }

        @Override
        public @NotNull LegacyTrackerHookSupport legacyTrackerHookSupport() {
            return RuntimeSupportFixtures.legacyTrackerHookSupport();
        }
    }

    private static @NotNull UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("RuntimeSupportFixtures no-op support should not execute in this test.");
    }
}
