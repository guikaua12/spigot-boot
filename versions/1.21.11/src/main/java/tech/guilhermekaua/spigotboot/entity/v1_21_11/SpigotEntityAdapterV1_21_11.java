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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

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
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;

import java.util.Objects;

/**
 * Native custom entity adapter for Minecraft 1.21.11.
 *
 * @since 2.0.2
 */
public final class SpigotEntityAdapterV1_21_11
        implements EntityVersionAdapter,
        EntityVersionMetadataProvider,
        PaperFreshSpawnStrategy_1_21_plus.Provider,
        PaperReplacementStrategy_1_21_plus.Provider {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    private final PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport =
            new PaperFreshSpawnStrategy_1_21_plus.Support() {
                @Override
                public <T extends Entity> @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
                        @NotNull EntityTemplate<T> template,
                        @NotNull SpawnOptions spawnOptions
                ) {
                    return resolvedPaperFreshSpawnSupport().prepareFreshSpawn(template, spawnOptions);
                }

                @Override
                public @NotNull Object createNativeEntity(
                        @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                        @NotNull Location location
                ) {
                    return resolvedPaperFreshSpawnSupport().createNativeEntity(preparedSpawn, location);
                }

                @Override
                public <T extends Entity> void bindLifecycleToNativeEntity(
                        @NotNull Object nativeEntity,
                        @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    resolvedPaperFreshSpawnSupport().bindLifecycleToNativeEntity(nativeEntity, preparedSpawn, lifecycle);
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
                    return resolvedPaperFreshSpawnSupport().resolveBukkitWrapper(nativeEntity);
                }

                @Override
                public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
                    return resolvedPaperFreshSpawnSupport().resolveNativeWorldHandle(location);
                }

                @Override
                public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(
                        @NotNull Object nativeEntity
                ) {
                    return resolvedPaperFreshSpawnSupport().resolveTrackingHandles(nativeEntity);
                }
            };
    private final PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport =
            new PaperReplacementStrategy_1_21_plus.Support() {
                @Override
                public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
                    return resolvedPaperReplacementSupport().resolveCurrentNativeHandle(entity);
                }

                @Override
                public <T extends Entity> @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle
                ) {
                    return resolvedPaperReplacementSupport().prepareReplacement(entity, currentNativeHandle);
                }

                @Override
                public @NotNull Object allocateReplacementHandle(
                        @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
                ) {
                    return resolvedPaperReplacementSupport().allocateReplacementHandle(preparedReplacement);
                }

                @Override
                public <T extends Entity> void bindLifecycleToReplacement(
                        @NotNull Object replacementHandle,
                        @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    resolvedPaperReplacementSupport().bindLifecycleToReplacement(
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
                    resolvedPaperReplacementSupport().rebindBukkitZombie(entity, replacementHandle);
                }

                @Override
                public void rebindModernBukkitBridge(
                        @NotNull Entity entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedPaperReplacementSupport().rebindModernBukkitBridge(
                            entity,
                            currentNativeHandle,
                            replacementHandle
                    );
                }

                @Override
                public void replaceModernWorldReferences(
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedPaperReplacementSupport().replaceModernWorldReferences(currentNativeHandle, replacementHandle);
                }

                @Override
                public void rewireModernVehicleAndPassengerReferences(
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedPaperReplacementSupport().rewireModernVehicleAndPassengerReferences(
                            currentNativeHandle,
                            replacementHandle
                    );
                }

                @Override
                public void refreshModernBukkitWrappers(@NotNull Entity entity) {
                    resolvedPaperReplacementSupport().refreshModernBukkitWrappers(entity);
                }

                @Override
                public void markModernEntityRemoved(@NotNull Object currentNativeHandle) {
                    resolvedPaperReplacementSupport().markModernEntityRemoved(currentNativeHandle);
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
                    return resolvedPaperReplacementSupport().resolveBukkitWrapper(replacementHandle);
                }

                @Override
                public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                        @NotNull Object replacementHandle
                ) {
                    return resolvedPaperReplacementSupport().resolveReplacementTrackingHandles(replacementHandle);
                }

                @Override
                public <T extends Entity> void scheduleRepairPass(
                        @NotNull NativeEntityLifecycle<T> lifecycle,
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                    resolvedPaperReplacementSupport().scheduleRepairPass(
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
        return EntityFactoryV1_21_11.entityCapabilities();
    }

    @Override
    public @NotNull EntityVersionBindings entityBindings() {
        return EntityFactoryV1_21_11.entityBindings();
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
        return paperFreshSpawnSupport;
    }

    @Override
    public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
        return paperReplacementSupport;
    }

    private @NotNull PaperFreshSpawnStrategy_1_21_plus.Support resolvedPaperFreshSpawnSupport() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint();
        if (!(resolvedEntrypoint instanceof PaperFreshSpawnStrategy_1_21_plus.Support)) {
            throw new IllegalStateException(
                    "Minecraft 1.21.11 entrypoint does not expose the shared paper fresh-spawn support bridge."
            );
        }
        return (PaperFreshSpawnStrategy_1_21_plus.Support) resolvedEntrypoint;
    }

    private @NotNull PaperReplacementStrategy_1_21_plus.Support resolvedPaperReplacementSupport() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint();
        if (!(resolvedEntrypoint instanceof PaperReplacementStrategy_1_21_plus.Support)) {
            throw new IllegalStateException(
                    "Minecraft 1.21.11 entrypoint does not expose the shared paper replacement support bridge."
            );
        }
        return (PaperReplacementStrategy_1_21_plus.Support) resolvedEntrypoint;
    }

    private @NotNull EntityVersionEntrypoint entrypoint() {
        EntityVersionEntrypoint resolvedEntrypoint = entrypoint;
        if (resolvedEntrypoint != null) {
            return resolvedEntrypoint;
        }

        synchronized (this) {
            if (entrypoint == null) {
                entrypoint = new EntityFactoryV1_21_11();
            }
            return entrypoint;
        }
    }
}
