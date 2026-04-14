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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.EntityRuntimeProfile;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.entity.runtime.support.RuntimeSupportMatrix;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSupportMatrixTest {

    @Test
    void shouldDeclareRuntimeReleaseCriteriaForEveryClaimedProfile() {
        RuntimeSupportMatrix.ReleaseCriteria releaseCriteria = RuntimeSupportMatrix.releaseCriteria();

        assertIterableEquals(
                Arrays.asList(
                        "SpigotEntityBootstrapTest",
                        "VersionedEntityPlatformTest",
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

    private static EntityRuntimeProfile spigotProfile(MinecraftVersion version) {
        return new EntityRuntimeProfile(version, RuntimeServerFlavor.SPIGOT, false, false, false);
    }

    private static EntityRuntimeProfile paperProfile(
            MinecraftVersion version,
            boolean trackerStateAvailable,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        return new EntityRuntimeProfile(
                version,
                RuntimeServerFlavor.PAPER,
                trackerStateAvailable,
                paperChunkSystemAvailable,
                paperMoonriseChunkSystemAvailable
        );
    }
}
