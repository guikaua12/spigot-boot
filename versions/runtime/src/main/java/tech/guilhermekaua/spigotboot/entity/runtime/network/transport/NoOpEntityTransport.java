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

import org.jetbrains.annotations.NotNull;

final class NoOpEntityTransport implements EntityTransport {
    static final EntityTransport INSTANCE = new NoOpEntityTransport();

    private NoOpEntityTransport() {
    }

    @Override
    public void spawnForViewer(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void destroyForViewer(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncRelativeMove(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncAbsoluteMove(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncRotation(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncHeadRotation(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncVelocity(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void syncPassengersOrVehicle(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void sendInitialMetadataSnapshot(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void sendDirtyMetadataDelta(@NotNull EntityTransportRequest request) {
    }

    @Override
    public void sendLivingInitialization(@NotNull EntityTransportRequest request) {
    }
}
