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
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.util.Objects;

/**
 * Shared tracking strategy used by legacy 1.8-1.12 runtimes.
 *
 * @since 2.0.2
 */
public final class LegacyTrackingBindingStrategy_1_8_to_1_12 implements TrackingBindingStrategy {
    private final String id;
    private final boolean freshSpawnTrackerEntryHandleAvailable;
    private final boolean freshSpawnTrackerStateHandleAvailable;
    private final boolean replacementTrackerEntryHandleAvailable;
    private final boolean replacementTrackerStateHandleAvailable;

    /**
     * Creates a new shared legacy tracking strategy.
     *
     * @param id the runtime-selected strategy id
     * @param freshSpawnTrackerEntryHandleAvailable whether fresh spawn exposes a tracker-entry handle
     * @param freshSpawnTrackerStateHandleAvailable whether fresh spawn exposes a tracker-state handle
     * @param replacementTrackerEntryHandleAvailable whether replacement exposes a tracker-entry handle
     * @param replacementTrackerStateHandleAvailable whether replacement exposes a tracker-state handle
     */
    public LegacyTrackingBindingStrategy_1_8_to_1_12(
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
     * Resolves and binds the fresh-spawn tracking snapshot into the runtime network state.
     *
     * @param support the version-local fresh-spawn support bridge
     * @param nativeEntity the published native entity
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public <T extends Entity> void bindFreshSpawnTracking(
            @NotNull FreshSupport support,
            @NotNull Object nativeEntity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        bindTracking(resolveTrackingHandles(support, nativeEntity), lifecycle);
    }

    /**
     * Resolves and binds the replacement tracking snapshot into the runtime network state.
     *
     * @param support the version-local replacement support bridge
     * @param replacementHandle the published replacement handle
     * @param lifecycle the runtime lifecycle bridge
     * @param <T> the Bukkit entity type exposed to plugin code
     */
    public <T extends Entity> void bindReplacementTracking(
            @NotNull ReplacementSupport support,
            @NotNull Object replacementHandle,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        bindTracking(resolveTrackingHandles(support, replacementHandle), lifecycle);
    }

    private static @NotNull TrackingHandles resolveTrackingHandles(
            @NotNull Support support,
            @NotNull Object trackedHandle
    ) {
        return new TrackingHandles(support.resolveTrackerEntryHandle(trackedHandle), null);
    }

    private static <T extends Entity> void bindTracking(
            @NotNull TrackingHandles trackingHandles,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        lifecycle.handle().networkState().setTrackerEntryHandle(trackingHandles.trackerEntryHandle());
        lifecycle.handle().networkState().setTrackerStateHandle(trackingHandles.trackerStateHandle());
    }

    /**
     * Version-local support bridge for legacy tracking snapshot resolution.
     *
     * @since 2.0.2
     */
    public interface Support {

        /**
         * Resolves the legacy tracker-entry handle for a published entity handle.
         *
         * @param trackedHandle the published native entity or replacement handle
         * @return the tracker-entry handle, or {@code null}
         */
        @Nullable Object resolveTrackerEntryHandle(@NotNull Object trackedHandle);
    }

    /**
     * Version-local support bridge for legacy fresh-spawn tracking binding.
     *
     * @since 2.0.2
     */
    public interface FreshSupport extends Support {
    }

    /**
     * Version-local support bridge for legacy replacement tracking binding.
     *
     * @since 2.0.2
     */
    public interface ReplacementSupport extends Support {
    }

    private static final class TrackingHandles {
        private final Object trackerEntryHandle;
        private final Object trackerStateHandle;

        private TrackingHandles(@Nullable Object trackerEntryHandle, @Nullable Object trackerStateHandle) {
            this.trackerEntryHandle = trackerEntryHandle;
            this.trackerStateHandle = trackerStateHandle;
        }

        private @Nullable Object trackerEntryHandle() {
            return trackerEntryHandle;
        }

        private @Nullable Object trackerStateHandle() {
            return trackerStateHandle;
        }
    }
}
