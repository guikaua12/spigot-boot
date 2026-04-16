package tech.guilhermekaua.spigotboot.testPlugin.services;

/**
 * Test-only bridge exposing the package-private {@code ScenarioRecorder} surface without widening
 * production API. Mirrors the pattern established by {@link EntityScenarioArtifactsBridge}.
 */
public final class EntityDemoServiceRecorderBridge {
    private EntityDemoServiceRecorderBridge() {
    }

    /**
     * Starts a new recorder for the supplied scenario id.
     *
     * @param scenarioId the registered scenario id (must exist in {@code EntityScenarioDescriptor})
     * @return a fresh recorder with the default {@code pass=FALSE} seed
     */
    public static EntityDemoService.ScenarioRecorder start(String scenarioId) {
        return EntityDemoService.ScenarioRecorder.start(scenarioId);
    }

    /**
     * Forwards to {@code ScenarioRecorder.set(String, Object)}.
     */
    public static void set(EntityDemoService.ScenarioRecorder recorder, String key, Object value) {
        recorder.set(key, value);
    }

    /**
     * Forwards to {@code ScenarioRecorder.putIfAbsent(String, Object)}.
     */
    public static void putIfAbsent(EntityDemoService.ScenarioRecorder recorder, String key, Object value) {
        recorder.putIfAbsent(key, value);
    }

    /**
     * Forwards to {@code ScenarioRecorder.add(String, Integer)}.
     */
    public static void add(EntityDemoService.ScenarioRecorder recorder, String key, Integer amount) {
        recorder.add(key, amount);
    }

    /**
     * Forwards to {@code ScenarioRecorder.complete()}.
     */
    public static void complete(EntityDemoService.ScenarioRecorder recorder) {
        recorder.complete();
    }

    /**
     * Invokes the same {@link EntityDemoService#completeAtDeadline} logic that the production
     * {@code scheduleCompletion} runnable executes at its deadline. Commenting out the sentinel
     * inside {@code completeAtDeadline} will therefore also break the contract tests, which is the
     * intended negative-control signal.
     */
    public static void runScheduledCompletion(EntityDemoService.ScenarioRecorder recorder) {
        EntityDemoService.completeAtDeadline(recorder);
    }
}
