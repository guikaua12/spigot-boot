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
package tech.guilhermekaua.spigotboot.versions.runtime.strategy;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationFreshSupport;

import java.util.Objects;

/**
 * Shared constructor-first fresh-spawn strategy used by legacy 1.8-1.12 runtimes.
 *
 * @since 2.0.2
 */
public final class LegacyFreshSpawnStrategy_1_8_to_1_12 implements FreshSpawnStrategy {
    private static final LegacyTrackingBindingStrategy_1_8_to_1_12 DEFAULT_TRACKING_BINDING_STRATEGY =
            new LegacyTrackingBindingStrategy_1_8_to_1_12("legacy-entry-only", true, false, true, false);

    private final String id;
    private final LegacyWorldAddStrategy_1_8_to_1_12 worldAddStrategy;
    private final LegacyTrackingBindingStrategy_1_8_to_1_12 trackingBindingStrategy;

    /**
     * Creates a new shared legacy fresh-spawn strategy.
     *
     * @param id the runtime-selected strategy id
     */
    public LegacyFreshSpawnStrategy_1_8_to_1_12(@NotNull String id) {
        this(
                id,
                new LegacyWorldAddStrategy_1_8_to_1_12("legacy-constructor-add-and-rewrite"),
                DEFAULT_TRACKING_BINDING_STRATEGY
        );
    }

    /**
     * Creates a new shared legacy fresh-spawn strategy.
     *
     * @param id the runtime-selected strategy id
     * @param worldAddStrategy the shared legacy world-add strategy
     */
    public LegacyFreshSpawnStrategy_1_8_to_1_12(
            @NotNull String id,
            @NotNull LegacyWorldAddStrategy_1_8_to_1_12 worldAddStrategy
    ) {
        this(id, worldAddStrategy, DEFAULT_TRACKING_BINDING_STRATEGY);
    }

    /**
     * Creates a new shared legacy fresh-spawn strategy.
     *
     * @param id the runtime-selected strategy id
     * @param worldAddStrategy the shared legacy world-add strategy
     * @param trackingBindingStrategy the shared legacy tracking-binding strategy
     */
    public LegacyFreshSpawnStrategy_1_8_to_1_12(
            @NotNull String id,
            @NotNull LegacyWorldAddStrategy_1_8_to_1_12 worldAddStrategy,
            @NotNull LegacyTrackingBindingStrategy_1_8_to_1_12 trackingBindingStrategy
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.worldAddStrategy = Objects.requireNonNull(worldAddStrategy, "worldAddStrategy cannot be null");
        this.trackingBindingStrategy = Objects.requireNonNull(
                trackingBindingStrategy,
                "trackingBindingStrategy cannot be null"
        );
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull VersionAdapter adapter,
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(adapter, "adapter cannot be null");
        return spawn(resolveSupport(adapter), template, spawnOptions, lifecycle);
    }

    /**
     * Executes the shared legacy constructor-first fresh-spawn flow through a version-local support bridge.
     *
     * @param support the version-local fresh-spawn support bridge
     * @param template the template to spawn
     * @param spawnOptions the spawn options
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live spawned entity
     */
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull Support support,
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Object spawnedEntity = spawnFreshEntity(support, template, spawnOptions, lifecycle);
        lifecycle.onSpawn();
        if (!(spawnedEntity instanceof SpawnedEntity)) {
            throw new IllegalStateException(
                    "Spawn lifecycle did not return a SpawnedEntity for base type '" + template.baseType() + "'."
            );
        }
        return (SpawnedEntity<T>) spawnedEntity;
    }

    private static @NotNull Support resolveSupport(@NotNull VersionAdapter adapter) {
        if (adapter instanceof Provider) {
            return ((Provider) adapter).legacyFreshSpawnSupport();
        }
        throw new IllegalStateException(
                "The runtime-selected legacy fresh-spawn strategy requires adapter '"
                        + adapter.getClass().getName()
                        + "' to implement LegacyFreshSpawnStrategy_1_8_to_1_12.Provider."
        );
    }

    private <T extends Entity> @NotNull Object spawnFreshEntity(
            @NotNull Support support,
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        final PreparedSpawn preparedSpawn;
        final Object nativeEntity;
        try {
            preparedSpawn = support.prepareFreshSpawn(template, spawnOptions);
            nativeEntity = support.createNativeEntity(preparedSpawn, spawnOptions.location());
        } catch (RuntimeException exception) {
            return support.recoverPreparationFailure(template, spawnOptions, lifecycle, exception);
        }
        support.bindLifecycleToNativeEntity(nativeEntity, preparedSpawn, lifecycle);

        T bukkitEntity = template.bukkitType().cast(support.resolveBukkitWrapper(nativeEntity));
        lifecycle.bind(bukkitEntity);

        try {
            resolvePublicationBackend(lifecycle).addFreshEntity(support, nativeEntity, spawnOptions.location());
            trackingBindingStrategy.bindFreshSpawnTracking(support, nativeEntity, lifecycle);
            return lifecycle.handle();
        } catch (RuntimeException exception) {
            bukkitEntity.remove();
            throw exception;
        }
    }

    /**
     * Optional adapter-side bridge used by the shared legacy fresh-spawn strategy.
     *
     * @since 2.0.2
     */
    public interface Provider {

        /**
         * Returns the version-local support bridge for the shared legacy fresh-spawn flow.
         *
         * @return the version-local support bridge
         */
        @NotNull Support legacyFreshSpawnSupport();
    }

    /**
     * Version-local bridge that provides the exact legacy fresh-spawn collaborators.
     *
     * @since 2.0.2
     */
    public interface Support extends LegacyWorldAddStrategy_1_8_to_1_12.FreshSupport,
            EntityPublicationFreshSupport,
            LegacyTrackingBindingStrategy_1_8_to_1_12.FreshSupport {

        @Override
        default void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
            LegacyWorldAddStrategy_1_8_to_1_12.FreshSupport.super.beforeWorldAdd(nativeEntity, location);
        }

        /**
         * Resolves and prepares the version-local fresh-spawn metadata for one request.
         *
         * @param template the template to spawn
         * @param spawnOptions the spawn options
         * @param <T> the Bukkit entity type exposed to plugin code
         * @return the prepared spawn token
         */
        <T extends Entity> @NotNull PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        );

        /**
         * Creates the generated native entity for the prepared fresh-spawn request.
         *
         * @param preparedSpawn the prepared spawn token
         * @param location the spawn location
         * @return the generated native entity
         */
        @NotNull Object createNativeEntity(@NotNull PreparedSpawn preparedSpawn, @NotNull Location location);

        /**
         * Binds the runtime lifecycle to the generated native entity before world registration.
         *
         * @param nativeEntity the generated native entity
         * @param preparedSpawn the prepared spawn token
         * @param lifecycle the runtime lifecycle bridge
         * @param <T> the Bukkit entity type exposed to plugin code
         */
        <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        );

        /**
         * Resolves the Bukkit entity wrapper for the generated native entity.
         *
         * @param nativeEntity the generated native entity
         * @return the Bukkit entity wrapper
         */
        @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity);

        /**
         * Resolves the legacy tracker-entry handle written back into the runtime network state.
         *
         * @param nativeEntity the generated native entity
         * @return the tracker-entry handle, or {@code null}
         */
        @Override
        @Nullable Object resolveTrackerEntryHandle(@NotNull Object nativeEntity);

        /**
         * Recovers from a legacy pre-registration preparation failure before the shared strategy has bound runtime state.
         *
         * @param template the template to spawn
         * @param spawnOptions the spawn options
         * @param lifecycle the runtime lifecycle bridge
         * @param cause the preparation failure raised before world registration
         * @param <T> the Bukkit entity type exposed to plugin code
         * @return the recovered spawned entity
         */
        default <T extends Entity> @NotNull SpawnedEntity<T> recoverPreparationFailure(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull RuntimeException cause
        ) {
            throw cause;
        }
    }

    /**
     * Opaque prepared spawn token passed between the shared strategy and a version-local support bridge.
     *
     * @since 2.0.2
     */
    public static final class PreparedSpawn {
        private final Object preparedMetadata;

        /**
         * Creates a new opaque prepared spawn token.
         *
         * @param preparedMetadata the version-local prepared metadata
         */
        public PreparedSpawn(@NotNull Object preparedMetadata) {
            this.preparedMetadata = Objects.requireNonNull(preparedMetadata, "preparedMetadata cannot be null");
        }

        /**
         * Returns the opaque version-local prepared metadata.
         *
         * @return the version-local prepared metadata
         */
        public @NotNull Object preparedMetadata() {
            return preparedMetadata;
        }
    }

    private static @NotNull EntityPublicationBackend resolvePublicationBackend(@NotNull NativeEntityLifecycle<?> lifecycle) {
        if (lifecycle.handle() instanceof AbstractRuntimeControlledEntity) {
            return ((AbstractRuntimeControlledEntity<?>) lifecycle.handle()).publicationBackend();
        }
        return EntityPublicationBackendResolver.noop();
    }
}
