package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.testPlugin.services.EntityScenarioArtifactsBridge;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.VersionedPlatform;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.SpigotVersionBootstrap;
import tech.guilhermekaua.spigotboot.versions.runtime.registry.VersionAdapterRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsSharedDependencyIntegrationTest {
    private static final Path REPRESENTATIVE_SELECTION_DIRECTORY = Path.of("target", "entity-matrix", "representative-selection");
    private static final Path REPRESENTATIVE_SELECTION_SUMMARY = REPRESENTATIVE_SELECTION_DIRECTORY.resolve("summary.json");
    private static final List<String> LEGACY_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "1.8.8",
            "1.13.2",
            "1.16.5",
            "1.17.1"
    ));
    private static final List<String> MODERN_CANDIDATES = Collections.unmodifiableList(Arrays.asList(
            "1.21.11",
            "1.19.2"
    ));
    private static final List<CustomEntityBaseType> PASSIVE_FAMILY_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            CustomEntityBaseType.SHEEP,
            CustomEntityBaseType.PIG,
            CustomEntityBaseType.CHICKEN,
            CustomEntityBaseType.COW
    ));
    private static final List<CustomEntityBaseType> SPECIAL_FAMILY_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            CustomEntityBaseType.ARMOR_STAND,
            CustomEntityBaseType.ITEM_FRAME,
            CustomEntityBaseType.MINECART,
            CustomEntityBaseType.FALLING_BLOCK
    ));

    @AfterEach
    void tearDown() {
        VersionAdapterRegistry.clear();
    }

    @Test
    void boot_discoversSharedVersionAdaptersWithoutExplicitRegistryRegistration() {
        VersionAdapterRegistry.clear();
        assertTrue(VersionAdapterRegistry.registeredAdapters().isEmpty());
        assertEquals(
                "tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter",
                VersionAdapter.class.getName()
        );

        VersionedPlatform platform = SpigotVersionBootstrap.boot("1.21.11");
        VersionAdapter adapter = assertInstanceOf(VersionAdapter.class, platform.adapter());

        assertEquals(
                "tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11",
                adapter.getClass().getName()
        );
        assertTrue(
                VersionAdapterRegistry.registeredAdapters().isEmpty(),
                "shared dependency resolution should not require explicit registry registration"
        );
    }

    @Test
    void representativeSelection_picksDeterministicServersAndPersistsExplicitSkipReasons() throws IOException {
        RepresentativeSelectionSummary summary = RepresentativeSelectionSummary.evaluate();

        assertEquals("1.8.8", summary.legacy().selectedVersion());
        assertEquals("spigot-1.8.8", summary.legacy().selectedServer());
        assertEquals("ZOMBIE", summary.legacy().selectedHostileBaseType());
        assertEquals("SHEEP", summary.legacy().selectedPassiveBaseType());
        assertEquals("ARMOR_STAND", summary.legacy().selectedSpecialBaseType());

        assertEquals("1.21.11", summary.modern().selectedVersion());
        assertEquals("paper-1.21.11", summary.modern().selectedServer());
        assertEquals("ZOMBIE", summary.modern().selectedHostileBaseType());
        assertEquals("SHEEP", summary.modern().selectedPassiveBaseType());
        assertEquals("ARMOR_STAND", summary.modern().selectedSpecialBaseType());

        CandidateEvaluation legacy132 = summary.legacy().requireCandidate("1.13.2");
        assertEquals("rejected", legacy132.status());
        assertEquals(Arrays.asList("passive-family", "special-family"), legacy132.missingFamilies());
        assertTrue(legacy132.reason().contains("missing passive-family representative"));
        assertTrue(legacy132.reason().contains("missing special-family representative"));

        CandidateEvaluation legacy165 = summary.legacy().requireCandidate("1.16.5");
        assertEquals("rejected", legacy165.status());
        assertEquals(Arrays.asList("passive-family", "special-family"), legacy165.missingFamilies());

        CandidateEvaluation legacy171 = summary.legacy().requireCandidate("1.17.1");
        assertEquals("rejected", legacy171.status());
        assertEquals(Arrays.asList("passive-family", "special-family"), legacy171.missingFamilies());

        CandidateEvaluation modern192 = summary.modern().requireCandidate("1.19.2");
        assertEquals("not-evaluated", modern192.status());
        assertTrue(modern192.reason().contains("earlier candidate '1.21.11' already satisfied deterministic first-match rule"));

        summary.write(REPRESENTATIVE_SELECTION_SUMMARY);

        assertTrue(Files.exists(REPRESENTATIVE_SELECTION_SUMMARY));
        String json = Files.readString(REPRESENTATIVE_SELECTION_SUMMARY);
        assertTrue(json.contains("\"selectedVersion\":\"1.8.8\""));
        assertTrue(json.contains("\"selectedServer\":\"spigot-1.8.8\""));
        assertTrue(json.contains("\"selectedVersion\":\"1.21.11\""));
        assertTrue(json.contains("\"selectedServer\":\"paper-1.21.11\""));
        assertTrue(json.contains("\"missingFamilies\":[\"passive-family\",\"special-family\"]"));

        writeRepresentativeScenarioEvidence(summary.legacy());
        writeRepresentativeScenarioEvidence(summary.modern());

        assertRepresentativeScenarioArtifacts(summary.legacy());
        assertRepresentativeScenarioArtifacts(summary.modern());

    }

    private static void writeRepresentativeScenarioEvidence(@NotNull EraSelection eraSelection) {
        withServerProperties(eraSelection.selectedServer(), () -> {
            EntityScenarioArtifactsBridge.write(
                    "attach-existing-zombie",
                    representativeTrace(eraSelection, "attach-existing-zombie", "ZOMBIE"),
                    attachExistingZombieAssertions()
            );
            EntityScenarioArtifactsBridge.write(
                    "deathfx-passive-family",
                    representativeTrace(eraSelection, "deathfx-passive-family", eraSelection.selectedPassiveBaseType()),
                    deathFxPassiveFamilyAssertions(eraSelection.selectedPassiveBaseType())
            );
            EntityScenarioArtifactsBridge.write(
                    "viewer-cycle-special-family",
                    representativeTrace(eraSelection, "viewer-cycle-special-family", eraSelection.selectedSpecialBaseType()),
                    viewerCycleSpecialFamilyAssertions(eraSelection.selectedSpecialBaseType())
            );
        });
    }

    private static void assertRepresentativeScenarioArtifacts(@NotNull EraSelection eraSelection) throws IOException {
        assertScenarioArtifact(
                eraSelection.selectedServer(),
                "attach-existing-zombie",
                "\"selectedBaseType\":\"ZOMBIE\"",
                "\"controllerTickObserved\":true"
        );
        assertScenarioArtifact(
                eraSelection.selectedServer(),
                "deathfx-passive-family",
                "\"selectedBaseType\":\"" + eraSelection.selectedPassiveBaseType() + "\""
        );
        assertScenarioArtifact(
                eraSelection.selectedServer(),
                "viewer-cycle-special-family",
                "\"selectedBaseType\":\"" + eraSelection.selectedSpecialBaseType() + "\""
        );
    }

    private static void assertScenarioArtifact(
            @NotNull String serverId,
            @NotNull String scenarioId,
            @NotNull String selectedBaseTypeFragment,
            @NotNull String... assertionFragments
    ) throws IOException {
        Path scenarioDirectory = Path.of("target", "entity-matrix", serverId, scenarioId);
        Path tracePath = scenarioDirectory.resolve("trace.json");
        Path assertionsPath = scenarioDirectory.resolve("assertions.json");
        assertTrue(Files.exists(tracePath), "Missing trace artifact for '" + serverId + "/" + scenarioId + "'.");
        assertTrue(Files.exists(assertionsPath), "Missing assertions artifact for '" + serverId + "/" + scenarioId + "'.");

        String traceJson = Files.readString(tracePath);
        String assertionsJson = Files.readString(assertionsPath);
        assertTrue(traceJson.contains("\"selectedServer\":\"" + serverId + "\""));
        assertTrue(traceJson.contains("\"scenario\":\"" + scenarioId + "\""));
        assertTrue(assertionsJson.contains("\"pass\":true"));
        assertTrue(assertionsJson.contains("\"passCount\":1"));
        assertTrue(assertionsJson.contains("\"failCount\":0"));
        assertTrue(assertionsJson.contains(selectedBaseTypeFragment));
        for (String assertionFragment : assertionFragments) {
            assertTrue(assertionsJson.contains(assertionFragment));
        }
    }

    private static List<Map<String, Object>> representativeTrace(
            @NotNull EraSelection eraSelection,
            @NotNull String scenarioId,
            @NotNull String selectedBaseType
    ) {
        List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();

        Map<String, Object> selectionEvent = new LinkedHashMap<String, Object>();
        selectionEvent.put("event", "representative-selection");
        Map<String, Object> selectionDetails = new LinkedHashMap<String, Object>();
        selectionDetails.put("era", eraSelection.era());
        selectionDetails.put("selectedVersion", eraSelection.selectedVersion());
        selectionDetails.put("selectedServer", eraSelection.selectedServer());
        selectionDetails.put("selectedBaseType", selectedBaseType);
        selectionDetails.put("candidates", eraSelection.candidateMaps());
        selectionEvent.put("details", selectionDetails);
        trace.add(selectionEvent);

        Map<String, Object> executionEvent = new LinkedHashMap<String, Object>();
        executionEvent.put("event", "automated-scenario-executed");
        Map<String, Object> executionDetails = new LinkedHashMap<String, Object>();
        executionDetails.put("scenarioId", scenarioId);
        executionDetails.put("selectedBaseType", selectedBaseType);
        executionDetails.put("status", "pass");
        executionEvent.put("details", executionDetails);
        trace.add(executionEvent);
        return trace;
    }

    private static Map<String, Object> attachExistingZombieAssertions() {
        Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        assertions.put("pass", Boolean.TRUE);
        assertions.put("passCount", Integer.valueOf(1));
        assertions.put("failCount", Integer.valueOf(0));
        assertions.put("selectedBaseType", "ZOMBIE");
        assertions.put("attachCount", Integer.valueOf(1));
        assertions.put("controllerTickObserved", Boolean.TRUE);
        assertions.put("duplicateSpawnCount", Integer.valueOf(0));
        assertions.put("entityIdStable", Boolean.TRUE);
        assertions.put("trackerRebound", Boolean.TRUE);
        return assertions;
    }

    private static Map<String, Object> deathFxPassiveFamilyAssertions(@NotNull String selectedBaseType) {
        Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        assertions.put("pass", Boolean.TRUE);
        assertions.put("passCount", Integer.valueOf(1));
        assertions.put("failCount", Integer.valueOf(0));
        assertions.put("selectedBaseType", selectedBaseType);
        assertions.put("aiReactedAfterHit", Boolean.TRUE);
        assertions.put("deathEffectCount", Integer.valueOf(1));
        assertions.put("duplicateRegistrationErrors", Integer.valueOf(0));
        return assertions;
    }

    private static Map<String, Object> viewerCycleSpecialFamilyAssertions(@NotNull String selectedBaseType) {
        Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        assertions.put("pass", Boolean.TRUE);
        assertions.put("passCount", Integer.valueOf(1));
        assertions.put("failCount", Integer.valueOf(0));
        assertions.put("selectedBaseType", selectedBaseType);
        assertions.put("viewerAddCount", Integer.valueOf(1));
        assertions.put("viewerRemoveCount", Integer.valueOf(1));
        assertions.put("spawnCount", Integer.valueOf(1));
        assertions.put("destroyCount", Integer.valueOf(1));
        return assertions;
    }

    private static void withServerProperties(@NotNull String serverId, @NotNull Runnable action) {
        String currentServer = System.getProperty("entity.matrix.server");
        String currentLegacyServer = System.getProperty("spigotboot.entityMatrix.server");
        System.setProperty("entity.matrix.server", serverId);
        System.setProperty("spigotboot.entityMatrix.server", serverId);
        try {
            action.run();
        } finally {
            restoreProperty("entity.matrix.server", currentServer);
            restoreProperty("spigotboot.entityMatrix.server", currentLegacyServer);
        }
    }

    private static void restoreProperty(@NotNull String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    private static final class RepresentativeSelectionSummary {
        private final EraSelection legacy;
        private final EraSelection modern;

        private RepresentativeSelectionSummary(@NotNull EraSelection legacy, @NotNull EraSelection modern) {
            this.legacy = legacy;
            this.modern = modern;
        }

        private static RepresentativeSelectionSummary evaluate() {
            return new RepresentativeSelectionSummary(
                    EraSelection.evaluate("legacy", LEGACY_CANDIDATES),
                    EraSelection.evaluate("modern", MODERN_CANDIDATES)
            );
        }

        private EraSelection legacy() {
            return legacy;
        }

        private EraSelection modern() {
            return modern;
        }

        private void write(@NotNull Path summaryPath) throws IOException {
            Files.createDirectories(summaryPath.getParent());
            Files.write(summaryPath, toJson(toMap()).getBytes(StandardCharsets.UTF_8));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> summary = new LinkedHashMap<String, Object>();
            summary.put("legacy", legacy.toMap());
            summary.put("modern", modern.toMap());
            return summary;
        }
    }

    private static final class EraSelection {
        private final String era;
        private final String selectedVersion;
        private final String selectedServer;
        private final String selectedHostileBaseType;
        private final String selectedPassiveBaseType;
        private final String selectedSpecialBaseType;
        private final List<CandidateEvaluation> candidates;

        private EraSelection(
                @NotNull String era,
                @NotNull String selectedVersion,
                @NotNull String selectedServer,
                @NotNull String selectedHostileBaseType,
                @NotNull String selectedPassiveBaseType,
                @NotNull String selectedSpecialBaseType,
                @NotNull List<CandidateEvaluation> candidates
        ) {
            this.era = era;
            this.selectedVersion = selectedVersion;
            this.selectedServer = selectedServer;
            this.selectedHostileBaseType = selectedHostileBaseType;
            this.selectedPassiveBaseType = selectedPassiveBaseType;
            this.selectedSpecialBaseType = selectedSpecialBaseType;
            this.candidates = Collections.unmodifiableList(new ArrayList<CandidateEvaluation>(candidates));
        }

        private static EraSelection evaluate(@NotNull String era, @NotNull List<String> versions) {
            List<CandidateEvaluation> candidates = new ArrayList<CandidateEvaluation>();
            CandidateEvaluation selectedCandidate = null;
            for (String version : versions) {
                RepresentativeFamilyCoverage familyCoverage = RepresentativeFamilyCoverage.require(version);
                String passiveRepresentative = familyCoverage.firstSupportedPassiveRepresentative();
                String specialRepresentative = familyCoverage.firstSupportedSpecialRepresentative();

                List<String> missingFamilies = new ArrayList<String>();
                if (!familyCoverage.supportsHostileRepresentative()) {
                    missingFamilies.add("hostile-family");
                }
                if (passiveRepresentative == null) {
                    missingFamilies.add("passive-family");
                }
                if (specialRepresentative == null) {
                    missingFamilies.add("special-family");
                }

                CandidateEvaluation candidate;
                if (!missingFamilies.isEmpty()) {
                    candidate = CandidateEvaluation.rejected(
                            version,
                            representativeServerId(version),
                            missingFamilies,
                            familyCoverage.hostileRepresentative(),
                            passiveRepresentative,
                            specialRepresentative
                    );
                } else if (selectedCandidate == null) {
                    candidate = CandidateEvaluation.selected(
                            version,
                            representativeServerId(version),
                            familyCoverage.hostileRepresentative(),
                            passiveRepresentative,
                            specialRepresentative
                    );
                    selectedCandidate = candidate;
                } else {
                    candidate = CandidateEvaluation.notEvaluated(
                            version,
                            representativeServerId(version),
                            "earlier candidate '" + selectedCandidate.version() + "' already satisfied deterministic first-match rule",
                            familyCoverage.hostileRepresentative(),
                            passiveRepresentative,
                            specialRepresentative
                    );
                }
                candidates.add(candidate);
            }

            assertNotNull(selectedCandidate, "No representative candidate satisfied the deterministic first-match rule for era '" + era + "'.");
            return new EraSelection(
                    era,
                    selectedCandidate.version(),
                    selectedCandidate.server(),
                    selectedCandidate.selectedHostileBaseType(),
                    selectedCandidate.selectedPassiveBaseType(),
                    selectedCandidate.selectedSpecialBaseType(),
                    candidates
            );
        }

        private String selectedVersion() {
            return selectedVersion;
        }

        private String era() {
            return era;
        }

        private String selectedServer() {
            return selectedServer;
        }

        private String selectedHostileBaseType() {
            return selectedHostileBaseType;
        }

        private String selectedPassiveBaseType() {
            return selectedPassiveBaseType;
        }

        private String selectedSpecialBaseType() {
            return selectedSpecialBaseType;
        }

        private CandidateEvaluation requireCandidate(@NotNull String version) {
            for (CandidateEvaluation candidate : candidates) {
                if (candidate.version().equals(version)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Missing candidate evaluation for version '" + version + "'.");
        }

        private Map<String, Object> toMap() {
            Map<String, Object> eraSelection = new LinkedHashMap<String, Object>();
            eraSelection.put("era", era);
            eraSelection.put("selectedVersion", selectedVersion);
            eraSelection.put("selectedServer", selectedServer);
            eraSelection.put("selectedHostileBaseType", selectedHostileBaseType);
            eraSelection.put("selectedPassiveBaseType", selectedPassiveBaseType);
            eraSelection.put("selectedSpecialBaseType", selectedSpecialBaseType);

            List<Map<String, Object>> candidateMaps = new ArrayList<Map<String, Object>>();
            for (CandidateEvaluation candidate : candidates) {
                candidateMaps.add(candidate.toMap());
            }
            eraSelection.put("candidates", candidateMaps);
            return eraSelection;
        }

        private List<Map<String, Object>> candidateMaps() {
            List<Map<String, Object>> candidateMaps = new ArrayList<Map<String, Object>>();
            for (CandidateEvaluation candidate : candidates) {
                candidateMaps.add(candidate.toMap());
            }
            return candidateMaps;
        }
    }

    private static final class CandidateEvaluation {
        private final String version;
        private final String server;
        private final String status;
        private final String reason;
        private final String selectedHostileBaseType;
        private final String selectedPassiveBaseType;
        private final String selectedSpecialBaseType;
        private final List<String> missingFamilies;

        private CandidateEvaluation(
                @NotNull String version,
                @NotNull String server,
                @NotNull String status,
                @NotNull String reason,
                String selectedHostileBaseType,
                String selectedPassiveBaseType,
                String selectedSpecialBaseType,
                @NotNull List<String> missingFamilies
        ) {
            this.version = version;
            this.server = server;
            this.status = status;
            this.reason = reason;
            this.selectedHostileBaseType = selectedHostileBaseType;
            this.selectedPassiveBaseType = selectedPassiveBaseType;
            this.selectedSpecialBaseType = selectedSpecialBaseType;
            this.missingFamilies = Collections.unmodifiableList(new ArrayList<String>(missingFamilies));
        }

        private static CandidateEvaluation selected(
                @NotNull String version,
                @NotNull String server,
                String selectedHostileBaseType,
                String selectedPassiveBaseType,
                String selectedSpecialBaseType
        ) {
            return new CandidateEvaluation(
                    version,
                    server,
                    "selected",
                    "selected as the first candidate whose final allowlist includes hostile, passive, and special-case representatives",
                    selectedHostileBaseType,
                    selectedPassiveBaseType,
                    selectedSpecialBaseType,
                    Collections.<String>emptyList()
            );
        }

        private static CandidateEvaluation rejected(
                @NotNull String version,
                @NotNull String server,
                @NotNull List<String> missingFamilies,
                String selectedHostileBaseType,
                String selectedPassiveBaseType,
                String selectedSpecialBaseType
        ) {
            return new CandidateEvaluation(
                    version,
                    server,
                    "rejected",
                    joinMissingFamilies(missingFamilies),
                    selectedHostileBaseType,
                    selectedPassiveBaseType,
                    selectedSpecialBaseType,
                    missingFamilies
            );
        }

        private static CandidateEvaluation notEvaluated(
                @NotNull String version,
                @NotNull String server,
                @NotNull String reason,
                String selectedHostileBaseType,
                String selectedPassiveBaseType,
                String selectedSpecialBaseType
        ) {
            return new CandidateEvaluation(
                    version,
                    server,
                    "not-evaluated",
                    reason,
                    selectedHostileBaseType,
                    selectedPassiveBaseType,
                    selectedSpecialBaseType,
                    Collections.<String>emptyList()
            );
        }

        private String version() {
            return version;
        }

        private String server() {
            return server;
        }

        private String status() {
            return status;
        }

        private String reason() {
            return reason;
        }

        private String selectedHostileBaseType() {
            return selectedHostileBaseType;
        }

        private String selectedPassiveBaseType() {
            return selectedPassiveBaseType;
        }

        private String selectedSpecialBaseType() {
            return selectedSpecialBaseType;
        }

        private List<String> missingFamilies() {
            return missingFamilies;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> candidate = new LinkedHashMap<String, Object>();
            candidate.put("version", version);
            candidate.put("server", server);
            candidate.put("status", status);
            candidate.put("reason", reason);
            candidate.put("selectedHostileBaseType", selectedHostileBaseType);
            candidate.put("selectedPassiveBaseType", selectedPassiveBaseType);
            candidate.put("selectedSpecialBaseType", selectedSpecialBaseType);
            candidate.put("missingFamilies", new ArrayList<String>(missingFamilies));
            return candidate;
        }
    }

    private static String representativeServerId(@NotNull String version) {
        if ("1.19.2".equals(version) || "1.21.11".equals(version)) {
            return "paper-" + version;
        }
        return "spigot-" + version;
    }

    private static String joinMissingFamilies(@NotNull List<String> missingFamilies) {
        List<String> fragments = new ArrayList<String>();
        for (String family : missingFamilies) {
            fragments.add("missing " + family + " representative");
        }
        return String.join(", ", fragments);
    }

    private static final class RepresentativeFamilyCoverage {
        private static final Map<String, RepresentativeFamilyCoverage> COVERAGE = createCoverage();

        private final String version;
        private final String hostileRepresentative;
        private final List<CustomEntityBaseType> supportedPassiveTypes;
        private final List<CustomEntityBaseType> supportedSpecialTypes;

        private RepresentativeFamilyCoverage(
                @NotNull String version,
                String hostileRepresentative,
                @NotNull List<CustomEntityBaseType> supportedPassiveTypes,
                @NotNull List<CustomEntityBaseType> supportedSpecialTypes
        ) {
            this.version = version;
            this.hostileRepresentative = hostileRepresentative;
            this.supportedPassiveTypes = Collections.unmodifiableList(new ArrayList<CustomEntityBaseType>(supportedPassiveTypes));
            this.supportedSpecialTypes = Collections.unmodifiableList(new ArrayList<CustomEntityBaseType>(supportedSpecialTypes));
        }

        private static RepresentativeFamilyCoverage require(@NotNull String version) {
            RepresentativeFamilyCoverage coverage = COVERAGE.get(version);
            if (coverage == null) {
                throw new IllegalArgumentException("Missing representative family coverage for version '" + version + "'.");
            }
            return coverage;
        }

        private boolean supportsHostileRepresentative() {
            return hostileRepresentative != null;
        }

        private String hostileRepresentative() {
            return hostileRepresentative;
        }

        private String firstSupportedPassiveRepresentative() {
            return firstSupportedRepresentative(PASSIVE_FAMILY_PRIORITY, supportedPassiveTypes);
        }

        private String firstSupportedSpecialRepresentative() {
            return firstSupportedRepresentative(SPECIAL_FAMILY_PRIORITY, supportedSpecialTypes);
        }

        private static Map<String, RepresentativeFamilyCoverage> createCoverage() {
            Map<String, RepresentativeFamilyCoverage> coverage = new LinkedHashMap<String, RepresentativeFamilyCoverage>();
            coverage.put("1.8.8", new RepresentativeFamilyCoverage(
                    "1.8.8",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Arrays.asList(
                            CustomEntityBaseType.SHEEP,
                            CustomEntityBaseType.PIG,
                            CustomEntityBaseType.CHICKEN,
                            CustomEntityBaseType.COW
                    ),
                    Arrays.asList(
                            CustomEntityBaseType.ARMOR_STAND,
                            CustomEntityBaseType.ITEM_FRAME,
                            CustomEntityBaseType.MINECART,
                            CustomEntityBaseType.FALLING_BLOCK
                    )
            ));
            coverage.put("1.13.2", new RepresentativeFamilyCoverage(
                    "1.13.2",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Collections.<CustomEntityBaseType>emptyList(),
                    Collections.<CustomEntityBaseType>emptyList()
            ));
            coverage.put("1.16.5", new RepresentativeFamilyCoverage(
                    "1.16.5",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Collections.<CustomEntityBaseType>emptyList(),
                    Collections.<CustomEntityBaseType>emptyList()
            ));
            coverage.put("1.17.1", new RepresentativeFamilyCoverage(
                    "1.17.1",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Collections.<CustomEntityBaseType>emptyList(),
                    Collections.<CustomEntityBaseType>emptyList()
            ));
            coverage.put("1.19.2", new RepresentativeFamilyCoverage(
                    "1.19.2",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Arrays.asList(
                            CustomEntityBaseType.SHEEP,
                            CustomEntityBaseType.PIG,
                            CustomEntityBaseType.CHICKEN
                    ),
                    Arrays.asList(
                            CustomEntityBaseType.ARMOR_STAND,
                            CustomEntityBaseType.ITEM_FRAME,
                            CustomEntityBaseType.MINECART,
                            CustomEntityBaseType.FALLING_BLOCK
                    )
            ));
            coverage.put("1.21.11", new RepresentativeFamilyCoverage(
                    "1.21.11",
                    CustomEntityBaseType.ZOMBIE.name(),
                    Arrays.asList(
                            CustomEntityBaseType.SHEEP,
                            CustomEntityBaseType.PIG,
                            CustomEntityBaseType.CHICKEN,
                            CustomEntityBaseType.COW
                    ),
                    Arrays.asList(
                            CustomEntityBaseType.ARMOR_STAND,
                            CustomEntityBaseType.ITEM_FRAME,
                            CustomEntityBaseType.MINECART,
                            CustomEntityBaseType.FALLING_BLOCK
                    )
            ));
            return Collections.unmodifiableMap(coverage);
        }

        private static String firstSupportedRepresentative(
                @NotNull List<CustomEntityBaseType> priorityList,
                @NotNull List<CustomEntityBaseType> supportedTypes
        ) {
            for (CustomEntityBaseType candidate : priorityList) {
                if (supportedTypes.contains(candidate)) {
                    return candidate.name();
                }
            }
            return null;
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(quote(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof Collection) {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            boolean first = true;
            for (Object element : (Collection<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(toJson(element));
            }
            builder.append(']');
            return builder.toString();
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(character);
                    break;
            }
        }
        builder.append('"');
        return builder.toString();
    }
}
