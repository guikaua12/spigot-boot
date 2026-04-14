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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detects runtime feature probes used during adapter selection.
 *
 * @since 2.0.2
 */
public final class RuntimeFeatureProbeRegistry {
    private static final String[] PAPER_CHUNK_SYSTEM_MARKERS = new String[]{
            "io/papermc/paper/chunk/system/entity/EntityLookup.class"
    };
    private static final String[] PAPER_WORLD_ENTITY_EVENT_MARKERS = new String[]{
            "com/destroystokyo/paper/event/entity/EntityAddToWorldEvent.class",
            "com/destroystokyo/paper/event/entity/EntityRemoveFromWorldEvent.class"
    };

    private static final RuntimeFeatureProbeRegistry DEFAULT = new RuntimeFeatureProbeRegistry(
            new TrackerStateFeatureProbe(),
            new VersionedPaperFeatureProbe(MinecraftVersion.of(1, 19, 2), PAPER_CHUNK_SYSTEM_MARKERS),
            new MoonriseChunkSystemFeatureProbe()
    );

    private final FeatureProbe trackerStateProbe;
    private final FeatureProbe paperChunkSystemProbe;
    private final FeatureProbe paperMoonriseChunkSystemProbe;

    /**
     * Creates a new registry.
     *
     * @param trackerStateProbe the tracker-state probe
     * @param paperChunkSystemProbe the Paper chunk-system probe
     * @param paperMoonriseChunkSystemProbe the Moonrise chunk-system probe
     */
    public RuntimeFeatureProbeRegistry(
            @NotNull FeatureProbe trackerStateProbe,
            @NotNull FeatureProbe paperChunkSystemProbe,
            @NotNull FeatureProbe paperMoonriseChunkSystemProbe
    ) {
        this.trackerStateProbe = Objects.requireNonNull(trackerStateProbe, "trackerStateProbe cannot be null");
        this.paperChunkSystemProbe = Objects.requireNonNull(
                paperChunkSystemProbe,
                "paperChunkSystemProbe cannot be null"
        );
        this.paperMoonriseChunkSystemProbe = Objects.requireNonNull(
                paperMoonriseChunkSystemProbe,
                "paperMoonriseChunkSystemProbe cannot be null"
        );
    }

    /**
     * Returns the shared default registry.
     *
     * @return the shared default registry
     */
    public static @NotNull RuntimeFeatureProbeRegistry defaultRegistry() {
        return DEFAULT;
    }

    /**
     * Resolves a complete runtime profile from the supplied version, flavor, and class loaders.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param serverFlavor the resolved server flavor
     * @param classLoaders the class loaders to inspect
     * @return the resolved runtime profile
     */
    public @NotNull EntityRuntimeProfile createProfile(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull RuntimeServerFlavor serverFlavor,
            @NotNull Iterable<? extends ClassLoader> classLoaders
    ) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        Objects.requireNonNull(serverFlavor, "serverFlavor cannot be null");
        Objects.requireNonNull(classLoaders, "classLoaders cannot be null");

        List<ClassLoader> runtimeClassLoaders = snapshot(classLoaders);
        return new EntityRuntimeProfile(
                minecraftVersion,
                serverFlavor,
                trackerStateProbe.detect(minecraftVersion, serverFlavor, runtimeClassLoaders),
                paperChunkSystemProbe.detect(minecraftVersion, serverFlavor, runtimeClassLoaders),
                paperMoonriseChunkSystemProbe.detect(minecraftVersion, serverFlavor, runtimeClassLoaders)
        );
    }

    /**
     * Detects a single runtime feature probe.
     *
     * @since 2.0.2
     */
    public interface FeatureProbe {

        /**
         * Detects whether a runtime feature is available.
         *
         * @param minecraftVersion the resolved Minecraft version
         * @param serverFlavor the resolved server flavor
         * @param classLoaders the class loaders to inspect
         * @return {@code true} when the feature is available
         */
        boolean detect(
                @NotNull MinecraftVersion minecraftVersion,
                @NotNull RuntimeServerFlavor serverFlavor,
                @NotNull Iterable<? extends ClassLoader> classLoaders
        );
    }

    private static @NotNull List<ClassLoader> snapshot(@NotNull Iterable<? extends ClassLoader> classLoaders) {
        List<ClassLoader> snapshot = new ArrayList<ClassLoader>();
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null) {
                snapshot.add(classLoader);
            }
        }
        return snapshot;
    }

    private static boolean hasAnyMarker(
            @NotNull Iterable<? extends ClassLoader> classLoaders,
            @NotNull String[] markerResources
    ) {
        for (ClassLoader classLoader : classLoaders) {
            for (String markerResource : markerResources) {
                if (classLoader.getResource(markerResource) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class VersionedPaperFeatureProbe implements FeatureProbe {
        private final MinecraftVersion minimumPaperVersion;
        private final String[] markerResources;

        private VersionedPaperFeatureProbe(@NotNull MinecraftVersion minimumPaperVersion, @NotNull String[] markerResources) {
            this.minimumPaperVersion = Objects.requireNonNull(
                    minimumPaperVersion,
                    "minimumPaperVersion cannot be null"
            );
            this.markerResources = Objects.requireNonNull(markerResources, "markerResources cannot be null");
        }

        @Override
        public boolean detect(
                @NotNull MinecraftVersion minecraftVersion,
                @NotNull RuntimeServerFlavor serverFlavor,
                @NotNull Iterable<? extends ClassLoader> classLoaders
        ) {
            if (serverFlavor != RuntimeServerFlavor.PAPER) {
                return false;
            }
            return minecraftVersion.isAtLeast(minimumPaperVersion) || hasAnyMarker(classLoaders, markerResources);
        }
    }

    private static final class TrackerStateFeatureProbe implements FeatureProbe {

        @Override
        public boolean detect(
                @NotNull MinecraftVersion minecraftVersion,
                @NotNull RuntimeServerFlavor serverFlavor,
                @NotNull Iterable<? extends ClassLoader> classLoaders
        ) {
            if (serverFlavor != RuntimeServerFlavor.PAPER) {
                return false;
            }
            return minecraftVersion.isAtLeast(MinecraftVersion.of(1, 17, 0))
                    || hasAnyMarker(classLoaders, PAPER_CHUNK_SYSTEM_MARKERS)
                    || hasAnyMarker(classLoaders, PAPER_WORLD_ENTITY_EVENT_MARKERS);
        }
    }

    private static final class MoonriseChunkSystemFeatureProbe implements FeatureProbe {

        @Override
        public boolean detect(
                @NotNull MinecraftVersion minecraftVersion,
                @NotNull RuntimeServerFlavor serverFlavor,
                @NotNull Iterable<? extends ClassLoader> classLoaders
        ) {
            if (serverFlavor != RuntimeServerFlavor.PAPER) {
                return false;
            }
            if (minecraftVersion.isAtLeast(MinecraftVersion.of(1, 21, 0))) {
                return true;
            }
            return !hasAnyMarker(classLoaders, PAPER_CHUNK_SYSTEM_MARKERS)
                    && hasAnyMarker(classLoaders, PAPER_WORLD_ENTITY_EVENT_MARKERS);
        }
    }
}
