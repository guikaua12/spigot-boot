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
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityVersionLegacyTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;

import java.util.Objects;

/**
 * Resolves the internal semantic transport backend for one selected runtime bundle.
 *
 * @since 2.0.2
 */
public final class EntityTransportResolver {
    private EntityTransportResolver() {
    }

    /**
     * Returns the shared semantic no-op transport.
     *
     * @return the shared semantic no-op transport
     */
    public static @NotNull EntityTransport noop() {
        return NoOpEntityTransport.INSTANCE;
    }

    /**
     * Resolves the semantic transport backend for one runtime bundle.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param runtimeBundle the selected runtime bundle
     * @param adapter the selected version adapter
     * @param metadataContract the resolved metadata contract
     * @return the resolved semantic transport backend
     */
    public static @NotNull EntityTransport resolve(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull EntityNetworkRuntimeBundle runtimeBundle,
            @NotNull VersionAdapter adapter,
            @NotNull EntityNetworkMetadataContract metadataContract
    ) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        Objects.requireNonNull(runtimeBundle, "runtimeBundle cannot be null");
        Objects.requireNonNull(adapter, "adapter cannot be null");
        Objects.requireNonNull(metadataContract, "metadataContract cannot be null");

        EntityTransportFamily family = runtimeBundle.transportFamily();
        if (family == EntityTransportFamily.UNSPECIFIED) {
            return noop();
        }
        if (family == EntityTransportFamily.LEGACY_1_8_TO_1_13_2) {
            return resolveLegacySupport(adapter, metadataContract);
        }

        ModernTransportSupport support = resolveModernSupport(runtimeProfile, adapter);
        if (support == null) {
            return noop();
        }
        if (support.family() != family) {
            throw new IllegalStateException(
                    "Transport bridge '"
                            + support.id()
                            + "' exposes family '"
                            + support.family().id()
                            + "' but runtime selection resolved '"
                            + family.id()
                            + "'."
            );
        }

        switch (family) {
            case MODERN_1_14_TO_1_16_5:
                return new ModernEntityTransportV1_14To1_16_5(support, metadataContract);
            case MODERN_1_17_TO_1_18_2:
                return new ModernEntityTransportV1_17To1_18_2(support, metadataContract);
            case MODERN_1_19_2_TO_1_20_6:
                return new ModernEntityTransportV1_19_2To1_20_6(support, metadataContract);
            case LATEST_1_21_X:
                return new LatestEntityTransportV1_21_X(support, metadataContract);
            default:
                throw new IllegalStateException(
                        "Unsupported transport family '" + family.id() + "'."
                );
        }
    }

    private static @Nullable ModernTransportSupport resolveModernSupport(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull VersionAdapter adapter
    ) {
        if (!(adapter instanceof VersionTransportProvider)) {
            return null;
        }
        return ((VersionTransportProvider) adapter).entityTransportSupport(runtimeProfile);
    }

    private static @NotNull EntityTransport resolveLegacySupport(
            @NotNull VersionAdapter adapter,
            @NotNull EntityNetworkMetadataContract metadataContract
    ) {
        if (!metadataContract.isSpecified()) {
            return noop();
        }
        if (!(adapter instanceof EntityVersionLegacyTransportProvider)) {
            throw new IllegalStateException(
                    "Legacy transport family requires an EntityVersionLegacyTransportProvider bridge on adapter '"
                            + adapter.getClass().getName()
                            + "'."
            );
        }
        return new LegacyEntityTransportV1_8_to_1_13_2(
                ((EntityVersionLegacyTransportProvider) adapter).legacyTransportSupport(),
                metadataContract
        );
    }
}
