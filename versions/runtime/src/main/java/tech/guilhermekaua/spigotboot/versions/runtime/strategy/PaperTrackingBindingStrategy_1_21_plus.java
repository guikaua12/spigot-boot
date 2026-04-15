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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared tracking strategy used by the modern 1.14+ tracker family.
 *
 * <p>The historical class name remains because the task-11 skeleton already wired the modern constructor-first family
 * through this type and later tasks build on that seam.
 *
 * @since 2.0.2
 */
public final class PaperTrackingBindingStrategy_1_21_plus implements TrackingBindingStrategy {
    private final String id;
    private final boolean freshSpawnTrackerEntryHandleAvailable;
    private final boolean freshSpawnTrackerStateHandleAvailable;
    private final boolean replacementTrackerEntryHandleAvailable;
    private final boolean replacementTrackerStateHandleAvailable;

    /**
     * Creates a new shared modern tracking strategy.
     *
     * @param id the runtime-selected strategy id
     * @param freshSpawnTrackerEntryHandleAvailable whether fresh spawn exposes a tracker-entry handle
     * @param freshSpawnTrackerStateHandleAvailable whether fresh spawn exposes a tracker-state handle
     * @param replacementTrackerEntryHandleAvailable whether replacement exposes a tracker-entry handle
     * @param replacementTrackerStateHandleAvailable whether replacement exposes a tracker-state handle
     */
    public PaperTrackingBindingStrategy_1_21_plus(
            @NotNull String id,
            boolean freshSpawnTrackerEntryHandleAvailable,
            boolean freshSpawnTrackerStateHandleAvailable,
            boolean replacementTrackerEntryHandleAvailable,
            boolean replacementTrackerStateHandleAvailable
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.freshSpawnTrackerEntryHandleAvailable = freshSpawnTrackerEntryHandleAvailable;
        this.freshSpawnTrackerStateHandleAvailable = freshSpawnTrackerStateHandleAvailable;
        this.replacementTrackerEntryHandleAvailable = replacementTrackerEntryHandleAvailable;
        this.replacementTrackerStateHandleAvailable = replacementTrackerStateHandleAvailable;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public boolean freshSpawnTrackerEntryHandleAvailable() {
        return freshSpawnTrackerEntryHandleAvailable;
    }

    @Override
    public boolean freshSpawnTrackerStateHandleAvailable() {
        return freshSpawnTrackerStateHandleAvailable;
    }

    @Override
    public boolean replacementTrackerEntryHandleAvailable() {
        return replacementTrackerEntryHandleAvailable;
    }

    @Override
    public boolean replacementTrackerStateHandleAvailable() {
        return replacementTrackerStateHandleAvailable;
    }

    /**
     * Resolves, binds, and prepares the fresh-spawn modern tracker hook.
     *
     * @param support the version-local fresh-spawn support bridge
     * @param nativeEntity the published native entity
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the active modern tracker hook
     */
    public <T extends Entity> @NotNull ModernTrackerHook bindFreshSpawnTracking(
            @NotNull FreshSupport support,
            @NotNull Object nativeEntity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        TrackingHandles trackingHandles = support.resolveTrackingHandles(nativeEntity);
        ModernTrackerHook hook = bindTracking(trackingHandles, lifecycle);
        if (trackingHandles.trackerStateHandle() != null
                && support.installFreshSpawnTrackingHook(nativeEntity, hook)) {
            requireRuntimeLifecycle(lifecycle).bindTrackerHookNetworkDispatch();
        }
        return hook;
    }

    /**
     * Resolves, binds, and prepares the replacement modern tracker hook.
     *
     * @param support the version-local replacement support bridge
     * @param replacementHandle the published replacement handle
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     * @return the active modern tracker hook
     */
    public <T extends Entity> @NotNull ModernTrackerHook bindReplacementTracking(
            @NotNull ReplacementSupport support,
            @NotNull Object replacementHandle,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        TrackingHandles trackingHandles = support.resolveReplacementTrackingHandles(replacementHandle);
        ModernTrackerHook hook = bindTracking(trackingHandles, lifecycle);
        if (trackingHandles.trackerStateHandle() != null
                && support.installReplacementTrackingHook(replacementHandle, hook)) {
            requireRuntimeLifecycle(lifecycle).bindTrackerHookNetworkDispatch();
        }
        return hook;
    }

    private static <T extends Entity> @NotNull ModernTrackerHook bindTracking(
            @NotNull TrackingHandles trackingHandles,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        lifecycle.handle().networkState().setTrackerEntryHandle(trackingHandles.trackerEntryHandle());
        lifecycle.handle().networkState().setTrackerStateHandle(trackingHandles.trackerStateHandle());
        return new ModernTrackerHook(requireRuntimeLifecycle(lifecycle), trackingHandles);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull AbstractRuntimeControlledEntity<T> requireRuntimeLifecycle(
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        if (lifecycle instanceof AbstractRuntimeControlledEntity) {
            return (AbstractRuntimeControlledEntity<T>) lifecycle;
        }
        throw new IllegalStateException(
                "Modern tracker hooks require a runtime-controlled lifecycle but received '"
                        + lifecycle.getClass().getName()
                        + "'."
        );
    }

    /**
     * Adapter-side bridge exposing the shared modern tracking support.
     *
     * @since 2.0.2
     */
    public interface Provider {

        /**
         * Returns the version-local modern tracking support bridge.
         *
         * @return the version-local modern tracking support bridge
         */
        @NotNull Support paperTrackingBindingSupport();
    }

    /**
     * Combined modern tracking support bridge for adapters that expose both fresh-spawn and replacement bindings.
     *
     * @since 2.0.2
     */
    public interface Support extends FreshSupport, ReplacementSupport {
    }

    /**
     * Version-local support bridge for modern fresh-spawn tracking binding.
     *
     * @since 2.0.2
     */
    public interface FreshSupport {

        /**
         * Resolves the modern fresh-spawn tracking snapshot for a published native entity.
         *
         * @param nativeEntity the published native entity
         * @return the resolved tracking snapshot
         */
        @NotNull TrackingHandles resolveTrackingHandles(@NotNull Object nativeEntity);

        /**
         * Installs the active modern tracker hook into the version-local fresh-spawn handle path when supported.
         *
         * @param nativeEntity the published native entity
         * @param trackerHook the active modern tracker hook
         * @return {@code true} when the tracker hook now owns network tick dispatch
         */
        default boolean installFreshSpawnTrackingHook(
                @NotNull Object nativeEntity,
                @NotNull ModernTrackerHook trackerHook
        ) {
            Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
            Objects.requireNonNull(trackerHook, "trackerHook cannot be null");
            return false;
        }
    }

    /**
     * Version-local support bridge for modern replacement tracking binding.
     *
     * @since 2.0.2
     */
    public interface ReplacementSupport {

        /**
         * Resolves the modern replacement tracking snapshot for a published replacement handle.
         *
         * @param replacementHandle the published replacement handle
         * @return the resolved tracking snapshot
         */
        @NotNull TrackingHandles resolveReplacementTrackingHandles(@NotNull Object replacementHandle);

        /**
         * Installs the active modern tracker hook into the version-local replacement handle path when supported.
         *
         * @param replacementHandle the published replacement handle
         * @param trackerHook the active modern tracker hook
         * @return {@code true} when the tracker hook now owns network tick dispatch
         */
        default boolean installReplacementTrackingHook(
                @NotNull Object replacementHandle,
                @NotNull ModernTrackerHook trackerHook
        ) {
            Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
            Objects.requireNonNull(trackerHook, "trackerHook cannot be null");
            return false;
        }
    }

    /**
     * Tracker-state-specific bridge preserving broadcast and passenger semantics across the shared modern family.
     *
     * @since 2.0.2
     */
    public interface TrackerStateBridge {

        /**
         * Returns the shared no-op tracker-state bridge.
         *
         * @return the shared no-op tracker-state bridge
         */
        static @NotNull TrackerStateBridge noop() {
            return NoOpTrackerStateBridge.INSTANCE;
        }

        /**
         * Applies the pre-transport tracker-state tick work.
         */
        void beforeTick();

        /**
         * Applies the post-transport tracker-state tick work.
         */
        void afterTick();

        /**
         * Broadcasts one raw packet through the preserved state consumer.
         *
         * @param packet the raw packet value
         */
        void broadcast(@Nullable Object packet);

        /**
         * Returns the current passenger snapshot.
         *
         * @return the current passenger snapshot
         */
        @NotNull List<Object> passengerSnapshot();

        /**
         * Returns the current vehicle snapshot.
         *
         * @return the current vehicle snapshot, or {@code null}
         */
        @Nullable Object vehicleSnapshot();

        /**
         * Returns the preserved original broadcast consumer.
         *
         * @return the preserved original broadcast consumer, or {@code null}
         */
        @Nullable Consumer<Object> originalBroadcastConsumer();
    }

    /**
     * Tracking handles written back into the shared runtime network state after publication.
     *
     * @since 2.0.2
     */
    public static class TrackingHandles {
        private final Object trackerEntryHandle;
        private final Object trackerStateHandle;
        private final TrackerStateBridge trackerStateBridge;

        /**
         * Creates a new tracking-handle snapshot.
         *
         * @param trackerEntryHandle the tracker entry handle, or {@code null}
         * @param trackerStateHandle the tracker state handle, or {@code null}
         */
        public TrackingHandles(@Nullable Object trackerEntryHandle, @Nullable Object trackerStateHandle) {
            this(trackerEntryHandle, trackerStateHandle, createDefaultTrackerStateBridge(trackerStateHandle));
        }

        /**
         * Creates a new tracking-handle snapshot with an explicit tracker-state bridge.
         *
         * @param trackerEntryHandle the tracker entry handle, or {@code null}
         * @param trackerStateHandle the tracker state handle, or {@code null}
         * @param trackerStateBridge the tracker-state bridge, or {@code null} for the reflective default
         */
        public TrackingHandles(
                @Nullable Object trackerEntryHandle,
                @Nullable Object trackerStateHandle,
                @Nullable TrackerStateBridge trackerStateBridge
        ) {
            this.trackerEntryHandle = trackerEntryHandle;
            this.trackerStateHandle = trackerStateHandle;
            this.trackerStateBridge = trackerStateBridge != null
                    ? trackerStateBridge
                    : createDefaultTrackerStateBridge(trackerStateHandle);
        }

        /**
         * Returns the tracker entry handle, or {@code null} when unavailable.
         *
         * @return the tracker entry handle, or {@code null}
         */
        public @Nullable Object trackerEntryHandle() {
            return trackerEntryHandle;
        }

        /**
         * Returns the tracker state handle, or {@code null} when unavailable.
         *
         * @return the tracker state handle, or {@code null}
         */
        public @Nullable Object trackerStateHandle() {
            return trackerStateHandle;
        }

        /**
         * Returns the tracker-state bridge preserved for this binding.
         *
         * @return the tracker-state bridge
         */
        public @NotNull TrackerStateBridge trackerStateBridge() {
            return trackerStateBridge;
        }
    }

    private static @NotNull TrackerStateBridge createDefaultTrackerStateBridge(@Nullable Object trackerStateHandle) {
        if (trackerStateHandle == null) {
            return TrackerStateBridge.noop();
        }
        return new ReflectiveTrackerStateBridge(trackerStateHandle);
    }

    private static final class NoOpTrackerStateBridge implements TrackerStateBridge {
        private static final NoOpTrackerStateBridge INSTANCE = new NoOpTrackerStateBridge();

        @Override
        public void beforeTick() {
        }

        @Override
        public void afterTick() {
        }

        @Override
        public void broadcast(@Nullable Object packet) {
        }

        @Override
        public @NotNull List<Object> passengerSnapshot() {
            return Collections.emptyList();
        }

        @Override
        public @Nullable Object vehicleSnapshot() {
            return null;
        }

        @Override
        public @Nullable Consumer<Object> originalBroadcastConsumer() {
            return null;
        }
    }

    private static final class ReflectiveTrackerStateBridge implements TrackerStateBridge {
        private final Object trackerStateHandle;
        private final Field tickCounterField;
        private final Field timeSinceLocationSyncField;
        private final Field passengersField;
        private final Field vehicleField;
        private final Consumer<Object> originalBroadcastConsumer;

        private ReflectiveTrackerStateBridge(@NotNull Object trackerStateHandle) {
            this.trackerStateHandle = Objects.requireNonNull(trackerStateHandle, "trackerStateHandle cannot be null");
            Class<?> trackerStateType = trackerStateHandle.getClass();
            this.tickCounterField = ReflectionSupport.findField(trackerStateType, "tickCounter");
            this.timeSinceLocationSyncField = ReflectionSupport.findField(trackerStateType, "timeSinceLocationSync");
            this.passengersField = ReflectionSupport.findField(trackerStateType, "opt_passengers", "passengers");
            this.vehicleField = ReflectionSupport.findField(trackerStateType, "opt_vehicle", "vehicle");
            this.originalBroadcastConsumer = resolveBroadcastConsumer(trackerStateType, trackerStateHandle);
        }

        @Override
        public void beforeTick() {
            incrementIntegerField(timeSinceLocationSyncField);
        }

        @Override
        public void afterTick() {
            incrementIntegerField(tickCounterField);
        }

        @Override
        public void broadcast(@Nullable Object packet) {
            if (originalBroadcastConsumer != null) {
                originalBroadcastConsumer.accept(packet);
            }
        }

        @Override
        public @NotNull List<Object> passengerSnapshot() {
            if (passengersField == null) {
                return Collections.emptyList();
            }
            Object passengers = ReflectionSupport.readField(passengersField, trackerStateHandle);
            if (!(passengers instanceof List)) {
                return Collections.emptyList();
            }

            List<Object> snapshot = new ArrayList<Object>();
            for (Object passenger : (List<?>) passengers) {
                snapshot.add(passenger);
            }
            return snapshot;
        }

        @Override
        public @Nullable Object vehicleSnapshot() {
            return vehicleField == null ? null : ReflectionSupport.readField(vehicleField, trackerStateHandle);
        }

        @Override
        public @Nullable Consumer<Object> originalBroadcastConsumer() {
            return originalBroadcastConsumer;
        }

        private void incrementIntegerField(@Nullable Field field) {
            if (field == null) {
                return;
            }

            Object value = ReflectionSupport.readField(field, trackerStateHandle);
            if (!(value instanceof Number)) {
                return;
            }
            ReflectionSupport.writeField(field, trackerStateHandle, Integer.valueOf(((Number) value).intValue() + 1));
        }

        @SuppressWarnings("unchecked")
        private static @Nullable Consumer<Object> resolveBroadcastConsumer(
                @NotNull Class<?> trackerStateType,
                @NotNull Object trackerStateHandle
        ) {
            Field broadcastField = ReflectionSupport.findField(trackerStateType, "broadcastMethod", "broadcast");
            if (broadcastField == null) {
                return null;
            }

            Object broadcastConsumer = ReflectionSupport.readField(broadcastField, trackerStateHandle);
            if (!(broadcastConsumer instanceof Consumer)) {
                return null;
            }
            return (Consumer<Object>) broadcastConsumer;
        }
    }
}
