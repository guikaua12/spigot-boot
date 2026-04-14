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

/**
 * Source of runtime-owned watcher and metadata payload components.
 *
 * @since 2.0.2
 */
public interface EntityNetworkMetadataSource {

    /**
     * Returns the full watcher payload used for an initial snapshot.
     *
     * @return the full watcher payload
     */
    default @NotNull WatcherPayload watcherPayload() {
        return WatcherPayload.empty();
    }

    /**
     * Returns the dirty watcher delta used for incremental synchronization.
     *
     * @return the dirty watcher delta
     */
    default @NotNull WatcherDelta dirtyWatcherDelta() {
        return WatcherDelta.empty();
    }

    /**
     * Returns the living-entity initialization payload.
     *
     * @return the living-entity initialization payload
     */
    default @NotNull LivingEntityMetadata livingMetadata() {
        return LivingEntityMetadata.empty();
    }

    /**
     * Returns the head rotation payload.
     *
     * @return the head rotation payload
     */
    default @NotNull HeadRotation headRotation() {
        return HeadRotation.absent();
    }

    /**
     * Returns the passenger and vehicle state payload.
     *
     * @return the passenger and vehicle state payload
     */
    default @NotNull PassengerVehicleState passengerVehicleState() {
        return PassengerVehicleState.empty();
    }
}
