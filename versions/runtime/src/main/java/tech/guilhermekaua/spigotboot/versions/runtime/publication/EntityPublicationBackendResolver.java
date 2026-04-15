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
package tech.guilhermekaua.spigotboot.versions.runtime.publication;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;

import java.util.Objects;

/**
 * Resolves explicit publication backends from the selected network runtime bundle.
 *
 * @since 2.0.2
 */
public final class EntityPublicationBackendResolver {
    private static final EntityPublicationBackend NO_OP = new NoOpEntityPublicationBackend();
    private static final EntityPublicationBackend LEGACY_WORLD_LISTENER = new LegacyWorldListenerPublicationBackend();
    private static final EntityPublicationBackend ENTITIES_BY_UUID = new EntitiesByUuidPublicationBackend();
    private static final EntityPublicationBackend SECTION_MANAGER = new SectionManagerPublicationBackend();
    private static final EntityPublicationBackend PAPER_CHUNK_SYSTEM = new PaperChunkSystemPublicationBackend();
    private static final EntityPublicationBackend PAPER_MOONRISE = new PaperMoonriseChunkSystemPublicationBackend();

    private EntityPublicationBackendResolver() {
    }

    /**
     * Resolves the publication backend for one selected network runtime bundle.
     *
     * @param networkRuntime the selected runtime bundle
     * @return the resolved publication backend
     */
    public static @NotNull EntityPublicationBackend resolve(@NotNull EntityNetworkRuntimeBundle networkRuntime) {
        Objects.requireNonNull(networkRuntime, "networkRuntime cannot be null");
        return resolve(networkRuntime.publicationFamily());
    }

    /**
     * Resolves the publication backend for one publication family.
     *
     * @param family the selected publication family
     * @return the resolved publication backend
     */
    public static @NotNull EntityPublicationBackend resolve(@NotNull EntityPublicationFamily family) {
        Objects.requireNonNull(family, "family cannot be null");
        switch (family) {
            case LEGACY_WORLD_LISTENER:
                return LEGACY_WORLD_LISTENER;
            case ENTITIES_BY_UUID:
                return ENTITIES_BY_UUID;
            case SECTION_MANAGER:
                return SECTION_MANAGER;
            case PAPER_CHUNK_SYSTEM:
                return PAPER_CHUNK_SYSTEM;
            case PAPER_MOONRISE_CHUNK_SYSTEM:
                return PAPER_MOONRISE;
            case UNSPECIFIED:
            default:
                return NO_OP;
        }
    }

    /**
     * Returns the no-op publication backend used when explicit publication metadata is unavailable.
     *
     * @return the no-op publication backend
     */
    public static @NotNull EntityPublicationBackend noop() {
        return NO_OP;
    }
}
