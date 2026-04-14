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
package tech.guilhermekaua.spigotboot.entity.runtime.network.transport;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherItem;

import java.util.List;

/**
 * Version-local packet bridge used by the shared legacy transport backend.
 *
 * @since 2.0.2
 */
public interface LegacyTransportSupport {

    /**
     * Returns the stable runtime bridge id.
     *
     * @return the stable bridge id
     */
    @NotNull String id();

    /**
     * Creates the metadata source used by the shared transport backend.
     *
     * @param request the transport request
     * @return the metadata source
     */
    @NotNull EntityNetworkMetadataSource metadataSource(@NotNull EntityTransportRequest request);

    /**
     * Creates the spawn packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createSpawnPacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the destroy packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createDestroyPacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the relative movement packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createRelativeMovePacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the absolute movement packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the body rotation packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createRotationPacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the head rotation packet for the supplied request.
     *
     * @param request the transport request
     * @param headRotation the resolved head rotation payload
     * @return the created packet, or {@code null}
     */
    @Nullable Object createHeadRotationPacket(
            @NotNull EntityTransportRequest request,
            @NotNull HeadRotation headRotation
    );

    /**
     * Creates the velocity packet for the supplied request.
     *
     * @param request the transport request
     * @return the created packet, or {@code null}
     */
    @Nullable Object createVelocityPacket(@NotNull EntityTransportRequest request);

    /**
     * Creates the passenger and vehicle packets for the supplied request.
     *
     * @param request the transport request
     * @param state the resolved passenger and vehicle state
     * @return the created packets
     */
    @NotNull List<Object> createPassengerVehiclePackets(
            @NotNull EntityTransportRequest request,
            @NotNull PassengerVehicleState state
    );

    /**
     * Creates the metadata packet for the supplied request.
     *
     * @param request the transport request
     * @param items the resolved watcher items
     * @param initialSnapshot whether the packet represents an initial snapshot
     * @return the created packet, or {@code null}
     */
    @Nullable Object createMetadataPacket(
            @NotNull EntityTransportRequest request,
            @NotNull List<WatcherItem> items,
            boolean initialSnapshot
    );

    /**
     * Creates the living initialization packets for the supplied request.
     *
     * @param request the transport request
     * @param livingMetadata the resolved living metadata payload
     * @return the created packets
     */
    @NotNull List<Object> createLivingInitializationPackets(
            @NotNull EntityTransportRequest request,
            @NotNull LivingEntityMetadata livingMetadata
    );

    /**
     * Sends one packet to one explicit viewer.
     *
     * @param viewer the targeted viewer
     * @param packet the packet to send
     */
    void sendPacket(@NotNull Player viewer, @NotNull Object packet);

    /**
     * Broadcasts one packet to the tracked viewer set.
     *
     * @param request the tracked-viewer transport request
     * @param packet the packet to broadcast
     */
    void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet);
}
