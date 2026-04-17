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
package tech.guilhermekaua.spigotboot.v1_13_2.entity;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityVersionLegacyTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.LegacyTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerHookSupport;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotVersionAdapterV1_13_2Test {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORT = EnumSet.of(
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SKELETON
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS = EnumSet.of(CustomEntityBaseType.COW);

    @Test
    void shouldInstantiateWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);

        assertEquals(MinecraftVersion.of(1, 13, 0), adapter.minimumVersion());
        assertEquals(MinecraftVersion.of(1, 13, 2), adapter.maximumVersion());
        assertTrue(adapter.supports(MinecraftVersion.of(1, 13, 0)));
        assertTrue(adapter.supports(MinecraftVersion.of(1, 13, 2)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 12, 2)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 14, 0)));
    }

    @Test
    void shouldMatchTheExplicitSupportMatrixContract() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);

        wireEntrypoint(adapter, allocateFactoryWithoutConstructor());

        assertSupportMatrix(adapter::supports, ADVERTISED_SUPPORT, PRESERVED_EXCLUSIONS);
    }

    @Test
    void shouldExposeLegacyMetadataWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);

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
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);

        LegacyFreshSpawnStrategy_1_8_to_1_12.Provider provider = assertInstanceOf(
                LegacyFreshSpawnStrategy_1_8_to_1_12.Provider.class,
                adapter
        );

        assertSame(provider.legacyFreshSpawnSupport(), provider.legacyFreshSpawnSupport());
    }

    @Test
    void shouldExposeStableLegacyReplacementSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);

        LegacyReplacementStrategy_1_8_to_1_12.Provider provider = assertInstanceOf(
                LegacyReplacementStrategy_1_8_to_1_12.Provider.class,
                adapter
        );

        assertSame(provider.legacyReplacementSupport(), provider.legacyReplacementSupport());
    }

    @Test
    void shouldExposeTransitionalLegacyTrackerHookBridgeForBothSupportPaths() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);
        LegacyFreshSpawnStrategy_1_8_to_1_12.Provider freshProvider = assertInstanceOf(
                LegacyFreshSpawnStrategy_1_8_to_1_12.Provider.class,
                adapter
        );
        LegacyReplacementStrategy_1_8_to_1_12.Provider replacementProvider = assertInstanceOf(
                LegacyReplacementStrategy_1_8_to_1_12.Provider.class,
                adapter
        );

        LegacyTrackerHookSupport freshSupport = freshProvider.legacyFreshSpawnSupport().legacyTrackerHookSupport();
        LegacyTrackerHookSupport replacementSupport = replacementProvider.legacyReplacementSupport().legacyTrackerHookSupport();

        assertNotNull(freshSupport);
        assertSame(freshSupport, replacementSupport);
        assertEquals("legacy-entry-hook-1.13.0-1.13.2", freshSupport.overlayId());
    }

    @Test
    void shouldExposeSpecifiedTransitionalMetadataContractAndTransportBridge() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);
        VersionNetworkMetadataProvider metadataProvider = assertInstanceOf(
                VersionNetworkMetadataProvider.class,
                adapter
        );
        EntityVersionLegacyTransportProvider transportProvider = assertInstanceOf(
                EntityVersionLegacyTransportProvider.class,
                adapter
        );

        EntityNetworkMetadataContract contract = metadataProvider.entityNetworkMetadataContract();
        LegacyTransportSupport support = transportProvider.legacyTransportSupport();

        assertTrue(contract.isSpecified());
        assertEquals("legacy-datawatcher-1.13.0-1.13.2", contract.id());
        assertNotNull(support);
        assertSame(support, transportProvider.legacyTransportSupport());
        assertEquals("legacy-packet-transport-1.13.0-1.13.2", support.id());
    }

    @Test
    void shouldExposeLegacyGoalSupportMetadataThroughTheSharedProviderSeam() {
        SpigotVersionAdapterV1_13_2 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_13_2::new);
        VersionGoalSupportProvider provider = assertInstanceOf(VersionGoalSupportProvider.class, adapter);

        VersionGoalSupportMetadata metadata = provider.entityGoalSupportMetadata();

        assertTrue(metadata.specified());
        assertEquals(
                EnumSet.of(
                        VanillaGoalKey.FLOAT,
                        VanillaGoalKey.MELEE_ATTACK,
                        VanillaGoalKey.RANDOM_STROLL_LAND,
                        VanillaGoalKey.LOOK_AT_PLAYER,
                        VanillaGoalKey.RANDOM_LOOK_AROUND,
                        VanillaGoalKey.HURT_BY_TARGET,
                        VanillaGoalKey.NEAREST_ATTACKABLE_TARGET
                ),
                metadata.supportedVanillaGoalKeys()
        );
        assertTrue(metadata.attachedManagedSnapshotAvailable());
        assertTrue(metadata.spawnedExecutorFactoryAvailable());
        assertTrue(metadata.attachedExecutorFactoryAvailable());
    }

    private static void assertSupportMatrix(
            @NotNull Predicate<CustomEntityBaseType> supportProbe,
            @NotNull EnumSet<CustomEntityBaseType> advertisedSupport,
            @NotNull EnumSet<CustomEntityBaseType> preservedExclusions
    ) {
        EnumSet<CustomEntityBaseType> actualIncluded = EnumSet.noneOf(CustomEntityBaseType.class);
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            SupportExpectation expectation = classify(baseType, advertisedSupport, preservedExclusions);
            boolean supported = supportProbe.test(baseType);

            assertEquals(
                    expectation.included(),
                    supported,
                    "Support matrix mismatch for " + baseType + ": " + expectation.rationale()
            );
            if (supported) {
                actualIncluded.add(baseType);
            }
        }

        assertEquals(advertisedSupport, actualIncluded, "Supported entities should match the advertised contract exactly.");
    }

    private static @NotNull SupportExpectation classify(
            @NotNull CustomEntityBaseType baseType,
            @NotNull EnumSet<CustomEntityBaseType> advertisedSupport,
            @NotNull EnumSet<CustomEntityBaseType> preservedExclusions
    ) {
        if (PERMANENT_EXCLUSIONS.contains(baseType)) {
            return new SupportExpectation(false, "permanent exclusion");
        }
        if (baseType.entityTypeOrNull() == null) {
            return new SupportExpectation(false, "Bukkit EntityType is absent for this version");
        }
        if (advertisedSupport.contains(baseType)) {
            return new SupportExpectation(true, "advertised version contract includes this base type");
        }
        if (preservedExclusions.contains(baseType)) {
            return new SupportExpectation(false, "version-local preserved exclusion");
        }
        return new SupportExpectation(false, "advertised version contract excludes this base type");
    }

    private static final class SupportExpectation {
        private final boolean included;
        private final String rationale;

        private SupportExpectation(boolean included, @NotNull String rationale) {
            this.included = included;
            this.rationale = rationale;
        }

        private boolean included() {
            return included;
        }

        private @NotNull String rationale() {
            return rationale;
        }
    }

    @SuppressWarnings("unchecked")
    private static @NotNull EntityFactoryV1_13_2 allocateFactoryWithoutConstructor() {
        return (EntityFactoryV1_13_2) ReflectionSupport.allocateInstance(EntityFactoryV1_13_2.class);
    }

    private static void wireEntrypoint(
            @NotNull SpigotVersionAdapterV1_13_2 adapter,
            @NotNull EntityFactoryV1_13_2 entrypoint
    ) {
        try {
            Field field = SpigotVersionAdapterV1_13_2.class.getDeclaredField("entrypoint");
            field.setAccessible(true);
            field.set(adapter, entrypoint);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
