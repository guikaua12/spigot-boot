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
package tech.guilhermekaua.spigotboot.versions.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Vehicle and passenger snapshot prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class PassengerVehicleState {
    private static final PassengerVehicleState EMPTY = new PassengerVehicleState(null, Collections.<Integer>emptyList());

    private final Integer vehicleEntityId;
    private final List<Integer> passengerEntityIds;

    /**
     * Creates one vehicle and passenger state snapshot.
     *
     * @param vehicleEntityId the current vehicle entity id, or {@code null} when there is no vehicle
     * @param passengerEntityIds the passenger entity ids riding this entity
     */
    public PassengerVehicleState(@Nullable Integer vehicleEntityId, @NotNull List<Integer> passengerEntityIds) {
        this.vehicleEntityId = vehicleEntityId;
        this.passengerEntityIds = MetadataCollectionSupport.immutableCopy(
                Objects.requireNonNull(passengerEntityIds, "passengerEntityIds cannot be null")
        );
    }

    /**
     * Returns the shared empty passenger/vehicle state.
     *
     * @return the empty passenger/vehicle state
     */
    public static @NotNull PassengerVehicleState empty() {
        return EMPTY;
    }

    /**
     * Returns the current vehicle entity id.
     *
     * @return the current vehicle entity id, or {@code null}
     */
    public @Nullable Integer vehicleEntityId() {
        return vehicleEntityId;
    }

    /**
     * Returns the passenger entity ids.
     *
     * @return the passenger entity ids
     */
    public @NotNull List<Integer> passengerEntityIds() {
        return passengerEntityIds;
    }

    /**
     * Returns whether the passenger and vehicle state is empty.
     *
     * @return {@code true} when there is no vehicle and no passenger ids
     */
    public boolean isEmpty() {
        return vehicleEntityId == null && passengerEntityIds.isEmpty();
    }
}
