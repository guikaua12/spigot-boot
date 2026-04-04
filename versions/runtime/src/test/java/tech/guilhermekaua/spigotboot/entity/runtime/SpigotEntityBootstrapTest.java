/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntity;
import tech.guilhermekaua.spigotboot.entity.api.EntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.capability.EntityCapabilities;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKeys;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKeys;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotEntityBootstrapTest {

    @Test
    void shouldSelectLegacyAdapterForLegacyVersion() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.8.8-R0.1-SNAPSHOT",
                Arrays.<EntityVersionAdapter>asList(
                        new FakeAdapter(MinecraftVersion.of(1, 8, 8)),
                        new FakeAdapter(MinecraftVersion.of(1, 21, 11))
                )
        );

        assertEquals(MinecraftVersion.of(1, 8, 8), platform.adapter().minimumVersion());
    }

    @Test
    void shouldExposeGoalSupportFromSelectedAdapter() {
        VersionedEntityPlatform platform = SpigotEntityBootstrap.boot(
                "1.21.11",
                Collections.<EntityVersionAdapter>singletonList(new FakeAdapter(MinecraftVersion.of(1, 21, 11)))
        );

        assertTrue(platform.supportsGoal(EntityTypeKeys.ZOMBIE, EntityGoalKeys.FLOAT));
    }

    private static final class FakeAdapter implements EntityVersionAdapter {
        private final MinecraftVersion version;

        private FakeAdapter(MinecraftVersion version) {
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
        public EntityCapabilities capabilities() {
            return new EntityCapabilities() {
                @Override
                public Set<EntityGoalKey> supportedGoals(EntityTypeKey entityType) {
                    return Collections.singleton(EntityGoalKeys.FLOAT);
                }
            };
        }

        @Override
        public CustomEntity createEntity(EntityTypeKey entityType, EntitySpawnRequest spawnRequest) {
            throw new UnsupportedOperationException();
        }
    }
}
