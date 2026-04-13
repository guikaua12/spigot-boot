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
package tech.guilhermekaua.spigotboot.entity.v1_8_8;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotEntityAdapterV1_8_8Test {

    @Test
    void shouldInstantiateWithoutResolvingNativeClasses() {
        SpigotEntityAdapterV1_8_8 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_8_8::new);

        assertEquals(MinecraftVersion.of(1, 8, 8), adapter.minimumVersion());
        assertEquals(MinecraftVersion.of(1, 8, 8), adapter.maximumVersion());
    }

    @Test
    void shouldExposeLegacyMetadataWithoutResolvingNativeClasses() {
        SpigotEntityAdapterV1_8_8 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_8_8::new);

        assertEquals(EntityFreshSpawnPath.CONSTRUCTOR_FIRST, adapter.entityCapabilities().freshSpawnPath());
        assertFalse(adapter.entityCapabilities().trackerStateHandleAvailable());
        assertTrue(adapter.entityBindings().freshSpawn().hasConstructorBindings());
        assertEquals(
                Arrays.asList(
                        NativeEntityConstructorShape.LEVEL_AND_POSITION,
                        NativeEntityConstructorShape.LEVEL_ONLY
                ),
                adapter.entityBindings().freshSpawn().constructorPriority()
        );
        assertEquals(
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                adapter.entityBindings().freshSpawn().worldRegistrationMode()
        );
        assertTrue(adapter.entityBindings().freshSpawn().trackerEntryHandleAvailable());
        assertFalse(adapter.entityBindings().freshSpawn().trackerStateHandleAvailable());
        assertEquals(
                EntityWorldRegistrationMode.REFERENCE_REWRITE,
                adapter.entityBindings().replacement().worldRegistrationMode()
        );
        assertTrue(adapter.entityBindings().replacement().trackerEntryHandleAvailable());
        assertFalse(adapter.entityBindings().replacement().trackerStateHandleAvailable());
    }

    @Test
    void shouldExposeStableLegacyFreshSpawnSupportThroughTheSharedStrategyBridge() {
        SpigotEntityAdapterV1_8_8 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_8_8::new);

        LegacyFreshSpawnStrategy_1_8_to_1_12.Provider provider = assertInstanceOf(
                LegacyFreshSpawnStrategy_1_8_to_1_12.Provider.class,
                adapter
        );

        assertSame(provider.legacyFreshSpawnSupport(), provider.legacyFreshSpawnSupport());
    }

    @Test
    void shouldExposeStableLegacyReplacementSupportThroughTheSharedStrategyBridge() {
        SpigotEntityAdapterV1_8_8 adapter = assertDoesNotThrow(SpigotEntityAdapterV1_8_8::new);

        LegacyReplacementStrategy_1_8_to_1_12.Provider provider = assertInstanceOf(
                LegacyReplacementStrategy_1_8_to_1_12.Provider.class,
                adapter
        );

        assertSame(provider.legacyReplacementSupport(), provider.legacyReplacementSupport());
    }
}
