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
 * Initial network metadata snapshot requested by the runtime transport layer.
 *
 * @since 2.0.2
 */
public final class EntityNetworkMetadataSnapshot {
    private static final EntityNetworkMetadataSnapshot EMPTY = new EntityNetworkMetadataSnapshot(
            WatcherPayload.empty(),
            LivingEntityMetadata.empty(),
            HeadRotation.absent(),
            PassengerVehicleState.empty()
    );

    private final WatcherPayload watcherPayload;
    private final LivingEntityMetadata livingMetadata;
    private final HeadRotation headRotation;
    private final PassengerVehicleState passengerVehicleState;

    /**
     * Creates a new initial metadata snapshot.
     *
     * @param watcherPayload the full watcher payload
     * @param livingMetadata the explicit living-entity payload
     * @param headRotation the head rotation payload
     * @param passengerVehicleState the passenger and vehicle state
     */
    public EntityNetworkMetadataSnapshot(
            @NotNull WatcherPayload watcherPayload,
            @NotNull LivingEntityMetadata livingMetadata,
            @NotNull HeadRotation headRotation,
            @NotNull PassengerVehicleState passengerVehicleState
    ) {
        this.watcherPayload = Objects.requireNonNull(watcherPayload, "watcherPayload cannot be null");
        this.livingMetadata = Objects.requireNonNull(livingMetadata, "livingMetadata cannot be null");
        this.headRotation = Objects.requireNonNull(headRotation, "headRotation cannot be null");
        this.passengerVehicleState = Objects.requireNonNull(
                passengerVehicleState,
                "passengerVehicleState cannot be null"
        );
    }

    /**
     * Returns the shared empty metadata snapshot.
     *
     * @return the empty metadata snapshot
     */
    public static @NotNull EntityNetworkMetadataSnapshot empty() {
        return EMPTY;
    }

    /**
     * Returns the full watcher payload.
     *
     * @return the full watcher payload
     */
    public @NotNull WatcherPayload watcherPayload() {
        return watcherPayload;
    }

    /**
     * Returns the explicit living-entity metadata.
     *
     * @return the explicit living-entity metadata
     */
    public @NotNull LivingEntityMetadata livingMetadata() {
        return livingMetadata;
    }

    /**
     * Returns the head rotation payload.
     *
     * @return the head rotation payload
     */
    public @NotNull HeadRotation headRotation() {
        return headRotation;
    }

    /**
     * Returns the passenger and vehicle state.
     *
     * @return the passenger and vehicle state
     */
    public @NotNull PassengerVehicleState passengerVehicleState() {
        return passengerVehicleState;
    }

    /**
     * Returns whether the snapshot carries any network metadata payloads.
     *
     * @return {@code true} when every payload component is empty or absent
     */
    public boolean isEmpty() {
        return watcherPayload.isEmpty()
                && livingMetadata.isEmpty()
                && !headRotation.isPresent()
                && passengerVehicleState.isEmpty();
    }
}
