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
package tech.guilhermekaua.spigotboot.versions.runtime.bootstrap;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.registry.VersionAdapterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

final class VersionAdapterDiscovery {

    private VersionAdapterDiscovery() {
    }

    static @NotNull List<VersionAdapter> discover(@NotNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");

        Map<Class<? extends VersionAdapter>, VersionAdapter> adapters =
                new LinkedHashMap<Class<? extends VersionAdapter>, VersionAdapter>();

        ServiceLoader<VersionAdapter> loader = ServiceLoader.load(VersionAdapter.class, classLoader);
        for (VersionAdapter adapter : loader) {
            adapters.put(adapter.getClass(), adapter);
        }

        for (VersionAdapter adapter : VersionAdapterRegistry.registeredAdapters()) {
            adapters.put(adapter.getClass(), adapter);
        }

        return new ArrayList<VersionAdapter>(adapters.values());
    }
}
