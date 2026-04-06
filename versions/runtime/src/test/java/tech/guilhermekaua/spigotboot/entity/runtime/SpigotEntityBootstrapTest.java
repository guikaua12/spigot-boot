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

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.entity.runtime.exception.EntityAdapterNotFoundException;
import tech.guilhermekaua.spigotboot.entity.runtime.registry.EntityAdapterRegistry;
import tech.guilhermekaua.spigotboot.entity.runtime.support.ServiceLoadedAdapter;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                Arrays.asList(
                        new InlineAdapter(MinecraftVersion.of(1, 8, 8)),
                        new InlineAdapter(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
    }

    @Test
    void shouldDiscoverServiceLoadedAdapters() {
        List<EntityVersionAdapter> adapters = SpigotEntityBootstrap.discoverAdapters(getClass().getClassLoader());

        assertTrue(adapters.stream().anyMatch(adapter -> adapter instanceof ServiceLoadedAdapter));
    }

    @Test
    void shouldResolvePlatformFromExplicitlyRegisteredAdapters() {
        EntityAdapterRegistry.register(new InlineAdapter(MinecraftVersion.of(1, 8, 8)));

        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot("1.8.8-R0.1-SNAPSHOT");

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
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

    private static final class InlineAdapter implements EntityVersionAdapter {
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
        public <T extends LivingEntity> CustomEntityHandle<T> spawn(
                CustomEntityDefinition<T> definition,
                CustomEntitySpawnRequest spawnRequest,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends LivingEntity> ControlledEntity<T> attach(
                T entity,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
