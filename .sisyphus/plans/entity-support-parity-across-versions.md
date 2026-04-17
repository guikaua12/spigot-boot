# Entity Support Parity Across Versions

## TL;DR
> **Summary**: Expand every version-local `EntityFactoryV1_*` to advertise and implement the maximum safe set of entity base types for that version, with attach/replacement parity required for every included entity and explicit exclusions for unsafe or unsupported families.
> **Deliverables**:
> - explicit per-version support allowlists/exclusion rules across `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11`
> - version-local spawn + attach/replacement parity for every newly included entity
> - runtime and version-module tests that reject overclaimed support
> - representative sample-plugin scenarios for hostile, passive, and special-case families
> **Effort**: Large
> **Parallel**: YES - 2 waves
> **Critical Path**: T1 -> T3/T4/T5/T6/T7/T8 -> T9 -> T10

## Context
### Original Request
Make all version-specific entity factories support the maximum safe number of entity types possible, using `versions/1.21.11/.../EntityFactoryV1_21_11.java` as the benchmark for broad coverage, while excluding super-complex or unsupported entities the same way `resolveBaseType(Entity)` and the modern metadata registry already do.

### Interview Summary
- The task spans all version-local entity factories, so the plan targets one cross-version execution plan instead of isolated version patches.
- Research confirmed support breadth is non-monotonic: `1.8.8` and `1.21.11` are broad, `1.19.2` is broad with explicit exclusions, and `1.13.2`, `1.16.5`, and `1.17.1` are zombie-only.
- The user selected **attach parity required everywhere**: an entity only counts as supported if fresh spawn and attach/replacement are both safe for that version.
- The user selected **tests + representative scenarios**: runtime/version tests are mandatory, plus representative sample-plugin evidence on selected legacy and modern versions.
- The plan therefore treats `1.21.11` as a coverage benchmark, not a literal implementation template.

### Metis Review (gaps addressed)
- Added a mandatory **per-version capability matrix** so support is classified as included/excluded with rationale instead of inferred from raw enum iteration.
- Added a hard rule that **`supports()` must never overclaim**; every included entity must satisfy spawn + attach/replacement safety.
- Added explicit guardrails for **special families** and **absent-vs-unsafe** distinctions.
- Added representative QA requirements for **hostile**, **passive**, and **special-case** entities per era.
- Added negative-test requirements for preserved exclusions so broad registries cannot silently regress.

## Work Objectives
### Core Objective
Replace ad hoc per-version support declarations with explicit, tested version-local support allowlists that include every entity base type proven safe for spawn and attach/replacement on that version, while preserving permanent exclusions for unsupported or inherently complex families.

### Deliverables
- Explicit support matrix fixtures and regression tests for all six version factories.
- Updated version-local factories with aligned `supports()`, metadata registry construction, and attach gating.
- Expanded version-local replacement/attach tests covering new representative entity families.
- Sample-plugin scenario registration and artifact coverage for hostile, passive, and special-case families.
- Final green full-suite evidence from `mvnw.cmd clean test`.

### Definition of Done (verifiable conditions with commands)
- `mvnw.cmd clean test` exits with `BUILD SUCCESS`.
- Version-local tests assert the final supported and excluded base types for each affected factory.
- Newly supported entities pass both fresh-spawn and attach/replacement regression tests on their version.
- Preserved exclusions fail predictably with explicit negative assertions.
- Representative scenario artifacts exist for selected legacy and modern servers under `.sisyphus/evidence/` and/or `target/entity-matrix/...`.

### Must Have
- Permanent cross-version exclusions for `UNKNOWN`, `PLAYER`, `WEATHER`, and `COMPLEX_PART`.
- Explicit distinction between:
  - entity absent from the Bukkit/API surface for that version
  - entity present in Bukkit but unsafe for spawn/attach/replacement
- One explicit support allowlist or equivalent explicit include/exclude contract per version factory.
- Attach/replacement parity for every newly included entity.
- Negative tests for preserved exclusions and special-case families that remain out of scope.

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- Do not treat raw `CustomEntityBaseType.values()` iteration as sufficient evidence of support.
- Do not unify packet/metadata transport behavior across incompatible eras as part of this task.
- Do not count spawn-only entities as supported.
- Do not silently inherit `1.21.11` exclusions or inclusions into older eras without version-local proof.
- Do not leave zombie-only assumptions in runtime tests or sample-plugin scenarios once broader support is claimed.

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: tests-after using existing JUnit 5 + Mockito infrastructure, plus representative scenario execution in `test-plugin`
- QA policy: Every task includes agent-executed happy-path and preserved-exclusion scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: support-matrix foundations + scenario fixtures + zombie-only era expansions (`T1`-`T5`)

Wave 2: broad-era hardening + cross-version gates + final cleanup (`T6`-`T10`)

### Dependency Matrix (full, all tasks)
- `T1`: none
- `T2`: blocked by `T1`
- `T3`: blocked by `T1`
- `T4`: blocked by `T1`
- `T5`: blocked by `T1`
- `T6`: blocked by `T1`
- `T7`: blocked by `T1`
- `T8`: blocked by `T1`
- `T9`: blocked by `T2`, `T3`, `T4`, `T5`, `T6`, `T7`, `T8`
- `T10`: blocked by `T9`

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 -> 5 tasks -> `deep` x3, `unspecified-high` x2
- Wave 2 -> 5 tasks -> `deep` x3, `unspecified-high` x2

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Establish the explicit per-version support matrix contract

  **What to do**: Create a single deterministic support-matrix test pattern that every version factory must satisfy. The matrix must classify each candidate `CustomEntityBaseType` as either included or excluded with rationale. Use these permanent rules everywhere: always exclude `UNKNOWN`, `PLAYER`, `WEATHER`, and `COMPLEX_PART`; exclude any entity whose Bukkit `EntityType` is absent for that version; only include entities that satisfy spawn + attach/replacement safety on that version. Add or update shared test fixtures so every affected version test can assert both positive support and preserved exclusions without re-deriving the rules ad hoc.
  **Must NOT do**: Do not use raw enum iteration as proof of support. Do not encode packet/metadata transport changes into this task.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this sets the contract that all later version tasks must implement consistently.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - scenario/browser tooling is not needed for this foundational test contract.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: [2, 3, 4, 5, 6, 7, 8] | Blocked By: []

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/CustomEntityBaseType.java:35-209` - shared union of logical base types; use this as the candidate source, not as automatic support evidence.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2.java:626-667` - current zombie-only registry + attach gate that must be generalized into an explicit contract.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java:967-1017` - existing broad-with-exclusions pattern, including the explicit `COW` exclusion.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/RuntimeSupportMatrixTest.java:41-56` - current runtime release-gate pattern requiring unit suites and matrix evidence.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2Test.java:66-67` - existing zombie/cow assertion shape to expand into a richer matrix.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5Test.java:66-70` - same zombie-only assertion shape in the mid-era family.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1Test.java:54-58` - same zombie-only assertion shape in the modern-transition family.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2Test.java:65-71` - existing modern exclusion assertion for `COW`.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=RuntimeSupportMatrixTest,EntityFactoryV1_13_2Test,EntityFactoryV1_16_5Test,EntityFactoryV1_17_1Test,EntityFactoryV1_19_2Test` exits with `BUILD SUCCESS`.
  - [ ] Every affected version test now contains explicit assertions for included entities, permanent exclusions, and version-local preserved exclusions.
  - [ ] The support matrix fixture makes `supports()` overclaims a test failure.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Cross-version support matrix contract passes
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=RuntimeSupportMatrixTest,EntityFactoryV1_13_2Test,EntityFactoryV1_16_5Test,EntityFactoryV1_17_1Test,EntityFactoryV1_19_2Test`; capture surefire output and the updated test files.
    Expected: BUILD SUCCESS; no version test relies on zombie-only assertions alone; matrix/release-gate suites stay green.
    Evidence: .sisyphus/evidence/task-1-support-matrix.txt

  Scenario: Preserved exclusions remain explicit
    Tool: Bash
    Steps: Re-run the same focused suite and inspect assertions covering `PLAYER`, `WEATHER`, `COMPLEX_PART`, and at least one version-local exclusion such as `COW` on `1.19.2`.
    Expected: Negative-path assertions pass because exclusions are asserted intentionally, not because support is undefined.
    Evidence: .sisyphus/evidence/task-1-support-matrix-error.txt
  ```

  **Commit**: YES | Message: `test(entity-support): codify version support matrix contract` | Files: [`versions/runtime/src/test/java/...`, `versions/*/src/test/java/.../EntityFactoryV1_*Test.java`]

- [x] 2. Add representative scenario families to the sample-plugin matrix

  **What to do**: Extend the sample-plugin scenario registry so verification no longer depends only on zombie-centric scenarios. Add deterministic family scenarios for a passive entity and a special-case entity without coupling the plan to one exact entity across all versions. Use these fixed priority lists when selecting the representative entity for a scenario run: passive family `[SHEEP, PIG, CHICKEN, COW]`; special-case family `[ARMOR_STAND, ITEM_FRAME, MINECART, FALLING_BLOCK]`. Register new scenario IDs `deathfx-passive-family` and `viewer-cycle-special-family`, and ensure the artifact/assertion contract records the chosen base type plus pass/fail counts.
  **Must NOT do**: Do not remove existing zombie scenarios. Do not add manual in-game-only validation.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: this is test-plugin and evidence-contract work with multiple touched files but limited architecture risk.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - the sample plugin is verified through tests/artifacts, not browser automation.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [9, 10] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java:28-61` - current scenario IDs and assertion-key contract.
  - Pattern: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java:65-94` - artifact directory scheme and JSON assertion output contract.
  - Pattern: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java` - scenario registration/selection implementation point.
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/CustomEntityBaseType.java:122-159` - hostile/passive/special-case candidates already defined in the shared enum.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityScenarioRegistrationTest` exits with `BUILD SUCCESS`.
  - [ ] `EntityScenarioRegistrationTest` asserts the new scenario IDs and assertion keys, including a field for the selected representative base type.
  - [ ] Artifact output continues to use `target/entity-matrix/<server>/<scenario>/trace.json` and `assertions.json`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Scenario registry exposes non-zombie family coverage
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityScenarioRegistrationTest`; read the resulting assertions for the registered scenario IDs.
    Expected: BUILD SUCCESS; registry includes `deathfx-passive-family` and `viewer-cycle-special-family`; assertion keys include the selected representative base type.
    Evidence: .sisyphus/evidence/task-2-scenarios.txt

  Scenario: Artifact contract remains stable
    Tool: Bash
    Steps: Execute the artifact-writer test path from `EntityScenarioRegistrationTest` and inspect the generated `target/entity-matrix/unit-test-server/*` outputs.
    Expected: `trace.json` and `assertions.json` are generated with the expected directory scheme and include scenario/server metadata.
    Evidence: .sisyphus/evidence/task-2-scenarios-error.txt
  ```

  **Commit**: YES | Message: `test(entity-matrix): add passive and special-case scenarios` | Files: [`test-plugin/src/main/java/.../EntityDemoService.java`, `test-plugin/src/test/java/.../EntityScenarioRegistrationTest.java`]

- [x] 3. Expand `1.13.2` from zombie-only to an explicit safe allowlist

  **What to do**: Replace the zombie-only `createMetadataRegistry()` and `requireSupportedBaseType()` behavior in `EntityFactoryV1_13_2` with a version-local explicit allowlist that includes every base type proven safe for both fresh spawn and attach/replacement in the `1.13.x` family. Use one source of truth for `supports()`, metadata registry creation, and attach gating so they cannot drift. The inclusion algorithm is fixed: evaluate version-available base types, reject permanent exclusions, reject any type with empty hook specs, unsupported constructor shape, or failing replacement/attach regression behavior, and document every preserved exclusion in tests. When choosing representative positive cases, prefer `ZOMBIE` for hostile, then the first included passive from `[SHEEP, PIG, CHICKEN, COW]`, and then the first included special-case from `[ARMOR_STAND, ITEM_FRAME, MINECART, FALLING_BLOCK]`.
  **Must NOT do**: Do not keep `requireSupportedBaseType()` hard-coded to `ZOMBIE`. Do not claim support for spawn-only entities.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this changes a version-local factory, replacement bridge assumptions, and its tests together.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - this task is NMS factory and unit-test work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2.java:117-167` - current attach path and zombie-only gate entry points.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2.java:617-667` - current `requireMetadata`, `createMetadataRegistry`, `resolveBaseType`, and `requireSupportedBaseType` implementation.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2.java:719-989` - legacy world reference / tracker replacement path that must remain valid for every included entity.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2Test.java:66-67` - current zombie/cow support assertion anchor.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2ReplacementBridgeTest.java` - existing attach/replacement bridge regression coverage to extend.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/CustomEntityBaseType.java:122-159` - common hostile/passive/special-case candidates already in the enum.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityFactoryV1_13_2Test,EntityFactoryV1_13_2ReplacementBridgeTest` exits with `BUILD SUCCESS`.
  - [ ] `EntityFactoryV1_13_2Test` asserts the final included and excluded base types for `1.13.2`, not only zombie support.
  - [ ] At least one newly included non-zombie entity has passing attach/replacement coverage; if no passive or special-case family survives the safety bar, that preserved exclusion is asserted explicitly in the test suite.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.13.2 safe allowlist passes spawn + attach tests
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityFactoryV1_13_2Test,EntityFactoryV1_13_2ReplacementBridgeTest`; capture the final supported/excluded assertions.
    Expected: BUILD SUCCESS; the version advertises more than just `ZOMBIE` if safe entities exist; every included entity is covered by attach/replacement-safe tests.
    Evidence: .sisyphus/evidence/task-3-1-13-2-support.txt

  Scenario: 1.13.2 preserved exclusions still reject unsupported families
    Tool: Bash
    Steps: Execute the same focused suite and inspect negative assertions for permanent exclusions plus any version-local unsafe families.
    Expected: BUILD SUCCESS; unsupported entities fail in a deliberate, asserted way instead of falling through implicitly.
    Evidence: .sisyphus/evidence/task-3-1-13-2-support-error.txt
  ```

  **Commit**: YES | Message: `feat(entities-1.13.2): expand safe attach parity support` | Files: [`versions/1.13.2/src/main/java/.../EntityFactoryV1_13_2.java`, `versions/1.13.2/src/test/java/...`]

- [x] 4. Expand `1.16.5` from zombie-only to an explicit safe allowlist

  **What to do**: Apply the same explicit-allowlist pattern to `EntityFactoryV1_16_5`. Replace the current zombie-only metadata registry and `requireSupportedBaseType()` gate with one version-local support contract reused by `supports()`, `requireMetadata`, and attach preparation. Use the same inclusion algorithm as Task 3 and the same representative priority lists for hostile/passive/special-case families. Extend the replacement/goal-support test coverage only as far as attach/replacement parity remains safe for the included entities.
  **Must NOT do**: Do not preserve zombie-only behavior just because the prior test only asserted `COW` as unsupported. Do not broaden support without replacement-path proof.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this is a version-local factory expansion plus attach/replacement safety work in the 1.14-1.16.5 family.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - no browser/UI work is involved.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java:677-729` - current zombie-only metadata registry, base-type resolution, and attach gate.
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java` - full replacement path for this Paper-era family; update only the support contract, not transport semantics.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5Test.java:66-70` - current zombie-only assertion.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java` - replacement bridge regression anchor.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5GoalSupportTest.java` - existing goal-support regression surface that must not be broken while expanding support.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityFactoryV1_16_5Test,EntityFactoryV1_16_5ReplacementBridgeTest,EntityFactoryV1_16_5GoalSupportTest` exits with `BUILD SUCCESS`.
  - [ ] `EntityFactoryV1_16_5` uses one explicit allowlist/exclusion contract across support checks and attach gating.
  - [ ] At least one non-zombie included entity passes attach/replacement regression coverage, or the tests explicitly preserve the exclusion with rationale if the safety bar rejects every candidate family.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.16.5 allowlist supports only attach-safe entities
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityFactoryV1_16_5Test,EntityFactoryV1_16_5ReplacementBridgeTest,EntityFactoryV1_16_5GoalSupportTest`; capture the support assertions and replacement-path regressions.
    Expected: BUILD SUCCESS; any newly included entity is covered by attach/replacement-safe tests and existing goal-support regressions remain green.
    Evidence: .sisyphus/evidence/task-4-1-16-5-support.txt

  Scenario: 1.16.5 preserved exclusions stay explicit
    Tool: Bash
    Steps: Inspect the focused suite's negative assertions for permanent exclusions plus any version-local unsafe families.
    Expected: BUILD SUCCESS; excluded entities are asserted intentionally and do not rely on implicit fall-through behavior.
    Evidence: .sisyphus/evidence/task-4-1-16-5-support-error.txt
  ```

  **Commit**: YES | Message: `feat(entities-1.16.5): expand safe attach parity support` | Files: [`versions/1.16.5/src/main/java/.../EntityFactoryV1_16_5.java`, `versions/1.16.5/src/test/java/...`]

- [x] 5. Expand `1.17.1` from zombie-only to an explicit safe allowlist

  **What to do**: Apply the same explicit-allowlist contract to `EntityFactoryV1_17_1`, which currently gates support through a zombie-only metadata registry even though the replacement path is already much more sophisticated. Reuse one version-local support contract across `supports()`, `requireMetadata`, and attach preparation. Use the same inclusion algorithm and representative-family priority lists defined in Tasks 1 and 3. Extend `EntityFactoryV1_17_1GoalAttachTest` and the base factory tests so any newly included entity has attach/replacement parity proof.
  **Must NOT do**: Do not assume the existing section-manager callback complexity automatically makes every entity safe. Do not leave goal-attach coverage zombie-only if additional families are included.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task combines modern-transition NMS replacement logic with support expansion and goal-attach testing.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - this is version-local NMS/test work.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1.java:698-722` - current zombie-only metadata registry and support gate.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1.java:767-879` - modern world-reference / level-callback replacement path that must stay safe for included entities.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1.java:986-1058` - section membership and lifecycle-collection rewrite path tied to attach parity.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1Test.java:54-58` - current zombie-only assertion to replace with a real allowlist matrix.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1GoalAttachTest.java` - attach/goal regression anchor to extend beyond zombie-only coverage when safe.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/SpigotVersionAdapterV1_17_1Test.java` - version-adapter contract that must remain green after support expansion.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityFactoryV1_17_1Test,EntityFactoryV1_17_1GoalAttachTest,SpigotVersionAdapterV1_17_1Test` exits with `BUILD SUCCESS`.
  - [ ] `EntityFactoryV1_17_1` uses one explicit allowlist/exclusion contract across support checks and attach gating.
  - [ ] Any newly included entity has attach/goal-attach regression coverage; preserved exclusions remain asserted explicitly.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.17.1 support expansion preserves attach parity
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityFactoryV1_17_1Test,EntityFactoryV1_17_1GoalAttachTest,SpigotVersionAdapterV1_17_1Test`; capture support assertions and attach regressions.
    Expected: BUILD SUCCESS; included entities are covered by attach-safe tests and adapter behavior remains green.
    Evidence: .sisyphus/evidence/task-5-1-17-1-support.txt

  Scenario: 1.17.1 unsafe families remain preserved exclusions
    Tool: Bash
    Steps: Inspect the focused suite's negative assertions for permanent exclusions and any version-local unsafe families.
    Expected: BUILD SUCCESS; exclusions are deliberate, documented, and still enforced.
    Evidence: .sisyphus/evidence/task-5-1-17-1-support-error.txt
  ```

  **Commit**: YES | Message: `feat(entities-1.17.1): expand safe attach parity support` | Files: [`versions/1.17.1/src/main/java/.../EntityFactoryV1_17_1.java`, `versions/1.17.1/src/test/java/...`]

- [x] 6. Harden `1.8.8` broad support into an explicit validated allowlist

  **What to do**: Keep the broad ambition of `1.8.8`, but stop relying on a raw loop over `CustomEntityBaseType.values()` as the support contract. Introduce an explicit allowlist/exclusion contract for `EntityFactoryV1_8_8` and validate it with new factory-level tests, because this version currently has broad registry logic but no direct `EntityFactoryV1_8_8Test` equivalent. Preserve the existing `EntityTypes` mapping repair behavior, but only for entities that survive the same spawn + attach/replacement safety bar used everywhere else.
  **Must NOT do**: Do not assume all currently loop-included entities are safe. Do not change legacy packet transport behavior as part of this task.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this mixes legacy NMS mapping, broad support hardening, and new test creation.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - no browser/UI work is involved.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/EntityFactoryV1_8_8.java:711-747` - current broad `requireMetadata`, `resolveBaseType`, and enum-loop metadata registry.
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/EntityFactoryV1_8_8.java:786-818` - legacy `EntityTypes` mapping repair that must remain intact for included generated types.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/nativebridge/FieldCopySupport.java:44-80` - field-copy behavior that makes attach parity the real inclusion gate.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/SpigotVersionAdapterV1_8_8Test.java` - existing adapter contract anchor.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/LegacyPacketTransportV1_8_to_1_13_2Test.java` - legacy transport regression anchor to keep green.
  - Pattern: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2Test.java` - model the new `1.8.8` factory tests after the newer factory-test shape rather than leaving the version untested.

  **Acceptance Criteria** (agent-executable only):
  - [ ] New factory-level tests for `1.8.8` exist and `mvnw.cmd clean test -Dtest=SpigotVersionAdapterV1_8_8Test,EntityFactoryV1_8_8Test,LegacyPacketTransportV1_8_to_1_13_2Test` exits with `BUILD SUCCESS`.
  - [ ] `EntityFactoryV1_8_8` uses an explicit allowlist/exclusion contract instead of implicit raw enum iteration.
  - [ ] Broad support is preserved only for entities proven attach-safe; preserved exclusions are asserted explicitly.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.8.8 broad support stays validated rather than assumed
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=SpigotVersionAdapterV1_8_8Test,EntityFactoryV1_8_8Test,LegacyPacketTransportV1_8_to_1_13_2Test`; capture the new support assertions.
    Expected: BUILD SUCCESS; the version keeps broad safe coverage but every claim is backed by explicit tests.
    Evidence: .sisyphus/evidence/task-6-1-8-8-support.txt

  Scenario: 1.8.8 preserved exclusions remain intentional
    Tool: Bash
    Steps: Inspect negative assertions for permanent exclusions and any legacy-unsafe families retained out of scope.
    Expected: BUILD SUCCESS; unsupported families are explicitly rejected and no raw-loop overclaim remains.
    Evidence: .sisyphus/evidence/task-6-1-8-8-support-error.txt
  ```

  **Commit**: YES | Message: `refactor(entities-1.8.8): codify validated support allowlist` | Files: [`versions/1.8.8/src/main/java/.../EntityFactoryV1_8_8.java`, `versions/1.8.8/src/test/java/...`]

- [x] 7. Expand and document the `1.19.2` allowlist

  **What to do**: Audit `EntityFactoryV1_19_2` and convert its current broad-with-exclusions behavior into an explicit version-local support contract shared by `supports()`, metadata registry construction, and attach/replacement tests. Preserve `COW` as excluded unless the task proves it attach-safe through focused regression coverage; if `COW` becomes safe, remove the exclusion and add direct regression tests proving why. Apply the same rule to any other currently implicit edge case.
  **Must NOT do**: Do not remove `COW` from the exclusion list without new attach/replacement proof. Do not change Paper chunk-system transport behavior.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task touches the most nuanced modern pre-1.21 support family and its exclusions.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - no browser/UI work is involved.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java:967-1017` - current broad metadata registry with explicit `COW` exclusion and common base-type resolution.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java:1049-1159` - world-reference replacement path that must stay safe for every included entity.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2Test.java:65-71` - current support assertion that explicitly rejects `COW`.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2GoalAttachTest.java` - attach/goal regression anchor.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java` - replacement bridge regression anchor.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/SpigotVersionAdapterV1_19_2Test.java` - adapter contract that must remain green.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityFactoryV1_19_2Test,EntityFactoryV1_19_2GoalAttachTest,EntityFactoryV1_19_2ReplacementBridgeTest,SpigotVersionAdapterV1_19_2Test` exits with `BUILD SUCCESS`.
  - [ ] `EntityFactoryV1_19_2` uses an explicit allowlist/exclusion contract and the tests document why preserved exclusions remain out.
  - [ ] If `COW` is still excluded, the exclusion stays explicit in both production code and tests; if included, new attach/replacement regression tests prove it safe.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.19.2 allowlist aligns with tested attach-safe reality
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityFactoryV1_19_2Test,EntityFactoryV1_19_2GoalAttachTest,EntityFactoryV1_19_2ReplacementBridgeTest,SpigotVersionAdapterV1_19_2Test`; capture support and exclusion assertions.
    Expected: BUILD SUCCESS; the final support set is explicit and attach-safe.
    Evidence: .sisyphus/evidence/task-7-1-19-2-support.txt

  Scenario: 1.19.2 edge-case exclusions remain justified
    Tool: Bash
    Steps: Inspect the focused suite's handling of `COW` and any other preserved exclusions after the task changes.
    Expected: BUILD SUCCESS; the code either proves the entity safe or keeps an explicit documented exclusion.
    Evidence: .sisyphus/evidence/task-7-1-19-2-support-error.txt
  ```

  **Commit**: YES | Message: `feat(entities-1.19.2): align safe allowlist with attach parity` | Files: [`versions/1.19.2/src/main/java/.../EntityFactoryV1_19_2.java`, `versions/1.19.2/src/test/java/...`]

- [x] 8. Harden `1.21.11` broad coverage into an explicit exclusion contract

  **What to do**: Keep `1.21.11` as the broad-coverage benchmark, but convert its implicit broad registry into the same explicit allowlist/exclusion contract used elsewhere so the version cannot overclaim silently. Preserve the existing permanent exclusions and any additional unsafe families proven by tests. Add support assertions and negative tests that cover representative hostile/passive/special-case families and verify that Moonrise/section-manager replacement behavior stays correct for included entities.
  **Must NOT do**: Do not reduce `1.21.11` to zombie-centric coverage. Do not change Moonrise/Paper publication mechanics beyond what is required to align support claims and tests.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this is the broadest modern version and the benchmark for the overall task.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - this task is version-local factory/test work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: [9] | Blocked By: [1]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11.java:712-748` - current `requireMetadata`, `resolveBaseType`, and broad metadata registry.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11.java:809-919` - Moonrise/modern world-reference replacement path that must stay safe for included entities.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11Test.java:47-139` - current section-manager / collection replacement regression coverage.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityGoalAttachV1_21_11Test.java` - attach regression anchor.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11GoalSupportTest.java` - goal-support regression anchor.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/SpigotVersionAdapterV1_21_11Test.java` - adapter contract that must remain green.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=EntityFactoryV1_21_11Test,EntityGoalAttachV1_21_11Test,EntityFactoryV1_21_11GoalSupportTest,SpigotVersionAdapterV1_21_11Test` exits with `BUILD SUCCESS`.
  - [ ] `1.21.11` support is asserted explicitly for representative hostile/passive/special-case families plus preserved exclusions.
  - [ ] Moonrise/section-manager replacement regressions remain green for all included families covered by tests.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.21.11 benchmark coverage stays broad and explicit
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=EntityFactoryV1_21_11Test,EntityGoalAttachV1_21_11Test,EntityFactoryV1_21_11GoalSupportTest,SpigotVersionAdapterV1_21_11Test`; capture support assertions and replacement regressions.
    Expected: BUILD SUCCESS; broad modern support remains intact and is now explicit rather than implied.
    Evidence: .sisyphus/evidence/task-8-1-21-11-support.txt

  Scenario: 1.21.11 preserved exclusions still reject unsupported families
    Tool: Bash
    Steps: Inspect negative assertions for permanent exclusions and any additional version-local unsafe families retained out of scope.
    Expected: BUILD SUCCESS; exclusions remain deliberate and documented.
    Evidence: .sisyphus/evidence/task-8-1-21-11-support-error.txt
  ```

  **Commit**: YES | Message: `refactor(entities-1.21.11): codify explicit support exclusions` | Files: [`versions/1.21.11/src/main/java/.../EntityFactoryV1_21_11.java`, `versions/1.21.11/src/test/java/...`]

- [x] 9. Align runtime and adapter gates with the final per-version support matrix

  **What to do**: Update the shared runtime and adapter-level tests so the repository's claimed support is consistent with the final version-local allowlists. Extend `VersionedEntityPlatformTest`, `RuntimeSupportMatrixTest`, and the affected `SpigotVersionAdapterV1_*Test` suites so they fail when a version claims support for an entity family that its factory/exclusion contract does not cover. The runtime-layer contract must stay profile-based, but the entity-support assertions must now reference the version-local matrices built in Tasks 1-8.
  **Must NOT do**: Do not redesign `RuntimeSupportMatrix` into an entity-type registry. Do not loosen release gates just to make tests pass.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: this task is primarily cross-module regression wiring and release-gate alignment.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - this task is test-wiring and runtime-gate work.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: [10] | Blocked By: [2, 3, 4, 5, 6, 7, 8]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/RuntimeSupportMatrixTest.java:41-56` - release-gate and matrix-evidence requirements that must remain strict.
  - Pattern: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/RuntimeSupportMatrixTest.java:60-153` - representative claimed-profile assertions and fail-fast behavior.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedEntityPlatformTest.java` - platform-level spawn/attach contract that must reflect the new support matrices.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/SpigotVersionAdapterV1_8_8Test.java` - legacy adapter gate.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/SpigotVersionAdapterV1_13_2Test.java` - legacy-transition adapter gate.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/SpigotVersionAdapterV1_16_5Test.java` - mid-era adapter gate.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/SpigotVersionAdapterV1_17_1Test.java` - modern-transition adapter gate.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/SpigotVersionAdapterV1_19_2Test.java` - modern adapter gate.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/SpigotVersionAdapterV1_21_11Test.java` - benchmark adapter gate.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test -Dtest=VersionedEntityPlatformTest,RuntimeSupportMatrixTest,SpigotVersionAdapterV1_8_8Test,SpigotVersionAdapterV1_13_2Test,SpigotVersionAdapterV1_16_5Test,SpigotVersionAdapterV1_17_1Test,SpigotVersionAdapterV1_19_2Test,SpigotVersionAdapterV1_21_11Test` exits with `BUILD SUCCESS`.
  - [ ] Runtime- and adapter-level tests fail if any version claims support for an entity not present in its explicit allowlist.
  - [ ] The release-gate suites still require matrix evidence and do not regress to zombie-only assumptions.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Runtime and adapter gates match the final entity support matrices
    Tool: Bash
    Steps: Run `mvnw.cmd clean test -Dtest=VersionedEntityPlatformTest,RuntimeSupportMatrixTest,SpigotVersionAdapterV1_8_8Test,SpigotVersionAdapterV1_13_2Test,SpigotVersionAdapterV1_16_5Test,SpigotVersionAdapterV1_17_1Test,SpigotVersionAdapterV1_19_2Test,SpigotVersionAdapterV1_21_11Test`; capture the gate output.
    Expected: BUILD SUCCESS; no adapter or runtime gate overclaims unsupported families.
    Evidence: .sisyphus/evidence/task-9-runtime-gates.txt

  Scenario: Overclaim prevention remains enforced
    Tool: Bash
    Steps: Inspect the focused suite's negative assertions/fail-fast messages related to undeclared or partially wired support.
    Expected: The release-gate logic still fails fast for unsupported or partially wired claims instead of silently passing.
    Evidence: .sisyphus/evidence/task-9-runtime-gates-error.txt
  ```

  **Commit**: YES | Message: `test(entity-support): align runtime gates with version matrices` | Files: [`versions/runtime/src/test/java/...`, `versions/*/src/test/java/.../SpigotVersionAdapterV1_*Test.java`]

- [x] 10. Produce final full-suite and representative scenario evidence

  **What to do**: Run the full repository verification and generate representative scenario evidence after the support matrices and gates are aligned. Use deterministic representative-version selection: for the legacy representative, choose the first version in `[1.8.8, 1.13.2, 1.16.5, 1.17.1]` whose final allowlist includes one hostile, one passive, and one special-case family; for the modern representative, choose the first version in `[1.21.11, 1.19.2]` meeting the same rule. Execute `attach-existing-zombie`, `deathfx-passive-family`, and `viewer-cycle-special-family` through an automated test-plugin harness (extend existing MockBukkit/integration coverage if needed) and persist the resulting artifacts.
  **Must NOT do**: Do not rely on manual in-game checks. Do not mark the work complete if only unit tests pass but scenario evidence is missing.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: this is final verification orchestration, test harness completion, and evidence collection.
  - Skills: `[]` - no extra skill is required.
  - Omitted: `['/playwright']` - verification is server/test-harness based, not browser based.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: [] | Blocked By: [9]

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityScenarioRegistrationTest.java:28-94` - scenario IDs, assertion keys, and artifact contract.
  - Test: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/AutoDiscoveryIntegrationTest.java` - existing test-plugin integration anchor with MockBukkit.
  - Test: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/VersionsSharedDependencyIntegrationTest.java` - shared-dependency/integration anchor for test-plugin verification.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/versions/runtime/RuntimeSupportMatrixTest.java:41-56` - release gate still requires matrix evidence.
  - Pattern: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java` - scenario execution entry point to extend for automated family scenarios.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `mvnw.cmd clean test` exits with `BUILD SUCCESS`.
  - [ ] Automated scenario evidence exists for one legacy representative server and one modern representative server, each covering `attach-existing-zombie`, `deathfx-passive-family`, and `viewer-cycle-special-family`.
  - [ ] Scenario artifacts record the selected representative base type and pass/fail counts, and preserved exclusions are explicit if a family could not be represented on a candidate version.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Full suite and representative scenario matrix complete successfully
    Tool: Bash
    Steps: Run `mvnw.cmd clean test`; then inspect `target/entity-matrix/<legacy-server>/...` and `target/entity-matrix/<modern-server>/...` for `attach-existing-zombie`, `deathfx-passive-family`, and `viewer-cycle-special-family` outputs.
    Expected: BUILD SUCCESS; both representative servers produce complete artifact sets with the selected base type recorded.
    Evidence: .sisyphus/evidence/task-10-full-suite.txt

  Scenario: Missing family support is reported explicitly, not silently skipped
    Tool: Bash
    Steps: Inspect the scenario assertions for the chosen representative versions and any rejected candidate versions considered during deterministic selection.
    Expected: Every skipped candidate or preserved exclusion is explicit in assertions/artifacts; there are no silent omissions.
    Evidence: .sisyphus/evidence/task-10-full-suite-error.txt
  ```

  **Commit**: YES | Message: `test(entity-support): finalize representative scenario evidence` | Files: [`test-plugin/src/test/java/...`, `test-plugin/src/main/java/...`]

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
- [x] F2. Code Quality Review — unspecified-high
- [x] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [x] F4. Scope Fidelity Check — deep

## Commit Strategy
- Create one conventional commit per completed task.
- Keep support-matrix/test-only work under `test(...)` or `refactor(...)` prefixes.
- Keep version-specific factory changes under `feat(entities-<version>)` unless the task only tightens exclusions, in which case use `fix(...)`.
- Do not combine multiple version-era implementation tasks into one commit.

## Success Criteria
- Every `EntityFactoryV1_*` under `versions/*/src/main/java/**/entity/` advertises support through an explicit tested contract rather than implicit assumptions.
- Every newly supported entity is verified for both fresh spawn and attach/replacement on its version.
- Every preserved exclusion is tested and documented.
- Runtime and sample-plugin verification no longer rely on zombie-only evidence to justify broad support claims.
