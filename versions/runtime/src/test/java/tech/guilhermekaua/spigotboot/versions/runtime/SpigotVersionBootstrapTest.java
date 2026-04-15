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
package tech.guilhermekaua.spigotboot.versions.runtime;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.SpigotVersionBootstrap;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.exception.VersionAdapterNotFoundException;
import tech.guilhermekaua.spigotboot.versions.runtime.registry.VersionAdapterRegistry;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyTrackingBindingStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.support.RuntimeSupportMatrix;
import tech.guilhermekaua.spigotboot.versions.runtime.support.ServiceLoadedAdapter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotVersionBootstrapTest {

    @AfterEach
    void tearDown() {
        VersionAdapterRegistry.clear();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    @Test
    void shouldSelectLegacyAdapterForLegacyVersion() {
        VersionedPlatform platform = SpigotVersionBootstrap.boot(
                spigotProfile(MinecraftVersion.of(1, 8, 8)),
                Arrays.<VersionAdapter>asList(
                        InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 8, 8)),
                        InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertSupportDeclarationId("legacy-1.8.8-1.12.2", spigotProfile(MinecraftVersion.of(1, 8, 8)), platform);
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
        assertInstanceOf(LegacyTrackingBindingStrategy_1_8_to_1_12.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldSelectDeterministicFamilyRangeAdapterForPlannedVersions() {
        InlineAdapter v1_8_8 = new InlineAdapter(MinecraftVersion.of(1, 8, 8), MinecraftVersion.of(1, 12, 2));
        InlineAdapter v1_13_2 = new InlineAdapter(MinecraftVersion.of(1, 13, 0), MinecraftVersion.of(1, 13, 2));
        InlineAdapter v1_16_5 = new InlineAdapter(MinecraftVersion.of(1, 14, 0), MinecraftVersion.of(1, 16, 5));
        InlineAdapter v1_17_1 = new InlineAdapter(MinecraftVersion.of(1, 17, 0), MinecraftVersion.of(1, 18, 2));
        InlineAdapter v1_19_2 = new InlineAdapter(MinecraftVersion.of(1, 19, 2), MinecraftVersion.of(1, 20, 6));
        InlineAdapter v1_21_11 = new InlineAdapter(MinecraftVersion.of(1, 21, 0), MinecraftVersion.of(1, 21, 11));
        List<VersionAdapter> adapters = Arrays.<VersionAdapter>asList(
                v1_8_8,
                v1_13_2,
                v1_16_5,
                v1_17_1,
                v1_19_2,
                v1_21_11
        );

        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 8, 8)), adapters, v1_8_8);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 12, 2)), adapters, v1_8_8);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 13, 0)), adapters, v1_13_2);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 13, 2)), adapters, v1_13_2);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 14, 0)), adapters, v1_16_5);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 16, 5)), adapters, v1_16_5);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 17, 0)), adapters, v1_17_1);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 18, 2)), adapters, v1_17_1);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 19, 2)), adapters, v1_19_2);
        assertResolvesExactlyOneAdapter(paperProfile(MinecraftVersion.of(1, 20, 6), true, true, false), adapters, v1_19_2);
        assertResolvesExactlyOneAdapter(spigotProfile(MinecraftVersion.of(1, 21, 0)), adapters, v1_21_11);
        assertResolvesExactlyOneAdapter(paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true), adapters, v1_21_11);
    }

    @Test
    void shouldDiscoverServiceLoadedAdapters() {
        List<VersionAdapter> adapters = SpigotVersionBootstrap.discoverAdapters(getClass().getClassLoader());

        assertTrue(adapters.stream().anyMatch(adapter -> adapter instanceof ServiceLoadedAdapter));
    }

    @Test
    void shouldResolvePlatformFromExplicitlyRegisteredAdapters() {
        VersionAdapterRegistry.register(InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 8, 8)));

        VersionedPlatform platform = SpigotVersionBootstrap.boot("1.8.8-R0.1-SNAPSHOT");

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertSupportDeclarationId("legacy-1.8.8-1.12.2", spigotProfile(MinecraftVersion.of(1, 8, 8)), platform);
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
    }

    @Test
    void shouldExposePaperLikeStrategyBundleForModernMetadataAdapter() {
        VersionedPlatform platform = SpigotVersionBootstrap.boot(
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                Arrays.<VersionAdapter>asList(
                        InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 8, 8)),
                        InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 21, 11), platform.adapter().minimumVersion());
        assertSupportDeclarationId(
                "paper-moonrise-1.21.x",
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                platform
        );
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
        assertInstanceOf(PaperTrackingBindingStrategy_1_21_plus.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldKeepLegacyWorldAndTrackingStrategiesSelectorDrivenForModernVersionMetadata() {
        VersionedPlatform platform = SpigotVersionBootstrap.boot(
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                Collections.<VersionAdapter>singletonList(InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 21, 11)))
        );

        assertEquals(MinecraftVersion.of(1, 21, 11), platform.adapter().minimumVersion());
        assertSupportDeclarationId(
                "paper-moonrise-1.21.x",
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                platform
        );
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
        assertInstanceOf(LegacyTrackingBindingStrategy_1_8_to_1_12.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldKeepPaperWorldAndTrackingStrategiesSelectorDrivenForLegacyVersionMetadata() {
        VersionedPlatform platform = SpigotVersionBootstrap.boot(
                spigotProfile(MinecraftVersion.of(1, 8, 8)),
                Collections.<VersionAdapter>singletonList(InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 8, 8)))
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertSupportDeclarationId("legacy-1.8.8-1.12.2", spigotProfile(MinecraftVersion.of(1, 8, 8)), platform);
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
        assertInstanceOf(PaperTrackingBindingStrategy_1_21_plus.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldFallbackToUnspecifiedStrategyBundleForPlainBootstrapAdapter() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SpigotVersionBootstrap.boot(
                        spigotProfile(MinecraftVersion.of(1, 21, 11)),
                        Collections.singletonList(new InlineAdapter(MinecraftVersion.of(1, 21, 11)))
                )
        );

        assertTrue(exception.getMessage().contains("only partially wired"));
        assertTrue(exception.getMessage().contains("tracker hook backend is unspecified"));
    }

    @Test
    void shouldDiscoverServiceLoadedAdaptersFromRuntimeClassLoaderWhenContextClassLoaderCannotSeeThem() {
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));

        VersionedPlatform platform = SpigotVersionBootstrap.boot("1.21.11");

        assertTrue(platform.adapter() instanceof ServiceLoadedAdapter);
        assertSupportDeclarationId("spigot-1.21.x", spigotProfile(MinecraftVersion.of(1, 21, 11)), platform);
    }

    @Test
    void shouldRejectProfilesThatAreNotExplicitlyDeclaredAsSupported() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SpigotVersionBootstrap.boot(
                        paperProfile(MinecraftVersion.of(1, 19, 2), true, false, false),
                        Collections.<VersionAdapter>singletonList(InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 19, 2)))
                )
        );

        assertTrue(exception.getMessage().contains("No runtime support declaration claims profile"));
    }

    @Test
    void shouldRejectUnsupportedVersions() {
        VersionAdapterNotFoundException exception = assertThrows(
                VersionAdapterNotFoundException.class,
                () -> SpigotVersionBootstrap.boot("1.7.10", Collections.singletonList(new InlineAdapter(MinecraftVersion.of(1, 8, 8))))
        );

        assertTrue(exception.getMessage().contains("No version adapter supports Minecraft 1.7.10"));
    }

    private static class InlineAdapter implements VersionAdapter {
        private final MinecraftVersion minimumVersion;
        private final MinecraftVersion maximumVersion;

        private InlineAdapter(MinecraftVersion version) {
            this(version, version);
        }

        private InlineAdapter(MinecraftVersion minimumVersion, MinecraftVersion maximumVersion) {
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

    private static final class InlineSupportedAdapter extends RuntimeSupportFixtures.SupportedMetadataAdapter {

        private InlineSupportedAdapter(
                MinecraftVersion version,
                VersionCapabilities capabilities,
                VersionBindings bindings
        ) {
            super(version, version, capabilities, bindings, RuntimeSupportFixtures.metadataContract(version));
        }

        private static InlineSupportedAdapter legacy(MinecraftVersion version) {
            return new InlineSupportedAdapter(
                    version,
                    RuntimeSupportFixtures.legacyCapabilities(),
                    RuntimeSupportFixtures.legacyBindings()
            );
        }

        private static InlineSupportedAdapter paperLike(MinecraftVersion version) {
            return new InlineSupportedAdapter(
                    version,
                    RuntimeSupportFixtures.modernCapabilities(),
                    RuntimeSupportFixtures.modernBindings()
            );
        }
    }

    private static VersionRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new VersionRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static void assertResolvesExactlyOneAdapter(
            VersionRuntimeProfile runtimeProfile,
            List<VersionAdapter> adapters,
            VersionAdapter expected
    ) {
        long matchingAdapters = adapters.stream()
                .filter(adapter -> adapter.supports(runtimeProfile.minecraftVersion()))
                .count();

        assertEquals(
                1L,
                matchingAdapters,
                "Expected exactly one version-family adapter for " + runtimeProfile.minecraftVersion()
        );
        assertSame(expected, SpigotVersionBootstrap.selectAdapter(runtimeProfile, adapters));
    }

    private static void assertSupportDeclarationId(
            String expectedId,
            VersionRuntimeProfile runtimeProfile,
            VersionedPlatform platform
    ) {
        assertEquals(expectedId, RuntimeSupportMatrix.requireSupported(runtimeProfile, platform.adapter()).id());
    }

    private static VersionRuntimeProfile paperProfile(
            MinecraftVersion version,
            boolean trackerStateAvailable,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        return new VersionRuntimeProfile(
                version,
                RuntimeServerFlavor.PAPER,
                trackerStateAvailable,
                paperChunkSystemAvailable,
                paperMoonriseChunkSystemAvailable
        );
    }
}
