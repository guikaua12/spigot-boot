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

/**
 * Internal semantic transport contract used by the runtime-owned network pipeline.
 *
 * @since 2.0.2
 */
public interface EntityTransport {

    /**
     * Sends the initial spawn representation for one viewer.
     *
     * @param request the transport request
     */
    void spawnForViewer(@NotNull EntityTransportRequest request);

    /**
     * Sends the destroy representation for one viewer.
     *
     * @param request the transport request
     */
    void destroyForViewer(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes a relative movement update for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncRelativeMove(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes an absolute movement update for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncAbsoluteMove(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes body rotation for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncRotation(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes head rotation for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncHeadRotation(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes velocity for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncVelocity(@NotNull EntityTransportRequest request);

    /**
     * Synchronizes passenger or vehicle state for the targeted recipients.
     *
     * @param request the transport request
     */
    void syncPassengersOrVehicle(@NotNull EntityTransportRequest request);

    /**
     * Sends the initial metadata snapshot for one viewer.
     *
     * @param request the transport request
     */
    void sendInitialMetadataSnapshot(@NotNull EntityTransportRequest request);

    /**
     * Sends the dirty metadata delta for the targeted recipients.
     *
     * @param request the transport request
     */
    void sendDirtyMetadataDelta(@NotNull EntityTransportRequest request);

    /**
     * Sends living-entity initialization for one viewer.
     *
     * @param request the transport request
     */
    void sendLivingInitialization(@NotNull EntityTransportRequest request);
}
