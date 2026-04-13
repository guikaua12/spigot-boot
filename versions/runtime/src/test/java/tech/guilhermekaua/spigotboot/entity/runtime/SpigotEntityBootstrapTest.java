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
import tech.guilhermekaua.spigotboot.entity.runtime.support.ServiceLoadedAdapter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                "1.8.8-R0.1-SNAPSHOT",
                Arrays.<EntityVersionAdapter>asList(
                        InlineMetadataAdapter.legacy(MinecraftVersion.of(1, 8, 8)),
                        InlineMetadataAdapter.paperLike(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
        assertInstanceOf(LegacyTrackingBindingStrategy_1_8_to_1_12.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldDiscoverServiceLoadedAdapters() {
        List<EntityVersionAdapter> adapters = SpigotEntityBootstrap.discoverAdapters(getClass().getClassLoader());

        assertTrue(adapters.stream().anyMatch(adapter -> adapter instanceof ServiceLoadedAdapter));
    }

    @Test
    void shouldResolvePlatformFromExplicitlyRegisteredAdapters() {
        EntityAdapterRegistry.register(InlineMetadataAdapter.legacy(MinecraftVersion.of(1, 8, 8)));

        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot("1.8.8-R0.1-SNAPSHOT");

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
    }

    @Test
    void shouldExposePaperLikeStrategyBundleForModernMetadataAdapter() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.21.11",
                Arrays.<EntityVersionAdapter>asList(
                        InlineMetadataAdapter.legacy(MinecraftVersion.of(1, 8, 8)),
                        InlineMetadataAdapter.paperLike(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 21, 11), platform.adapter().minimumVersion());
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
        assertInstanceOf(PaperTrackingBindingStrategy_1_21_plus.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldKeepLegacyWorldAndTrackingStrategiesSelectorDrivenForModernVersionMetadata() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.21.11",
                Collections.<EntityVersionAdapter>singletonList(InlineMetadataAdapter.legacy(MinecraftVersion.of(1, 21, 11)))
        );

        assertEquals(MinecraftVersion.of(1, 21, 11), platform.adapter().minimumVersion());
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
        assertInstanceOf(LegacyTrackingBindingStrategy_1_8_to_1_12.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldKeepPaperWorldAndTrackingStrategiesSelectorDrivenForLegacyVersionMetadata() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.8.8-R0.1-SNAPSHOT",
                Collections.<EntityVersionAdapter>singletonList(InlineMetadataAdapter.paperLike(MinecraftVersion.of(1, 8, 8)))
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
        assertInstanceOf(PaperTrackingBindingStrategy_1_21_plus.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldFallbackToUnspecifiedStrategyBundleForPlainBootstrapAdapter() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.21.11",
                Collections.singletonList(new InlineAdapter(MinecraftVersion.of(1, 21, 11)))
        );

        assertEquals("unspecified", platform.strategies().freshSpawn().id());
        assertEquals("unspecified", platform.strategies().replacement().id());
        assertEquals("unspecified", platform.strategies().worldAdd().id());
        assertEquals("unspecified", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldDiscoverServiceLoadedAdaptersFromRuntimeClassLoaderWhenContextClassLoaderCannotSeeThem() {
        Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));

        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot("1.21.11");

        assertTrue(platform.adapter() instanceof ServiceLoadedAdapter);
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
        private final MinecraftVersion version;

        private InlineAdapter(MinecraftVersion version) {
            this.version = version;
        }

        @Override
        public MinecraftVersion minimumVersion() {
            return version;
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return version;
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

    private static final class InlineMetadataAdapter extends InlineAdapter implements EntityVersionMetadataProvider {
        private final EntityVersionCapabilities capabilities;
        private final EntityVersionBindings bindings;

        private InlineMetadataAdapter(
                MinecraftVersion version,
                EntityVersionCapabilities capabilities,
                EntityVersionBindings bindings
        ) {
            super(version);
            this.capabilities = capabilities;
            this.bindings = bindings;
        }

        private static InlineMetadataAdapter legacy(MinecraftVersion version) {
            return new InlineMetadataAdapter(
                    version,
                    new EntityVersionCapabilities(
                            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                            false,
                            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                            EntityWorldRegistrationMode.REFERENCE_REWRITE
                    ),
                    new EntityVersionBindings(
                            new EntityFreshSpawnBinding(
                                    Arrays.asList(
                                            NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                            NativeEntityConstructorShape.LEVEL_ONLY
                                    ),
                                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                                    true,
                                    false
                            ),
                            new EntityReplacementBinding(
                                    EntityWorldRegistrationMode.REFERENCE_REWRITE,
                                    true,
                                    false
                            )
                    )
            );
        }

        private static InlineMetadataAdapter paperLike(MinecraftVersion version) {
            return new InlineMetadataAdapter(
                    version,
                    new EntityVersionCapabilities(
                            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                            true,
                            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                            EntityWorldRegistrationMode.REFERENCE_REWRITE
                    ),
                    new EntityVersionBindings(
                            new EntityFreshSpawnBinding(
                                    Arrays.asList(
                                            NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                            NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                                            NativeEntityConstructorShape.LEVEL_ONLY
                                    ),
                                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                                    true,
                                    true
                            ),
                            new EntityReplacementBinding(
                                    EntityWorldRegistrationMode.REFERENCE_REWRITE,
                                    true,
                                    true
                            )
                    )
            );
        }

        @Override
        public EntityVersionCapabilities entityCapabilities() {
            return capabilities;
        }

        @Override
        public EntityVersionBindings entityBindings() {
            return bindings;
        }
    }
}
