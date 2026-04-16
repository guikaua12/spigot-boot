package tech.guilhermekaua.spigotboot.testPlugin.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests pinning the producer-side behavior of the nested {@code ScenarioRecorder}.
 *
 * <p>These tests guard against two regression modes:</p>
 * <ul>
 *   <li>The {@code putIfAbsent} sentinel in {@code scheduleCompletion} must still mark
 *       {@code controllerTickObserved=FALSE} at the deadline when no controller tick ever arrived,
 *       so the scenario flunks instead of passing silently.</li>
 *   <li>{@code containsFailureSignal} must continue to treat {@code null}, negative {@code Number},
 *       and {@code Boolean.FALSE} as failure signals on every non-{@code pass} assertion key.</li>
 * </ul>
 */
class EntityDemoServiceRecorderContractTest {
    private static final String OUTPUT_DIRECTORY_PROPERTY = "entity.matrix.outputDir";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        System.setProperty(OUTPUT_DIRECTORY_PROPERTY, tempDir.toString());
    }

    @AfterEach
    void teardown() {
        System.clearProperty(OUTPUT_DIRECTORY_PROPERTY);
    }

    @Test
    void controllerTickObserved_defaultsToFalseAtCompletion_whenNeverSet_andStillMarksFail() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        // simulate the production scheduleCompletion body — no controller ticks ever arrived
        EntityDemoServiceRecorderBridge.runScheduledCompletion(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"controllerTickObserved\":false"),
                "expected putIfAbsent to pin controllerTickObserved=false at deadline: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "expected pass=false because controllerTickObserved defaulted to false: " + assertionsJson
        );
    }

    @Test
    void controllerTickObserved_preservesTrue_whenSetBeforeCompletion() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        // all descriptor keys populated with passing values; controllerTickObserved TRUE before the sentinel runs
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.TRUE);

        // runScheduledCompletion must not overwrite the TRUE flip because putIfAbsent only writes when absent
        EntityDemoServiceRecorderBridge.runScheduledCompletion(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"controllerTickObserved\":true"),
                "putIfAbsent must not overwrite a prior TRUE observation: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":true"),
                "expected pass=true when all signals are positive: " + assertionsJson
        );
    }

    @Test
    void entityIdStable_false_stillFlunksScenario() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", Boolean.FALSE);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.TRUE);

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"entityIdStable\":false"),
                "assertion must preserve the FALSE signal: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "entityIdStable=false must flunk the scenario via containsFailureSignal: " + assertionsJson
        );
    }

    @Test
    void trackerRebound_false_stillFlunksScenario() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.FALSE);

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"trackerRebound\":false"),
                "assertion must preserve the FALSE signal: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "trackerRebound=false must flunk the scenario via containsFailureSignal: " + assertionsJson
        );
    }

    @Test
    void aiReactedAfterHit_false_stillFlunksScenario() throws IOException {
        // aiReactedAfterHit is on the deathfx-cow descriptor; use that scenario to keep the key native to the JSON output
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("deathfx-cow");
        EntityDemoServiceRecorderBridge.set(recorder, "aiReactedAfterHit", Boolean.FALSE);
        EntityDemoServiceRecorderBridge.set(recorder, "deathEffectCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateRegistrationErrors", Integer.valueOf(0));

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"aiReactedAfterHit\":false"),
                "assertion must preserve the FALSE signal: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "aiReactedAfterHit=false must flunk the scenario via containsFailureSignal: " + assertionsJson
        );
    }

    @Test
    void goalMutationReplacedExistingEntry_false_stillFlunksScenario() throws IOException {
        // goalMutationReplacedExistingEntry is not a descriptor key, but is set as an extra diagnostic in production;
        // containsFailureSignal must still flag it when FALSE even though it will not be serialized.
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "goalMutationReplacedExistingEntry", Boolean.FALSE);

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "goalMutationReplacedExistingEntry=false must flunk via containsFailureSignal even when filtered from output: "
                        + assertionsJson
        );
        // the key is filtered out of the serialized JSON because it is not in the attach-existing-zombie descriptor
        assertFalse(
                assertionsJson.contains("goalMutationReplacedExistingEntry"),
                "non-descriptor keys must not leak into the assertions JSON: " + assertionsJson
        );
    }

    @Test
    void negativeCount_stillFlunksScenario() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(-1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.TRUE);

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"attachCount\":-1"),
                "negative count must be preserved in JSON: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "negative Number count must flunk via containsFailureSignal: " + assertionsJson
        );
    }

    @Test
    void nullValue_stillFlunksScenario() throws IOException {
        EntityDemoService.ScenarioRecorder recorder = EntityDemoServiceRecorderBridge.start("attach-existing-zombie");
        EntityDemoServiceRecorderBridge.set(recorder, "selectedBaseType", "ZOMBIE");
        EntityDemoServiceRecorderBridge.set(recorder, "attachCount", Integer.valueOf(1));
        EntityDemoServiceRecorderBridge.set(recorder, "controllerTickObserved", Boolean.TRUE);
        EntityDemoServiceRecorderBridge.set(recorder, "duplicateSpawnCount", Integer.valueOf(0));
        // entityIdStable explicitly set to null — containsFailureSignal must flag a present-but-null value
        EntityDemoServiceRecorderBridge.set(recorder, "entityIdStable", null);
        EntityDemoServiceRecorderBridge.set(recorder, "trackerRebound", Boolean.TRUE);

        EntityDemoServiceRecorderBridge.complete(recorder);

        String assertionsJson = readAssertions();
        assertTrue(
                assertionsJson.contains("\"entityIdStable\":null"),
                "null value must be preserved in JSON: " + assertionsJson
        );
        assertTrue(
                assertionsJson.contains("\"pass\":false"),
                "null value must flunk via containsFailureSignal: " + assertionsJson
        );
    }

    private String readAssertions() throws IOException {
        Path assertionsFile = tempDir.resolve("assertions.json");
        assertTrue(Files.exists(assertionsFile), "expected assertions.json at " + assertionsFile);
        return Files.readString(assertionsFile);
    }
}
