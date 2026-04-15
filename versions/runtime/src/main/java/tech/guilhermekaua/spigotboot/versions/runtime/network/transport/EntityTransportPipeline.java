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

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.versions.api.EntityNetworkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered runtime-owned dispatcher that translates lifecycle events into semantic transport operations.
 *
 * @since 2.0.2
 */
public final class EntityTransportPipeline {
    private final EntityTransport transport;

    /**
     * Creates a new transport pipeline.
     *
     * @param transport the semantic transport backend
     */
    public EntityTransportPipeline(@NotNull EntityTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
    }

    /**
     * Dispatches the late-viewer snapshot sequence for one viewer.
     *
     * @param entity the controlled entity
     * @param networkState the mutable network state
     * @param viewer the targeted viewer
     */
    public void onViewerAdded(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkState networkState,
            @NotNull Player viewer
    ) {
        EntityTransportRequest request = EntityTransportRequest.forViewer(entity, networkState, viewer);
        transport.spawnForViewer(request);
        transport.sendInitialMetadataSnapshot(request);
        if (entity.bukkitEntity() instanceof LivingEntity) {
            transport.sendLivingInitialization(request);
        }
        transport.syncVelocity(request);
        transport.syncPassengersOrVehicle(request);
        transport.syncHeadRotation(request);

        if (networkState.viewers().size() == 1) {
            markFullStateSynced(networkState);
        }
    }

    /**
     * Dispatches the destroy sequence for one viewer.
     *
     * @param entity the controlled entity
     * @param networkState the mutable network state
     * @param viewer the targeted viewer
     */
    public void onViewerRemoved(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkState networkState,
            @NotNull Player viewer
    ) {
        transport.destroyForViewer(EntityTransportRequest.forViewer(entity, networkState, viewer));
    }

    /**
     * Dispatches per-tick transport work for the tracked viewer set.
     *
     * @param entity the controlled entity
     * @param networkController the active network controller
     * @param networkState the mutable network state
     */
    public void onTick(
            @NotNull ControlledEntity<?> entity,
            @NotNull EntityNetworkController<?> networkController,
            @NotNull EntityNetworkState networkState
    ) {
        if (networkState.viewers().isEmpty()) {
            return;
        }

        EntityTransportRequest request = EntityTransportRequest.forTrackedViewers(entity, networkState);
        transport.syncPassengersOrVehicle(request);

        boolean absoluteSync = networkController.shouldSendAbsoluteSync(networkState);
        boolean relativeSync = !absoluteSync && networkController.shouldSendRelativeSync(networkState);
        boolean rotationSyncRequested = networkController.shouldSendRotationSync(networkState);
        boolean bodyRotationSync = bodyRotationChanged(networkState);
        boolean headRotationSync = headRotationChanged(networkState);
        if (rotationSyncRequested && !bodyRotationSync && !headRotationSync) {
            bodyRotationSync = true;
            headRotationSync = true;
        }

        if (absoluteSync) {
            transport.syncAbsoluteMove(request);
            networkState.markPositionSyncedFromLive();
            networkState.resetAbsoluteSyncCounter();
        } else if (relativeSync) {
            transport.syncRelativeMove(request);
            networkState.markPositionSyncedFromLive();
        }

        if (bodyRotationSync) {
            transport.syncRotation(request);
            networkState.markRotationSyncedFromLive();
        }

        if (networkController.shouldSendVelocitySync(networkState)) {
            transport.syncVelocity(request);
            networkState.markVelocitySyncedFromLive();
        }

        transport.sendDirtyMetadataDelta(request);

        if (headRotationSync) {
            transport.syncHeadRotation(request);
            networkState.markHeadRotationSyncedFromLive();
        }
    }

    /**
     * Dispatches destroy operations for every tracked viewer during unbind or removal.
     *
     * @param entity the controlled entity
     * @param networkState the mutable network state
     */
    public void onUnbind(@NotNull ControlledEntity<?> entity, @NotNull EntityNetworkState networkState) {
        if (networkState.viewers().isEmpty()) {
            return;
        }

        List<Player> viewers = new ArrayList<Player>(networkState.viewers());
        for (Player viewer : viewers) {
            transport.destroyForViewer(EntityTransportRequest.forViewer(entity, networkState, viewer));
        }
        networkState.clearViewers();
    }

    private static void markFullStateSynced(@NotNull EntityNetworkState networkState) {
        networkState.markPositionSyncedFromLive();
        networkState.markVelocitySyncedFromLive();
        networkState.markRotationSyncedFromLive();
        networkState.markHeadRotationSyncedFromLive();
        networkState.resetAbsoluteSyncCounter();
    }

    private static boolean bodyRotationChanged(@NotNull EntityNetworkState networkState) {
        return Math.abs(networkState.liveYaw() - networkState.syncedYaw()) >= EntityNetworkController.RELATIVE_ROTATION_THRESHOLD
                || Math.abs(networkState.livePitch() - networkState.syncedPitch()) >= EntityNetworkController.RELATIVE_ROTATION_THRESHOLD;
    }

    private static boolean headRotationChanged(@NotNull EntityNetworkState networkState) {
        return Math.abs(networkState.liveHeadYaw() - networkState.syncedHeadYaw()) >= EntityNetworkController.RELATIVE_ROTATION_THRESHOLD;
    }
}
