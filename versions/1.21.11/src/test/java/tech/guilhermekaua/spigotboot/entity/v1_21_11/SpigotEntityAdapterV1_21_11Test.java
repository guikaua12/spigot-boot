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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionTransportProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.entity.runtime.network.transport.ModernTransportSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityTransportFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotEntityAdapterV1_21_11Test {

    @Test
    void shouldInstantiateWithoutResolvingNativeClasses() {
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

        assertEquals(MinecraftVersion.of(1, 21, 0), adapter.minimumVersion());
        assertEquals(MinecraftVersion.of(1, 21, 11), adapter.maximumVersion());
        assertTrue(adapter.supports(MinecraftVersion.of(1, 21, 0)));
        assertTrue(adapter.supports(MinecraftVersion.of(1, 21, 11)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 20, 6)));
    }

    @Test
    void shouldExposePaperLikeMetadataWithoutResolvingNativeClasses() {
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

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
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

        PaperFreshSpawnStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperFreshSpawnStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperFreshSpawnSupport(), provider.paperFreshSpawnSupport());
    }

    @Test
    void shouldExposeStableModernTrackingSupportThroughTheSharedStrategyBridge() {
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

        PaperTrackingBindingStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperTrackingBindingStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperTrackingBindingSupport(), provider.paperTrackingBindingSupport());
    }

    @Test
    void shouldExposeStablePaperReplacementSupportThroughTheSharedStrategyBridge() {
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

        PaperReplacementStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperReplacementStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperReplacementSupport(), provider.paperReplacementSupport());
    }

    @Test
    void shouldExposeLatestPaperOverlayOnlyWhenTheRuntimeProfileProbesNeedIt() {
        SpigotEntityAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_21_11::new);

        EntityVersionNetworkMetadataProvider metadataProvider = assertInstanceOf(
                EntityVersionNetworkMetadataProvider.class,
                adapter
        );
        EntityVersionTransportProvider transportProvider = assertInstanceOf(
                EntityVersionTransportProvider.class,
                adapter
        );
        EntityNetworkMetadataContract contract = metadataProvider.entityNetworkMetadataContract();
        ModernTransportSupport spigotSupport = transportProvider.entityTransportSupport(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.SPIGOT, true, false, false)
        );
        ModernTransportSupport paperChunkSupport = transportProvider.entityTransportSupport(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.PAPER, true, true, false)
        );
        ModernTransportSupport paperSupport = transportProvider.entityTransportSupport(
                new EntityRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.PAPER, true, false, true)
        );

        assertEquals("latest-synched-entity-data-1_21-x", contract.id());
        assertEquals(EntityTransportFamily.LATEST_1_21_X, spigotSupport.family());
        assertNotSame(spigotSupport, paperChunkSupport);
        assertNotSame(spigotSupport, paperSupport);
        assertEquals("latest-1_21-x-spigot", spigotSupport.id());
        assertEquals("latest-1_21-x-paper-overlay", paperChunkSupport.id());
        assertEquals("latest-1_21-x-paper-overlay", paperSupport.id());
    }
}
