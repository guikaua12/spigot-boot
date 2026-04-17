# Fix ClassCastException in `attach-existing-zombie` scenario on spigot-1.16.5

## TL;DR

> **Quick Summary**: A reflective field lookup in `EntityFactoryV1_16_5` resolves the obfuscated fallback name `"ag"` on NMS `EntityLiving` (which is a `static final DataWatcherObject<Byte> DATA_LIVING_FLAGS`) instead of the real `Entity.passengers : List` on the superclass, then blind-casts to `List<Object>` → `ClassCastException`. Primary fix is a new type-filtered `requireFieldOfType` helper in `ReflectionSupport`; migrate the two offending call sites in 1.16.5 and a latent same-shape site in 1.19.2; also fix a pre-populated-FALSE trap in `ScenarioRecorder` that could keep the scenario failing post-fix.
>
> **Deliverables**:
> - New `findFieldOfType` + `requireFieldOfType` helpers in `ReflectionSupport` (additive; no existing behavior change)
> - New `ReflectionSupportFindFieldHierarchyTest` (first unit test for this class)
> - Fixed `EntityFactoryV1_16_5.rewireModernVehicleAndPassengerReferencesInternal` (lines 1140-1141) + hierarchical-stub regression in `EntityFactoryV1_16_5ReplacementBridgeTest`
> - Fixed latent `EntityFactoryV1_19_2.rewireVehicleAndPassengerReferencesModern` (lines 1606-1607) + regression in `EntityFactoryV1_19_2ReplacementBridgeTest`
> - Producer-side fix in `EntityDemoService.ScenarioRecorder` (remove pre-populated `Boolean.FALSE` at lines 473,504; `putIfAbsent` in `scheduleCompletion`) + `EntityDemoServiceRecorderContractTest`
> - `attach-existing-zombie` passes on spigot-1.16.5 matrix harness (`pass=true` in `assertions.json`); `metadata-dirty-zombie` and `viewer-cycle-zombie` continue to pass
>
> **Estimated Effort**: Medium (9 tasks including reviews)
> **Parallel Execution**: YES - 4 waves (2 + 2 + 2 + 4 parallel reviews)
> **Critical Path**: T1 (helpers) → T3 (1.16.5 migration) → T5 (full build) → T6 (harness) → F1-F4 → user okay

---

## Context

### Original Request
Investigate the exception in `target/entity-matrix/spigot-1.16.5/attach-existing-zombie` and fix the issue.

### Exception artifacts (evidence)
- `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/assertions.json` → `{"pass":false, …all keys null}` (synthesized by `EntityScenarioArtifacts.writeFailure`)
- `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/trace.json` → single event `autorun-failure` with `java.lang.ClassCastException`
- `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/server.log:168-187` — full stack trace

### Root Cause (evidence-verified)
1. `EntityFactoryV1_16_5.java:1140` calls `requireEntityHandleField(oldHandle, "passengers", "ag", "passengerList")`.
2. `requireEntityHandleField` (line 1214) passes `entityHandle.getClass()` → runtime leaf NMS class (e.g., `EntityZombie`).
3. `ReflectionSupport.findField` (`versions/runtime/.../nativebridge/ReflectionSupport.java:274-291`) walks `EntityZombie` → `EntityMonster` → `EntityCreature` → `EntityInsentient` → `EntityLiving` → `Entity`. For each class, it tries every candidate name before going up.
4. On `net.minecraft.server.v1_16_R3.EntityLiving`, the candidate `"ag"` matches `DATA_LIVING_FLAGS : static final DataWatcherObject<Byte>` (confirmed by BKCommonLib template `local-only/BKCommonLib/.../net/minecraft/entity/living.txt:48`).
5. Search never reaches `Entity.passengers` (un-obfuscated on v1_16_R3; BKCommonLib template `entity.txt:95-147`).
6. `ReflectionSupport.readField` returns the static `DataWatcherObject<Byte>`.
7. `EntityFactoryV1_16_5.java:1144` blind-casts to `List<Object>` → **ClassCastException**.

### Why tests didn't catch it
The existing `EntityFactoryV1_16_5ReplacementBridgeTest` uses a plain Java stub `ReplacementPublicationHandle` with a single field `List<Object> passengers` at line 306. It cannot reproduce the superclass-shadowing trap because it has no hierarchy where a subclass declares a same-named non-`List` field.

### Version dispatch (not the bug)
`PaperReplacementStrategy_1_21_plus` is invoked on 1.16.5 intentionally. `EntityStrategyBundleSelector.isPaperLikeBundle` (selection/EntityStrategyBundleSelector.java:323-331) keys on capability flags, not version. `SpigotVersionAdapterV1_16_5:68` explicitly implements `PaperReplacementStrategy_1_21_plus.Provider`. The class name is misleading but the behavior is correct. **Rename is explicitly OUT OF SCOPE**.

### Collateral findings
- Latent same-shape bug at `EntityFactoryV1_19_2.java:1606-1607` — `"au"` is shared between passenger and vehicle candidate lists. Under some obfuscation configurations, the vehicle field could win for passengers (or vice versa).
- `ScenarioRecorder.containsFailureSignal` walks every assertion entry except `pass` and treats `null`, negative `Number`, or `Boolean.FALSE` as failure. `EntityDemoService:473,504` pre-populate `controllerTickObserved=Boolean.FALSE` as an observation sentinel. If the `WrappedZombieController.onTick` doesn't fire within the 10-tick deadline (line 498), scenario reports `pass=false` even if the CCE is fixed.

### Metis Review — Applied Corrections
- **Dropped**: ReflectionSupport iteration-order change (per-class → per-name). Introduces new latent failure at 1.19.2 without compensating fix; cosmetic at best.
- **Reduced scope**: Do NOT modify `EntityFactoryV1_13_2` (deliberate 1.8→1.9 `passenger`/`passengers` cross-version fallback), `EntityFactoryV1_17_1` (already uses safe `entityHandleClass()` pattern), `EntityFactoryV1_21_11` (single Mojang name, no risk), `EntityFactoryV1_8_8` (no passenger reflection).
- **Reduced scope**: Only sites at `EntityFactoryV1_16_5:1140-1141` need the fix (the other 5 same-file sites feed `Map` reads, writes, or are type-gated — not the CCE source).
- **Corrected fix target**: `ScenarioRecorder` fix must be producer-side (remove pre-populations) NOT consumer-side (loosen `containsFailureSignal`) — the latter would hide real failures for `entityIdStable`, `trackerRebound`, `aiReactedAfterHit`, `goalMutationReplacedExistingEntry`, all pinned in `VersionsSharedDependencyIntegrationTest.java:235-248` and documented as contracts in `wiki/AttachingAndWrappingExplained.md:46-50`.
- **Corrected location**: `ScenarioRecorder` is a nested class inside `EntityDemoService.java` (starts at line 1302), not a standalone file.
- **Explicitly deferred**: scenarios.json drift (5 vs 7 in descriptor), missing `*ReplacementBridgeTest` for 1.17.1/1.21.11, Windows CI workflow, unified rewire helper, auditing `findNamedMethod`/`findCompatibleMethod` iteration order, and ReflectionSupport iteration order refactor.

---

## Work Objectives

### Core Objective
Eliminate the `ClassCastException: DataWatcherObject cannot be cast to List` in the `attach-existing-zombie` scenario on spigot-1.16.5 by introducing type-filtered field lookup in `ReflectionSupport`, migrating the offending call sites in `EntityFactoryV1_16_5` and `EntityFactoryV1_19_2`, and hardening `ScenarioRecorder` against a pre-populated-FALSE trap that could still flunk the scenario after the CCE is fixed.

### Concrete Deliverables
- New `findFieldOfType` + `requireFieldOfType` in `versions/runtime/.../ReflectionSupport.java`
- New `versions/runtime/src/test/java/.../nativebridge/ReflectionSupportFindFieldHierarchyTest.java` (first unit test for this class)
- Modified `versions/1.16.5/src/main/java/.../EntityFactoryV1_16_5.java` (2 lines: 1140, 1141)
- Extended `versions/1.16.5/src/test/java/.../EntityFactoryV1_16_5ReplacementBridgeTest.java` (new hierarchical-stub regression)
- Modified `versions/1.19.2/src/main/java/.../EntityFactoryV1_19_2.java` (2 lines: 1606, 1607)
- Extended `versions/1.19.2/src/test/java/.../EntityFactoryV1_19_2ReplacementBridgeTest.java` (new hierarchical-stub regression)
- Modified `test-plugin/src/main/java/.../EntityDemoService.java` (remove 2 pre-population lines + modify `scheduleCompletion` to `putIfAbsent` before `complete()`)
- New `test-plugin/src/test/java/.../EntityDemoServiceRecorderContractTest.java`
- Regenerated `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/assertions.json` with `pass=true` + expected keys, verified by re-running `scripts/run-entity-matrix.ps1`
- Regenerated `target/entity-matrix/spigot-1.16.5/metadata-dirty-zombie/assertions.json` and `.../viewer-cycle-zombie/assertions.json` both with `pass=true` (regression)

### Definition of Done
- [ ] `mvnw.cmd clean test` exits 0 on a clean checkout
- [ ] `mvnw.cmd -pl versions/runtime -am test -Dtest=ReflectionSupportFindFieldHierarchyTest` passes
- [ ] `mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest` passes including new hierarchical-stub tests
- [ ] `mvnw.cmd -pl versions/1.19.2 -am test -Dtest=EntityFactoryV1_19_2ReplacementBridgeTest` passes including new hierarchical-stub tests
- [ ] `mvnw.cmd -pl test-plugin -am test -Dtest=EntityDemoServiceRecorderContractTest` passes
- [ ] `pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie` exits 0 AND `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/assertions.json` contains `"pass":true`
- [ ] `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/trace.json` contains no `autorun-failure` event
- [ ] `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/server.log` contains no `ClassCastException`
- [ ] Reverting any single new test from Task 1/3/4 → that test must still pass (the test exercises the new helper/migration, not dead code)
- [ ] Reverting the core fix line in `EntityFactoryV1_16_5:1140` → the new hierarchical-stub regression test must FAIL with a `ClassCastException` (proof the test exercises the fix)
- [ ] Reverting `EntityDemoService:473,504` deletion → `EntityDemoServiceRecorderContractTest` must FAIL (proof the test exercises the recorder fix)

### Must Have
- Type-filtered field lookup is the PRIMARY fix mechanism (not iteration-order swap; not `instanceof` guard; not scope-to-base helper)
- Both `EntityFactoryV1_16_5` (known CCE) and `EntityFactoryV1_19_2` (latent same-class CCE) are migrated in this plan
- `ScenarioRecorder` pre-populated-FALSE trap is fixed producer-side, not consumer-side
- New unit tests reproduce the bug on master (FAIL before fix) and pass after
- No behavior change to `ReflectionSupport.findField`/`requireField` (additive helpers only)

### Must NOT Have (Guardrails)
- **MUST NOT** modify `ReflectionSupport.findField` or `requireField` iteration order / null-return contract
- **MUST NOT** modify `ReflectionSupport.findNamedMethod`, `findCompatibleMethod`, `findCompatibleMethodByName`, `findMethodBySignature`, `collectNamedMethods`, `requireCompatibleConstructor`, `readField`, `writeField`, `invoke`, `instantiate`, `allocateInstance`
- **MUST NOT** modify `EntityFactoryV1_13_2` — `instanceof List` + singular `passenger` fallback is deliberate cross-version handling
- **MUST NOT** modify `EntityFactoryV1_17_1` — already safe (`requireField(entityHandleClass(), …)`)
- **MUST NOT** modify `EntityFactoryV1_21_11` — single Mojang name, no obfuscated fallback, no risk
- **MUST NOT** modify `EntityFactoryV1_8_8` — does not touch passenger reflection
- **MUST NOT** modify `ScenarioRecorder.containsFailureSignal` — it correctly flunks real failures (`entityIdStable=false`, `trackerRebound=false`, `aiReactedAfterHit=false`, `goalMutationReplacedExistingEntry=false`). Touching it would hide real regressions.
- **MUST NOT** modify `scripts/run-entity-matrix.ps1`, `scripts/provision-entity-matrix.ps1`, `scripts/entity-matrix/common.ps1`, `scripts/entity-matrix/servers.json`, `scripts/entity-matrix/scenarios.json` — harness contract is stable
- **MUST NOT** modify `test-plugin/src/main/java/.../Main.java`, `EntityMatrixAutorunService.java`, `EntityMatrixRuntimeRequest.java`, `EntityScenarioArtifacts.java`, `EntityScenarioDescriptor.java` — autorun/artifacts contract is stable
- **MUST NOT** modify `wiki/wiki/AttachingAndWrappingExplained.md` — contract documentation; its `entityIdStable=true`/`trackerRebound=true`/`aiReactedAfterHit=true`/`controllerTickObserved=true` pins must continue to hold
- **MUST NOT** modify `assertions.json` or `trace.json` JSON schema (keys, order, value types)
- **MUST NOT** rename `PaperReplacementStrategy_1_21_plus` (cosmetic ~14-file change; separate concern)
- **MUST NOT** add abstraction layers (factory base classes, helper class hierarchies)
- **MUST NOT** frame the iteration-order swap as "the fix" — iteration order is NOT changed in this plan
- **MUST NOT** introduce `assert` statements, `@NotNull` duplication, or commentary in the style "this ensures correctness" (avoid AI-slop hallmarks)
- **MUST NOT** add emojis to any committed file
- **MUST NOT** fix `scenarios.json` vs `EntityScenarioDescriptor` drift (known orthogonal issue)
- **MUST NOT** add new `*ReplacementBridgeTest` files for 1.17.1 or 1.21.11 (deferred; requires NMS-class-loading workarounds)
- **MUST NOT** add a Windows CI workflow (optional follow-up; not blocking)

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.
> Acceptance criteria requiring "user manually tests/confirms" are FORBIDDEN.

### Test Decision
- **Infrastructure exists**: YES (JUnit 5 + Mockito + MockBukkit-v1.20 per `test-plugin/pom.xml:167-171` and per-module `pom.xml`)
- **Automated tests**: TESTS-AFTER (user's choice). Each task ships implementation + regression test together. Unit tests are the CI gate; matrix harness is Windows-developer smoke.
- **Framework**: JUnit Jupiter 5 + Mockito + MockBukkit. No new framework introduced.
- **TDD pattern used**: For each migration task, the plan requires the new regression test to FAIL against current master if the fix line is reverted — a retroactive RED-GREEN proof that the test exercises the actual fix.

### QA Policy
Every task MUST include agent-executed QA scenarios with exact commands. Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Java unit tests**: Use `Bash` with `mvnw.cmd -pl <module> -am test -Dtest=<ClassName>` — exit code + Surefire XML report under `<module>/target/surefire-reports/`
- **Full build gate**: Use `Bash` with `mvnw.cmd clean test` — exit code + aggregated Surefire reports
- **Entity-matrix harness (Windows developer)**: Use `Bash` with `pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie` — exit code + `target/entity-matrix/spigot-1.16.5/attach-existing-zombie/{assertions,trace}.json` + `server.log`
- **JSON assertion**: Parse `assertions.json` with PowerShell `Get-Content | ConvertFrom-Json` and assert `.pass -eq $true` and all required keys present
- **Log absence assertion**: Grep `server.log` for `ClassCastException` — must be empty match
- **Negative-control proof**: For each migration, revert one line of the fix in a scratch branch and re-run the regression test; it MUST fail. Capture both outputs as evidence.

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — foundation, MAX PARALLEL):
├── Task 1: ReflectionSupport type-filtered helpers + unit test [deep]
└── Task 2: ScenarioRecorder producer-side fix + contract test [unspecified-high]

Wave 2 (After Wave 1 — version-factory migrations, parallel):
├── Task 3: EntityFactoryV1_16_5 migration + hierarchical-stub regression (depends: 1) [deep]
└── Task 4: EntityFactoryV1_19_2 migration + hierarchical-stub regression (depends: 1) [unspecified-high]

Wave 3 (After Wave 2 — integration gates, parallel where possible):
├── Task 5: Full mvnw.cmd clean test gate (depends: 1-4) [deep]
└── Task 6: Entity-matrix harness verification on spigot-1.16.5 (depends: 1-4) [unspecified-high]

Wave 4 (After Wave 3 — commit packaging, sequential):
└── Task 7: Squash-or-stack commits with Conventional Commit messages (depends: 5, 6) [git]

Wave FINAL (After ALL tasks — 4 parallel reviews, then user okay):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA execution (unspecified-high)
└── Task F4: Scope fidelity check (deep)
→ Present results → Get explicit user okay

Critical Path: Task 1 → Task 3 → Task 5 → Task 6 → Task 7 → F1-F4 → user okay
Parallel Speedup: ~40% faster than sequential (Wave 1 has 2 parallel, Wave 2 has 2 parallel, Wave 3 has 2 parallel)
Max Concurrent: 2 (Waves 1-3); 4 (Wave FINAL)
```

### Dependency Matrix

- **T1** (ReflectionSupport helpers): - | Blocks: T3, T4, T5, T6, T7 | Blocked by: None (can start immediately)
- **T2** (ScenarioRecorder producer fix): - | Blocks: T5, T6, T7 | Blocked by: None (can start immediately)
- **T3** (EntityFactoryV1_16_5 migration): T1 | Blocks: T5, T6, T7 | Blocked by: T1
- **T4** (EntityFactoryV1_19_2 migration): T1 | Blocks: T5, T7 | Blocked by: T1
- **T5** (mvnw.cmd clean test gate): T1, T2, T3, T4 | Blocks: T7 | Blocked by: T1, T2, T3, T4
- **T6** (matrix harness smoke): T1, T2, T3 | Blocks: T7 | Blocked by: T1, T2, T3
- **T7** (commit packaging): T5, T6 | Blocks: F1-F4 | Blocked by: T5, T6

### Agent Dispatch Summary

- **Wave 1**: **2 parallel** — T1 → `deep` (algorithm-level helper with test reproducing real NMS semantics via plain-Java hierarchy), T2 → `unspecified-high` (producer-side refactor + contract test with ~6 pinned assertions)
- **Wave 2**: **2 parallel** — T3 → `deep` (NMS reflection + hierarchical stub demonstrating real CCE mechanism), T4 → `unspecified-high` (same shape as T3, lighter because latent-only)
- **Wave 3**: **2 parallel** — T5 → `deep` (cross-module build verification + regression triage), T6 → `unspecified-high` (Windows harness execution + JSON/log assertion)
- **Wave 4**: **1 sequential** — T7 → `git` (Conventional Commit stack; fast)
- **Wave FINAL**: **4 parallel** — F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [x] 1. Add `findFieldOfType` + `requireFieldOfType` helpers to `ReflectionSupport` with a first-of-its-kind hierarchy unit test

  **What to do**:
  - Edit `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java`. Add two public static methods immediately after `requireField` (after current line 301). Do NOT modify `findField`, `requireField`, or any other existing method:
    ```java
    /**
     * Resolves a declared field by walking the supertype chain, filtering candidates by an expected type.
     *
     * Iteration mirrors {@link #findField(Class, String...)} — for each class from leaf to base, each
     * candidate name is attempted in order. A candidate only matches when the resolved field's declared
     * type is assignable to {@code expectedType}. This protects hard-cast call sites from shadowing where
     * a subclass declares a same-named field of an unrelated type (e.g. a static DataWatcher key named
     * "ag" on EntityLiving while the real "passengers" List lives on Entity).
     *
     * @param type           the class to begin searching from (typically the runtime class of a target object)
     * @param expectedType   the required field type; matches use {@link Class#isAssignableFrom(Class)}
     * @param candidateNames field names to try, highest preference first
     * @return the first accessible, type-compatible field, or {@code null} if none matched
     */
    public static @Nullable Field findFieldOfType(
            @NotNull Class<?> type,
            @NotNull Class<?> expectedType,
            @NotNull String... candidateNames) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(expectedType, "expectedType cannot be null");
        Objects.requireNonNull(candidateNames, "candidateNames cannot be null");

        Class<?> current = type;
        while (current != null) {
            for (String candidateName : candidateNames) {
                try {
                    Field field = current.getDeclaredField(candidateName);
                    if (expectedType.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Type-filtered variant of {@link #requireField(Class, String...)}.
     *
     * @param type           the class to begin searching from
     * @param expectedType   the required field type
     * @param candidateNames field names to try
     * @return the resolved field; never null
     * @throws IllegalStateException if no accessible, type-compatible field is found
     */
    public static @NotNull Field requireFieldOfType(
            @NotNull Class<?> type,
            @NotNull Class<?> expectedType,
            @NotNull String... candidateNames) {
        Field field = findFieldOfType(type, expectedType, candidateNames);
        if (field == null) {
            throw new IllegalStateException(
                    "Unable to resolve field of type " + expectedType.getName()
                            + " on " + type.getName()
                            + " using candidates " + Arrays.toString(candidateNames));
        }
        return field;
    }
    ```
  - Ensure the `Arrays` import is present; add `import java.util.Arrays;` if missing (check existing imports first; do not add duplicates).
  - Create `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupportFindFieldHierarchyTest.java` with at MINIMUM these `@Test` methods (use JUnit Jupiter 5 per repo convention; no Mockito needed for pure Java reflection tests):
    - `findFieldOfType_returnsParentFieldWhenChildShadowsWithWrongType`: builds a `Parent` class with `public List<Object> passengers = new ArrayList<>()` and a `Child extends Parent` with `public static final Object passengers = "not a list"` (via a String). Asserts `findFieldOfType(Child.class, List.class, "passengers").getDeclaringClass()` is `Parent.class` and its `getType()` is `List.class`.
    - `findFieldOfType_returnsChildFieldWhenChildShadowsWithCorrectType`: builds `Parent { List<Object> passengers }` + `Child extends Parent { List<Integer> passengers }`. Asserts the returned field's declaring class is `Child.class` (leaf wins when type also matches — preserves `findField` legacy ordering semantics).
    - `findFieldOfType_reproducesOriginalBugWithDataWatcherObjectShadowing`: builds a minimal `StubDataWatcherObject` class with no generic params, a `StubEntity { public List<Object> passengers = new ArrayList<>(); }`, a `StubEntityLiving extends StubEntity` with `public static final StubDataWatcherObject ag = new StubDataWatcherObject()` AND `public static final StubDataWatcherObject passengers = new StubDataWatcherObject()` (simulating obfuscated shadowing). Calls `findFieldOfType(StubEntityLiving.class, List.class, "passengers", "ag", "passengerList")` and asserts the returned field's declaring class is `StubEntity.class` (the `List` field), NOT `StubEntityLiving.class`. Then reads the value via `field.get(new StubEntityLiving())` and asserts it is an `ArrayList`. This test MUST fail on master if `findFieldOfType` is replaced by `findField` (proving it exercises the fix mechanism, not just any reflection call).
    - `findFieldOfType_returnsNullWhenNoNameMatches`: asserts `null` for a non-existent name.
    - `findFieldOfType_returnsNullWhenNameMatchesButTypeIncompatible`: builds `Parent { public static final Object passengers = "string"; }`. Asserts `findFieldOfType(Parent.class, List.class, "passengers")` is `null`.
    - `requireFieldOfType_throwsWhenNoMatch`: asserts `IllegalStateException` with a message containing the expected type's name and the candidate list (use `assertThrows` + `getMessage().contains(...)`).
    - `findFieldOfType_setsAccessibleOnReturnedField`: builds `class Priv { private List<Object> passengers = new ArrayList<>(); }`. Asserts the returned field's `isAccessible()` is true (or just read the value without `setAccessible` in the test — success implies accessibility was set).
    - `findFieldOfType_walksFullSuperclassChain`: builds `A { List passengers }` ← `B extends A {}` ← `C extends B {}`. Asserts lookup from `C.class` finds `A.passengers`.
    - `findFieldOfType_secondCandidateNameWinsWhenFirstMissing`: builds `X { List au }` (only). Calls `findFieldOfType(X.class, List.class, "passengers", "au")` and asserts declaring class is `X`, field name `"au"`.

  **Must NOT do**:
  - Do NOT modify `findField`, `requireField`, or change their iteration order
  - Do NOT modify any other method in `ReflectionSupport.java`
  - Do NOT add a "version" or "strategy" parameter
  - Do NOT add an interface-walk (`getInterfaces()`) — out of scope; preserve current supertype semantics
  - Do NOT add supertype-expected-type fallback (strict `isAssignableFrom`)
  - Do NOT add Javadoc to `findField`/`requireField` in this commit (focus)
  - Do NOT add emojis or tutorial-style comments ("This method does X, which ensures Y")
  - Do NOT use `Assertions.assertAll(...)` — keep tests atomic for clearer Surefire reports

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Requires careful understanding of Java reflection semantics, superclass-walk iteration, and designing a regression test whose failure mode on master is the exact mechanism of the production CCE. Single module but high rigor on edge cases.
  - **Skills**: `[]`
    - No project-specific skills are configured that overlap this domain.
  - **Skills Evaluated but Omitted**:
    - None applicable (skills list is empty in this environment).

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Task 3, Task 4, Task 5, Task 6, Task 7
  - **Blocked By**: None — can start immediately

  **References**:

  **Pattern References** (existing code to follow):
  - `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java:274-301` — existing `findField`/`requireField` implementation. New helpers mirror the exact supertype-walk shape (for-class outer / for-name inner) to preserve the leaf-wins legacy ordering semantics. Copy the structure verbatim; only the `isAssignableFrom` filter is new.
  - `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/tracker/ModernTrackerHookSupportTest.java` (search for `FakeTrackerStateHandle` inner static class) — canonical in-repo pattern for stub-hierarchy unit tests without MockBukkit or NMS jars. Extract: (a) inner `static class` declarations, (b) `public` visibility trick for `setAccessible`-free assertions, (c) `getClass()` + `Modifier` use in assertions.
  - `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/EntityFactoryV1_8_8Test.java:186-217` — pattern for invoking `private static` methods via reflection in tests (not needed here since new helpers are `public`, but establishes the repo idiom for reflection-as-test-harness).

  **API/Type References** (contracts to implement against):
  - `org.jetbrains.annotations.NotNull` and `Nullable` — repo uses JetBrains annotations on all reflection-helper signatures; match the existing style at `ReflectionSupport.java:274,293`
  - `java.util.Arrays.toString(Object[])` — for the exception message in `requireFieldOfType`; matches the existing style at `ReflectionSupport.java:297-299`

  **Test References** (testing patterns to follow):
  - `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/tracker/ModernTrackerHookSupportTest.java` — existing JUnit Jupiter 5 layout in this module: `@Test` per method, `Assertions.assertEquals`/`assertSame`/`assertThrows`, single-expression per assertion
  - `AGENTS.md` Testing Guidelines: "JUnit 5, Mockito, MockBukkit. Name test classes `*Test`. Add focused regression coverage for every behavior change."

  **External References** (libraries and frameworks):
  - JUnit Jupiter 5 docs: https://junit.org/junit5/docs/current/user-guide/#writing-tests-assertions — `assertThrows`, `assertSame`, `assertEquals` patterns
  - Java SE 8 Reflection: `Class.getDeclaredField(String)` throws `NoSuchFieldException` (checked). Existing `findField` swallows this; new helpers must do the same.

  **WHY Each Reference Matters**:
  - `ReflectionSupport.java:274-301` is the template: the new helpers MUST mirror its class-walk shape so that the legacy `findField` behavior is preserved byte-for-byte in the non-filtered case, reducing risk of iteration-order surprises across versions that depend on it.
  - `ModernTrackerHookSupportTest.FakeTrackerStateHandle` proves stub-hierarchy tests already work in `versions/runtime` without MockBukkit — copying this pattern avoids adding new test-scope dependencies.
  - `AGENTS.md` Testing Guidelines locks the test class naming (`*Test`) and framework (JUnit 5 + Mockito, though Mockito is not needed here). No new framework.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: findFieldOfType picks the correct typed field despite wrong-type shadowing (reproduces the production bug)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Repo at working tree with Task 1 implementation applied
      - No other concurrent maven build
    Steps:
      1. Run: mvnw.cmd -pl versions/runtime -am test -Dtest=ReflectionSupportFindFieldHierarchyTest#findFieldOfType_reproducesOriginalBugWithDataWatcherObjectShadowing
      2. Assert: exit code is 0
      3. Assert: surefire XML at versions/runtime/target/surefire-reports/TEST-tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupportFindFieldHierarchyTest.xml contains <testcase name="findFieldOfType_reproducesOriginalBugWithDataWatcherObjectShadowing"/> with NO <failure> or <error> child
      4. Assert: console output contains "Tests run: 1, Failures: 0, Errors: 0"
    Expected Result: Exit 0; Surefire report shows the test passed
    Failure Indicators: Non-zero exit; <failure> element in Surefire XML; ClassCastException in stderr
    Evidence: .sisyphus/evidence/task-1-bug-repro-test.txt (stdout capture via `mvnw.cmd … > task-1-bug-repro-test.txt 2>&1`)

  Scenario: requireFieldOfType throws informatively when no type-compatible field exists (failure path)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 1 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/runtime -am test -Dtest=ReflectionSupportFindFieldHierarchyTest#requireFieldOfType_throwsWhenNoMatch
      2. Assert: exit code is 0
      3. Assert: Surefire XML shows <testcase name="requireFieldOfType_throwsWhenNoMatch"/> passing
    Expected Result: Test passes — IllegalStateException was thrown with a message matching the expected pattern (contains the type's FQN and the candidate-names list)
    Failure Indicators: AssertionError reported in stderr; IllegalStateException NOT thrown, or thrown with unexpected message
    Evidence: .sisyphus/evidence/task-1-require-throws-test.txt

  Scenario: Negative-control — reverting findFieldOfType's type filter must cause the bug-repro test to fail
    Tool: Bash (mvnw.cmd + git)
    Preconditions:
      - Task 1 implementation applied and committed
    Steps:
      1. git stash — save clean working tree
      2. Use Edit tool to replace "if (expectedType.isAssignableFrom(field.getType())) {" with "if (true) { // BROKEN" in ReflectionSupport.java findFieldOfType
      3. Run: mvnw.cmd -pl versions/runtime -am test -Dtest=ReflectionSupportFindFieldHierarchyTest#findFieldOfType_reproducesOriginalBugWithDataWatcherObjectShadowing
      4. Assert: exit code is NON-ZERO (test failure expected)
      5. Assert: console output contains "ClassCastException" OR "expected:" (JUnit failure message)
      6. git checkout -- versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java — restore the fix
      7. git stash pop — restore working tree
      8. Re-run step 3 to confirm test passes again
    Expected Result: Test fails when fix is reverted (proves the test genuinely exercises the new type filter); passes again when fix is restored
    Failure Indicators: Test STILL passes after reverting the type filter — means the test is not actually exercising the fix (test is useless and must be strengthened)
    Evidence: .sisyphus/evidence/task-1-negative-control-pre-revert.txt, .sisyphus/evidence/task-1-negative-control-during-revert.txt, .sisyphus/evidence/task-1-negative-control-post-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-1-bug-repro-test.txt`
  - [ ] `.sisyphus/evidence/task-1-require-throws-test.txt`
  - [ ] `.sisyphus/evidence/task-1-negative-control-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-1-negative-control-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-1-negative-control-post-revert.txt`

  **Commit**: YES
  - Message: `feat(runtime): add type-filtered field lookup helpers to ReflectionSupport`
  - Files: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java`, `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupportFindFieldHierarchyTest.java`
  - Pre-commit: `mvnw.cmd -pl versions/runtime -am test`

- [x] 2. Fix `ScenarioRecorder` pre-populated-FALSE observation-marker trap in `EntityDemoService` (producer-side only)

  **What to do**:
  - Edit `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java`:
    1. DELETE line 473 entirely: `recorder.set("controllerTickObserved", Boolean.FALSE);` (inside `attachHeadlessExistingZombieScenario`)
    2. DELETE line 504 entirely: `recorder.set("controllerTickObserved", Boolean.FALSE);` (inside `attachExistingZombieScenario` if present at the same offset — verify exact line by grepping `recorder.set("controllerTickObserved", Boolean.FALSE)` and deleting ALL matches that are pre-populations, keeping only the legitimate TRUE flips inside `WrappedZombieController.onTick` at the existing call site)
    3. In `scheduleCompletion(ScenarioRecorder recorder, long delayTicks)` (around line 795-811), inside the scheduled runnable body, BEFORE `recorder.complete();`, add:
       ```java
       recorder.putIfAbsent("controllerTickObserved", Boolean.FALSE);
       ```
    4. Ensure `ScenarioRecorder` has a `putIfAbsent(String key, Object value)` method. If absent, add it:
       ```java
       void putIfAbsent(@NotNull String key, @NotNull Object value) {
           Objects.requireNonNull(key, "key cannot be null");
           Objects.requireNonNull(value, "value cannot be null");
           if (!assertions.containsKey(key)) {
               assertions.put(key, value);
           }
       }
       ```
       (Add alongside existing `set` method within the nested `ScenarioRecorder` class starting at line 1302.)
  - Optionally widen the deadline: find `scheduleCompletion(recorder, 10L)` call in `attachHeadlessExistingZombieScenario` (around line 498) and change `10L` to `30L` to match the viewer scenario at line 455. ONLY widen this ONE call site (attach-existing-zombie), not the others. Explicit rationale comment required: `// Wider window avoids flakes when a controller tick is delayed by server load (matches viewerCycle 30L)`.
  - Create `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoServiceRecorderContractTest.java`. Since `ScenarioRecorder` is package-private nested, use same package `tech.guilhermekaua.spigotboot.testPlugin.services`. Use the existing `EntityScenarioArtifactsBridge` pattern or add a new `EntityDemoServiceRecorderBridge` in `test-plugin/src/test/java/.../services/` that exposes the nested class via a factory method.
  - Required `@Test` methods:
    - `controllerTickObserved_defaultsToFalseAtCompletion_whenNeverSet_andStillMarksFail`: create recorder, call `putIfAbsent` on the completion runnable equivalent, call `complete()`. Read the emitted assertions.json file. Assert `pass=false` (because the default FALSE still marks as failure, preserving the original signal meaning — but now it's explicit rather than a pre-pop trap).
    - `controllerTickObserved_preservesTrue_whenSetBeforeCompletion`: create recorder, `set("controllerTickObserved", Boolean.TRUE)`, then `putIfAbsent` (no-op since already set), then `complete()`. Assert `pass=true` (assuming no other keys are falsy).
    - `entityIdStable_false_stillFlunksScenario`: create recorder, `set("entityIdStable", Boolean.FALSE)` alongside other keys set to TRUE / positive numbers. Assert `pass=false`. Proves `containsFailureSignal` behavior is INTACT.
    - `trackerRebound_false_stillFlunksScenario`: same shape. Proves trackerRebound guard is intact.
    - `aiReactedAfterHit_false_stillFlunksScenario`: same shape. Proves aiReactedAfterHit guard is intact.
    - `goalMutationReplacedExistingEntry_false_stillFlunksScenario`: same shape.
    - `negativeCount_stillFlunksScenario`: set `failCount = -1`. Assert `pass=false`.
    - `nullValue_stillFlunksScenario`: simulate a key that is present but null (write through reflection or the bridge). Assert `pass=false`.

  **Must NOT do**:
  - Do NOT modify `containsFailureSignal` (keep signature, keep logic, keep behavior)
  - Do NOT modify `EntityScenarioArtifacts`, `EntityScenarioDescriptor`, `EntityMatrixAutorunService`, `EntityMatrixRuntimeRequest`, `Main.java`
  - Do NOT add / remove / rename assertion keys in `EntityScenarioDescriptor`
  - Do NOT change the `10L` deadline on other scenarios (only `attach-existing-zombie` is widened to `30L`)
  - Do NOT export the `ScenarioRecorder` class to public API
  - Do NOT add a public `EntityDemoService.getLastRecorder()` accessor for testing — use the bridge pattern instead
  - Do NOT remove existing `recorder.set("controllerTickObserved", Boolean.TRUE)` calls that flip the flag on controller tick (those are the legitimate observations)
  - Do NOT widen the deadline across all scenarios (only the one with an observation-marker dependency)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Multi-file edit touching production service code + new contract test + careful preservation of existing semantics. Not algorithm-heavy but requires discipline to avoid loosening the failure detection.
  - **Skills**: `[]`
    - No project-specific skills applicable.
  - **Skills Evaluated but Omitted**:
    - None applicable.

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Task 5, Task 6, Task 7
  - **Blocked By**: None — can start immediately

  **References**:

  **Pattern References** (existing code to follow):
  - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:1302-1378` — existing `ScenarioRecorder` nested class. New `putIfAbsent` must match the style of existing `set` method (package-private, `@NotNull` params, terse).
  - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:455` — the `scheduleCompletion(recorder, 30L)` call in the viewer-cycle scenario; template for widening the attach-existing-zombie deadline from 10L to 30L.
  - `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioArtifactsBridge.java` (13 lines) — canonical pattern for exposing package-private test seams without widening production API. Template: small test-scope class in the same package that forwards to the package-private method.
  - `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java` — existing JUnit Jupiter 5 layout in this module.

  **API/Type References** (contracts to implement against):
  - `ScenarioRecorder.containsFailureSignal` at `EntityDemoService.java:1360` — MUST continue to flag `null`, negative `Number`, and `Boolean.FALSE` as failures for ALL non-`pass` keys. Contract-test must pin this.
  - `EntityScenarioArtifacts.write(String scenarioId, Map trace, Map assertions)` — existing entry point for writing JSON artifacts. Contract test re-uses this to emit artifacts and parse them back.
  - `wiki/wiki/AttachingAndWrappingExplained.md:46-50` — documented contract for `entityIdStable`, `trackerRebound`, `aiReactedAfterHit`, `controllerTickObserved` semantics. The fix must not silently change what these signals mean.
  - `versions/runtime/src/test/java/.../shared/VersionsSharedDependencyIntegrationTest.java:235-248` — pinned assertion keys (entityIdStable=true, trackerRebound=true, aiReactedAfterHit=true, controllerTickObserved=true) that will regress if `containsFailureSignal` is loosened. MUST continue to pass after this task.

  **Test References** (testing patterns to follow):
  - `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioArtifactsBridge.java` — bridge pattern for test-only API surface
  - `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java` — `@TempDir` use, file-content assertions via `Files.readString(path)` + `JsonParser`, Jupiter assertion style

  **External References** (libraries and frameworks):
  - MockBukkit is on the test classpath but is NOT needed here — the test exercises pure producer-side recorder logic without a real server
  - JSON parsing via `org.json.JSONObject` (repo convention; check `test-plugin/pom.xml` for the exact artifact)

  **WHY Each Reference Matters**:
  - `EntityScenarioArtifactsBridge` is the canonical test seam — using it avoids adding a new `public` surface on `EntityDemoService`, keeping production API unchanged.
  - `VersionsSharedDependencyIntegrationTest:235-248` is the load-bearing regression signal. If this test breaks, it means `containsFailureSignal` was accidentally loosened.
  - `wiki/AttachingAndWrappingExplained.md` is the user-facing contract; semantics of these keys must not change.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: controllerTickObserved defaults to FALSE at completion when never set (preserves the fail signal)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 2 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl test-plugin -am test -Dtest=EntityDemoServiceRecorderContractTest#controllerTickObserved_defaultsToFalseAtCompletion_whenNeverSet_andStillMarksFail
      2. Assert: exit code 0
      3. Assert: Surefire XML contains <testcase ... /> with NO <failure> / <error>
    Expected Result: Test passes — putIfAbsent sets FALSE at deadline; containsFailureSignal flags FALSE; pass=false
    Failure Indicators: <failure> in Surefire; AssertionError suggesting pass=true (which would mean putIfAbsent is broken or containsFailureSignal is loosened)
    Evidence: .sisyphus/evidence/task-2-default-false.txt

  Scenario: entityIdStable=FALSE still flunks scenario (regression-proof for containsFailureSignal)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 2 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl test-plugin -am test -Dtest=EntityDemoServiceRecorderContractTest#entityIdStable_false_stillFlunksScenario
      2. Assert: exit code 0
      3. Assert: Surefire XML shows test passed
    Expected Result: Test passes — entityIdStable=false produces pass=false, proving containsFailureSignal was NOT loosened
    Failure Indicators: Test fails (pass=true unexpectedly) — means containsFailureSignal was accidentally modified
    Evidence: .sisyphus/evidence/task-2-entityidstable-guard.txt

  Scenario: Existing VersionsSharedDependencyIntegrationTest still passes (integration regression-proof)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 2 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/runtime -am test -Dtest=VersionsSharedDependencyIntegrationTest
      2. Assert: exit code 0
    Expected Result: Test class passes — pinned assertions (entityIdStable=true, trackerRebound=true, aiReactedAfterHit=true, controllerTickObserved=true) still resolve pass=true as expected
    Failure Indicators: Any failure — means the producer-side fix accidentally changed an observable pin
    Evidence: .sisyphus/evidence/task-2-integration-regression.txt

  Scenario: Negative control — reverting the putIfAbsent line must cause the default-false test to fail
    Tool: Bash (mvnw.cmd + git)
    Preconditions:
      - Task 2 implementation applied and committed
    Steps:
      1. git stash
      2. Edit EntityDemoService.java: comment out the `recorder.putIfAbsent("controllerTickObserved", Boolean.FALSE);` line in scheduleCompletion
      3. Run: mvnw.cmd -pl test-plugin -am test -Dtest=EntityDemoServiceRecorderContractTest#controllerTickObserved_defaultsToFalseAtCompletion_whenNeverSet_andStillMarksFail
      4. Assert: exit code NON-ZERO
      5. git checkout -- test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java
      6. git stash pop
      7. Re-run step 3; assert exit 0
    Expected Result: Test fails when putIfAbsent is removed; passes when restored. Proves the test exercises the fix.
    Failure Indicators: Test still passes after removing putIfAbsent — test is useless
    Evidence: .sisyphus/evidence/task-2-negative-control-{pre,during,post}-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-2-default-false.txt`
  - [ ] `.sisyphus/evidence/task-2-entityidstable-guard.txt`
  - [ ] `.sisyphus/evidence/task-2-integration-regression.txt`
  - [ ] `.sisyphus/evidence/task-2-negative-control-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-2-negative-control-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-2-negative-control-post-revert.txt`

  **Commit**: YES
  - Message: `fix(test-plugin): prevent ScenarioRecorder observation-marker trap`
  - Files: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java`, `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoServiceRecorderContractTest.java`, possibly a new `EntityDemoServiceRecorderBridge.java` in the same test package
  - Pre-commit: `mvnw.cmd -pl test-plugin -am test`

- [x] 3. Migrate `EntityFactoryV1_16_5.rewireModernVehicleAndPassengerReferencesInternal` passengers/vehicle lookups to `requireFieldOfType` and add a hierarchical-stub regression test

  **What to do**:
  - Edit `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java`:
    1. At lines 1140-1141 (inside `rewireModernVehicleAndPassengerReferencesInternal`), replace:
       ```java
       Field passengersField = requireEntityHandleField(oldHandle, "passengers", "ag", "passengerList");
       Field vehicleField = requireEntityHandleField(oldHandle, "vehicle", "ah");
       ```
       with:
       ```java
       Field passengersField = ReflectionSupport.requireFieldOfType(
               oldHandle.getClass(), List.class, "passengers", "ag", "passengerList");
       Field vehicleField = ReflectionSupport.requireFieldOfType(
               oldHandle.getClass(), Object.class, "vehicle", "ah");
       ```
       Rationale: `passengers` uses `List.class` to filter out any shadowing `DataWatcherObject`. `vehicle` uses `Object.class` as a minimal type filter — since `Entity` type is obfuscation-dependent and using `Object.class` still prevents `DataWatcherObject` (which IS-A `Object` though, so this filter is trivially true). **Note**: because every class extends `Object`, `Object.class` does NOT actually filter. For `vehicle`, prefer resolving the NMS Entity class lazily:
       ```java
       // At class scope (near other static fields around line 114):
       private static final Class<?> NMS_ENTITY_CLASS = resolveNmsEntityClass();
       private static Class<?> resolveNmsEntityClass() {
           try {
               return Class.forName("net.minecraft.server.v1_16_R3.Entity");
           } catch (ClassNotFoundException exception) {
               return null; // tolerated in unit tests without NMS jar
           }
       }
       // Then at line 1141:
       Class<?> vehicleType = (NMS_ENTITY_CLASS != null) ? NMS_ENTITY_CLASS : Object.class;
       Field vehicleField = ReflectionSupport.requireFieldOfType(
               oldHandle.getClass(), vehicleType, "vehicle", "ah");
       ```
       This resolves the vehicle type filter to the NMS `Entity` class at runtime (rejecting any `DataWatcherObject` matches on `"ah"`) while gracefully falling back to `Object.class` in unit tests where the NMS jar is absent.
    2. Do NOT modify lines 818-819, 966-967, 1113, 1132, 1173 — those are out of scope per Metis audit.
    3. Do NOT modify `requireEntityHandleField` / `findEntityHandleField` helpers at lines 1214-1220 — keep the existing `findField`/`requireField` based helpers for other call sites.
  - Extend `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java` with new `@Test` methods that use a hierarchical stub mimicking the production trap:
    - Add static inner classes inside the test file:
      ```java
      public static class HierarchicalStubDataWatcherObject {}
      public static class HierarchicalStubEntity {
          public List<Object> passengers = new ArrayList<>();
          public Object vehicle = null;
      }
      public static class HierarchicalStubEntityLiving extends HierarchicalStubEntity {
          public static final HierarchicalStubDataWatcherObject ag = new HierarchicalStubDataWatcherObject();
          public static final HierarchicalStubDataWatcherObject ah = new HierarchicalStubDataWatcherObject();
      }
      public static class HierarchicalStubZombie extends HierarchicalStubEntityLiving {}
      ```
    - New `@Test` method `rewireModernVehicleAndPassengerReferencesInternal_hierarchicalStub_doesNotThrowClassCastException`:
      - Create `old = new HierarchicalStubZombie()`, `replacement = new HierarchicalStubZombie()`
      - Set `old.passengers = new ArrayList<>(Arrays.asList(replacement))` (or a neutral passenger instance)
      - Invoke `EntityFactoryV1_16_5.rewireModernVehicleAndPassengerReferencesInternal` via reflection (it's `private static`; use the existing pattern from `EntityFactoryV1_8_8Test.java:186-217`)
      - Assert no exception thrown
      - Assert `old.passengers` was rewritten appropriately
    - New `@Test` method `rewireModernVehicleAndPassengerReferencesInternal_hierarchicalStub_picksListFieldNotDataWatcherField`:
      - Same setup
      - Invoke the private method
      - Use reflection to read `old.passengers` via `getDeclaredField("passengers")` on `HierarchicalStubEntity.class` and assert its value is a `List` (not the static `HierarchicalStubDataWatcherObject`)
    - Preserve the existing `ReplacementPublicationHandle`-based test coverage — do NOT delete existing `@Test` methods.

  **Must NOT do**:
  - Do NOT modify sibling call sites (lines 818-819, 966-967, 1113, 1132, 1173) in this file
  - Do NOT modify `requireEntityHandleField` / `findEntityHandleField` helpers
  - Do NOT delete existing tests in `EntityFactoryV1_16_5ReplacementBridgeTest.java`
  - Do NOT import `net.minecraft.server.v1_16_R3.*` directly in the test (will fail without NMS jar) — use Class.forName with null-fallback
  - Do NOT add a new `import` for `java.util.List` if already present at top of file (check first)
  - Do NOT "clean up" or reformat other methods in the file
  - Do NOT add new `@SuppressWarnings` annotations unless strictly necessary for a single-line cast
  - Do NOT add emojis or tutorial-style comments

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Requires careful reflective invocation of a `private static` method with mixed type expectations and constructing a hierarchical stub that exactly mirrors the production NMS shadowing pattern. Must not introduce regressions in the rest of the file.
  - **Skills**: `[]`
    - No applicable project-specific skills.
  - **Skills Evaluated but Omitted**:
    - None applicable.

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 4)
  - **Parallel Group**: Wave 2 (with Task 4)
  - **Blocks**: Task 5, Task 6, Task 7
  - **Blocked By**: Task 1 (needs `requireFieldOfType` helper)

  **References**:

  **Pattern References** (existing code to follow):
  - `versions/1.17.1/src/main/java/.../EntityFactoryV1_17_1.java:1267-1294, 1331-1341` — reference pattern for safe passenger lookup. Shows `requireField(entityHandleClass(), "passengers", "at")` scoped to base class; our fix achieves equivalent safety via `requireFieldOfType`.
  - `versions/1.13.2/src/main/java/.../EntityFactoryV1_13_2.java:1124-1137` — reference defensive `instanceof List` guard. Our fix is stricter: `isAssignableFrom(field.getType())` means we never even read a wrong-typed field.
  - `versions/1.8.8/src/test/java/.../EntityFactoryV1_8_8Test.java:186-217` — canonical repo pattern for invoking `private static` factory methods via reflection in tests. Extract: `Method m = cls.getDeclaredMethod(...); m.setAccessible(true); m.invoke(null, args);` plus `Unsafe.allocateInstance(...)` if a concrete instance is needed.
  - `versions/1.16.5/src/test/java/.../EntityFactoryV1_16_5ReplacementBridgeTest.java:306` — existing `ReplacementPublicationHandle` stub. New hierarchical stubs sit alongside this; do NOT replace it.
  - `versions/runtime/src/test/java/.../tracker/ModernTrackerHookSupportTest.java` `FakeTrackerStateHandle` — proves stub-hierarchy tests work in this module without MockBukkit.

  **API/Type References** (contracts to implement against):
  - `ReflectionSupport.requireFieldOfType(Class, Class, String...)` — introduced in Task 1; returns `@NotNull Field` or throws `IllegalStateException`
  - `java.lang.Class.forName(String)` — used with try/catch for optional NMS class resolution
  - `java.lang.reflect.Method.invoke(Object, Object...)` — for test-side private-method invocation

  **Test References** (testing patterns to follow):
  - `versions/1.8.8/src/test/java/.../EntityFactoryV1_8_8Test.java:186-217` — private-static invocation idiom
  - `versions/1.16.5/src/test/java/.../EntityFactoryV1_16_5ReplacementBridgeTest.java` — existing test-file layout, package structure, assertion style, `@BeforeEach` usage (if any)

  **External References** (libraries and frameworks):
  - BKCommonLib template `local-only/BKCommonLib/src/main/templates/com/bergerkiller/templates/net/minecraft/entity/living.txt:48` — authoritative source confirming `EntityLiving.ag` is `DATA_LIVING_FLAGS : static final DataWatcherObject<Byte>` on v1_16_R3
  - BKCommonLib template `local-only/BKCommonLib/.../entity.txt:95-147` — confirms `Entity.passengers` is un-obfuscated on v1_16_R3
  - CraftBukkit 1.16.5 source (reference only, not on classpath): `net.minecraft.server.v1_16_R3.Entity` has `public List<Entity> passengers`

  **WHY Each Reference Matters**:
  - `EntityFactoryV1_17_1:1267-1294` is the most-similar working implementation; our fix gives 1.16.5 equivalent type safety without changing the helper-method signature.
  - `EntityFactoryV1_8_8Test.java:186-217` is the proven pattern for private-static invocation — copying it avoids introducing new test helpers.
  - BKCommonLib templates are the ONLY authoritative source for the obfuscated field mapping claims; they are the evidence that `"ag"` on `EntityLiving` is a `DataWatcherObject`, not a `List`.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Hierarchical stub test reproduces the original CCE mechanism and passes after fix
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 1 applied (requireFieldOfType exists)
      - Task 3 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest#rewireModernVehicleAndPassengerReferencesInternal_hierarchicalStub_doesNotThrowClassCastException
      2. Assert: exit 0
      3. Assert: Surefire XML at versions/1.16.5/target/surefire-reports/*.xml contains passing testcase
      4. Assert: console output contains "Tests run:" with zero failures for this test
    Expected Result: Test passes — hierarchical stub's passenger rewire completes without ClassCastException
    Failure Indicators: <failure> element with message containing "ClassCastException"; stderr shows the CCE
    Evidence: .sisyphus/evidence/task-3-hierarchical-stub.txt

  Scenario: Field-resolution test confirms the List field on parent wins over DataWatcherObject on child
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 3 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest#rewireModernVehicleAndPassengerReferencesInternal_hierarchicalStub_picksListFieldNotDataWatcherField
      2. Assert: exit 0
      3. Assert: Surefire XML shows the test passed
    Expected Result: Test passes — the resolved passengers field is the List on HierarchicalStubEntity, not the DataWatcherObject on HierarchicalStubEntityLiving
    Failure Indicators: AssertionError showing the field was picked from the subclass (type HierarchicalStubDataWatcherObject) instead of parent (List)
    Evidence: .sisyphus/evidence/task-3-field-resolution.txt

  Scenario: Existing ReplacementBridgeTest coverage still passes (no regression in original stub path)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 3 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest
      2. Assert: exit 0
      3. Assert: ALL @Test methods in the class pass (not only the new ones)
    Expected Result: Full class passes; no regression in existing @Test methods
    Failure Indicators: Any previously-passing test now fails
    Evidence: .sisyphus/evidence/task-3-full-class.txt

  Scenario: Negative control — reverting the requireFieldOfType migration must cause the hierarchical-stub test to fail with CCE
    Tool: Bash (mvnw.cmd + git)
    Preconditions:
      - Task 3 applied and committed
    Steps:
      1. git stash
      2. Edit EntityFactoryV1_16_5.java: restore the original `Field passengersField = requireEntityHandleField(oldHandle, "passengers", "ag", "passengerList");` at line 1140 (replacing the requireFieldOfType call)
      3. Run: mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest#rewireModernVehicleAndPassengerReferencesInternal_hierarchicalStub_doesNotThrowClassCastException
      4. Assert: exit NON-ZERO
      5. Assert: Surefire XML contains <failure> with type containing "ClassCastException" OR <error> showing the CCE
      6. git checkout -- versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java
      7. git stash pop
      8. Re-run step 3; assert exit 0
    Expected Result: Test fails with CCE when fix reverted; passes when restored. Proves the test genuinely exercises the fix.
    Failure Indicators: Test STILL passes after reverting — means the hierarchical stub doesn't actually trigger the trap, and the test is useless
    Evidence: .sisyphus/evidence/task-3-negative-control-{pre,during,post}-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-3-hierarchical-stub.txt`
  - [ ] `.sisyphus/evidence/task-3-field-resolution.txt`
  - [ ] `.sisyphus/evidence/task-3-full-class.txt`
  - [ ] `.sisyphus/evidence/task-3-negative-control-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-3-negative-control-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-3-negative-control-post-revert.txt`

  **Commit**: YES
  - Message: `fix(1.16.5): resolve passengers/vehicle fields by type to avoid DataWatcherObject collision`
  - Files: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java`, `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java`
  - Pre-commit: `mvnw.cmd -pl versions/1.16.5 -am test`

- [x] 4. Migrate `EntityFactoryV1_19_2` passenger/vehicle lookups to `requireFieldOfType` to close the latent `"au"` token collision

  **What to do**:
  - Edit `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java`:
    1. At lines 1606-1607 (inside `rewireVehicleAndPassengerReferencesModern` or similar — verify exact method name by reading the file), replace the existing:
       ```java
       Field passengersField = ReflectionSupport.findField(oldHandle.getClass(), "passengers", "au");
       Field vehicleField    = ReflectionSupport.findField(oldHandle.getClass(), "vehicle", "av", "au");
       ```
       with:
       ```java
       Field passengersField = ReflectionSupport.requireFieldOfType(
               oldHandle.getClass(), List.class, "passengers", "au");
       Class<?> vehicleType = resolveNmsEntityClassV1_19_2();
       Field vehicleField = ReflectionSupport.requireFieldOfType(
               oldHandle.getClass(), (vehicleType != null) ? vehicleType : Object.class,
               "vehicle", "av", "au");
       ```
       Add a private helper:
       ```java
       private static Class<?> resolveNmsEntityClassV1_19_2() {
           try {
               return Class.forName("net.minecraft.world.entity.Entity");
           } catch (ClassNotFoundException exception) {
               return null;
           }
       }
       ```
       Rationale: With `findField`, the shared `"au"` token can resolve to vehicle-as-passenger or passenger-as-vehicle under some obfuscation shapes. Type-filtered lookup rejects the wrong match.
    2. If the existing code handles `null` return from `findField` via `if (… != null)`, update the surrounding logic to call the nullable variant `findFieldOfType` instead of the `require*` variant — but prefer `require*` if the callers always expect a non-null field.
    3. Handle the subsequent hard-casts on lines 1613+ (adjacent to the original cast sites) in a similar way: if they cast to `List`, the type filter at resolution time already protects them; just keep the cast.
  - Extend `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java` with a hierarchical-stub regression test mirroring Task 3:
    - Static inner stub classes with `passengers: List`, `vehicle: Object` on the parent, and `au: SomeSharedSentinel` on the child (simulating the `"au"` token shared between passenger and vehicle candidate lists)
    - `@Test` method `rewireVehicleAndPassengerReferencesModern_hierarchicalStub_picksCorrectFieldDespiteAuCollision` verifying that `passengers` resolves to the `List` field and `vehicle` resolves to a non-`List` reference field
    - `@Test` method `rewireVehicleAndPassengerReferencesModern_hierarchicalStub_doesNotThrowClassCastException` verifying no CCE on invocation
  - If no `EntityFactoryV1_19_2ReplacementBridgeTest.java` file exists yet (verify first), create it following the 1.16.5 pattern. If it exists, extend it.

  **Must NOT do**:
  - Do NOT modify sibling call sites in `EntityFactoryV1_19_2.java` that are not in `rewireVehicleAndPassengerReferencesModern`
  - Do NOT replace `findField` with `findFieldOfType` globally in the file — only the specific passenger/vehicle sites
  - Do NOT modify the existing test stubs / bridges in the 1.19.2 test directory
  - Do NOT copy the 1.16.5 stub classes verbatim; use distinct class names to avoid confusion (e.g., `HierarchicalStubV1_19_2_Entity`)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Same shape as Task 3 but slightly lighter (latent-only bug, no production symptom). Requires same rigor for hierarchical stub and negative-control.
  - **Skills**: `[]`
    - No applicable project-specific skills.
  - **Skills Evaluated but Omitted**:
    - None applicable.

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 3)
  - **Parallel Group**: Wave 2 (with Task 3)
  - **Blocks**: Task 5, Task 7
  - **Blocked By**: Task 1 (needs `requireFieldOfType` helper)

  **References**:

  **Pattern References** (existing code to follow):
  - `versions/1.19.2/src/main/java/.../EntityFactoryV1_19_2.java:1602-1631` — existing `rewireVehicleAndPassengerReferencesModern` implementation
  - Task 3's migration plan for `EntityFactoryV1_16_5` — mirror its structure (requireFieldOfType + optional NMS class resolution via Class.forName)
  - `versions/1.19.2/src/test/java/...` — existing test-file layout in this module (may need to be created if no bridge test exists yet)

  **API/Type References** (contracts to implement against):
  - `ReflectionSupport.requireFieldOfType` — from Task 1
  - `net.minecraft.world.entity.Entity` — Mojang-mapped NMS Entity class name for 1.19.2 (via Class.forName with fallback)

  **Test References** (testing patterns to follow):
  - Task 3's hierarchical-stub test pattern
  - `versions/1.16.5/src/test/java/.../EntityFactoryV1_16_5ReplacementBridgeTest.java` — existing bridge test structure

  **External References** (libraries and frameworks):
  - None beyond standard JDK reflection and JUnit 5

  **WHY Each Reference Matters**:
  - Mirroring Task 3's structure ensures consistency across version modules and reduces review cognitive load.
  - Class.forName fallback to null is the same pattern used in Task 3; distributing this pattern across versions standardizes the NMS-class-resolution idiom.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Hierarchical stub confirms no CCE even with "au" token shared across passenger/vehicle candidates
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 1 applied
      - Task 4 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/1.19.2 -am test -Dtest=EntityFactoryV1_19_2ReplacementBridgeTest#rewireVehicleAndPassengerReferencesModern_hierarchicalStub_doesNotThrowClassCastException
      2. Assert: exit 0
      3. Assert: Surefire XML shows the test passed
    Expected Result: Test passes — the "au" collision no longer causes either lookup to return the wrong field
    Failure Indicators: <failure> element mentioning ClassCastException or AssertionError
    Evidence: .sisyphus/evidence/task-4-hierarchical-stub.txt

  Scenario: Full 1.19.2 bridge test class still passes (no regression in existing coverage)
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Task 4 implementation applied
    Steps:
      1. Run: mvnw.cmd -pl versions/1.19.2 -am test -Dtest=EntityFactoryV1_19_2ReplacementBridgeTest
      2. Assert: exit 0
      3. Assert: ALL @Test methods pass
    Expected Result: No regression in existing tests
    Failure Indicators: Any previously-passing test now failing
    Evidence: .sisyphus/evidence/task-4-full-class.txt

  Scenario: Negative control — reverting the migration at line 1606-1607 must cause the hierarchical stub test to fail
    Tool: Bash (mvnw.cmd + git)
    Preconditions:
      - Task 4 applied and committed
    Steps:
      1. git stash
      2. Edit EntityFactoryV1_19_2.java: restore the original `findField(oldHandle.getClass(), "passengers", "au")` and `findField(oldHandle.getClass(), "vehicle", "av", "au")` lines
      3. Run: mvnw.cmd -pl versions/1.19.2 -am test -Dtest=EntityFactoryV1_19_2ReplacementBridgeTest#rewireVehicleAndPassengerReferencesModern_hierarchicalStub_doesNotThrowClassCastException
      4. Assert: exit NON-ZERO
      5. git checkout -- versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java
      6. git stash pop
      7. Re-run step 3; assert exit 0
    Expected Result: Test fails when fix reverted; passes when restored
    Failure Indicators: Test still passes after reverting — means hierarchical stub doesn't trigger the latent bug (need to adjust the stub to be more faithful to the trap shape)
    Evidence: .sisyphus/evidence/task-4-negative-control-{pre,during,post}-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-4-hierarchical-stub.txt`
  - [ ] `.sisyphus/evidence/task-4-full-class.txt`
  - [ ] `.sisyphus/evidence/task-4-negative-control-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-4-negative-control-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-4-negative-control-post-revert.txt`

  **Commit**: YES
  - Message: `fix(1.19.2): resolve passengers/vehicle fields by type to avoid au-token collision`
  - Files: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java`, `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java`
  - Pre-commit: `mvnw.cmd -pl versions/1.19.2 -am test`

- [x] 5. Run the full Maven build + test suite across all modules and verify zero regressions

  **What to do**:
  - From repo root, run: `mvnw.cmd clean test`
  - Capture full stdout/stderr to `.sisyphus/evidence/task-5-full-build.txt`
  - Parse aggregated Surefire reports across all modules: `**/target/surefire-reports/*.xml`
  - Cross-reference the pinned `VersionsSharedDependencyIntegrationTest` (expected: `entityIdStable=true`, `trackerRebound=true`, `aiReactedAfterHit=true`, `controllerTickObserved=true`). This test was identified by Metis as the regression canary for the recorder fix.
  - If any test fails:
    1. Identify the failing test class and specific `@Test` method from the Surefire XML
    2. Determine which task caused the regression (likely T2 if recorder tests fail; T1 if ReflectionSupport tests fail; T3/T4 if version-factory tests fail)
    3. Do NOT proceed to T6 until all regressions are fixed and the full build passes clean
  - On clean pass: capture aggregated per-module counts for the evidence file.

  **Must NOT do**:
  - Do NOT use `-DskipTests` — this task's purpose is EXECUTING tests
  - Do NOT use `-fae` (fail at end) — fail-fast is the intent
  - Do NOT skip modules — ALL modules must be exercised
  - Do NOT fix regressions in a way that re-introduces the original bug (revert any changes from T1-T4 only via explicit rollback + re-planning)

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Cross-module regression triage requires systematic analysis of Surefire reports and reasoning about which task's changes could produce observed failures. Build-time analysis of multi-module Maven.
  - **Skills**: `[]`
    - No applicable project-specific skills.

  **Parallelization**:
  - **Can Run In Parallel**: YES (with Task 6, though both consume CPU)
  - **Parallel Group**: Wave 3 (with Task 6)
  - **Blocks**: Task 7
  - **Blocked By**: Task 1, Task 2, Task 3, Task 4

  **References**:

  **Pattern References** (existing code to follow):
  - `.github/workflows/ci.yml` — CI uses `mvn test -B`. Local equivalent is `mvnw.cmd test` (batch mode implicit on Windows).
  - `AGENTS.md` Build Commands: `mvnw.cmd clean test` is the documented full-suite command.

  **API/Type References** (contracts to implement against):
  - Surefire XML format: `testsuite @name @tests @failures @errors` + per-testcase children

  **Test References** (testing patterns to follow):
  - N/A (this task runs tests, does not write them)

  **External References** (libraries and frameworks):
  - Maven Surefire Plugin reports: https://maven.apache.org/surefire/maven-surefire-plugin/examples/generating-report.html

  **WHY Each Reference Matters**:
  - `AGENTS.md` commits us to `mvnw.cmd clean test` as the gate; any deviation (e.g., running only one module) would not satisfy the contract.
  - Surefire XML format is the machine-parseable evidence for per-test pass/fail — critical for the negative-control proofs referenced in T1-T4.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Full Maven build and all tests pass clean
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Tasks 1-4 implementations applied
      - No uncommitted test-modification state
    Steps:
      1. cd to repo root
      2. Run: mvnw.cmd clean test > .sisyphus/evidence/task-5-full-build.txt 2>&1
      3. Capture: $LASTEXITCODE (PowerShell) or $? (bash) — MUST be 0
      4. Parse: glob **/target/surefire-reports/*.xml
      5. For each XML: assert attribute failures="0" and errors="0"
      6. Count aggregated: total tests, total pass, total fail/error. Record in evidence file.
    Expected Result: Exit 0; all modules compile; all tests pass; aggregated "0 failures, 0 errors" across all Surefire reports
    Failure Indicators: Non-zero exit; any <failure> or <error> in any Surefire XML; BUILD FAILURE in stdout
    Evidence: .sisyphus/evidence/task-5-full-build.txt; optional .sisyphus/evidence/task-5-surefire-summary.txt with per-module breakdown

  Scenario: VersionsSharedDependencyIntegrationTest (regression canary) specifically passes
    Tool: Bash (mvnw.cmd)
    Preconditions:
      - Tasks 1-4 applied
    Steps:
      1. Run: mvnw.cmd -pl versions/runtime -am test -Dtest=VersionsSharedDependencyIntegrationTest
      2. Assert: exit 0
      3. Assert: Surefire XML for this test shows 0 failures/errors
    Expected Result: Pinned assertions (entityIdStable=true, trackerRebound=true, aiReactedAfterHit=true, controllerTickObserved=true) still resolve to pass=true
    Failure Indicators: Any failure — means T2's producer-side recorder fix accidentally broke the pinned contract
    Evidence: .sisyphus/evidence/task-5-regression-canary.txt

  Scenario: Failure-path — T2 regression check (proof the build gate catches producer-side regressions)
    Tool: Bash (mvnw.cmd + git)
    Preconditions:
      - T1-T4 applied and committed
    Steps:
      1. git stash
      2. In EntityDemoService.java, CORRUPT containsFailureSignal by adding `if (value instanceof Boolean && ((Boolean) value) == Boolean.FALSE) continue;` at the top of the loop (simulating a bad loosening)
      3. Run: mvnw.cmd clean test
      4. Assert: exit NON-ZERO
      5. Assert: VersionsSharedDependencyIntegrationTest OR a contract test from T2 FAILS
      6. git checkout -- test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java
      7. git stash pop
      8. Re-run mvnw.cmd clean test — assert exit 0
    Expected Result: Build FAILS with a specific regression signal when containsFailureSignal is maliciously loosened; passes when restored
    Failure Indicators: Build still passes after corruption — means the regression canary is not load-bearing
    Evidence: .sisyphus/evidence/task-5-failure-path-{pre,during,post}-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-5-full-build.txt`
  - [ ] `.sisyphus/evidence/task-5-surefire-summary.txt` (per-module pass/fail counts)
  - [ ] `.sisyphus/evidence/task-5-regression-canary.txt`
  - [ ] `.sisyphus/evidence/task-5-failure-path-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-5-failure-path-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-5-failure-path-post-revert.txt`

  **Commit**: NO (verification only)

- [x] 6. Run the entity-matrix harness on spigot-1.16.5 for `attach-existing-zombie` + adjacent at-risk scenarios and assert `pass=true` with no `ClassCastException`

  **What to do**:
  - Executor environment: Windows with PowerShell. Linux / macOS executors must SKIP this task and explicitly document the skip in `.sisyphus/evidence/task-6-skipped.txt` with rationale — the harness is Windows-only today (no CI equivalent exists).
  - Preflight: verify `.tools/entity-matrix/spigot-1.16.5/server.jar` and `.tools/entity-matrix/jdks/17/bin/java.exe` exist. If not, allow `run-entity-matrix.ps1` to call `provision-entity-matrix.ps1` (one-time ~10-min BuildTools run). Document provision time in evidence if triggered.
  - Clean prior artifacts: delete `target/entity-matrix/spigot-1.16.5/{attach-existing-zombie,metadata-dirty-zombie,viewer-cycle-zombie}/` directories.
  - Run three scenarios on spigot-1.16.5 (sequentially; each takes ~15-30 seconds post-provision):
    ```powershell
    pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie
    pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario metadata-dirty-zombie
    pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario viewer-cycle-zombie
    ```
  - For each scenario, after the run completes:
    1. Capture the script's exit code (MUST be 0)
    2. Parse `target/entity-matrix/spigot-1.16.5/<scenario>/assertions.json` via `Get-Content | ConvertFrom-Json`
    3. Assert `.pass -eq $true`
    4. Assert all `expectedAssertionKeys` from `scripts/entity-matrix/scenarios.json` are present and non-null
    5. Grep `target/entity-matrix/spigot-1.16.5/<scenario>/server.log` for "ClassCastException" — MUST be empty match
    6. Parse `target/entity-matrix/spigot-1.16.5/<scenario>/trace.json` and assert NO event has `"event":"autorun-failure"`
    7. Copy the three artifact files to `.sisyphus/evidence/task-6-<scenario>-{assertions,trace,server-log-tail}.txt`

  **Must NOT do**:
  - Do NOT modify the harness scripts or `scenarios.json` / `servers.json`
  - Do NOT delete `target/entity-matrix/` contents beyond the three scenario directories being re-run
  - Do NOT run scenarios on other servers in this task (out of scope; cross-platform regression is a separate follow-up)
  - Do NOT edit `.tools/entity-matrix/` state — let the harness manage it
  - Do NOT commit any `target/` or `.sisyphus/evidence/` files to git (`.sisyphus/evidence/` is not git-tracked by convention)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: End-to-end smoke execution + JSON/log assertion + evidence capture on a Windows executor. Not algorithm-heavy but requires environment setup awareness and PowerShell fluency.
  - **Skills**: `[]`
    - No applicable project-specific skills.

  **Parallelization**:
  - **Can Run In Parallel**: YES with T5 (they use CPU differently — T5 is JVM compile + JUnit, T6 is spinning up a real server)
  - **Parallel Group**: Wave 3 (with Task 5)
  - **Blocks**: Task 7
  - **Blocked By**: Task 1, Task 2, Task 3 (T4 is 1.19.2-only, not exercised on this server/scenario matrix)

  **References**:

  **Pattern References** (existing code to follow):
  - `scripts/run-entity-matrix.ps1:167-226` — harness flag composition (`-D...` + environment variables). Do not duplicate; invoke the script.
  - `scripts/entity-matrix/common.ps1:43-94` — `Wait-ForScenarioOutputs` polling logic (500ms interval, scenario-specific timeout from scenarios.json). Informs how long to wait before declaring a hang.
  - `scripts/entity-matrix/scenarios.json` — the declared `expectedAssertionKeys` for each scenario (5-key subset for `attach-existing-zombie`)

  **API/Type References** (contracts to implement against):
  - JSON schema of `assertions.json`: object with `pass`, `passCount`, `failCount`, plus scenario-specific keys. Verified by `EntityScenarioArtifacts.write` (see test-plugin code).
  - JSON schema of `trace.json`: object with `scenario`, `server`, `events[]`. Each event has `event: string, details: object`.

  **Test References** (testing patterns to follow):
  - `scripts/run-entity-matrix.ps1:98-...` `Assert-ScenarioOutputs` function — our verification logic can call this directly rather than duplicating.

  **External References** (libraries and frameworks):
  - PowerShell `ConvertFrom-Json` cmdlet: https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/convertfrom-json

  **WHY Each Reference Matters**:
  - Invoking the script rather than reimplementing its flag-composition avoids drift with the harness contract (harness is out of scope for modification).
  - Using `Assert-ScenarioOutputs` from the script reuses the battle-tested assertion logic — our task only needs to call the script and check its exit code.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: attach-existing-zombie on spigot-1.16.5 passes end-to-end
    Tool: Bash (pwsh)
    Preconditions:
      - Tasks 1-4 applied and built (jar packaged by harness)
      - Windows executor with PowerShell
      - .tools/entity-matrix/spigot-1.16.5/server.jar present (or willing to wait for provision)
    Steps:
      1. Delete: target/entity-matrix/spigot-1.16.5/attach-existing-zombie/
      2. Run: pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie
      3. Assert: $LASTEXITCODE -eq 0
      4. Assert: target/entity-matrix/spigot-1.16.5/attach-existing-zombie/assertions.json exists and its .pass value is $true
      5. Assert: .attachCount is 1, .entityIdStable is $true, .duplicateSpawnCount is 0, .trackerRebound is $true, .controllerTickObserved is $true
      6. Assert: target/entity-matrix/spigot-1.16.5/attach-existing-zombie/server.log does NOT contain "ClassCastException"
      7. Assert: target/entity-matrix/spigot-1.16.5/attach-existing-zombie/trace.json has no event with "event":"autorun-failure"
    Expected Result: All assertions pass; artifacts captured as evidence
    Failure Indicators: Non-zero exit; pass=false in assertions.json; any ClassCastException in server.log; any autorun-failure in trace.json
    Evidence: .sisyphus/evidence/task-6-attach-existing-zombie-assertions.json, .sisyphus/evidence/task-6-attach-existing-zombie-trace.json, .sisyphus/evidence/task-6-attach-existing-zombie-server-log-tail.txt (last 200 lines)

  Scenario: metadata-dirty-zombie regression — same server, different scenario, still passes (proves no collateral break)
    Tool: Bash (pwsh)
    Preconditions:
      - Tasks 1-4 applied
    Steps:
      1. Delete: target/entity-matrix/spigot-1.16.5/metadata-dirty-zombie/
      2. Run: pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario metadata-dirty-zombie
      3. Assert: exit 0; .pass is $true; expected keys present; server.log clean of ClassCastException
    Expected Result: Scenario still passes
    Failure Indicators: Any failure — T1/T3 accidentally affected the metadata-dirty path
    Evidence: .sisyphus/evidence/task-6-metadata-dirty-zombie-assertions.json, …-trace.json, …-server-log-tail.txt

  Scenario: viewer-cycle-zombie regression
    Tool: Bash (pwsh)
    Preconditions:
      - Tasks 1-4 applied
    Steps:
      1. Delete: target/entity-matrix/spigot-1.16.5/viewer-cycle-zombie/
      2. Run: pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario viewer-cycle-zombie
      3. Assert: exit 0; .pass is $true; expected keys present; server.log clean
    Expected Result: Scenario still passes
    Failure Indicators: Any failure — collateral regression
    Evidence: .sisyphus/evidence/task-6-viewer-cycle-zombie-assertions.json, …-trace.json, …-server-log-tail.txt

  Scenario: Failure-path — revert T3 fix only, re-run harness, confirm CCE reappears
    Tool: Bash (pwsh + git)
    Preconditions:
      - Tasks 1-4 applied and committed
      - Already have evidence that Task 6's happy path passes
    Steps:
      1. git stash
      2. Edit EntityFactoryV1_16_5.java: restore original `requireEntityHandleField(oldHandle, "passengers", "ag", "passengerList")` at line 1140
      3. Run: mvnw.cmd -pl test-plugin -am package -DskipTests (re-package)
      4. Delete: target/entity-matrix/spigot-1.16.5/attach-existing-zombie/
      5. Run: pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie
      6. Assert: exit NON-ZERO OR assertions.json has pass=false OR server.log contains ClassCastException
      7. git checkout -- versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java
      8. git stash pop
      9. Re-run step 5; assert exit 0 and pass=true
    Expected Result: Revert reproduces the original CCE; restoring fix re-passes. Proves end-to-end evidence is load-bearing.
    Failure Indicators: Revert still passes end-to-end — means the harness isn't actually exercising the fix site, plan is suspect
    Evidence: .sisyphus/evidence/task-6-failure-path-{pre,during,post}-revert.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-6-attach-existing-zombie-assertions.json`
  - [ ] `.sisyphus/evidence/task-6-attach-existing-zombie-trace.json`
  - [ ] `.sisyphus/evidence/task-6-attach-existing-zombie-server-log-tail.txt`
  - [ ] `.sisyphus/evidence/task-6-metadata-dirty-zombie-assertions.json`
  - [ ] `.sisyphus/evidence/task-6-metadata-dirty-zombie-trace.json`
  - [ ] `.sisyphus/evidence/task-6-metadata-dirty-zombie-server-log-tail.txt`
  - [ ] `.sisyphus/evidence/task-6-viewer-cycle-zombie-assertions.json`
  - [ ] `.sisyphus/evidence/task-6-viewer-cycle-zombie-trace.json`
  - [ ] `.sisyphus/evidence/task-6-viewer-cycle-zombie-server-log-tail.txt`
  - [ ] `.sisyphus/evidence/task-6-failure-path-pre-revert.txt`
  - [ ] `.sisyphus/evidence/task-6-failure-path-during-revert.txt`
  - [ ] `.sisyphus/evidence/task-6-failure-path-post-revert.txt`

  **Commit**: NO (verification only)

- [x] 7. Verify Conventional Commit stack and ensure no forbidden file was touched

  **What to do**:
  - Run `git log --oneline <base>..HEAD` where `<base>` is the branch starting point (likely `master` or `dev` — determine via `git merge-base master HEAD` then `git log <hash>..HEAD --oneline`).
  - Assert the commit stack contains exactly four commits, in order (or fewer if any task was a no-op for this executor — document the deviation):
    1. `feat(runtime): add type-filtered field lookup helpers to ReflectionSupport`
    2. `fix(test-plugin): prevent ScenarioRecorder observation-marker trap`
    3. `fix(1.16.5): resolve passengers/vehicle fields by type to avoid DataWatcherObject collision`
    4. `fix(1.19.2): resolve passengers/vehicle fields by type to avoid au-token collision`
  - Run `git diff --name-only <base>..HEAD` and assert the changed-file set matches expectations exactly — no file outside these directories:
    - `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java`
    - `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupportFindFieldHierarchyTest.java`
    - `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java`
    - `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java`
    - `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java`
    - `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java`
    - `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoServiceRecorderContractTest.java`
    - Optionally: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoServiceRecorderBridge.java` (if bridge was needed)
  - Assert NO changes in any of the guardrail-forbidden paths:
    - `scripts/**`
    - `versions/1.13.2/**`
    - `versions/1.17.1/**`
    - `versions/1.21.11/**`
    - `versions/1.8.8/**`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/Main.java`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityMatrixAutorunService.java`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityMatrixRuntimeRequest.java`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioArtifacts.java`
    - `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioDescriptor.java`
    - `wiki/**`
    - `local-only/**`
    - `.github/**`
    - Any `pom.xml` (new imports should not require new Maven dependencies)
  - If pre-commit hooks auto-modified any file since the last commit, re-verify with `git status --porcelain` — must be empty.
  - Generate a `.sisyphus/evidence/task-7-commit-audit.txt` capturing: the four commit hashes, their messages, their file lists (`git show --stat`), and the final `git diff --name-only <base>..HEAD` result.

  **Must NOT do**:
  - Do NOT squash the four commits into one (each is reviewable on its own)
  - Do NOT amend commits after they are reviewed
  - Do NOT force-push
  - Do NOT rewrite commit messages to remove Conventional Commit prefixes
  - Do NOT include `target/`, `.sisyphus/evidence/`, `.idea/`, `node_modules/`, `.tools/` or other non-source paths in commits

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pure git-log verification and diff auditing. No logic, no code. Fast.
  - **Skills**: `[]`
    - No applicable project-specific skills (a `git-master` skill would be ideal if available).

  **Parallelization**:
  - **Can Run In Parallel**: NO (must run AFTER T5 and T6 both complete)
  - **Parallel Group**: Wave 4 (sequential)
  - **Blocks**: F1-F4
  - **Blocked By**: T5, T6

  **References**:

  **Pattern References** (existing code to follow):
  - Repo commit history: `git log --oneline -n 50` shows Conventional Commit prefixes (`feat:`, `fix:`, `refactor:`, `test:`). Match this style.
  - `AGENTS.md` Commit & Pull Request Guidelines — PRs target master or dev, scoped commits.

  **API/Type References** (contracts to implement against):
  - Conventional Commits spec: https://www.conventionalcommits.org/en/v1.0.0/

  **Test References** (testing patterns to follow):
  - N/A — this is an audit task, not a test

  **External References** (libraries and frameworks):
  - Conventional Commits: https://www.conventionalcommits.org

  **WHY Each Reference Matters**:
  - Repo history is the locked-in style; deviating from it is a review burden.
  - `AGENTS.md` is authoritative for contribution style.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY)**:

  ```
  Scenario: Commit stack has exactly four Conventional Commits with expected prefixes and messages
    Tool: Bash (git)
    Preconditions:
      - T1-T4 committed
    Steps:
      1. Determine base: $base = $(git merge-base master HEAD) or $(git merge-base dev HEAD), pick whichever is closer to HEAD
      2. Run: git log --format='%H %s' $base..HEAD
      3. Assert: exactly 4 lines returned
      4. Assert: line 1 starts with "feat(runtime):"
      5. Assert: line 2 starts with "fix(test-plugin):"
      6. Assert: line 3 starts with "fix(1.16.5):"
      7. Assert: line 4 starts with "fix(1.19.2):"
    Expected Result: Exactly 4 commits in expected order and prefix
    Failure Indicators: Wrong count; wrong prefix; squashed; wrong order
    Evidence: .sisyphus/evidence/task-7-commit-stack.txt

  Scenario: Diff touches only allowed files
    Tool: Bash (git)
    Preconditions:
      - T1-T4 committed
    Steps:
      1. Run: git diff --name-only $base..HEAD | Sort-Object | Out-File .sisyphus/evidence/task-7-changed-files.txt
      2. Assert: the file list is a subset of the 8-9 allowed files enumerated in the What-to-do above
      3. Assert: no path under scripts/, versions/{1.13.2,1.17.1,1.21.11,1.8.8}/, wiki/, local-only/, .github/, or any pom.xml
    Expected Result: Only the allowed files changed; no forbidden paths touched
    Failure Indicators: Any file outside the allow-list — scope creep or accidental edit
    Evidence: .sisyphus/evidence/task-7-changed-files.txt; .sisyphus/evidence/task-7-forbidden-audit.txt (explicitly listing any violations OR a declaration of "none")

  Scenario: Working tree is clean after T5/T6 verification
    Tool: Bash (git)
    Preconditions:
      - T5 and T6 run; no evidence files committed
    Steps:
      1. Run: git status --porcelain
      2. Assert: output is empty OR only untracked `.sisyphus/evidence/*` files (which are not committed)
    Expected Result: No uncommitted source changes; only evidence files in untracked state
    Failure Indicators: Modified source files — means a verification step accidentally modified source
    Evidence: .sisyphus/evidence/task-7-working-tree-status.txt
  ```

  **Evidence to Capture**:
  - [ ] `.sisyphus/evidence/task-7-commit-stack.txt`
  - [ ] `.sisyphus/evidence/task-7-changed-files.txt`
  - [ ] `.sisyphus/evidence/task-7-forbidden-audit.txt`
  - [ ] `.sisyphus/evidence/task-7-working-tree-status.txt`
  - [ ] `.sisyphus/evidence/task-7-commit-audit.txt` (aggregated git show --stat)

  **Commit**: NO (this task verifies existing commits; no new commit)

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
>
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback → fix → re-run → present again → wait for okay.

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (open file, grep for the new helper, inspect the migrated call site). For each "Must NOT Have": search codebase for forbidden patterns (e.g., `git diff --stat` on forbidden files — must be empty; `containsFailureSignal` signature unchanged; `findField`/`requireField` iteration order unchanged; no `EntityFactoryV1_13_2`/`V1_17_1`/`V1_21_1*`/`V1_8_8` diff). Verify evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `mvnw.cmd clean test` fresh (no incremental). Review all changed files for: `as any` / `@ts-ignore` equivalents (Java: unchecked-cast warnings, `@SuppressWarnings("unchecked")` without a narrow scope), empty catch blocks, `System.out.println` or `e.printStackTrace()` in production, commented-out code, unused imports. Check AI-slop hallmarks: tutorial-style comments ("This method does X"), over-abstraction (`findFieldOfTypeFactoryStrategy`), generic names (`data`, `result`, `item`, `temp`). Verify Javadocs per `AGENTS.md` Coding Style (public/protected APIs with `@param`/`@return`/`@throws`). Verify no `private final java.util.List<String>` fully-qualified inline types (use `import` per repo convention).
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state (`git clean -fdx target/entity-matrix`). Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration: run `attach-existing-zombie` end-to-end (exercises T1's helper + T3's migration + T2's recorder fix together). Test edge cases: empty passenger list, zombie with a rider already, tracker rebound path on second `platform().get(zombie)` call. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (`git log --oneline <base>..HEAD` then `git show <hash>`). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance — verify no diff on the forbidden file list. Detect cross-task contamination: Task 1 must not touch 1.16.5 files; Task 3 must not touch 1.19.2 files; etc. Flag any unaccounted changes (e.g., IDE-formatter noise, whitespace diffs, accidental import cleanups).
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **T1**: `feat(runtime): add type-filtered field lookup helpers to ReflectionSupport` — `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupport.java`, `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/ReflectionSupportFindFieldHierarchyTest.java`. Pre-commit: `mvnw.cmd -pl versions/runtime -am test`
- **T2**: `fix(test-plugin): prevent ScenarioRecorder observation-marker trap` — `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java`, `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoServiceRecorderContractTest.java`. Pre-commit: `mvnw.cmd -pl test-plugin -am test`
- **T3**: `fix(1.16.5): resolve passengers/vehicle fields by type to avoid DataWatcherObject collision` — `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java`, `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java`. Pre-commit: `mvnw.cmd -pl versions/1.16.5 -am test`
- **T4**: `fix(1.19.2): resolve passengers/vehicle fields by type to avoid au-token collision` — `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java`, `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java`. Pre-commit: `mvnw.cmd -pl versions/1.19.2 -am test`
- **T5**: no commit (verification only)
- **T6**: no commit (verification only)
- **T7**: verify commit stack; no new commits unless pre-commit hooks modified files

---

## Success Criteria

### Verification Commands

```bash
# Build gate (algorithm-level)
mvnw.cmd clean test
# Expected: BUILD SUCCESS, zero test failures across all modules

# Targeted regression tests (fast per-task signal)
mvnw.cmd -pl versions/runtime  -am test -Dtest=ReflectionSupportFindFieldHierarchyTest
mvnw.cmd -pl versions/1.16.5   -am test -Dtest=EntityFactoryV1_16_5ReplacementBridgeTest
mvnw.cmd -pl versions/1.19.2   -am test -Dtest=EntityFactoryV1_19_2ReplacementBridgeTest
mvnw.cmd -pl test-plugin       -am test -Dtest=EntityDemoServiceRecorderContractTest
# Expected per call: BUILD SUCCESS, zero failures in Surefire report

# End-to-end smoke (Windows developer; Wave 5)
pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario attach-existing-zombie
# Expected:
#   - Exit code 0
#   - target/entity-matrix/spigot-1.16.5/attach-existing-zombie/assertions.json contains "pass":true
#   - target/entity-matrix/spigot-1.16.5/attach-existing-zombie/trace.json contains NO "autorun-failure" event
#   - target/entity-matrix/spigot-1.16.5/attach-existing-zombie/server.log contains NO "ClassCastException"
#   - All of: attachCount=1, entityIdStable=true, duplicateSpawnCount=0, trackerRebound=true, controllerTickObserved=true

# Regression smoke on adjacent at-risk scenarios (same server, same harness)
pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario metadata-dirty-zombie
pwsh ./scripts/run-entity-matrix.ps1 -Server spigot-1.16.5 -Scenario viewer-cycle-zombie
# Expected per call: exit 0, pass=true in assertions.json
```

### Final Checklist
- [ ] All "Must Have" present (verified by F1)
- [ ] All "Must NOT Have" absent (verified by F1, F4)
- [ ] All tests pass (verified by F2)
- [ ] Real end-to-end smoke passes (verified by F3)
- [ ] No scope contamination (verified by F4)
- [ ] User okay received after presenting F1-F4 results
