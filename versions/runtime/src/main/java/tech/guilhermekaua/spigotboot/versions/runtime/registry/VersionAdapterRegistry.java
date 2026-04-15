/*
 * The MIT License
 * Copyright Â© 2025 Guilherme KauÃ£ da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.registry;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stores the version adapters registered by the host plugin.
 *
 * @since 2.0.2
 */
public final class VersionAdapterRegistry {
    private static final List<VersionAdapter> REGISTERED_ADAPTERS = new ArrayList<VersionAdapter>();

    private VersionAdapterRegistry() {
    }

    /**
     * Registers a version adapter.
     *
     * <p>If an adapter of the same implementation class is already registered,
     * the existing entry is replaced with the new instance.
     *
     * @param adapter the adapter to register
     * @throws NullPointerException when the adapter is null
     */
    public static void register(@NotNull VersionAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter cannot be null");

        synchronized (REGISTERED_ADAPTERS) {
            int existingIndex = indexOf(adapter.getClass());
            if (existingIndex >= 0) {
                REGISTERED_ADAPTERS.set(existingIndex, adapter);
                return;
            }
            REGISTERED_ADAPTERS.add(adapter);
        }
    }

    /**
     * Registers each adapter from the supplied iterable.
     *
     * @param adapters the adapters to register
     * @throws NullPointerException when the iterable or any adapter is null
     */
    public static void registerAll(@NotNull Iterable<? extends VersionAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters cannot be null");

        for (VersionAdapter adapter : adapters) {
            register(adapter);
        }
    }

    /**
     * Returns an immutable snapshot of the registered adapters.
     *
     * @return the registered adapters
     */
    public static @NotNull List<VersionAdapter> registeredAdapters() {
        synchronized (REGISTERED_ADAPTERS) {
            return Collections.unmodifiableList(new ArrayList<VersionAdapter>(REGISTERED_ADAPTERS));
        }
    }

    /**
     * Clears every registered adapter.
     */
    public static void clear() {
        synchronized (REGISTERED_ADAPTERS) {
            REGISTERED_ADAPTERS.clear();
        }
    }

    private static int indexOf(@NotNull Class<? extends VersionAdapter> adapterType) {
        for (int index = 0; index < REGISTERED_ADAPTERS.size(); index++) {
            VersionAdapter registeredAdapter = REGISTERED_ADAPTERS.get(index);
            if (registeredAdapter.getClass().equals(adapterType)) {
                return index;
            }
        }
        return -1;
    }
}
