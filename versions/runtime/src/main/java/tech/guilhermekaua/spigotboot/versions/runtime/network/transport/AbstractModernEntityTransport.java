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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataDelta;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataSnapshot;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.WatcherDelta;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.WatcherPayload;

import java.util.List;
import java.util.Objects;

/**
 * Shared modern transport implementation backed by one version-local packet bridge.
 *
 * @since 2.0.2
 */
abstract class AbstractModernEntityTransport implements EntityTransport {
    private final ModernTransportSupport support;
    private final EntityNetworkMetadataContract metadataContract;

    protected AbstractModernEntityTransport(
            @NotNull ModernTransportSupport support,
            @NotNull EntityNetworkMetadataContract metadataContract
    ) {
        this.support = Objects.requireNonNull(support, "support cannot be null");
        this.metadataContract = Objects.requireNonNull(metadataContract, "metadataContract cannot be null");
    }

    final @NotNull ModernTransportSupport support() {
        return support;
    }

    @Override
    public final void spawnForViewer(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createSpawnPacket(request));
    }

    @Override
    public final void destroyForViewer(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createDestroyPacket(request));
    }

    @Override
    public final void syncRelativeMove(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createRelativeMovePacket(request));
    }

    @Override
    public final void syncAbsoluteMove(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createAbsoluteMovePacket(request));
    }

    @Override
    public final void syncRotation(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createRotationPacket(request));
    }

    @Override
    public final void syncHeadRotation(@NotNull EntityTransportRequest request) {
        EntityNetworkMetadataSource source = support.metadataSource(request);
        HeadRotation headRotation = request.targetsTrackedViewers()
                ? metadataContract.dirtyDelta(source).headRotation()
                : metadataContract.initialSnapshot(source).headRotation();
        if (!headRotation.isPresent()) {
            headRotation = source.headRotation();
        }
        if (!headRotation.isPresent()) {
            headRotation = HeadRotation.of(request.networkState().liveHeadYaw());
        }
        dispatch(request, support.createHeadRotationPacket(request, headRotation));
    }

    @Override
    public final void syncVelocity(@NotNull EntityTransportRequest request) {
        dispatch(request, support.createVelocityPacket(request));
    }

    @Override
    public final void syncPassengersOrVehicle(@NotNull EntityTransportRequest request) {
        EntityNetworkMetadataSource source = support.metadataSource(request);
        PassengerVehicleState state = request.targetsTrackedViewers()
                ? metadataContract.dirtyDelta(source).passengerVehicleState()
                : metadataContract.initialSnapshot(source).passengerVehicleState();
        if (state.isEmpty()) {
            state = source.passengerVehicleState();
        }
        dispatchAll(request, support.createPassengerVehiclePackets(request, state));
    }

    @Override
    public final void sendInitialMetadataSnapshot(@NotNull EntityTransportRequest request) {
        EntityNetworkMetadataSource source = support.metadataSource(request);
        EntityNetworkMetadataSnapshot snapshot = metadataContract.initialSnapshot(source);
        WatcherPayload watcherPayload = snapshot.watcherPayload();
        if (watcherPayload.isEmpty()) {
            watcherPayload = source.watcherPayload();
        }
        dispatch(
                request,
                support.createMetadataPacket(request, watcherPayload.items(), true)
        );
    }

    @Override
    public final void sendDirtyMetadataDelta(@NotNull EntityTransportRequest request) {
        EntityNetworkMetadataSource source = support.metadataSource(request);
        EntityNetworkMetadataDelta delta = metadataContract.dirtyDelta(source);
        WatcherDelta watcherDelta = delta.watcherDelta();
        if (watcherDelta.isEmpty()) {
            watcherDelta = source.dirtyWatcherDelta();
        }
        dispatch(
                request,
                support.createMetadataPacket(request, watcherDelta.items(), false)
        );
    }

    @Override
    public final void sendLivingInitialization(@NotNull EntityTransportRequest request) {
        EntityNetworkMetadataSource source = support.metadataSource(request);
        LivingEntityMetadata livingMetadata = metadataContract.initialSnapshot(source).livingMetadata();
        if (livingMetadata.isEmpty()) {
            livingMetadata = source.livingMetadata();
        }
        dispatchAll(request, support.createLivingInitializationPackets(request, livingMetadata));
    }

    private void dispatch(@NotNull EntityTransportRequest request, @Nullable Object packet) {
        if (packet == null) {
            return;
        }
        if (request.targetsTrackedViewers()) {
            support.broadcastPacket(request, packet);
            return;
        }
        support.sendPacket(request.viewer(), packet);
    }

    private void dispatchAll(@NotNull EntityTransportRequest request, @NotNull List<Object> packets) {
        for (Object packet : packets) {
            dispatch(request, packet);
        }
    }
}
