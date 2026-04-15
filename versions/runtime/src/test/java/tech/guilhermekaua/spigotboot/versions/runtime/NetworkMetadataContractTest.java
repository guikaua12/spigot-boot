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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityMetadataFamily;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkMetadataContractTest {

    @Test
    void shouldKeepVersionFactoryTypeMetadataSeparateFromNetworkMetadataContract() throws IOException {
        String legacyFactorySource = readModuleSource(
                "..",
                "1.8.8",
                "src",
                "main",
                "java",
                "tech",
                "guilhermekaua",
                "spigotboot",
                "v1_8_8",
                "entity",
                "EntityFactoryV1_8_8.java"
        );
        String latestFactorySource = readModuleSource(
                "..",
                "1.21.11",
                "src",
                "main",
                "java",
                "tech",
                "guilhermekaua",
                "spigotboot",
                "v1_21_11",
                "entity",
                "EntityFactoryV1_21_11.java"
        );

        assertTrue(legacyFactorySource.contains("Map<CustomEntityBaseType, EntityMetadata> metadataRegistry"));
        assertTrue(latestFactorySource.contains("Map<CustomEntityBaseType, EntityMetadata> metadataRegistry"));
        assertNotEquals("EntityMetadata", EntityNetworkMetadataContract.class.getSimpleName());
        assertTrue(
                EntityNetworkMetadataContract.class.getName().contains("versions.runtime.network.metadata"),
                "the dedicated watcher contract should live in the runtime network metadata package"
        );
    }

    @Test
    void shouldResolveDedicatedNetworkMetadataContractSeparatelyFromFamilySelection() {
        VersionedPlatform platform = new VersionedPlatform(
                MinecraftVersion.of(1, 21, 11),
                new MetadataContractAdapter(EntityNetworkMetadataContract.of("latest-snapshot-contract"))
        );

        assertSame(EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X, platform.networkRuntime().metadataFamily());
        assertEquals("latest-snapshot-contract", platform.networkMetadataContract().id());
        assertTrue(platform.networkMetadataContract().isSpecified());
    }

    @Test
    void shouldFallbackToUnspecifiedNetworkMetadataContractWhenAdapterDoesNotPublishOne() {
        VersionedPlatform platform = new VersionedPlatform(
                MinecraftVersion.of(1, 21, 11),
                new SelectionMetadataOnlyAdapter()
        );

        assertFalse(platform.networkMetadataContract().isSpecified());
        assertTrue(platform.networkMetadataContract().initialSnapshot(new NoOpMetadataSource()).isEmpty());
        assertTrue(platform.networkMetadataContract().dirtyDelta(new NoOpMetadataSource()).isEmpty());
    }

    private static String readModuleSource(String first, String... more) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(Path.of(first, more)).normalize());
    }

    private static final class MetadataContractAdapter implements VersionAdapter,
            VersionMetadataProvider,
            VersionNetworkMetadataProvider {
        private final EntityNetworkMetadataContract contract;

        private MetadataContractAdapter(EntityNetworkMetadataContract contract) {
            this.contract = contract;
        }

        @Override
        public @NotNull MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public @NotNull MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public @NotNull VersionCapabilities entityCapabilities() {
            return new VersionCapabilities(
                    EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                    true,
                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                    EntityWorldRegistrationMode.REFERENCE_REWRITE
            );
        }

        @Override
        public @NotNull VersionBindings entityBindings() {
            return new VersionBindings(
                    new EntityFreshSpawnBinding(
                            Arrays.asList(
                                    NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                    NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL
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
            );
        }

        @Override
        public @NotNull EntityNetworkMetadataContract entityNetworkMetadataContract() {
            return contract;
        }

        @Override
        public boolean supports(@NotNull CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> @NotNull ControlledEntity<T> attach(
                @NotNull T entity,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SelectionMetadataOnlyAdapter implements VersionAdapter, VersionMetadataProvider {

        @Override
        public @NotNull MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public @NotNull MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public @NotNull VersionCapabilities entityCapabilities() {
            return new VersionCapabilities(
                    EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                    true,
                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                    EntityWorldRegistrationMode.REFERENCE_REWRITE
            );
        }

        @Override
        public @NotNull VersionBindings entityBindings() {
            return new VersionBindings(
                    new EntityFreshSpawnBinding(
                            Arrays.asList(
                                    NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                    NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL
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
            );
        }

        @Override
        public boolean supports(@NotNull CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> @NotNull ControlledEntity<T> attach(
                @NotNull T entity,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoOpMetadataSource implements tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataSource {
    }
}
