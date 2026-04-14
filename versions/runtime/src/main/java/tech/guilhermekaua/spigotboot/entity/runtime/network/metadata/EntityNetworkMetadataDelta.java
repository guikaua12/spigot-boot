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
package tech.guilhermekaua.spigotboot.entity.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Dirty network metadata delta requested by the runtime transport layer.
 *
 * @since 2.0.2
 */
public final class EntityNetworkMetadataDelta {
    private static final EntityNetworkMetadataDelta EMPTY = new EntityNetworkMetadataDelta(
            WatcherDelta.empty(),
            HeadRotation.absent(),
            PassengerVehicleState.empty()
    );

    private final WatcherDelta watcherDelta;
    private final HeadRotation headRotation;
    private final PassengerVehicleState passengerVehicleState;

    /**
     * Creates a new dirty metadata delta.
     *
     * @param watcherDelta the dirty watcher delta
     * @param headRotation the head rotation delta payload
     * @param passengerVehicleState the passenger and vehicle state delta payload
     */
    public EntityNetworkMetadataDelta(
            @NotNull WatcherDelta watcherDelta,
            @NotNull HeadRotation headRotation,
            @NotNull PassengerVehicleState passengerVehicleState
    ) {
        this.watcherDelta = Objects.requireNonNull(watcherDelta, "watcherDelta cannot be null");
        this.headRotation = Objects.requireNonNull(headRotation, "headRotation cannot be null");
        this.passengerVehicleState = Objects.requireNonNull(
                passengerVehicleState,
                "passengerVehicleState cannot be null"
        );
    }

    /**
     * Returns the shared empty metadata delta.
     *
     * @return the empty metadata delta
     */
    public static @NotNull EntityNetworkMetadataDelta empty() {
        return EMPTY;
    }

    /**
     * Returns the dirty watcher delta.
     *
     * @return the dirty watcher delta
     */
    public @NotNull WatcherDelta watcherDelta() {
        return watcherDelta;
    }

    /**
     * Returns the head rotation delta payload.
     *
     * @return the head rotation delta payload
     */
    public @NotNull HeadRotation headRotation() {
        return headRotation;
    }

    /**
     * Returns the passenger and vehicle state delta payload.
     *
     * @return the passenger and vehicle state delta payload
     */
    public @NotNull PassengerVehicleState passengerVehicleState() {
        return passengerVehicleState;
    }

    /**
     * Returns whether the delta carries any network metadata payloads.
     *
     * @return {@code true} when every payload component is empty or absent
     */
    public boolean isEmpty() {
        return watcherDelta.isEmpty() && !headRotation.isPresent() && passengerVehicleState.isEmpty();
    }
}
