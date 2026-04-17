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
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityGoalSupportBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityGoalSupportBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityGoalSupportFamily;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityGoalSupportBundleSelectorTest {

    @Test
    void shouldSelectLegacyFamilyFor1_8_8WhenProviderIsPresent() {
        EntityGoalSupportBundle bundle = EntityGoalSupportBundleSelector.select(
                MinecraftVersion.of(1, 8, 8),
                metadata(),
                new NoOpGoalSupportProvider()
        );

        assertSame(EntityGoalSupportFamily.LEGACY_1_8_TO_1_12, bundle.family());
        assertTrue(bundle.providerAvailable());
    }

    @Test
    void shouldSelectTransitionalFamilyFor1_13_2WhenProviderIsPresent() {
        EntityGoalSupportBundle bundle = EntityGoalSupportBundleSelector.select(
                MinecraftVersion.of(1, 13, 2),
                metadata(),
                new NoOpGoalSupportProvider()
        );

        assertSame(EntityGoalSupportFamily.TRANSITIONAL_1_13, bundle.family());
    }

    @Test
    void shouldSelectModernFamilyFor1_16_5WhenProviderIsPresent() {
        EntityGoalSupportBundle bundle = EntityGoalSupportBundleSelector.select(
                MinecraftVersion.of(1, 16, 5),
                metadata(),
                new NoOpGoalSupportProvider()
        );

        assertSame(EntityGoalSupportFamily.MODERN_1_14_TO_1_20_6, bundle.family());
    }

    @Test
    void shouldSelectLatestFamilyFor1_21_11WhenProviderIsPresent() {
        EntityGoalSupportBundle bundle = EntityGoalSupportBundleSelector.select(
                MinecraftVersion.of(1, 21, 11),
                metadata(),
                new NoOpGoalSupportProvider()
        );

        assertSame(EntityGoalSupportFamily.LATEST_1_21_X, bundle.family());
    }

    @Test
    void shouldFallbackToUnspecifiedWhenProviderIsAbsent() {
        EntityGoalSupportBundle bundle = EntityGoalSupportBundleSelector.select(
                MinecraftVersion.of(1, 21, 11),
                VersionGoalSupportMetadata.unspecified(),
                null
        );

        assertSame(EntityGoalSupportFamily.UNSPECIFIED, bundle.family());
        assertFalse(bundle.providerAvailable());
    }

    private static VersionGoalSupportMetadata metadata() {
        return new VersionGoalSupportMetadata(
                EnumSet.of(VanillaGoalKey.FLOAT),
                true,
                true,
                true
        );
    }

    private static final class NoOpGoalSupportProvider implements VersionGoalSupportProvider {
        @Override
        public VersionGoalSupportMetadata entityGoalSupportMetadata() {
            return metadata();
        }

        @Override
        public <T extends Entity> RuntimeGoalMutationExecutor<T> createSpawnGoalMutationExecutor(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                MinecraftVersion minecraftVersion
        ) {
            return RuntimeGoalMutationExecutor.noop(template.goalProfile());
        }

        @Override
        public <T extends Entity> RuntimeGoalMutationExecutor<T> createAttachedGoalMutationExecutor(
                CustomEntityBaseType baseType,
                T entity,
                MinecraftVersion minecraftVersion
        ) {
            return RuntimeGoalMutationExecutor.noop(tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile
                    .builder((Class<T>) entity.getClass().asSubclass(Entity.class))
                    .build());
        }
    }
}
