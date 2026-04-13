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
package tech.guilhermekaua.spigotboot.entity.v1_8_8;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.EntityVersionEntrypoint;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;

import java.util.Objects;

/**
 * Native custom entity adapter for Minecraft 1.8.8.
 *
 * @since 2.0.2
 */
public final class SpigotEntityAdapterV1_8_8
        implements EntityVersionAdapter,
        EntityVersionMetadataProvider,
        LegacyFreshSpawnStrategy_1_8_to_1_12.Provider,
        LegacyReplacementStrategy_1_8_to_1_12.Provider {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 8, 8);

    private final LegacyFreshSpawnStrategy_1_8_to_1_12.Support legacyFreshSpawnSupport =
            new LegacyFreshSpawnStrategy_1_8_to_1_12.Support() {
                @Override
                public <T extends Entity> @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
                        @NotNull EntityTemplate<T> template,
                        @NotNull SpawnOptions spawnOptions
                ) {
                    return resolvedLegacyFreshSpawnSupport().prepareFreshSpawn(template, spawnOptions);
                }

                @Override
                public @NotNull Object createNativeEntity(
                        @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                        @NotNull Location location
                ) {
                    return resolvedLegacyFreshSpawnSupport().createNativeEntity(preparedSpawn, location);
                }

                @Override
                public <T extends Entity> void bindLifecycleToNativeEntity(
                        @NotNull Object nativeEntity,
                        @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    resolvedLegacyFreshSpawnSupport().bindLifecycleToNativeEntity(nativeEntity, preparedSpawn, lifecycle);
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
                    return resolvedLegacyFreshSpawnSupport().resolveBukkitWrapper(nativeEntity);
                }

                @Override
                public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
                    return resolvedLegacyFreshSpawnSupport().resolveNativeWorldHandle(location);
                }

                @Override
                public Object resolveTrackerEntryHandle(@NotNull Object nativeEntity) {
                    return resolvedLegacyFreshSpawnSupport().resolveTrackerEntryHandle(nativeEntity);
                }
            };
    private final LegacyReplacementStrategy_1_8_to_1_12.Support legacyReplacementSupport =
            new LegacyReplacementStrategy_1_8_to_1_12.Support() {
                @Override
                public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
                    return resolvedLegacyReplacementSupport().resolveCurrentNativeHandle(entity);
                }

                @Override
                public <T extends Entity> @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement prepareReplacement(
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle
                ) {
                    return resolvedLegacyReplacementSupport().prepareReplacement(entity, currentNativeHandle);
                }

                @Override
                public @NotNull Object allocateReplacementHandle(
                        @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
                ) {
                    return resolvedLegacyReplacementSupport().allocateReplacementHandle(preparedReplacement);
                }

                @Override
                public <T extends Entity> void bindLifecycleToReplacement(
                        @NotNull Object replacementHandle,
                        @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    resolvedLegacyReplacementSupport().bindLifecycleToReplacement(
                            replacementHandle,
                            preparedReplacement,
                            lifecycle
                    );
                }

                @Override
                public void rebindBukkitZombie(
                        @NotNull Entity entity,
                        @NotNull Object replacementHandle
                ) {
                    resolvedLegacyReplacementSupport().rebindBukkitZombie(entity, replacementHandle);
                }

                @Override
                public void rebindLegacyBukkitBridge(
                        @NotNull Entity entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedLegacyReplacementSupport().rebindLegacyBukkitBridge(
                            entity,
                            currentNativeHandle,
                            replacementHandle
                    );
                }

                @Override
                public void replaceLegacyWorldReferences(
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedLegacyReplacementSupport().replaceLegacyWorldReferences(currentNativeHandle, replacementHandle);
                }

                @Override
                public void rewireLegacyVehicleAndPassengerReferences(
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedLegacyReplacementSupport().rewireLegacyVehicleAndPassengerReferences(
                            currentNativeHandle,
                            replacementHandle
                    );
                }

                @Override
                public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
                    resolvedLegacyReplacementSupport().refreshLegacyBukkitWrappers(entity);
                }

                @Override
                public void markLegacyEntityRemoved(@NotNull Object currentNativeHandle) {
                    resolvedLegacyReplacementSupport().markLegacyEntityRemoved(currentNativeHandle);
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
                    return resolvedLegacyReplacementSupport().resolveBukkitWrapper(replacementHandle);
                }

                @Override
                public Object resolveTrackerEntryHandle(@NotNull Object replacementHandle) {
                    return resolvedLegacyReplacementSupport().resolveTrackerEntryHandle(replacementHandle);
                }

                @Override
                public <T extends Entity> void scheduleRepairPass(
                        @NotNull NativeEntityLifecycle<T> lifecycle,
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedLegacyReplacementSupport().scheduleRepairPass(
                            lifecycle,
                            entity,
                            currentNativeHandle,
                            replacementHandle
                    );
                }
            };

    private volatile EntityVersionEntrypoint entrypoint;

    @Override
    public @NotNull MinecraftVersion minimumVersion() {
        return VERSION;
    }

    @Override
    public @NotNull MinecraftVersion maximumVersion() {
        return VERSION;
    }

    @Override
    public @NotNull EntityVersionCapabilities entityCapabilities() {
        return EntityFactoryV1_8_8.entityCapabilities();
    }

    @Override
    public @NotNull EntityVersionBindings entityBindings() {
        return EntityFactoryV1_8_8.entityBindings();
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
    public @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.Support legacyFreshSpawnSupport() {
        return legacyFreshSpawnSupport;
    }

    @Override
    public @NotNull LegacyReplacementStrategy_1_8_to_1_12.Support legacyReplacementSupport() {
        return legacyReplacementSupport;
    }

    private @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.Support resolvedLegacyFreshSpawnSupport() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint();
        if (!(resolvedEntrypoint instanceof LegacyFreshSpawnStrategy_1_8_to_1_12.Support)) {
            throw new IllegalStateException(
                    "Minecraft 1.8.8 entrypoint does not expose the shared legacy fresh-spawn support bridge."
            );
        }
        return (LegacyFreshSpawnStrategy_1_8_to_1_12.Support) resolvedEntrypoint;
    }

    private @NotNull LegacyReplacementStrategy_1_8_to_1_12.Support resolvedLegacyReplacementSupport() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint();
        if (!(resolvedEntrypoint instanceof LegacyReplacementStrategy_1_8_to_1_12.Support)) {
            throw new IllegalStateException(
                    "Minecraft 1.8.8 entrypoint does not expose the shared legacy replacement support bridge."
            );
        }
        return (LegacyReplacementStrategy_1_8_to_1_12.Support) resolvedEntrypoint;
    }

    private @NotNull EntityVersionEntrypoint entrypoint() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint;
        if (resolvedEntrypoint != null) {
            return resolvedEntrypoint;
        }

        synchronized (this) {
            if (entrypoint == null) {
                entrypoint = new EntityFactoryV1_8_8();
            }
            return entrypoint;
        }
    }
}
