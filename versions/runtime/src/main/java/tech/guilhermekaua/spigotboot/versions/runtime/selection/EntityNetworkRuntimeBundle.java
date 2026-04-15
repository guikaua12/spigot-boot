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
package tech.guilhermekaua.spigotboot.versions.runtime.selection;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable runtime-owned selection bundle for transport-facing network subsystems.
 *
 * @since 2.0.2
 */
public final class EntityNetworkRuntimeBundle {
    private final EntityTrackerHookFamily trackerHookFamily;
    private final EntityPublicationFamily publicationFamily;
    private final EntityTransportFamily transportFamily;
    private final EntityMetadataFamily metadataFamily;

    /**
     * Creates a new composed network runtime bundle.
     *
     * @param trackerHookFamily the selected tracker-hook family
     * @param publicationFamily the selected publication family
     * @param transportFamily the selected transport family
     * @param metadataFamily the selected metadata family
     */
    public EntityNetworkRuntimeBundle(
            @NotNull EntityTrackerHookFamily trackerHookFamily,
            @NotNull EntityPublicationFamily publicationFamily,
            @NotNull EntityTransportFamily transportFamily,
            @NotNull EntityMetadataFamily metadataFamily
    ) {
        this.trackerHookFamily = Objects.requireNonNull(trackerHookFamily, "trackerHookFamily cannot be null");
        this.publicationFamily = Objects.requireNonNull(publicationFamily, "publicationFamily cannot be null");
        this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily cannot be null");
        this.metadataFamily = Objects.requireNonNull(metadataFamily, "metadataFamily cannot be null");
    }

    /**
     * Returns the selected tracker-hook family.
     *
     * @return the selected tracker-hook family
     */
    public @NotNull EntityTrackerHookFamily trackerHookFamily() {
        return trackerHookFamily;
    }

    /**
     * Returns the selected publication/add-remove family.
     *
     * @return the selected publication family
     */
    public @NotNull EntityPublicationFamily publicationFamily() {
        return publicationFamily;
    }

    /**
     * Returns the selected packet transport family.
     *
     * @return the selected transport family
     */
    public @NotNull EntityTransportFamily transportFamily() {
        return transportFamily;
    }

    /**
     * Returns the selected metadata synchronization family.
     *
     * @return the selected metadata family
     */
    public @NotNull EntityMetadataFamily metadataFamily() {
        return metadataFamily;
    }
}
