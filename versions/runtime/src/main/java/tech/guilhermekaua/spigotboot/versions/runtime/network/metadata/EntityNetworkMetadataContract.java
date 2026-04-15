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

import java.util.Objects;

/**
 * Dedicated runtime contract for watcher and metadata synchronization payload assembly.
 *
 * <p>This contract stays separate from version-factory type metadata and from the existing runtime capability and
 * binding metadata used for family selection. It models the transport-facing init and dirty-delta metadata paths.
 *
 * @since 2.0.2
 */
public final class EntityNetworkMetadataContract {
    private static final EntityNetworkMetadataContract UNSPECIFIED = new EntityNetworkMetadataContract(
            "unspecified",
            false
    );

    private final String id;
    private final boolean specified;

    private EntityNetworkMetadataContract(@NotNull String id, boolean specified) {
        String resolvedId = Objects.requireNonNull(id, "id cannot be null").trim();
        if (resolvedId.isEmpty()) {
            throw new IllegalArgumentException("id cannot be blank");
        }

        this.id = resolvedId;
        this.specified = specified;
    }

    /**
     * Creates a specified network metadata contract.
     *
     * @param id the stable runtime contract id
     * @return the created network metadata contract
     */
    public static @NotNull EntityNetworkMetadataContract of(@NotNull String id) {
        if ("unspecified".equals(Objects.requireNonNull(id, "id cannot be null").trim())) {
            return unspecified();
        }
        return new EntityNetworkMetadataContract(id, true);
    }

    /**
     * Returns the shared unspecified network metadata contract.
     *
     * @return the shared unspecified network metadata contract
     */
    public static @NotNull EntityNetworkMetadataContract unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the stable runtime contract id.
     *
     * @return the stable runtime contract id
     */
    public @NotNull String id() {
        return id;
    }

    /**
     * Returns whether this contract is specified by the active version family.
     *
     * @return {@code true} when the contract is specified
     */
    public boolean isSpecified() {
        return specified;
    }

    /**
     * Builds the initial metadata snapshot requested by the runtime transport layer.
     *
     * @param source the source of metadata payload components
     * @return the assembled initial metadata snapshot
     */
    public @NotNull EntityNetworkMetadataSnapshot initialSnapshot(@NotNull EntityNetworkMetadataSource source) {
        Objects.requireNonNull(source, "source cannot be null");
        if (!specified) {
            return EntityNetworkMetadataSnapshot.empty();
        }

        return new EntityNetworkMetadataSnapshot(
                source.watcherPayload(),
                source.livingMetadata(),
                source.headRotation(),
                source.passengerVehicleState()
        );
    }

    /**
     * Builds the dirty metadata delta requested by the runtime transport layer.
     *
     * @param source the source of metadata payload components
     * @return the assembled dirty metadata delta
     */
    public @NotNull EntityNetworkMetadataDelta dirtyDelta(@NotNull EntityNetworkMetadataSource source) {
        Objects.requireNonNull(source, "source cannot be null");
        if (!specified) {
            return EntityNetworkMetadataDelta.empty();
        }

        return new EntityNetworkMetadataDelta(
                source.dirtyWatcherDelta(),
                source.headRotation(),
                source.passengerVehicleState()
        );
    }
}
