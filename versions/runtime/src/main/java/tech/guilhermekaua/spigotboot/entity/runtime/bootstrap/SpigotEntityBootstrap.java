/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
import tech.guilhermekaua.spigotboot.entity.api.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.exception.EntityAdapterNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Bootstraps the versioned custom entity runtime.
 *
 * @since 2.0.2
 */
public final class SpigotEntityBootstrap {

    private SpigotEntityBootstrap() {
    }

    /**
     * Resolves the platform using the current Bukkit server version.
     *
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot() {
        return boot(RuntimeMinecraftVersionDetector.detectServerVersion());
    }

    /**
     * Resolves the platform using a raw server version string.
     *
     * @param serverVersion the raw server version string
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot(@NotNull String serverVersion) {
        return boot(serverVersion, loadAdapters());
    }

    /**
     * Resolves the platform using the supplied adapters.
     *
     * @param serverVersion the raw server version string
     * @param adapters the adapters to inspect
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot(
            @NotNull String serverVersion,
            @NotNull Iterable<EntityVersionAdapter> adapters
    ) {
        Objects.requireNonNull(serverVersion, "serverVersion cannot be null");
        Objects.requireNonNull(adapters, "adapters cannot be null");

        MinecraftVersion version = MinecraftVersion.parse(serverVersion);
        EntityVersionAdapter adapter = selectAdapter(version, adapters);
        return new VersionedEntityPlatform(version, adapter);
    }

    /**
     * Selects the best adapter for a concrete Minecraft version.
     *
     * @param version the Minecraft version to resolve
     * @param adapters the available adapters
     * @return the selected adapter
     */
    public static @NotNull EntityVersionAdapter selectAdapter(
            @NotNull MinecraftVersion version,
            @NotNull Iterable<EntityVersionAdapter> adapters
    ) {
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(adapters, "adapters cannot be null");

        EntityVersionAdapter selected = null;
        for (EntityVersionAdapter adapter : adapters) {
            if (!adapter.supports(version)) {
                continue;
            }

            if (selected == null || adapter.minimumVersion().compareTo(selected.minimumVersion()) > 0) {
                selected = adapter;
            }
        }

        if (selected == null) {
            throw new EntityAdapterNotFoundException(
                    "No entity adapter supports Minecraft " + version + "."
            );
        }
        return selected;
    }

    private static @NotNull List<EntityVersionAdapter> loadAdapters() {
        ServiceLoader<EntityVersionAdapter> loader = ServiceLoader.load(EntityVersionAdapter.class);
        List<EntityVersionAdapter> adapters = new ArrayList<EntityVersionAdapter>();
        for (EntityVersionAdapter adapter : loader) {
            adapters.add(adapter);
        }
        return adapters;
    }
}
