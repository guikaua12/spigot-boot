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
package tech.guilhermekaua.spigotboot.v1_19_2.entity;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.ModernTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotVersionAdapterV1_19_2Test {

    @Test
    void shouldInstantiateWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        assertEquals(MinecraftVersion.of(1, 19, 2), adapter.minimumVersion());
        assertEquals(MinecraftVersion.of(1, 20, 6), adapter.maximumVersion());
        assertTrue(adapter.supports(MinecraftVersion.of(1, 19, 2)));
        assertTrue(adapter.supports(MinecraftVersion.of(1, 20, 6)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 18, 2)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 21, 0)));
    }

    @Test
    void shouldExposePaperLikeMetadataWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        assertEquals(EntityFreshSpawnPath.CONSTRUCTOR_FIRST, adapter.entityCapabilities().freshSpawnPath());
        assertTrue(adapter.entityCapabilities().trackerStateHandleAvailable());
        assertEquals(
                Arrays.asList(
                        NativeEntityConstructorShape.LEVEL_AND_POSITION,
                        NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                        NativeEntityConstructorShape.LEVEL_ONLY
                ),
                adapter.entityBindings().freshSpawn().constructorPriority()
        );
        assertEquals(
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                adapter.entityBindings().freshSpawn().worldRegistrationMode()
        );
        assertTrue(adapter.entityBindings().freshSpawn().trackerEntryHandleAvailable());
        assertTrue(adapter.entityBindings().freshSpawn().trackerStateHandleAvailable());
        assertEquals(
                EntityWorldRegistrationMode.REFERENCE_REWRITE,
                adapter.entityBindings().replacement().worldRegistrationMode()
        );
        assertTrue(adapter.entityBindings().replacement().trackerEntryHandleAvailable());
        assertTrue(adapter.entityBindings().replacement().trackerStateHandleAvailable());
    }

    @Test
    void shouldExposeStablePaperFreshSpawnSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        PaperFreshSpawnStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperFreshSpawnStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperFreshSpawnSupport(), provider.paperFreshSpawnSupport());
    }

    @Test
    void shouldExposeStableModernTrackingSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        PaperFreshSpawnStrategy_1_21_plus.Provider freshProvider = assertInstanceOf(
                PaperFreshSpawnStrategy_1_21_plus.Provider.class,
                adapter
        );
        PaperTrackingBindingStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperTrackingBindingStrategy_1_21_plus.Provider.class,
                adapter
        );
        PaperReplacementStrategy_1_21_plus.Provider replacementProvider = assertInstanceOf(
                PaperReplacementStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperTrackingBindingSupport(), provider.paperTrackingBindingSupport());
        assertSame(freshProvider.paperFreshSpawnSupport(), provider.paperTrackingBindingSupport());
        assertSame(replacementProvider.paperReplacementSupport(), provider.paperTrackingBindingSupport());
    }

    @Test
    void shouldExposeStablePaperReplacementSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        PaperReplacementStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperReplacementStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperReplacementSupport(), provider.paperReplacementSupport());
    }

    @Test
    void shouldExposeProbeDrivenPaperChunkTransportOverlayWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_19_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_19_2::new);

        VersionNetworkMetadataProvider metadataProvider = assertInstanceOf(
                VersionNetworkMetadataProvider.class,
                adapter
        );
        VersionTransportProvider transportProvider = assertInstanceOf(
                VersionTransportProvider.class,
                adapter
        );
        EntityNetworkMetadataContract contract = metadataProvider.entityNetworkMetadataContract();
        ModernTransportSupport spigotSupport = transportProvider.entityTransportSupport(
                new VersionRuntimeProfile(MinecraftVersion.of(1, 19, 2), RuntimeServerFlavor.SPIGOT, true, false, false)
        );
        ModernTransportSupport paperWithoutChunkSupport = transportProvider.entityTransportSupport(
                new VersionRuntimeProfile(MinecraftVersion.of(1, 19, 2), RuntimeServerFlavor.PAPER, true, false, false)
        );
        ModernTransportSupport paperSupport = transportProvider.entityTransportSupport(
                new VersionRuntimeProfile(MinecraftVersion.of(1, 19, 2), RuntimeServerFlavor.PAPER, true, true, false)
        );

        assertEquals("modern-synched-entity-data-1_17-to-1_20_6", contract.id());
        assertEquals(EntityTransportFamily.MODERN_1_19_2_TO_1_20_6, spigotSupport.family());
        assertSame(spigotSupport, paperWithoutChunkSupport);
        assertNotSame(spigotSupport, paperSupport);
        assertEquals("modern-1_19_2-to-1_20_6-spigot", spigotSupport.id());
        assertEquals("modern-1_19_2-to-1_20_6-paper-chunk-system", paperSupport.id());
    }
}
