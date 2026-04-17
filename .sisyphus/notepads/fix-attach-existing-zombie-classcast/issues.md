# Issues — fix-attach-existing-zombie-classcast

> Gotchas, surprises, and hard-won diagnostics. Each entry: timestamp + task-id.

## [2026-04-16 atlas/pre-wave-1] Pre-existing LSP noise to IGNORE

The LSP server reports these errors in UNRELATED files (Lombok annotation-processing artifacts — Java/Maven compiles fine):

- `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java:39-40` — blank final fields (Lombok `@RequiredArgsConstructor`)
- `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java:30` — constructor visibility
- `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/Main.java:50` — `getServerName`/`getMaxPlayers` (Lombok `@Getter`)
- `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:93` — blank final `plugin` field (Lombok)
- `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/AutoDiscoveryIntegrationTest.java:40,63` — `getBootPlugin` Lombok getter

**Action**: Subagents can safely IGNORE LSP errors in these exact files. Only NEW errors in files you touch (or in files transitively affected by your changes) count as regressions. Verification gate is `mvnw.cmd` compile + test, NOT LSP diagnostics in pre-existing files.

## [2026-04-16 atlas/post-wave-1] Pre-existing wip scooped into T2 commit (e204703)

The working tree had ~500 lines of uncommitted wip in `test-plugin/.../EntityDemoService.java` from prior sessions (HEAD was 872 lines; working tree at T2 start was 1422 lines). T2's commit `e204703` therefore contains:

**T2-authored (per plan)**:
- `ScenarioRecorder.putIfAbsent(String, Object)` method
- `EntityDemoService.completeAtDeadline(ScenarioRecorder)` static extraction
- `scheduleCompletion(...)` runnable body delegates to `completeAtDeadline`
- Deleted two `recorder.set("controllerTickObserved", Boolean.FALSE)` pre-populations
- `scheduleCompletion(recorder, 10L)` → `scheduleCompletion(recorder, 30L)` at `attachHeadlessExistingZombieScenario` (line 498) with inline rationale comment
- Visibility widening on `ScenarioRecorder` from `private` → package-private (bridge access)
- Two new test files (`EntityDemoServiceRecorderBridge`, `EntityDemoServiceRecorderContractTest`)

**Pre-existing wip scooped in**:
- New methods: `spawnHeadlessDeathFxPassiveFamilyScenario`, `spawnHeadlessViewerCycleSpecialFamilyScenario`, `scheduleHeadlessDeathFxDamage`
- `scheduleCompletion(..., 30L)` calls at lines 175, 294, 339, 714 (new methods, created at 30L directly, not widened from 10L)
- `scheduleCompletion(..., 35L)` (deathfx passive family)
- Class fields for controller-tick tracking on `WrappedZombieController` etc.

**Atlas disposition**: ACCEPTED. Atlas re-ran verification independently — T2 contract test 8/8 green, regression canary `VersionsSharedDependencyIntegrationTest` 2/2 green, existing `EntityScenarioRegistrationTest` still green. The pre-existing wip predates this plan; it was in the working tree before `/start-work` was invoked. T7's commit audit will document this explicitly. F1/F4 review agents must understand the commit is LARGER than the plan's 6-section scope and should NOT reject on that ground alone — focus on "does the plan-authored work exist and function correctly".

**Action for downstream subagents (T3, T4)**: check `git status -- versions/1.16.5/ versions/1.19.2/` BEFORE editing to see if those modules also have uncommitted wip. Stage ONLY the files you intend to change, use `git commit --only -- <exact paths>` to isolate.


