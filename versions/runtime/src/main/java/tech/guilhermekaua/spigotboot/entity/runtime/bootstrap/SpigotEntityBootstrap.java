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
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.exception.EntityAdapterNotFoundException;
import tech.guilhermekaua.spigotboot.entity.runtime.support.RuntimeSupportMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bootstraps the multi-version custom entity runtime.
 *
 * @since 2.0.2
 */
public final class SpigotEntityBootstrap {

    /**
     * Describes how strongly an adapter matches a resolved runtime profile.
     *
     * @since 2.0.2
     */
    public enum RuntimeProfileMatch {

        /**
         * Adapter explicitly matches the resolved runtime profile.
         */
        EXACT_PROFILE(3),

        /**
         * Adapter is flavor-neutral and only constrains by version range.
         */
        FLAVOR_NEUTRAL_RANGE(2),

        /**
         * Adapter can act as a lower-priority fallback when no stronger match exists.
         */
        LOWER_PRIORITY_FALLBACK(1),

        /**
         * Adapter does not support the resolved runtime profile.
         */
        UNSUPPORTED(0);

        private final int priority;

        RuntimeProfileMatch(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    /**
     * Optional internal contract that allows adapters to participate in runtime-profile-aware selection.
     *
     * @since 2.0.2
     */
    public interface RuntimeProfileSelectionSupport {

        /**
         * Returns the match strength for the supplied runtime profile.
         *
         * @param runtimeProfile the resolved runtime profile
         * @return the runtime profile match strength
         */
        @NotNull RuntimeProfileMatch runtimeProfileMatch(@NotNull EntityRuntimeProfile runtimeProfile);
    }

    private SpigotEntityBootstrap() {
    }

    /**
     * Resolves the platform using the current Bukkit server version and discovered adapters.
     *
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot() {
        return boot(resolveRuntimeProfile());
    }

    /**
     * Resolves the platform using the supplied runtime profile and discovered adapters.
     *
     * @param runtimeProfile the resolved runtime profile
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot(@NotNull EntityRuntimeProfile runtimeProfile) {
        List<EntityVersionAdapter> adapters = discoverDefaultAdapters();
        if (adapters.isEmpty()) {
            throw new EntityAdapterNotFoundException(
                    "No entity adapters were discovered. "
                            + "Bundle a version module with ServiceLoader metadata or register adapters explicitly."
            );
        }
        return boot(runtimeProfile, adapters);
    }

    /**
     * Resolves the platform using the supplied raw server version string and discovered adapters.
     *
     * @param serverVersion the raw server version string
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot(@NotNull String serverVersion) {
        return boot(resolveRuntimeProfile(serverVersion));
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
            @NotNull Iterable<? extends EntityVersionAdapter> adapters
    ) {
        return boot(resolveRuntimeProfile(serverVersion), adapters);
    }

    /**
     * Resolves the platform using the supplied runtime profile and adapters.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param adapters the adapters to inspect
     * @return the resolved platform
     */
    public static @NotNull VersionedEntityPlatform boot(
            @NotNull EntityRuntimeProfile runtimeProfile,
            @NotNull Iterable<? extends EntityVersionAdapter> adapters
    ) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        Objects.requireNonNull(adapters, "adapters cannot be null");

        EntityVersionAdapter adapter = selectAdapter(runtimeProfile, adapters);
        RuntimeSupportMatrix.requireSupported(runtimeProfile, adapter);
        return new VersionedEntityPlatform(runtimeProfile, adapter);
    }

    /**
     * Resolves the active runtime profile using the current server version and default class loaders.
     *
     * @return the resolved runtime profile
     */
    public static @NotNull EntityRuntimeProfile resolveRuntimeProfile() {
        return resolveRuntimeProfile(RuntimeMinecraftVersionDetector.detectServerVersion());
    }

    /**
     * Resolves the active runtime profile using the supplied raw server version string.
     *
     * @param serverVersion the raw server version string
     * @return the resolved runtime profile
     */
    public static @NotNull EntityRuntimeProfile resolveRuntimeProfile(@NotNull String serverVersion) {
        Objects.requireNonNull(serverVersion, "serverVersion cannot be null");
        MinecraftVersion version = MinecraftVersion.parse(serverVersion);
        return resolveRuntimeProfile(version, defaultClassLoaders(), RuntimeFeatureProbeRegistry.defaultRegistry());
    }

    /**
     * Resolves the active runtime profile using the supplied version, class loaders, and feature probes.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param classLoaders the class loaders to inspect
     * @param featureProbeRegistry the feature probe registry to use
     * @return the resolved runtime profile
     */
    public static @NotNull EntityRuntimeProfile resolveRuntimeProfile(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull Iterable<? extends ClassLoader> classLoaders,
            @NotNull RuntimeFeatureProbeRegistry featureProbeRegistry
    ) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        Objects.requireNonNull(classLoaders, "classLoaders cannot be null");
        Objects.requireNonNull(featureProbeRegistry, "featureProbeRegistry cannot be null");

        List<ClassLoader> runtimeClassLoaders = new ArrayList<ClassLoader>();
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null) {
                runtimeClassLoaders.add(classLoader);
            }
        }

        RuntimeServerFlavor serverFlavor = RuntimeServerFlavorDetector.detect(runtimeClassLoaders);
        return featureProbeRegistry.createProfile(minecraftVersion, serverFlavor, runtimeClassLoaders);
    }

    /**
     * Discovers adapters using {@link java.util.ServiceLoader} plus the explicit runtime registry.
     *
     * @param classLoader the class loader used for ServiceLoader discovery
     * @return the discovered adapters
     */
    public static @NotNull List<EntityVersionAdapter> discoverAdapters(@NotNull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");
        return EntityAdapterDiscovery.discover(classLoader);
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
            @NotNull Iterable<? extends EntityVersionAdapter> adapters
    ) {
        Objects.requireNonNull(version, "version cannot be null");
        return selectAdapter(
                new EntityRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false),
                adapters
        );
    }

    /**
     * Selects the best adapter for a resolved runtime profile.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param adapters the available adapters
     * @return the selected adapter
     */
    public static @NotNull EntityVersionAdapter selectAdapter(
            @NotNull EntityRuntimeProfile runtimeProfile,
            @NotNull Iterable<? extends EntityVersionAdapter> adapters
    ) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        Objects.requireNonNull(adapters, "adapters cannot be null");

        List<AdapterCandidate> candidates = new ArrayList<AdapterCandidate>();
        for (EntityVersionAdapter adapter : adapters) {
            if (!adapter.supports(runtimeProfile.minecraftVersion())) {
                continue;
            }

            RuntimeProfileMatch match = resolveMatch(runtimeProfile, adapter);
            if (match == RuntimeProfileMatch.UNSUPPORTED) {
                continue;
            }
            candidates.add(new AdapterCandidate(adapter, match));
        }

        if (candidates.isEmpty()) {
            throw new EntityAdapterNotFoundException(
                    "No entity adapter supports Minecraft "
                            + runtimeProfile.minecraftVersion()
                            + " for runtime profile "
                            + runtimeProfile
                            + "."
            );
        }

        RuntimeProfileMatch strongestMatch = RuntimeProfileMatch.UNSUPPORTED;
        for (AdapterCandidate candidate : candidates) {
            if (candidate.match().priority() > strongestMatch.priority()) {
                strongestMatch = candidate.match();
            }
        }

        List<EntityVersionAdapter> strongestAdapters = new ArrayList<EntityVersionAdapter>();
        for (AdapterCandidate candidate : candidates) {
            if (candidate.match() == strongestMatch) {
                strongestAdapters.add(candidate.adapter());
            }
        }

        if (strongestAdapters.size() > 1) {
            throw ambiguousSelection(runtimeProfile, strongestMatch, strongestAdapters);
        }
        return strongestAdapters.get(0);
    }

    private static @NotNull ClassLoader defaultClassLoader() {
        ClassLoader bootstrapClassLoader = SpigotEntityBootstrap.class.getClassLoader();
        return bootstrapClassLoader != null ? bootstrapClassLoader : ClassLoader.getSystemClassLoader();
    }

    private static @NotNull List<EntityVersionAdapter> discoverDefaultAdapters() {
        Map<Class<? extends EntityVersionAdapter>, EntityVersionAdapter> adapters =
                new LinkedHashMap<Class<? extends EntityVersionAdapter>, EntityVersionAdapter>();

        for (ClassLoader classLoader : defaultClassLoaders()) {
            for (EntityVersionAdapter adapter : discoverAdapters(classLoader)) {
                adapters.put(adapter.getClass(), adapter);
            }
        }

        return new ArrayList<EntityVersionAdapter>(adapters.values());
    }

    private static @NotNull List<ClassLoader> defaultClassLoaders() {
        List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();
        ClassLoader bootstrapClassLoader = defaultClassLoader();
        classLoaders.add(bootstrapClassLoader);

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader != bootstrapClassLoader) {
            classLoaders.add(contextClassLoader);
        }

        return classLoaders;
    }

    private static @NotNull RuntimeProfileMatch resolveMatch(
            @NotNull EntityRuntimeProfile runtimeProfile,
            @NotNull EntityVersionAdapter adapter
    ) {
        if (!(adapter instanceof RuntimeProfileSelectionSupport)) {
            return RuntimeProfileMatch.FLAVOR_NEUTRAL_RANGE;
        }
        RuntimeProfileSelectionSupport selectionSupport = (RuntimeProfileSelectionSupport) adapter;
        RuntimeProfileMatch match = selectionSupport.runtimeProfileMatch(runtimeProfile);
        return match != null ? match : RuntimeProfileMatch.UNSUPPORTED;
    }

    private static @NotNull IllegalStateException ambiguousSelection(
            @NotNull EntityRuntimeProfile runtimeProfile,
            @NotNull RuntimeProfileMatch strongestMatch,
            @NotNull List<EntityVersionAdapter> adapters
    ) {
        List<String> adapterTypes = new ArrayList<String>();
        for (EntityVersionAdapter adapter : adapters) {
            adapterTypes.add(adapter.getClass().getName());
        }
        Collections.sort(adapterTypes);
        return new IllegalStateException(
                "Ambiguous entity adapters for runtime profile "
                        + runtimeProfile
                        + " at selection tier "
                        + strongestMatch
                        + ": "
                        + adapterTypes
                        + '.'
        );
    }

    private static final class AdapterCandidate {
        private final EntityVersionAdapter adapter;
        private final RuntimeProfileMatch match;

        private AdapterCandidate(@NotNull EntityVersionAdapter adapter, @NotNull RuntimeProfileMatch match) {
            this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
            this.match = Objects.requireNonNull(match, "match cannot be null");
        }

        private @NotNull EntityVersionAdapter adapter() {
            return adapter;
        }

        private @NotNull RuntimeProfileMatch match() {
            return match;
        }
    }
}
