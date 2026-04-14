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
package tech.guilhermekaua.spigotboot.entity.runtime.strategy;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.FieldCopySupport;
import tech.guilhermekaua.spigotboot.entity.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.entity.runtime.publication.EntityPublicationBackendResolver;
import tech.guilhermekaua.spigotboot.entity.runtime.publication.EntityPublicationReplacementSupport;

import java.util.Objects;

/**
 * Shared replacement strategy used by legacy 1.8-1.12 runtimes.
 *
 * @since 2.0.2
 */
public final class LegacyReplacementStrategy_1_8_to_1_12 implements ReplacementStrategy {
    private static final LegacyTrackingBindingStrategy_1_8_to_1_12 DEFAULT_TRACKING_BINDING_STRATEGY =
            new LegacyTrackingBindingStrategy_1_8_to_1_12("legacy-entry-only", true, false, true, false);

    private final String id;
    private final LegacyWorldAddStrategy_1_8_to_1_12 worldAddStrategy;
    private final LegacyTrackingBindingStrategy_1_8_to_1_12 trackingBindingStrategy;

    /**
     * Creates a new shared legacy replacement strategy.
     *
     * @param id the runtime-selected strategy id
     */
    public LegacyReplacementStrategy_1_8_to_1_12(@NotNull String id) {
        this(
                id,
                new LegacyWorldAddStrategy_1_8_to_1_12("legacy-constructor-add-and-rewrite"),
                DEFAULT_TRACKING_BINDING_STRATEGY
        );
    }

    /**
     * Creates a new shared legacy replacement strategy.
     *
     * @param id the runtime-selected strategy id
     * @param worldAddStrategy the shared legacy world-add strategy
     */
    public LegacyReplacementStrategy_1_8_to_1_12(
            @NotNull String id,
            @NotNull LegacyWorldAddStrategy_1_8_to_1_12 worldAddStrategy
    ) {
        this(id, worldAddStrategy, DEFAULT_TRACKING_BINDING_STRATEGY);
    }

    /**
     * Creates a new shared legacy replacement strategy.
     *
     * @param id the runtime-selected strategy id
     * @param worldAddStrategy the shared legacy world-add strategy
     * @param trackingBindingStrategy the shared legacy tracking-binding strategy
     */
    public LegacyReplacementStrategy_1_8_to_1_12(
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
    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull EntityVersionAdapter adapter,
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(adapter, "adapter cannot be null");
        return attach(resolveSupport(adapter), entity, lifecycle);
    }

    /**
     * Executes the shared legacy replacement flow through a version-local support bridge.
     *
     * @param support the version-local replacement support bridge
     * @param entity the existing Bukkit entity
     * @param lifecycle the runtime-managed lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the live controlled entity
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull Support support,
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Object currentNativeHandle = support.resolveCurrentNativeHandle(entity);
        ControlledEntity<?> existing = resolveExistingControlledEntity(currentNativeHandle);
        if (existing != null) {
            return (ControlledEntity<T>) existing;
        }

        PreparedReplacement preparedReplacement = support.prepareReplacement(entity, currentNativeHandle);
        Object replacementHandle = support.allocateReplacementHandle(preparedReplacement);
        FieldCopySupport.copyInstanceFields(currentNativeHandle, replacementHandle);
        support.bindLifecycleToReplacement(replacementHandle, preparedReplacement, lifecycle);
        resolvePublicationBackend(lifecycle).publishReplacement(support, entity, currentNativeHandle, replacementHandle);
        lifecycle.bind((T) support.resolveBukkitWrapper(replacementHandle));
        trackingBindingStrategy.bindReplacementTracking(support, replacementHandle, lifecycle);
        support.scheduleRepairPass(lifecycle, entity, currentNativeHandle, replacementHandle);
        return lifecycle.handle();
    }

    private static @NotNull Support resolveSupport(@NotNull EntityVersionAdapter adapter) {
        if (adapter instanceof Provider) {
            return ((Provider) adapter).legacyReplacementSupport();
        }
        throw new IllegalStateException(
                "The runtime-selected legacy replacement strategy requires adapter '"
                        + adapter.getClass().getName()
                        + "' to implement LegacyReplacementStrategy_1_8_to_1_12.Provider."
        );
    }

    private static @Nullable ControlledEntity<?> resolveExistingControlledEntity(@NotNull Object nativeHandle) {
        if (!(nativeHandle instanceof LifecycleAwareNativeEntity)) {
            return null;
        }
        NativeEntityLifecycle<?> lifecycle = ((LifecycleAwareNativeEntity) nativeHandle).spigotBootGetLifecycle();
        if (lifecycle == null || !(lifecycle.handle() instanceof ControlledEntity)) {
            return null;
        }
        return (ControlledEntity<?>) lifecycle.handle();
    }

    /**
     * Optional adapter-side bridge used by the shared legacy replacement strategy.
     *
     * @since 2.0.2
     */
    public interface Provider {

        /**
         * Returns the version-local support bridge for the shared legacy replacement flow.
         *
         * @return the version-local support bridge
         */
        @NotNull Support legacyReplacementSupport();
    }

    /**
     * Version-local bridge that provides the exact legacy replacement collaborators.
     *
     * @since 2.0.2
     */
    public interface Support extends LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport,
            EntityPublicationReplacementSupport,
            LegacyTrackingBindingStrategy_1_8_to_1_12.ReplacementSupport {

        /**
         * Resolves the current native handle for the supplied Bukkit entity.
         *
         * @param entity the existing Bukkit entity
         * @return the current native handle
         */
        @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity);

        /**
         * Resolves the prepared replacement metadata for one attach request.
         *
         * @param entity the existing Bukkit entity
         * @param currentNativeHandle the current native handle
         * @param <T> the Bukkit entity type exposed to plugin code
         * @return the prepared replacement token
         */
        <T extends Entity> @NotNull PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        );

        /**
         * Allocates the generated replacement instance for the prepared replacement request.
         *
         * @param preparedReplacement the prepared replacement token
         * @return the uninitialized replacement instance
         */
        @NotNull Object allocateReplacementHandle(@NotNull PreparedReplacement preparedReplacement);

        /**
         * Binds the runtime lifecycle to the allocated replacement before it is published.
         *
         * @param replacementHandle the allocated replacement instance
         * @param preparedReplacement the prepared replacement token
         * @param lifecycle the runtime lifecycle bridge
         * @param <T> the Bukkit entity type exposed to plugin code
         */
        <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        );

        /**
         * Resolves the Bukkit entity wrapper for the published replacement.
         *
         * @param replacementHandle the allocated replacement instance
         * @return the Bukkit entity wrapper
         */
        @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle);

        /**
         * Resolves the legacy tracker-entry handle after replacement publication.
         *
         * @param replacementHandle the published replacement handle
         * @return the tracker-entry handle, or {@code null}
         */
        @Override
        @Nullable Object resolveTrackerEntryHandle(@NotNull Object replacementHandle);

        /**
         * Schedules the version-local repair pass after replacement publication.
         *
         * @param lifecycle the runtime lifecycle bridge
         * @param entity the existing Bukkit entity
         * @param currentNativeHandle the original native handle
         * @param replacementHandle the replacement native handle
         * @param <T> the Bukkit entity type exposed to plugin code
         */
        <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        );
    }

    /**
     * Opaque prepared replacement token passed between the shared strategy and a version-local support bridge.
     *
     * @since 2.0.2
     */
    public static final class PreparedReplacement {
        private final Object preparedMetadata;

        /**
         * Creates a new opaque prepared replacement token.
         *
         * @param preparedMetadata the version-local prepared metadata
         */
        public PreparedReplacement(@NotNull Object preparedMetadata) {
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

    /**
     * Tracking handles written back into the shared runtime network state after replacement publication.
     *
     * @since 2.0.2
     */
}
