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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTrackerHookFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertSame;

class EntityNetworkRuntimeBundleSelectorTest {

    @Test
    void shouldSelectLegacyFamiliesFor1_8_8() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 8, 8)),
                legacyCapabilities(),
                legacyBindings()
        );

        assertSame(EntityTrackerHookFamily.LEGACY_ENTRY_HOOK, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.LEGACY_WORLD_LISTENER, bundle.publicationFamily());
        assertSame(EntityTransportFamily.LEGACY_1_8_TO_1_13_2, bundle.transportFamily());
        assertSame(EntityMetadataFamily.LEGACY_DATA_WATCHER, bundle.metadataFamily());
    }

    @Test
    void shouldSelectTransitionalMetadataFamilyFor1_13_2() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 13, 2)),
                legacyCapabilities(),
                legacyBindings()
        );

        assertSame(EntityTrackerHookFamily.LEGACY_ENTRY_HOOK, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.LEGACY_WORLD_LISTENER, bundle.publicationFamily());
        assertSame(EntityTransportFamily.LEGACY_1_8_TO_1_13_2, bundle.transportFamily());
        assertSame(EntityMetadataFamily.TRANSITIONAL_DATA_WATCHER_1_13, bundle.metadataFamily());
    }

    @Test
    void shouldSelectMidEraModernFamiliesFor1_16_5() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 16, 5)),
                modernCapabilities(),
                modernBindings()
        );

        assertSame(EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.ENTITIES_BY_UUID, bundle.publicationFamily());
        assertSame(EntityTransportFamily.MODERN_1_14_TO_1_16_5, bundle.transportFamily());
        assertSame(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5, bundle.metadataFamily());
    }

    @Test
    void shouldSelectSectionManagerFamiliesFor1_17_1() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 17, 1)),
                modernCapabilities(),
                modernBindings()
        );

        assertSame(EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.SECTION_MANAGER, bundle.publicationFamily());
        assertSame(EntityTransportFamily.MODERN_1_17_TO_1_18_2, bundle.transportFamily());
        assertSame(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6, bundle.metadataFamily());
    }

    @Test
    void shouldSplit1_19_2SpigotAndPaperPublicationFamilies() {
        EntityNetworkRuntimeBundle spigotBundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 19, 2)),
                modernCapabilities(),
                modernBindings()
        );
        EntityNetworkRuntimeBundle paperBundle = EntityNetworkRuntimeBundleSelector.select(
                paperProfile(MinecraftVersion.of(1, 19, 2), true, false),
                modernCapabilities(),
                modernBindings()
        );

        assertSame(EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE, spigotBundle.trackerHookFamily());
        assertSame(EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE, paperBundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.SECTION_MANAGER, spigotBundle.publicationFamily());
        assertSame(EntityPublicationFamily.PAPER_CHUNK_SYSTEM, paperBundle.publicationFamily());
        assertSame(EntityTransportFamily.MODERN_1_19_2_TO_1_20_6, spigotBundle.transportFamily());
        assertSame(EntityTransportFamily.MODERN_1_19_2_TO_1_20_6, paperBundle.transportFamily());
        assertSame(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6, spigotBundle.metadataFamily());
        assertSame(EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6, paperBundle.metadataFamily());
    }

    @Test
    void shouldSelectLatestFamiliesFor1_21_11Paper() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                paperProfile(MinecraftVersion.of(1, 21, 11), true, true),
                modernCapabilities(),
                modernBindings()
        );

        assertSame(EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.PAPER_MOONRISE_CHUNK_SYSTEM, bundle.publicationFamily());
        assertSame(EntityTransportFamily.LATEST_1_21_X, bundle.transportFamily());
        assertSame(EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X, bundle.metadataFamily());
    }

    @Test
    void shouldFallbackToUnspecifiedFamiliesWithoutRuntimeMetadata() {
        EntityNetworkRuntimeBundle bundle = EntityNetworkRuntimeBundleSelector.select(
                spigotProfile(MinecraftVersion.of(1, 21, 11)),
                VersionCapabilities.unspecified(),
                VersionBindings.unspecified()
        );

        assertSame(EntityTrackerHookFamily.UNSPECIFIED, bundle.trackerHookFamily());
        assertSame(EntityPublicationFamily.UNSPECIFIED, bundle.publicationFamily());
        assertSame(EntityTransportFamily.UNSPECIFIED, bundle.transportFamily());
        assertSame(EntityMetadataFamily.UNSPECIFIED, bundle.metadataFamily());
    }

    private static VersionRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new VersionRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static VersionRuntimeProfile paperProfile(
            MinecraftVersion version,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        return new VersionRuntimeProfile(
                version,
                RuntimeServerFlavor.PAPER,
                true,
                paperChunkSystemAvailable,
                paperMoonriseChunkSystemAvailable
        );
    }

    private static VersionCapabilities legacyCapabilities() {
        return new VersionCapabilities(
                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                false,
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                EntityWorldRegistrationMode.REFERENCE_REWRITE
        );
    }

    private static VersionCapabilities modernCapabilities() {
        return new VersionCapabilities(
                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                true,
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                EntityWorldRegistrationMode.REFERENCE_REWRITE
        );
    }

    private static VersionBindings legacyBindings() {
        return new VersionBindings(
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
        );
    }

    private static VersionBindings modernBindings() {
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
}
