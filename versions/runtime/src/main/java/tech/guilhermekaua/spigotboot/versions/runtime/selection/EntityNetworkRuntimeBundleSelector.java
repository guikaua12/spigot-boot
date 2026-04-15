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
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;

import java.util.Objects;

/**
 * Central runtime selector that composes transport-facing subsystem families.
 *
 * @since 2.0.2
 */
public final class EntityNetworkRuntimeBundleSelector {

    private EntityNetworkRuntimeBundleSelector() {
    }

    /**
     * Selects a composed network runtime bundle for the supplied metadata.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param capabilities the resolved runtime capabilities
     * @param bindings the resolved runtime bindings
     * @return the selected network runtime bundle
     */
    public static @NotNull EntityNetworkRuntimeBundle select(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull VersionCapabilities capabilities,
            @NotNull VersionBindings bindings
    ) {
        return select(new EntityNetworkRuntimeSelectionContext(runtimeProfile, capabilities, bindings));
    }

    /**
     * Selects a composed network runtime bundle for the supplied context.
     *
     * @param context the runtime selection context
     * @return the selected network runtime bundle
     */
    public static @NotNull EntityNetworkRuntimeBundle select(@NotNull EntityNetworkRuntimeSelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        return new EntityNetworkRuntimeBundle(
                EntityTrackerHookFamilySelector.select(context),
                EntityPublicationFamilySelector.select(context),
                EntityTransportFamilySelector.select(context),
                EntityMetadataFamilySelector.select(context)
        );
    }
}
