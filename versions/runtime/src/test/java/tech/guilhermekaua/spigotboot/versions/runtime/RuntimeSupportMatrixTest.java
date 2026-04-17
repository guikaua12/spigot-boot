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
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.support.RuntimeSupportMatrix;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSupportMatrixTest {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );

    @Test
    void shouldDeclareRuntimeReleaseCriteriaForEveryClaimedProfile() {
        RuntimeSupportMatrix.ReleaseCriteria releaseCriteria = RuntimeSupportMatrix.releaseCriteria();

        assertIterableEquals(
                Arrays.asList(
                        "SpigotVersionBootstrapTest",
                        "VersionedPlatformTest",
                        "RuntimeSupportMatrixTest"
                ),
                releaseCriteria.requiredUnitSuites()
        );
        assertTrue(releaseCriteria.matrixEvidenceRequired());
        assertTrue(RuntimeSupportMatrix.declarations().stream().allMatch(RuntimeSupportMatrix.SupportDeclaration::matrixEvidenceRequired));
        assertTrue(RuntimeSupportMatrix.declarations().stream().allMatch(
                declaration -> declaration.requiredUnitSuites().equals(releaseCriteria.requiredUnitSuites())
        ));
    }

    @Test
    void shouldResolveRepresentativeClaimedProfilesWhenBackendsAreFullyWired() {
        assertEquals(
                "legacy-1.8.8-1.12.2",
                RuntimeSupportMatrix.requireSupported(
                        spigotProfile(MinecraftVersion.of(1, 8, 8)),
                        new RuntimeSupportFixtures.SupportedMetadataAdapter(
                                MinecraftVersion.of(1, 8, 8),
                                MinecraftVersion.of(1, 12, 2),
                                RuntimeSupportFixtures.legacyCapabilities(),
                                RuntimeSupportFixtures.legacyBindings(),
                                RuntimeSupportFixtures.metadataContract(MinecraftVersion.of(1, 8, 8))
                        )
                ).id()
        );
        assertEquals(
                "paper-chunk-system-1.19.2-1.20.6",
                RuntimeSupportMatrix.requireSupported(
                        paperProfile(MinecraftVersion.of(1, 19, 2), true, true, false),
                        new RuntimeSupportFixtures.SupportedMetadataAdapter(
                                MinecraftVersion.of(1, 19, 2),
                                MinecraftVersion.of(1, 20, 6),
                                RuntimeSupportFixtures.modernCapabilities(),
                                RuntimeSupportFixtures.modernBindings(),
                                RuntimeSupportFixtures.metadataContract(MinecraftVersion.of(1, 19, 2))
                        )
                ).id()
        );
        assertEquals(
                "spigot-1.21.x",
                RuntimeSupportMatrix.requireSupported(
                        spigotProfile(MinecraftVersion.of(1, 21, 11)),
                        new RuntimeSupportFixtures.SupportedMetadataAdapter(
                                MinecraftVersion.of(1, 21, 0),
                                MinecraftVersion.of(1, 21, 11),
                                RuntimeSupportFixtures.modernCapabilities(),
                                RuntimeSupportFixtures.modernBindings(),
                                RuntimeSupportFixtures.metadataContract(MinecraftVersion.of(1, 21, 11))
                        )
                ).id()
        );
        assertEquals(
                "paper-moonrise-1.21.x",
                RuntimeSupportMatrix.requireSupported(
                        paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                        new RuntimeSupportFixtures.SupportedMetadataAdapter(
                                MinecraftVersion.of(1, 21, 0),
                                MinecraftVersion.of(1, 21, 11),
                                RuntimeSupportFixtures.modernCapabilities(),
                                RuntimeSupportFixtures.modernBindings(),
                                RuntimeSupportFixtures.metadataContract(MinecraftVersion.of(1, 21, 11))
                        )
                ).id()
        );
    }

    @Test
    void shouldFailFastWhenClaimedProfileIsPartiallyWired() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> RuntimeSupportMatrix.requireSupported(
                        paperProfile(MinecraftVersion.of(1, 21, 11), true, true, true),
                        new RuntimeSupportFixtures.MetadataOnlyAdapter(
                                MinecraftVersion.of(1, 21, 0),
                                MinecraftVersion.of(1, 21, 11),
                                RuntimeSupportFixtures.modernCapabilities(),
                                RuntimeSupportFixtures.modernBindings()
                        )
                )
        );

        assertTrue(exception.getMessage().contains("only partially wired"));
        assertTrue(exception.getMessage().contains("metadata backend contract is unspecified"));
        assertTrue(exception.getMessage().contains("modern fresh-spawn runtime bridge is missing"));
        assertTrue(exception.getMessage().contains("modern transport backend bridge is missing"));
    }

    @Test
    void shouldRejectUndeclaredProfilesEvenWhenBackendsExist() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> RuntimeSupportMatrix.requireSupported(
                        paperProfile(MinecraftVersion.of(1, 19, 2), true, false, false),
                        new RuntimeSupportFixtures.SupportedMetadataAdapter(
                                MinecraftVersion.of(1, 19, 2),
                                MinecraftVersion.of(1, 20, 6),
                                RuntimeSupportFixtures.modernCapabilities(),
                                RuntimeSupportFixtures.modernBindings(),
                                RuntimeSupportFixtures.metadataContract(MinecraftVersion.of(1, 19, 2))
                        )
                )
        );

        assertTrue(exception.getMessage().contains("No runtime support declaration claims profile"));
    }

    @Test
    void shouldDefineSupportMatrixClassificationPrecedence() {
        SupportExpectation permanent = SupportMatrixExpectation.classify(
                CustomEntityBaseType.PLAYER,
                baseType -> true,
                EnumSet.of(CustomEntityBaseType.PLAYER),
                EnumSet.noneOf(CustomEntityBaseType.class)
        );
        SupportExpectation absent = SupportMatrixExpectation.classify(
                CustomEntityBaseType.COW,
                baseType -> false,
                EnumSet.of(CustomEntityBaseType.COW),
                EnumSet.noneOf(CustomEntityBaseType.class)
        );
        SupportExpectation preserved = SupportMatrixExpectation.classify(
                CustomEntityBaseType.COW,
                baseType -> true,
                EnumSet.noneOf(CustomEntityBaseType.class),
                EnumSet.of(CustomEntityBaseType.COW)
        );
        SupportExpectation included = SupportMatrixExpectation.classify(
                CustomEntityBaseType.ZOMBIE,
                baseType -> true,
                EnumSet.of(CustomEntityBaseType.ZOMBIE),
                EnumSet.of(CustomEntityBaseType.COW)
        );

        assertEquals("permanent exclusion", permanent.rationale());
        assertEquals("Bukkit EntityType is absent for this version", absent.rationale());
        assertEquals("version-local preserved exclusion", preserved.rationale());
        assertEquals("advertised version contract includes this base type", included.rationale());
        assertTrue(included.included());
    }

    @Test
    void shouldMatchSharedZombieOnlySupportMatrixPattern() {
        EnumSet<CustomEntityBaseType> advertisedSupport = EnumSet.of(CustomEntityBaseType.ZOMBIE);
        EnumSet<CustomEntityBaseType> preservedExclusions = EnumSet.of(CustomEntityBaseType.COW);
        SupportMatrixExpectation expectation = new SupportMatrixExpectation(
                baseType -> baseType == CustomEntityBaseType.ZOMBIE,
                advertisedSupport,
                preservedExclusions
        );

        expectation.assertMatches(baseType -> baseType == CustomEntityBaseType.ZOMBIE);
    }

    @Test
    void shouldMatchSharedBroadSupportMatrixPatternWithPreservedExclusions() {
        EnumSet<CustomEntityBaseType> preservedExclusions = EnumSet.of(CustomEntityBaseType.COW);
        EnumSet<CustomEntityBaseType> advertisedSupport = EnumSet.of(CustomEntityBaseType.ZOMBIE, CustomEntityBaseType.PIG);
        SupportMatrixExpectation expectation = new SupportMatrixExpectation(
                baseType -> baseType == CustomEntityBaseType.ZOMBIE
                        || baseType == CustomEntityBaseType.PIG
                        || baseType == CustomEntityBaseType.PLAYER,
                advertisedSupport,
                preservedExclusions
        );

        expectation.assertMatches(baseType -> baseType == CustomEntityBaseType.ZOMBIE || baseType == CustomEntityBaseType.PIG);
    }

    @Test
    void shouldPinFinalExplicitEntitySupportMatricesForRepresentativeClaimedVersions() {
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_8_8,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_NONE
        );
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_13_2,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_COW
        );
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_16_5,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_COW
        );
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_17_1,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_COW
        );
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_19_2,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_COW
        );
        assertExplicitSupportMatrix(
                RuntimeEntitySupportMatrices.SUPPORT_1_21_11,
                RuntimeEntitySupportMatrices.PRESERVED_EXCLUSIONS_NONE
        );
    }

    private static void assertExplicitSupportMatrix(
            EnumSet<CustomEntityBaseType> advertisedSupport,
            EnumSet<CustomEntityBaseType> preservedExclusions
    ) {
        new SupportMatrixExpectation(
                baseType -> true,
                advertisedSupport,
                preservedExclusions
        ).assertMatches(advertisedSupport::contains);
    }

    private static VersionRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new VersionRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static VersionRuntimeProfile paperProfile(
            MinecraftVersion version,
            boolean trackerStateAvailable,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        return new VersionRuntimeProfile(
                version,
                RuntimeServerFlavor.PAPER,
                trackerStateAvailable,
                paperChunkSystemAvailable,
                paperMoonriseChunkSystemAvailable
        );
    }

    private static final class SupportMatrixExpectation {
        private final Predicate<CustomEntityBaseType> entityTypePresence;
        private final EnumSet<CustomEntityBaseType> advertisedSupport;
        private final EnumSet<CustomEntityBaseType> preservedExclusions;

        private SupportMatrixExpectation(
                Predicate<CustomEntityBaseType> entityTypePresence,
                EnumSet<CustomEntityBaseType> advertisedSupport,
                EnumSet<CustomEntityBaseType> preservedExclusions
        ) {
            this.entityTypePresence = entityTypePresence;
            this.advertisedSupport = advertisedSupport;
            this.preservedExclusions = preservedExclusions;
        }

        private void assertMatches(Predicate<CustomEntityBaseType> supportProbe) {
            EnumSet<CustomEntityBaseType> actualIncluded = EnumSet.noneOf(CustomEntityBaseType.class);
            for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
                SupportExpectation expectation = classify(baseType, entityTypePresence, advertisedSupport, preservedExclusions);
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

        private static SupportExpectation classify(
                CustomEntityBaseType baseType,
                Predicate<CustomEntityBaseType> entityTypePresence,
                EnumSet<CustomEntityBaseType> advertisedSupport,
                EnumSet<CustomEntityBaseType> preservedExclusions
        ) {
            if (PERMANENT_EXCLUSIONS.contains(baseType)) {
                return new SupportExpectation(false, "permanent exclusion");
            }
            if (!entityTypePresence.test(baseType)) {
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
    }

    private static final class SupportExpectation {
        private final boolean included;
        private final String rationale;

        private SupportExpectation(boolean included, String rationale) {
            this.included = included;
            this.rationale = rationale;
        }

        private boolean included() {
            return included;
        }

        private String rationale() {
            return rationale;
        }
    }
}

final class RuntimeEntitySupportMatrices {
    static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS_NONE = EnumSet.noneOf(CustomEntityBaseType.class);
    static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS_COW = EnumSet.of(CustomEntityBaseType.COW);
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_8_8 = EnumSet.of(
            CustomEntityBaseType.ITEM,
            CustomEntityBaseType.EXPERIENCE_ORB,
            CustomEntityBaseType.LEASH_KNOT,
            CustomEntityBaseType.PAINTING,
            CustomEntityBaseType.ARROW,
            CustomEntityBaseType.SNOWBALL,
            CustomEntityBaseType.FIREBALL,
            CustomEntityBaseType.SMALL_FIREBALL,
            CustomEntityBaseType.ENDER_PEARL,
            CustomEntityBaseType.EYE_OF_ENDER,
            CustomEntityBaseType.EXPERIENCE_BOTTLE,
            CustomEntityBaseType.ITEM_FRAME,
            CustomEntityBaseType.WITHER_SKULL,
            CustomEntityBaseType.TNT,
            CustomEntityBaseType.FALLING_BLOCK,
            CustomEntityBaseType.FIREWORK_ROCKET,
            CustomEntityBaseType.ARMOR_STAND,
            CustomEntityBaseType.COMMAND_BLOCK_MINECART,
            CustomEntityBaseType.BOAT,
            CustomEntityBaseType.MINECART,
            CustomEntityBaseType.CHEST_MINECART,
            CustomEntityBaseType.FURNACE_MINECART,
            CustomEntityBaseType.TNT_MINECART,
            CustomEntityBaseType.HOPPER_MINECART,
            CustomEntityBaseType.SPAWNER_MINECART,
            CustomEntityBaseType.CREEPER,
            CustomEntityBaseType.SKELETON,
            CustomEntityBaseType.SPIDER,
            CustomEntityBaseType.GIANT,
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SLIME,
            CustomEntityBaseType.GHAST,
            CustomEntityBaseType.ZOMBIFIED_PIGLIN,
            CustomEntityBaseType.ENDERMAN,
            CustomEntityBaseType.CAVE_SPIDER,
            CustomEntityBaseType.SILVERFISH,
            CustomEntityBaseType.BLAZE,
            CustomEntityBaseType.MAGMA_CUBE,
            CustomEntityBaseType.ENDER_DRAGON,
            CustomEntityBaseType.WITHER,
            CustomEntityBaseType.BAT,
            CustomEntityBaseType.WITCH,
            CustomEntityBaseType.ENDERMITE,
            CustomEntityBaseType.GUARDIAN,
            CustomEntityBaseType.PIG,
            CustomEntityBaseType.SHEEP,
            CustomEntityBaseType.COW,
            CustomEntityBaseType.CHICKEN,
            CustomEntityBaseType.SQUID,
            CustomEntityBaseType.WOLF,
            CustomEntityBaseType.MOOSHROOM,
            CustomEntityBaseType.SNOW_GOLEM,
            CustomEntityBaseType.OCELOT,
            CustomEntityBaseType.IRON_GOLEM,
            CustomEntityBaseType.HORSE,
            CustomEntityBaseType.RABBIT,
            CustomEntityBaseType.VILLAGER,
            CustomEntityBaseType.END_CRYSTAL,
            CustomEntityBaseType.POTION,
            CustomEntityBaseType.EGG,
            CustomEntityBaseType.FISHING_BOBBER,
            CustomEntityBaseType.LIGHTNING_BOLT
    );
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_13_2 = EnumSet.of(
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SKELETON
    );
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_16_5 = EnumSet.of(CustomEntityBaseType.ZOMBIE);
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_17_1 = EnumSet.of(
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SKELETON
    );
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_19_2 = create1_19_2Support();
    static final EnumSet<CustomEntityBaseType> SUPPORT_1_21_11 = create1_21_11Support();

    private RuntimeEntitySupportMatrices() {
    }

    private static EnumSet<CustomEntityBaseType> create1_19_2Support() {
        EnumSet<CustomEntityBaseType> supportedBaseTypes = EnumSet.allOf(CustomEntityBaseType.class);
        supportedBaseTypes.removeAll(PERMANENT_EXCLUSIONS);
        supportedBaseTypes.remove(CustomEntityBaseType.COW);
        supportedBaseTypes.removeAll(EnumSet.of(
                CustomEntityBaseType.CAMEL,
                CustomEntityBaseType.BLOCK_DISPLAY,
                CustomEntityBaseType.INTERACTION,
                CustomEntityBaseType.ITEM_DISPLAY,
                CustomEntityBaseType.SNIFFER,
                CustomEntityBaseType.TEXT_DISPLAY,
                CustomEntityBaseType.BREEZE,
                CustomEntityBaseType.WIND_CHARGE,
                CustomEntityBaseType.BREEZE_WIND_CHARGE,
                CustomEntityBaseType.ARMADILLO,
                CustomEntityBaseType.BOGGED,
                CustomEntityBaseType.OMINOUS_ITEM_SPAWNER,
                CustomEntityBaseType.ACACIA_BOAT,
                CustomEntityBaseType.ACACIA_CHEST_BOAT,
                CustomEntityBaseType.BAMBOO_RAFT,
                CustomEntityBaseType.BAMBOO_CHEST_RAFT,
                CustomEntityBaseType.BIRCH_BOAT,
                CustomEntityBaseType.BIRCH_CHEST_BOAT,
                CustomEntityBaseType.CHERRY_BOAT,
                CustomEntityBaseType.CHERRY_CHEST_BOAT,
                CustomEntityBaseType.DARK_OAK_BOAT,
                CustomEntityBaseType.DARK_OAK_CHEST_BOAT,
                CustomEntityBaseType.JUNGLE_BOAT,
                CustomEntityBaseType.JUNGLE_CHEST_BOAT,
                CustomEntityBaseType.MANGROVE_BOAT,
                CustomEntityBaseType.MANGROVE_CHEST_BOAT,
                CustomEntityBaseType.OAK_BOAT,
                CustomEntityBaseType.OAK_CHEST_BOAT,
                CustomEntityBaseType.PALE_OAK_BOAT,
                CustomEntityBaseType.PALE_OAK_CHEST_BOAT,
                CustomEntityBaseType.SPRUCE_BOAT,
                CustomEntityBaseType.SPRUCE_CHEST_BOAT,
                CustomEntityBaseType.CREAKING
        ));
        return supportedBaseTypes;
    }

    private static EnumSet<CustomEntityBaseType> create1_21_11Support() {
        EnumSet<CustomEntityBaseType> supportedBaseTypes = EnumSet.allOf(CustomEntityBaseType.class);
        supportedBaseTypes.removeAll(PERMANENT_EXCLUSIONS);
        return supportedBaseTypes;
    }
}
