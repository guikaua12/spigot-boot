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
package tech.guilhermekaua.spigotboot.entity.runtime;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeFeatureProbeRegistry;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavorDetector;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeProfileSelectionTest {

    @Test
    void shouldPreferExactProfileMatchOverFlavorNeutralAndFallbackAdapters() {
        EntityRuntimeProfile runtimeProfile = paperProfile(MinecraftVersion.of(1, 19, 2), true, true, false);
        ExactPaperAdapter exact = new ExactPaperAdapter(
                MinecraftVersion.of(1, 19, 2),
                MinecraftVersion.of(1, 20, 6),
                true,
                true,
                false
        );
        FlavorNeutralAdapter neutral = new FlavorNeutralAdapter(
                MinecraftVersion.of(1, 19, 2),
                MinecraftVersion.of(1, 20, 6)
        );
        FallbackAdapter fallback = new FallbackAdapter(
                MinecraftVersion.of(1, 19, 2),
                MinecraftVersion.of(1, 20, 6)
        );

        EntityVersionAdapter selected = SpigotEntityBootstrap.selectAdapter(
                runtimeProfile,
                Arrays.<EntityVersionAdapter>asList(fallback, neutral, exact)
        );

        assertSame(exact, selected);
    }

    @Test
    void shouldPreferFlavorNeutralRangeOverLowerPriorityFallback() {
        EntityRuntimeProfile runtimeProfile = spigotProfile(MinecraftVersion.of(1, 19, 2));
        FlavorNeutralAdapter neutral = new FlavorNeutralAdapter(
                MinecraftVersion.of(1, 19, 2),
                MinecraftVersion.of(1, 20, 6)
        );
        FallbackAdapter fallback = new FallbackAdapter(
                MinecraftVersion.of(1, 19, 2),
                MinecraftVersion.of(1, 20, 6)
        );

        EntityVersionAdapter selected = SpigotEntityBootstrap.selectAdapter(
                runtimeProfile,
                Arrays.<EntityVersionAdapter>asList(fallback, neutral)
        );

        assertSame(neutral, selected);
    }

    @Test
    void shouldResolveExactlyOneFamilyRangeAdapterForPlannedProfiles() {
        FlavorNeutralAdapter v1_8_8 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 8, 8), MinecraftVersion.of(1, 12, 2));
        FlavorNeutralAdapter v1_13_2 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 13, 0), MinecraftVersion.of(1, 13, 2));
        FlavorNeutralAdapter v1_16_5 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 14, 0), MinecraftVersion.of(1, 16, 5));
        FlavorNeutralAdapter v1_17_1 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 17, 0), MinecraftVersion.of(1, 18, 2));
        FlavorNeutralAdapter v1_19_2 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 19, 2), MinecraftVersion.of(1, 20, 6));
        FlavorNeutralAdapter v1_21_11 = new FlavorNeutralAdapter(MinecraftVersion.of(1, 21, 0), MinecraftVersion.of(1, 21, 11));
        List<EntityVersionAdapter> adapters = Arrays.<EntityVersionAdapter>asList(
                v1_8_8,
                v1_13_2,
                v1_16_5,
                v1_17_1,
                v1_19_2,
                v1_21_11
        );

        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 8, 8)), adapters, v1_8_8);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 12, 2)), adapters, v1_8_8);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 13, 0)), adapters, v1_13_2);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 13, 2)), adapters, v1_13_2);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 14, 0)), adapters, v1_16_5);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 16, 5)), adapters, v1_16_5);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 17, 0)), adapters, v1_17_1);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 18, 2)), adapters, v1_17_1);
        assertResolvesExactlyOneFamily(paperProfile(MinecraftVersion.of(1, 19, 2), true, true, false), adapters, v1_19_2);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 20, 6)), adapters, v1_19_2);
        assertResolvesExactlyOneFamily(spigotProfile(MinecraftVersion.of(1, 21, 0)), adapters, v1_21_11);
        assertResolvesExactlyOneFamily(paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true), adapters, v1_21_11);
    }

    @Test
    void shouldRejectOverlappingExactProfileMatchesWithStableMessage() {
        EntityRuntimeProfile runtimeProfile = paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true);
        EntityVersionAdapter alpha = new AlphaExactPaperAdapter();
        EntityVersionAdapter beta = new BetaExactPaperAdapter();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SpigotEntityBootstrap.selectAdapter(runtimeProfile, Arrays.asList(beta, alpha))
        );

        List<String> adapterTypes = new ArrayList<String>();
        adapterTypes.add(alpha.getClass().getName());
        adapterTypes.add(beta.getClass().getName());
        Collections.sort(adapterTypes);
        assertEquals(
                "Ambiguous entity adapters for runtime profile "
                        + runtimeProfile
                        + " at selection tier EXACT_PROFILE: "
                        + adapterTypes
                        + '.',
                exception.getMessage()
        );
    }

    @Test
    void shouldDetectPaperFlavorFromClassLoaderMarkersSafely() {
        RuntimeServerFlavor spigotFlavor = RuntimeServerFlavorDetector.detect(
                Collections.<ClassLoader>singletonList(new URLClassLoader(new URL[0], null))
        );
        RuntimeServerFlavor paperFlavor = RuntimeServerFlavorDetector.detect(
                Collections.<ClassLoader>singletonList(
                        new SelectiveClassLoader(
                                getClass().getClassLoader(),
                                "com/destroystokyo/paper/PaperConfig.class"
                        )
                )
        );

        assertEquals(RuntimeServerFlavor.SPIGOT, spigotFlavor);
        assertEquals(RuntimeServerFlavor.PAPER, paperFlavor);
    }

    @Test
    void shouldResolveDefaultFeatureProbeFlagsForPlannedPaperFamilies() {
        RuntimeFeatureProbeRegistry registry = RuntimeFeatureProbeRegistry.defaultRegistry();
        List<ClassLoader> emptyClassLoaders = Collections.<ClassLoader>singletonList(new URLClassLoader(new URL[0], null));

        EntityRuntimeProfile paper117 = registry.createProfile(
                MinecraftVersion.of(1, 17, 1),
                RuntimeServerFlavor.PAPER,
                emptyClassLoaders
        );
        EntityRuntimeProfile paper119 = registry.createProfile(
                MinecraftVersion.of(1, 19, 2),
                RuntimeServerFlavor.PAPER,
                emptyClassLoaders
        );
        EntityRuntimeProfile paper121 = registry.createProfile(
                MinecraftVersion.of(1, 21, 11),
                RuntimeServerFlavor.PAPER,
                emptyClassLoaders
        );
        EntityRuntimeProfile spigot121 = registry.createProfile(
                MinecraftVersion.of(1, 21, 11),
                RuntimeServerFlavor.SPIGOT,
                emptyClassLoaders
        );

        assertTrue(paper117.trackerStateAvailable());
        assertFalse(paper117.paperChunkSystemAvailable());
        assertFalse(paper117.paperMoonriseChunkSystemAvailable());

        assertTrue(paper119.trackerStateAvailable());
        assertTrue(paper119.paperChunkSystemAvailable());
        assertFalse(paper119.paperMoonriseChunkSystemAvailable());

        assertTrue(paper121.trackerStateAvailable());
        assertTrue(paper121.paperChunkSystemAvailable());
        assertTrue(paper121.paperMoonriseChunkSystemAvailable());

        assertFalse(spigot121.trackerStateAvailable());
        assertFalse(spigot121.paperChunkSystemAvailable());
        assertFalse(spigot121.paperMoonriseChunkSystemAvailable());
    }

    @Test
    void shouldUseClassLoaderMarkersForChunkSystemAndMoonriseDetection() {
        RuntimeFeatureProbeRegistry registry = RuntimeFeatureProbeRegistry.defaultRegistry();

        EntityRuntimeProfile oldChunkSystem = registry.createProfile(
                MinecraftVersion.of(1, 18, 2),
                RuntimeServerFlavor.PAPER,
                Collections.<ClassLoader>singletonList(
                        new SelectiveClassLoader(
                                getClass().getClassLoader(),
                                "io/papermc/paper/chunk/system/entity/EntityLookup.class"
                        )
                )
        );
        EntityRuntimeProfile moonriseProfile = registry.createProfile(
                MinecraftVersion.of(1, 18, 2),
                RuntimeServerFlavor.PAPER,
                Collections.<ClassLoader>singletonList(
                        new SelectiveClassLoader(
                                getClass().getClassLoader(),
                                "com/destroystokyo/paper/event/entity/EntityAddToWorldEvent.class",
                                "com/destroystokyo/paper/event/entity/EntityRemoveFromWorldEvent.class"
                        )
                )
        );

        assertTrue(oldChunkSystem.trackerStateAvailable());
        assertTrue(oldChunkSystem.paperChunkSystemAvailable());
        assertFalse(oldChunkSystem.paperMoonriseChunkSystemAvailable());

        assertTrue(moonriseProfile.trackerStateAvailable());
        assertFalse(moonriseProfile.paperChunkSystemAvailable());
        assertTrue(moonriseProfile.paperMoonriseChunkSystemAvailable());
    }

    private static EntityRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new EntityRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static void assertResolvesExactlyOneFamily(
            EntityRuntimeProfile runtimeProfile,
            List<EntityVersionAdapter> adapters,
            EntityVersionAdapter expected
    ) {
        long matchingAdapters = adapters.stream()
                .filter(adapter -> adapter.supports(runtimeProfile.minecraftVersion()))
                .count();

        assertEquals(
                1L,
                matchingAdapters,
                "Expected exactly one version-family adapter for " + runtimeProfile.minecraftVersion()
        );
        assertSame(expected, SpigotEntityBootstrap.selectAdapter(runtimeProfile, adapters));
    }

    private static EntityRuntimeProfile paperProfile(
            MinecraftVersion version,
            boolean trackerStateAvailable,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        return new EntityRuntimeProfile(
                version,
                RuntimeServerFlavor.PAPER,
                trackerStateAvailable,
                paperChunkSystemAvailable,
                paperMoonriseChunkSystemAvailable
        );
    }

    private abstract static class BaseAdapter implements EntityVersionAdapter {
        private final MinecraftVersion minimumVersion;
        private final MinecraftVersion maximumVersion;

        private BaseAdapter(MinecraftVersion minimumVersion, MinecraftVersion maximumVersion) {
            this.minimumVersion = minimumVersion;
            this.maximumVersion = maximumVersion;
        }

        @Override
        public MinecraftVersion minimumVersion() {
            return minimumVersion;
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return maximumVersion;
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> ControlledEntity<T> attach(
                T entity,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FlavorNeutralAdapter extends BaseAdapter {

        private FlavorNeutralAdapter(MinecraftVersion minimumVersion, MinecraftVersion maximumVersion) {
            super(minimumVersion, maximumVersion);
        }
    }

    private static class ExactPaperAdapter extends BaseAdapter implements SpigotEntityBootstrap.RuntimeProfileSelectionSupport {
        private final boolean trackerStateAvailable;
        private final boolean paperChunkSystemAvailable;
        private final boolean paperMoonriseChunkSystemAvailable;

        private ExactPaperAdapter(
                MinecraftVersion minimumVersion,
                MinecraftVersion maximumVersion,
                boolean trackerStateAvailable,
                boolean paperChunkSystemAvailable,
                boolean paperMoonriseChunkSystemAvailable
        ) {
            super(minimumVersion, maximumVersion);
            this.trackerStateAvailable = trackerStateAvailable;
            this.paperChunkSystemAvailable = paperChunkSystemAvailable;
            this.paperMoonriseChunkSystemAvailable = paperMoonriseChunkSystemAvailable;
        }

        @Override
        public SpigotEntityBootstrap.RuntimeProfileMatch runtimeProfileMatch(EntityRuntimeProfile runtimeProfile) {
            if (runtimeProfile.serverFlavor() != RuntimeServerFlavor.PAPER) {
                return SpigotEntityBootstrap.RuntimeProfileMatch.UNSUPPORTED;
            }
            if (runtimeProfile.trackerStateAvailable() != trackerStateAvailable) {
                return SpigotEntityBootstrap.RuntimeProfileMatch.UNSUPPORTED;
            }
            if (runtimeProfile.paperChunkSystemAvailable() != paperChunkSystemAvailable) {
                return SpigotEntityBootstrap.RuntimeProfileMatch.UNSUPPORTED;
            }
            if (runtimeProfile.paperMoonriseChunkSystemAvailable() != paperMoonriseChunkSystemAvailable) {
                return SpigotEntityBootstrap.RuntimeProfileMatch.UNSUPPORTED;
            }
            return SpigotEntityBootstrap.RuntimeProfileMatch.EXACT_PROFILE;
        }
    }

    private static final class FallbackAdapter extends BaseAdapter implements SpigotEntityBootstrap.RuntimeProfileSelectionSupport {

        private FallbackAdapter(MinecraftVersion minimumVersion, MinecraftVersion maximumVersion) {
            super(minimumVersion, maximumVersion);
        }

        @Override
        public SpigotEntityBootstrap.RuntimeProfileMatch runtimeProfileMatch(EntityRuntimeProfile runtimeProfile) {
            return SpigotEntityBootstrap.RuntimeProfileMatch.LOWER_PRIORITY_FALLBACK;
        }
    }

    private static final class AlphaExactPaperAdapter extends ExactPaperAdapter {

        private AlphaExactPaperAdapter() {
            super(MinecraftVersion.of(1, 21, 0), MinecraftVersion.of(1, 21, 11), true, true, true);
        }
    }

    private static final class BetaExactPaperAdapter extends ExactPaperAdapter {

        private BetaExactPaperAdapter() {
            super(MinecraftVersion.of(1, 21, 0), MinecraftVersion.of(1, 21, 11), true, true, true);
        }
    }

    private static final class SelectiveClassLoader extends ClassLoader {
        private final Set<String> visibleResources;

        private SelectiveClassLoader(ClassLoader parent, String... visibleResources) {
            super(parent);
            this.visibleResources = new HashSet<String>(Arrays.asList(visibleResources));
        }

        @Override
        public URL getResource(String name) {
            if (isRuntimeMarker(name) && !visibleResources.contains(name)) {
                return null;
            }
            return super.getResource(name);
        }

        private boolean isRuntimeMarker(String resourceName) {
            return resourceName.startsWith("com/destroystokyo/paper/")
                    || resourceName.startsWith("io/papermc/paper/");
        }
    }
}
