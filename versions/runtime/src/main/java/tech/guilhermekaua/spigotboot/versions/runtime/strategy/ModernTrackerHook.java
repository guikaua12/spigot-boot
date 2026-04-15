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

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Active modern tracker hook bound to one runtime-controlled entity.
 *
 * @since 2.0.2
 */
public final class ModernTrackerHook {
    private final AbstractRuntimeControlledEntity<?> controlledEntity;
    private final PaperTrackingBindingStrategy_1_21_plus.TrackingHandles trackingHandles;

    /**
     * Creates one active modern tracker hook.
     *
     * @param controlledEntity the runtime-controlled entity
     * @param trackingHandles the resolved modern tracking handles
     */
    public ModernTrackerHook(
            @NotNull AbstractRuntimeControlledEntity<?> controlledEntity,
            @NotNull PaperTrackingBindingStrategy_1_21_plus.TrackingHandles trackingHandles
    ) {
        this.controlledEntity = Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        this.trackingHandles = Objects.requireNonNull(trackingHandles, "trackingHandles cannot be null");
    }

    /**
     * Returns the bound tracker entry handle.
     *
     * @return the tracker entry handle, or {@code null}
     */
    public @Nullable Object trackerEntryHandle() {
        return trackingHandles.trackerEntryHandle();
    }

    /**
     * Returns the bound tracker state handle.
     *
     * @return the tracker state handle, or {@code null}
     */
    public @Nullable Object trackerStateHandle() {
        return trackingHandles.trackerStateHandle();
    }

    /**
     * Returns the original broadcast consumer preserved from the tracker state.
     *
     * @return the original broadcast consumer, or {@code null}
     */
    public @Nullable Consumer<Object> originalBroadcastConsumer() {
        return trackingHandles.trackerStateBridge().originalBroadcastConsumer();
    }

    /**
     * Returns the latest passenger snapshot preserved from the tracker state.
     *
     * @return the current passenger snapshot
     */
    public @NotNull List<Object> passengerSnapshot() {
        return trackingHandles.trackerStateBridge().passengerSnapshot();
    }

    /**
     * Returns the latest vehicle snapshot preserved from the tracker state.
     *
     * @return the current vehicle snapshot, or {@code null}
     */
    public @Nullable Object vehicleSnapshot() {
        return trackingHandles.trackerStateBridge().vehicleSnapshot();
    }

    /**
     * Dispatches the tracker-state tick into the runtime transport pipeline.
     */
    public void onTick() {
        trackingHandles.trackerStateBridge().beforeTick();
        controlledEntity.dispatchTrackerTick();
        trackingHandles.trackerStateBridge().afterTick();
    }

    /**
     * Routes one modern viewer pairing transition into the runtime viewer lifecycle.
     *
     * @param viewer the viewer being paired
     */
    public void addPairing(@NotNull Player viewer) {
        controlledEntity.registerViewer(Objects.requireNonNull(viewer, "viewer cannot be null"));
    }

    /**
     * Routes one modern viewer removal transition into the runtime viewer lifecycle.
     *
     * @param viewer the viewer being removed
     */
    public void removePairing(@NotNull Player viewer) {
        controlledEntity.unregisterViewer(Objects.requireNonNull(viewer, "viewer cannot be null"));
    }

    /**
     * Relays one packet through the original tracker-state broadcast consumer.
     *
     * @param packet the raw packet value
     */
    public void broadcast(@Nullable Object packet) {
        trackingHandles.trackerStateBridge().broadcast(packet);
    }
}
