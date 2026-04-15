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
package tech.guilhermekaua.spigotboot.versions.runtime.model;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;

/**
 * Optional runtime-facing selection metadata contract implemented by version-specific adapters.
 *
 * <p>This provider only exposes the capability and binding metadata used to resolve runtime strategy and
 * network-family selections. Dedicated watcher/network metadata synchronization contracts are published separately
 * through {@link VersionNetworkMetadataProvider} so they do not get conflated with factory type metadata.
 *
 * @since 2.0.2
 */
public interface VersionMetadataProvider {

    /**
     * Returns the capability summary for the implementing adapter.
     *
     * @return the capability summary
     */
    @NotNull VersionCapabilities entityCapabilities();

    /**
     * Returns the binding bundle for the implementing adapter.
     *
     * @return the binding bundle
     */
    @NotNull VersionBindings entityBindings();
}
