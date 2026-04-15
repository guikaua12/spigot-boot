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
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;

import java.util.Objects;

/**
 * Immutable runtime metadata used to select transport-facing subsystem families.
 *
 * @since 2.0.2
 */
public final class EntityNetworkRuntimeSelectionContext {
    private final VersionRuntimeProfile runtimeProfile;
    private final VersionCapabilities capabilities;
    private final VersionBindings bindings;

    /**
     * Creates a new network-runtime selection context.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param capabilities the resolved runtime capabilities
     * @param bindings the resolved runtime bindings
     */
    public EntityNetworkRuntimeSelectionContext(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull VersionCapabilities capabilities,
            @NotNull VersionBindings bindings
    ) {
        this.runtimeProfile = Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities cannot be null");
        this.bindings = Objects.requireNonNull(bindings, "bindings cannot be null");
    }

    /**
     * Returns the resolved runtime profile.
     *
     * @return the runtime profile
     */
    public @NotNull VersionRuntimeProfile runtimeProfile() {
        return runtimeProfile;
    }

    /**
     * Returns the resolved Minecraft version.
     *
     * @return the resolved Minecraft version
     */
    public @NotNull MinecraftVersion minecraftVersion() {
        return runtimeProfile.minecraftVersion();
    }

    /**
     * Returns the resolved runtime capabilities.
     *
     * @return the runtime capabilities
     */
    public @NotNull VersionCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Returns the resolved runtime bindings.
     *
     * @return the runtime bindings
     */
    public @NotNull VersionBindings bindings() {
        return bindings;
    }
}
