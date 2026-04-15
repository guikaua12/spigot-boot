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
package tech.guilhermekaua.spigotboot.versions.runtime.network.transport;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkState;

import java.util.Objects;

/**
 * Immutable semantic transport request used by the internal runtime pipeline.
 *
 * @since 2.0.2
 */
public final class EntityTransportRequest {
    private final ControlledEntity<?> entity;
    private final EntityNetworkState networkState;
    private final Player viewer;

    private EntityTransportRequest(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkState networkState,
            @Nullable Player viewer
    ) {
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");
        this.networkState = Objects.requireNonNull(networkState, "networkState cannot be null");
        this.viewer = viewer;
    }

    /**
     * Creates a viewer-scoped request.
     *
     * @param entity the controlled entity
     * @param networkState the mutable network state
     * @param viewer the targeted viewer
     * @return the created request
     */
    public static @NotNull EntityTransportRequest forViewer(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkState networkState,
            @NotNull Player viewer
    ) {
        return new EntityTransportRequest(entity, networkState, Objects.requireNonNull(viewer, "viewer cannot be null"));
    }

    /**
     * Creates a tracked-viewer broadcast request.
     *
     * @param entity the controlled entity
     * @param networkState the mutable network state
     * @return the created request
     */
    public static @NotNull EntityTransportRequest forTrackedViewers(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkState networkState
    ) {
        return new EntityTransportRequest(entity, networkState, null);
    }

    /**
     * Returns the controlled entity that owns this request.
     *
     * @return the controlled entity
     */
    public @NotNull ControlledEntity<?> entity() {
        return entity;
    }

    /**
     * Returns the mutable network state used to make the request.
     *
     * @return the mutable network state
     */
    public @NotNull EntityNetworkState networkState() {
        return networkState;
    }

    /**
     * Returns whether the request targets every tracked viewer.
     *
     * @return {@code true} when the request targets the tracked viewer set
     */
    public boolean targetsTrackedViewers() {
        return viewer == null;
    }

    /**
     * Returns the targeted viewer, or {@code null} when this request targets the tracked viewer set.
     *
     * @return the targeted viewer, or {@code null}
     */
    public @Nullable Player viewerOrNull() {
        return viewer;
    }

    /**
     * Returns the targeted viewer.
     *
     * @return the targeted viewer
     * @throws IllegalStateException when this request targets the tracked viewer set
     */
    public @NotNull Player viewer() {
        if (viewer == null) {
            throw new IllegalStateException("This transport request targets the tracked viewer set.");
        }
        return viewer;
    }
}
