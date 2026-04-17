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
package tech.guilhermekaua.spigotboot.v1_21_11.entity;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
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
import java.util.EnumSet;
import java.util.function.Predicate;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpigotVersionAdapterV1_21_11Test {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS = EnumSet.noneOf(CustomEntityBaseType.class);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORT = createAdvertisedSupport();

    @Test
    void shouldInstantiateWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        assertEquals(MinecraftVersion.of(1, 21, 0), adapter.minimumVersion());
        assertEquals(MinecraftVersion.of(1, 21, 11), adapter.maximumVersion());
        assertTrue(adapter.supports(MinecraftVersion.of(1, 21, 0)));
        assertTrue(adapter.supports(MinecraftVersion.of(1, 21, 11)));
        assertFalse(adapter.supports(MinecraftVersion.of(1, 20, 6)));
    }

    @Test
    void shouldMatchTheExplicitSupportMatrixContract() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        wireEntrypoint(adapter, allocateFactoryWithoutConstructor());

        assertSupportMatrix(adapter::supports, ADVERTISED_SUPPORT, PRESERVED_EXCLUSIONS);
    }

    @Test
    void shouldExposePaperLikeMetadataWithoutResolvingNativeClasses() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

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
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        PaperFreshSpawnStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperFreshSpawnStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperFreshSpawnSupport(), provider.paperFreshSpawnSupport());
    }

    @Test
    void shouldExposeStableModernTrackingSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        PaperTrackingBindingStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperTrackingBindingStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperTrackingBindingSupport(), provider.paperTrackingBindingSupport());
    }

    @Test
    void shouldExposeStablePaperReplacementSupportThroughTheSharedStrategyBridge() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        PaperReplacementStrategy_1_21_plus.Provider provider = assertInstanceOf(
                PaperReplacementStrategy_1_21_plus.Provider.class,
                adapter
        );

        assertSame(provider.paperReplacementSupport(), provider.paperReplacementSupport());
    }

    @Test
    void shouldAdvertiseLatestGoalSupportOnlyThroughTheInternalRuntimeProviderSeam() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

        VersionGoalSupportProvider provider = assertInstanceOf(VersionGoalSupportProvider.class, adapter);
        VersionGoalSupportMetadata metadata = provider.entityGoalSupportMetadata();

        assertEquals(EnumSet.allOf(VanillaGoalKey.class), metadata.supportedVanillaGoalKeys());
        assertTrue(metadata.attachedManagedSnapshotAvailable());
        assertTrue(metadata.spawnedExecutorFactoryAvailable());
        assertTrue(metadata.attachedExecutorFactoryAvailable());
        assertTrue(metadata.specified());
    }

    @Test
    void shouldExposeLatestPaperOverlayOnlyWhenTheRuntimeProfileProbesNeedIt() {
        SpigotVersionAdapterV1_21_11 adapter = assertDoesNotThrow(SpigotVersionAdapterV1_21_11::new);

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
                new VersionRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.SPIGOT, true, false, false)
        );
        ModernTransportSupport paperChunkSupport = transportProvider.entityTransportSupport(
                new VersionRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.PAPER, true, true, false)
        );
        ModernTransportSupport paperSupport = transportProvider.entityTransportSupport(
                new VersionRuntimeProfile(MinecraftVersion.of(1, 21, 11), RuntimeServerFlavor.PAPER, true, false, true)
        );

        assertEquals("latest-synched-entity-data-1_21-x", contract.id());
        assertEquals(EntityTransportFamily.LATEST_1_21_X, spigotSupport.family());
        assertNotSame(spigotSupport, paperChunkSupport);
        assertNotSame(spigotSupport, paperSupport);
        assertEquals("latest-1_21-x-spigot", spigotSupport.id());
        assertEquals("latest-1_21-x-paper-overlay", paperChunkSupport.id());
        assertEquals("latest-1_21-x-paper-overlay", paperSupport.id());
    }

    private static @NotNull EnumSet<CustomEntityBaseType> createAdvertisedSupport() {
        EnumSet<CustomEntityBaseType> advertisedSupport = EnumSet.noneOf(CustomEntityBaseType.class);
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            if (PERMANENT_EXCLUSIONS.contains(baseType)) {
                continue;
            }
            EntityType entityType = baseType.entityTypeOrNull();
            if (entityType == null || entityType.getEntityClass() == null) {
                continue;
            }
            advertisedSupport.add(baseType);
        }
        return advertisedSupport;
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
    private static @NotNull EntityFactoryV1_21_11 allocateFactoryWithoutConstructor() {
        return (EntityFactoryV1_21_11) ReflectionSupport.allocateInstance(EntityFactoryV1_21_11.class);
    }

    private static void wireEntrypoint(
            @NotNull SpigotVersionAdapterV1_21_11 adapter,
            @NotNull EntityFactoryV1_21_11 entrypoint
    ) {
        try {
            Field field = SpigotVersionAdapterV1_21_11.class.getDeclaredField("entrypoint");
            field.setAccessible(true);
            field.set(adapter, entrypoint);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
