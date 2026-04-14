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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.exception.EntityAdapterNotFoundException;
import tech.guilhermekaua.spigotboot.entity.runtime.registry.EntityAdapterRegistry;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyTrackingBindingStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.support.RuntimeSupportMatrix;
import tech.guilhermekaua.spigotboot.entity.runtime.support.ServiceLoadedAdapter;

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

class SpigotEntityBootstrapTest {

    @AfterEach
    void tearDown() {
        EntityAdapterRegistry.clear();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    @Test
    void shouldSelectLegacyAdapterForLegacyVersion() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                spigotProfile(MinecraftVersion.of(1, 8, 8)),
                Arrays.<EntityVersionAdapter>asList(
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
        List<EntityVersionAdapter> adapters = Arrays.<EntityVersionAdapter>asList(
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
        List<EntityVersionAdapter> adapters = SpigotEntityBootstrap.discoverAdapters(getClass().getClassLoader());

        assertTrue(adapters.stream().anyMatch(adapter -> adapter instanceof ServiceLoadedAdapter));
    }

    @Test
    void shouldResolvePlatformFromExplicitlyRegisteredAdapters() {
        EntityAdapterRegistry.register(InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 8, 8)));

        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot("1.8.8-R0.1-SNAPSHOT");

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertSupportDeclarationId("legacy-1.8.8-1.12.2", spigotProfile(MinecraftVersion.of(1, 8, 8)), platform);
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
    }

    @Test
    void shouldExposePaperLikeStrategyBundleForModernMetadataAdapter() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                Arrays.<EntityVersionAdapter>asList(
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
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                Collections.<EntityVersionAdapter>singletonList(InlineSupportedAdapter.legacy(MinecraftVersion.of(1, 21, 11)))
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
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                spigotProfile(MinecraftVersion.of(1, 8, 8)),
                Collections.<EntityVersionAdapter>singletonList(InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 8, 8)))
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
                () -> SpigotEntityBootstrap.boot(
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

        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot("1.21.11");

        assertTrue(platform.adapter() instanceof ServiceLoadedAdapter);
        assertSupportDeclarationId("spigot-1.21.x", spigotProfile(MinecraftVersion.of(1, 21, 11)), platform);
    }

    @Test
    void shouldRejectProfilesThatAreNotExplicitlyDeclaredAsSupported() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SpigotEntityBootstrap.boot(
                        paperProfile(MinecraftVersion.of(1, 19, 2), true, false, false),
                        Collections.<EntityVersionAdapter>singletonList(InlineSupportedAdapter.paperLike(MinecraftVersion.of(1, 19, 2)))
                )
        );

        assertTrue(exception.getMessage().contains("No runtime support declaration claims profile"));
    }

    @Test
    void shouldRejectUnsupportedVersions() {
        EntityAdapterNotFoundException exception = assertThrows(
                EntityAdapterNotFoundException.class,
                () -> SpigotEntityBootstrap.boot("1.7.10", Collections.singletonList(new InlineAdapter(MinecraftVersion.of(1, 8, 8))))
        );

        assertTrue(exception.getMessage().contains("No entity adapter supports Minecraft 1.7.10"));
    }

    private static class InlineAdapter implements EntityVersionAdapter {
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
                EntityVersionCapabilities capabilities,
                EntityVersionBindings bindings
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

    private static EntityRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new EntityRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static void assertResolvesExactlyOneAdapter(
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

    private static void assertSupportDeclarationId(
            String expectedId,
            EntityRuntimeProfile runtimeProfile,
            VersionedEntityPlatform platform
    ) {
        assertEquals(expectedId, RuntimeSupportMatrix.requireSupported(runtimeProfile, platform.adapter()).id());
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
}
