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
package tech.guilhermekaua.spigotboot.v1_16_5.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.ModernTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.ReflectiveModernTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

import java.util.Objects;

/**
 * Native custom version adapter scaffold for the Minecraft 1.14-1.16.5 family.
 *
 * @since 2.0.2
 */
public final class SpigotVersionAdapterV1_16_5
        implements VersionAdapter,
        VersionNetworkMetadataProvider,
        VersionTransportProvider,
        VersionMetadataProvider,
        VersionGoalSupportProvider,
        PaperTrackingBindingStrategy_1_21_plus.Provider,
        PaperFreshSpawnStrategy_1_21_plus.Provider,
        PaperReplacementStrategy_1_21_plus.Provider {
    private static final MinecraftVersion MINIMUM_VERSION = MinecraftVersion.of(1, 14, 0);
    private static final MinecraftVersion MAXIMUM_VERSION = MinecraftVersion.of(1, 16, 5);
    private static final EntityNetworkMetadataContract NETWORK_METADATA_CONTRACT = EntityNetworkMetadataContract.of(
            EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5.id()
    );
    private static final ModernTransportSupport TRANSPORT_SUPPORT = new ReflectiveModernTransportSupport(
            EntityTransportFamily.MODERN_1_14_TO_1_16_5.id(),
            EntityTransportFamily.MODERN_1_14_TO_1_16_5,
            ReflectiveModernTransportSupport.MetadataPacketMode.DATA_WATCHER_WITH_FLAG,
            false
    );

    private volatile EntityFactoryV1_16_5 entrypoint;

    @Override
    public @NotNull MinecraftVersion minimumVersion() {
        return MINIMUM_VERSION;
    }

    @Override
    public @NotNull MinecraftVersion maximumVersion() {
        return MAXIMUM_VERSION;
    }

    @Override
    public @NotNull VersionCapabilities entityCapabilities() {
        return EntityFactoryV1_16_5.entityCapabilities();
    }

    @Override
    public @NotNull VersionBindings entityBindings() {
        return EntityFactoryV1_16_5.entityBindings();
    }

    @Override
    public @NotNull VersionGoalSupportMetadata entityGoalSupportMetadata() {
        return EntityGoalSupportV1_16_5.metadata();
    }

    @Override
    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnGoalMutationExecutor(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        return EntityGoalSupportV1_16_5.createSpawnExecutor(template, minecraftVersion);
    }

    @Override
    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedGoalMutationExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        return EntityGoalSupportV1_16_5.createAttachedExecutor(baseType, entity, minecraftVersion);
    }

    @Override
    public @NotNull EntityNetworkMetadataContract entityNetworkMetadataContract() {
        return NETWORK_METADATA_CONTRACT;
    }

    @Override
    public @NotNull ModernTransportSupport entityTransportSupport(@NotNull VersionRuntimeProfile runtimeProfile) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        return TRANSPORT_SUPPORT;
    }

    @Override
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return entrypoint().supports(baseType);
    }

    @Override
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        return entrypoint().spawn(template, spawnOptions, lifecycle);
    }

    @Override
    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        return entrypoint().attach(entity, lifecycle);
    }

    @Override
    public @NotNull PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport() {
        return entrypoint();
    }

    @Override
    public @NotNull PaperTrackingBindingStrategy_1_21_plus.Support paperTrackingBindingSupport() {
        return entrypoint();
    }

    @Override
    public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
        return entrypoint();
    }

    private @NotNull EntityFactoryV1_16_5 entrypoint() {
        EntityFactoryV1_16_5 resolvedEntrypoint = entrypoint;
        if (resolvedEntrypoint != null) {
            return resolvedEntrypoint;
        }

        synchronized (this) {
            if (entrypoint == null) {
                entrypoint = new EntityFactoryV1_16_5();
            }
            return entrypoint;
        }
    }
}
