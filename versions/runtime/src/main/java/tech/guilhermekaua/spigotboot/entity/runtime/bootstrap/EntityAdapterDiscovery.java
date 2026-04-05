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
package tech.guilhermekaua.spigotboot.entity.runtime.bootstrap;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.runtime.registry.EntityAdapterRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

final class EntityAdapterDiscovery {

    private EntityAdapterDiscovery() {
    }

    static @NotNull List<EntityVersionAdapter> discover(@NotNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");

        Map<Class<? extends EntityVersionAdapter>, EntityVersionAdapter> adapters =
                new LinkedHashMap<Class<? extends EntityVersionAdapter>, EntityVersionAdapter>();

        ServiceLoader<EntityVersionAdapter> loader = ServiceLoader.load(EntityVersionAdapter.class, classLoader);
        for (EntityVersionAdapter adapter : loader) {
            adapters.put(adapter.getClass(), adapter);
        }

        for (EntityVersionAdapter adapter : EntityAdapterRegistry.registeredAdapters()) {
            adapters.put(adapter.getClass(), adapter);
        }

        return new ArrayList<EntityVersionAdapter>(adapters.values());
    }
}
