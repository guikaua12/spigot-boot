package tech.guilhermekaua.spigotboot.testPlugin.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityScenarioRegistrationTest {
    private static final String SERVER_PROPERTY = "spigotboot.entityMatrix.server";

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty(SERVER_PROPERTY);
        deleteRecursively(Path.of("target", "entity-matrix", "unit-test-server"));
    }

    @Test
    void scenarioRegistry_exposesAllPlannedScenarioIdsAndExactAssertionKeys() {
        EntityDemoService service = new EntityDemoService(null);

        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "orbit",
                        "deathfx-cow",
                        "deathfx-passive-family",
                        "metadata-dirty-zombie",
                        "viewer-cycle-zombie",
                        "viewer-cycle-special-family",
                        "attach-existing-zombie"
                )),
                service.scenarioIds()
        );
        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "pass",
                        "passCount",
                        "failCount",
                        "selectedBaseType",
                        "aiReactedAfterHit",
                        "deathEffectCount",
                        "duplicateRegistrationErrors"
                )),
                service.assertionKeys("deathfx-passive-family")
        );
        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "pass",
                        "viewerAddCount",
                        "viewerRemoveCount",
                        "spawnCount",
                        "destroyCount"
                )),
                service.assertionKeys("viewer-cycle-zombie")
        );
        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "pass",
                        "passCount",
                        "failCount",
                        "selectedBaseType",
                        "viewerAddCount",
                        "viewerRemoveCount",
                        "spawnCount",
                        "destroyCount"
                )),
                service.assertionKeys("viewer-cycle-special-family")
        );
        assertEquals(
                new LinkedHashSet<String>(Arrays.asList(
                        "pass",
                        "passCount",
                        "failCount",
                        "selectedBaseType",
                        "attachCount",
                        "controllerTickObserved",
                        "duplicateSpawnCount",
                        "entityIdStable",
                        "trackerRebound"
                )),
                service.assertionKeys("attach-existing-zombie")
        );
    }

    @Test
    void artifactWriter_usesExactPlanDirectorySchemeAndAssertionContract() throws IOException {
        System.setProperty(SERVER_PROPERTY, "unit-test-server");

        Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        assertions.put("pass", Boolean.TRUE);
        assertions.put("passCount", Integer.valueOf(1));
        assertions.put("failCount", Integer.valueOf(0));
        assertions.put("selectedBaseType", "ARMOR_STAND");
        assertions.put("viewerAddCount", Integer.valueOf(1));
        assertions.put("viewerRemoveCount", Integer.valueOf(1));
        assertions.put("spawnCount", Integer.valueOf(1));
        assertions.put("destroyCount", Integer.valueOf(1));

        Map<String, Object> traceEntry = new LinkedHashMap<String, Object>();
        traceEntry.put("event", "viewer-added");

        EntityScenarioArtifacts.write("viewer-cycle-special-family", Collections.singletonList(traceEntry), assertions);

        Path outputDirectory = Path.of("target", "entity-matrix", "unit-test-server", "viewer-cycle-special-family");
        Path traceFile = outputDirectory.resolve("trace.json");
        Path assertionsFile = outputDirectory.resolve("assertions.json");
        assertTrue(Files.exists(traceFile));
        assertTrue(Files.exists(assertionsFile));

        String traceJson = Files.readString(traceFile);
        String assertionsJson = Files.readString(assertionsFile);
        assertTrue(traceJson.contains("\"scenario\":\"viewer-cycle-special-family\""));
        assertTrue(traceJson.contains("\"server\":\"unit-test-server\""));
        assertTrue(assertionsJson.contains("\"passCount\":1"));
        assertTrue(assertionsJson.contains("\"failCount\":0"));
        assertTrue(assertionsJson.contains("\"selectedBaseType\":\"ARMOR_STAND\""));
        assertTrue(assertionsJson.contains("\"viewerAddCount\":1"));
        assertTrue(assertionsJson.contains("\"viewerRemoveCount\":1"));
        assertTrue(assertionsJson.contains("\"spawnCount\":1"));
        assertTrue(assertionsJson.contains("\"destroyCount\":1"));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(candidate -> {
                    try {
                        Files.deleteIfExists(candidate);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }
}
