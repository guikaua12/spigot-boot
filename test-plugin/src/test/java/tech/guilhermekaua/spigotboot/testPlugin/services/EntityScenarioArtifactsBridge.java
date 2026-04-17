package tech.guilhermekaua.spigotboot.testPlugin.services;

import java.util.List;
import java.util.Map;

public final class EntityScenarioArtifactsBridge {
    private EntityScenarioArtifactsBridge() {
    }

    public static void write(String scenarioId, List<Map<String, Object>> trace, Map<String, Object> assertions) {
        EntityScenarioArtifacts.write(scenarioId, trace, assertions);
    }
}
