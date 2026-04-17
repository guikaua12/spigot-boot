# Entity Goals / Pathfinder API for Versions 1.8.8+

## TL;DR
> **Summary**: Add a version-agnostic goals API to `versions/api` and `versions/runtime` that lets users configure goals during custom-entity creation and mutate goals on attached existing entities, while adapting internally to NMS goal-selector differences from `1.8.8` through `1.21.11`.
> **Deliverables**:
> - public goals API in `versions/api`
> - runtime goal management/mutation queue in `versions/runtime`
> - exact-version support across `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11`
> - automated contract/regression tests for builder-time and attached-entity flows
> **Effort**: XL
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 2/3/4 → 5 → 6/7/8/9 → 10/11

## Context
### Original Request
Implement a goals/pathfinder API in the `@versions/` module so users can modify entity pathfinding/goals both when creating custom entities and when attaching existing entities, with support for every version `1.8.8+`.

### Interview Summary
- v1 must support both **normal goals** and **target goals**.
- v1 must support both **builder-time configuration** and **runtime mutation** after spawn/attach.
- attached existing entities must support **adding/removing vanilla goals** and **adding/removing custom goals**.
- vanilla-goal removal in v1 must be **by stable goal type/key**, not arbitrary low-level selector inspection.
- test strategy is **tests-after** using the existing `JUnit 5` / `Mockito` / `MockBukkit` infrastructure.

### Metis Review (gaps addressed)
- froze ownership model: attached entities manage only library-recognized goals; unknown third-party goals remain untouched.
- froze removal semantics: remove-by-key removes **all recognized matches** in the requested selector and returns a count/result; unknown unmanaged goals are never touched.
- froze unsupported-operation behavior: unsupported entity/base-type/version/key combinations fail fast with explicit goal-specific exceptions; removing a supported key that is absent returns zero removals.
- froze mutation timing: runtime mutations are main-thread API calls that enqueue internal selector updates for the next safe tick boundary.

## Work Objectives
### Core Objective
Create a stable public goals API for the versions stack that works the same way for spawned custom entities and attached existing entities, while isolating all NMS/pathfinder differences behind runtime/version-family adapters.

### Deliverables
- New `versions/api` goal contracts and builders.
- New `versions/runtime` goal ownership, queueing, selector synchronization, and family selection infrastructure.
- Version-local goal bridges for `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11`.
- Public contract tests and version-family regression tests.

### Definition of Done (verifiable conditions with commands)
- `mvnw.cmd clean test` passes from repository root.
- spawned custom zombie entities can receive one normal goal and one target goal through builder-time configuration and remove both by stable key in automated tests.
- attached existing zombie entities can remove a recognized vanilla goal by stable key, preserve unknown/unmanaged goals, and add/remove a custom goal in automated tests.
- unsupported entities such as `ArmorStand` fail with the documented goals exception contract in automated tests.
- queued runtime mutations are verified as invisible before the safe tick and visible after the next tick in automated tests.

### Must Have
- same public model for spawn-time and attach-time usage
- support for normal + target selectors
- stable v1 vanilla-goal catalog
- custom-goal add/remove support
- explicit ownership semantics for attached entities
- exact-version support for every versions module currently present in the repo

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- no Brain/memory/sensor editing in v1
- no arbitrary inspection/reordering/removal of unknown unmanaged goals
- no support promise for non-AI entities
- no async mutation contract; callers must invoke runtime API on the main thread
- no Paper-only public API surface; Paper can be an internal backend only
- no silent no-op for unsupported add/remove operations

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: tests-after using `JUnit 5`, `Mockito`, and `MockBukkit`
- QA policy: Every task has agent-executed scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: public contract + builder/runtime foundations (`1-4`)
Wave 2: internal version seam + version-family implementations (`5-9`)
Wave 3: contract/regression tests + exact-version verification + sample verification surface (`10-12`)

### Dependency Matrix (full, all tasks)
- `1` blocks `2`, `3`, `4`, `5`, `10`, `11`, `12`
- `2` blocks `10`, `11`, `12`
- `3` blocks `4`, `10`, `11`, `12`
- `4` blocks `5`, `6`, `7`, `8`, `9`, `10`, `11`, `12`
- `5` blocks `6`, `7`, `8`, `9`
- `6`, `7`, `8`, `9` block `10`, `11`, `12`
- `10` blocks `11`, `12`

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 4 tasks → `deep`, `unspecified-high`
- Wave 2 → 5 tasks → `deep`, `ultrabrain`
- Wave 3 → 3 tasks → `unspecified-high`, `writing`

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Define the public goals contract and freeze the v1 catalog

  **What to do**: Add a new `versions/api/.../goal/` package and define the exact public surface for v1: `GoalSelectorType` (`NORMAL`, `TARGET`), `VanillaGoalKey` with the supported catalog (`FLOAT`, `MELEE_ATTACK`, `RANDOM_STROLL_LAND`, `LOOK_AT_PLAYER`, `RANDOM_LOOK_AROUND`, `HURT_BY_TARGET`, `NEAREST_ATTACKABLE_TARGET`), `CustomGoalKey`, immutable `GoalProfile<T extends Entity>`, `GoalProfile.Builder<T>`, immutable goal-add specs for vanilla/custom goals, and goal-specific exceptions/results. Freeze these semantics: attached entities manage only library-recognized keys; remove-by-key removes all recognized matches in the chosen selector; adding the same managed key in the same selector replaces the prior managed entry; removing a supported-but-absent key returns zero removals; unsupported entity/version/key additions fail fast with explicit goal exceptions.
  **Must NOT do**: Do not expose NMS classes, Paper classes, Brain APIs, raw selector internals, or arbitrary unknown-goal inspection/reorder APIs.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: public API design must be stable across all exact-version modules.
  - Skills: `[]` - no extra skill required.
  - Omitted: `git-master` - no git work should happen during implementation.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: `2, 3, 4, 5, 10, 11` | Blocked By: none

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/EntityTemplate.java:38-246` - immutable builder style to mirror for `GoalProfile`.
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/SpawnBuilder.java:40-168` - builder-time customization style for spawn flows.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/ControlledEntity.java:34-133` - existing live-handle API that will gain goal access.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/spi/VersionAdapter.java:41-102` - current cross-version seam the new goals support must integrate with.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/CustomEntityBaseType.java:42-320` - union enum proving unsupported/non-AI types must be explicitly filtered.
  - External: `https://github.com/PaperMC/Paper/blob/37e02efd956478fc93a9516ee232c8d379dc30cb/paper-api/src/main/java/com/destroystokyo/paper/entity/Pathfinder.java` - modern Paper pathfinder is internal inspiration only, not the public API shape.

  **Acceptance Criteria** (agent-executable only):
  - [ ] New goal API types compile with Javadocs and no raw NMS/Paper imports in `versions/api` goal package.
  - [ ] The v1 vanilla-goal catalog is explicitly encoded in source, not implied by docs/tests.
  - [ ] The public contract documents and encodes duplicate-add, remove-all, unsupported-operation, and supported-but-absent removal semantics.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Goal API compiles cleanly
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/api -am test`
    Expected: BUILD SUCCESS with the new goal package compiled
    Evidence: .sisyphus/evidence/task-1-public-goals-contract.txt

  Scenario: Public API stays implementation-agnostic
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/api -am test` and inspect failures if any mention `net.minecraft` or `com.destroystokyo.paper` imports in the new API package
    Expected: No new API class requires NMS or Paper types
    Evidence: .sisyphus/evidence/task-1-public-goals-contract-imports.txt
  ```

  **Commit**: NO | Message: `feat(versions-api): add entity goals contract` | Files: `versions/api/src/main/java/**`

- [x] 2. Integrate goals into template and spawn-builder flows

  **What to do**: Extend `EntityTemplate.Builder` and `SpawnBuilder` so custom-entity creation can define a `GoalProfile` at template time and override/append it at spawn time. The builder contract must preserve existing builder immutability and current spawn overload behavior in `VersionedPlatform.spawn(...)`. The chosen API must make both normal and target goals configurable, and it must pass goal data through immutable template/spawn structures rather than mutable shared state.
  **Must NOT do**: Do not overload existing generic metadata `data(String,Object)` as the primary long-term goal API; goal configuration must have first-class typed methods.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: modifies the two public builder surfaces that downstream plugins will use.
  - Skills: `[]` - no extra skill required.
  - Omitted: `frontend-ui-ux` - not applicable.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: `10, 11` | Blocked By: `1`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/EntityTemplate.java:126-246` - builder creation and immutable field capture pattern.
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/SpawnBuilder.java:51-168` - current spawn-time override path.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:393-455` - inline customizer path that must continue working.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/EntityInitializer.java:34-57` - preserve existing builder defaults/no-op conventions.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `EntityTemplate.Builder` can define both normal and target goals without breaking existing controller/network-controller/initializer behavior.
  - [ ] `SpawnBuilder` can override or extend goal configuration for a one-off spawn and still produce immutable `template()` and `spawnOptions()` outputs.
  - [ ] Existing spawn overloads in `VersionedPlatform` remain source-compatible.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Builder-time goal profile compiles through spawn API
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/runtime -am test`
    Expected: Tests compile with new builder methods and existing spawn overloads unchanged
    Evidence: .sisyphus/evidence/task-2-builder-goal-flow.txt

  Scenario: Spawn-time override preserves immutability
    Tool: Bash
    Steps: run the targeted builder/runtime tests added for goal profiles via `mvnw.cmd -pl versions/runtime -am -Dtest="*GoalProfile*,*SpawnBuilder*" test`
    Expected: Template state stays immutable and spawn-time overrides produce expected merged profile
    Evidence: .sisyphus/evidence/task-2-builder-goal-flow-immutability.txt
  ```

  **Commit**: NO | Message: `feat(versions-api): add goal configuration to templates and spawns` | Files: `versions/api/src/main/java/**`, `versions/runtime/src/test/java/**`

- [x] 3. Add runtime goal access to `ControlledEntity` with explicit mutation semantics

  **What to do**: Extend the live controlled-entity surface with a dedicated goal manager API, reachable from both spawned and attached entities. Define exact runtime operations for v1: add vanilla goal, remove vanilla goals by stable key, add custom goal, remove custom goals by key, clear managed goals for one selector, and inspect the managed recognized set only. Freeze semantics: runtime mutation API must be called on the main thread, operations enqueue internal selector changes for the next safe tick, and the API exposes deterministic results/counts rather than raw selector handles.
  **Must NOT do**: Do not expose `List<Object>`/reflection handles/raw selector collections to plugin code; do not promise inspection of unknown third-party goals.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this is the main live-entity developer API and must align with attach semantics.
  - Skills: `[]` - no extra skill required.
  - Omitted: `playwright` - not a browser workflow.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: `4, 10, 11` | Blocked By: `1`

  **References** (executor has NO interview context - be exhaustive):
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/ControlledEntity.java:34-133` - live entity contract to extend.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:255-303` - attached-entity lookup and lifecycle binding flow.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:458-513` - spawned entity registration and attached-entity registry usage.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Spawned and attached `ControlledEntity` instances expose the same goal-manager contract.
  - [ ] The runtime contract encodes main-thread-only calls plus next-safe-tick visibility for actual selector changes.
  - [ ] Managed inspection returns only library-recognized/managed entries, not arbitrary unknown goals.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Controlled entity exposes a goal manager for spawned and attached flows
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/runtime -am -Dtest="*ControlledEntity*Goal*,*VersionedPlatform*Goal*" test`
    Expected: Both spawn and attach contract tests pass against the same API surface
    Evidence: .sisyphus/evidence/task-3-controlled-entity-goals.txt

  Scenario: Runtime mutation stays queued until safe tick
    Tool: Bash
    Steps: run the dedicated queue-semantics test via `mvnw.cmd -pl versions/runtime -am -Dtest=*GoalMutationQueue* test`
    Expected: Mutation not visible before the queued tick and visible after one safe tick
    Evidence: .sisyphus/evidence/task-3-controlled-entity-goals-queue.txt
  ```

  **Commit**: NO | Message: `feat(versions-api): add controlled entity goal manager` | Files: `versions/api/src/main/java/**`, `versions/runtime/src/test/java/**`

- [x] 4. Build the shared runtime goal-management core

  **What to do**: Implement the shared runtime layer in `versions/runtime` that owns goal profiles, managed/unmanaged ownership metadata, attachment snapshots, queued mutation execution, selector-type routing, and version-family resolution. This layer must preserve unknown third-party goals on attached entities, track only recognized managed entries, and provide one internal orchestration path reused by both spawn and attach flows.
  **Must NOT do**: Do not duplicate queue/state logic inside exact-version factories; do not allow unknown-goal removal as a fallback shortcut.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: central runtime orchestration and lifecycle ownership are the highest-risk integration point.
  - Skills: `[]` - no extra skill required.
  - Omitted: `refactor` - this is new runtime infrastructure, not a generic refactor pass.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: `5, 6, 7, 8, 9, 10, 11` | Blocked By: `1, 3`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:72-123` - current runtime composition root.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:263-303` - attach lifecycle entry.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:458-513` - fresh-spawn lifecycle entry and registry bookkeeping.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/strategy/EntityStrategyBundle.java:34-94` - composition-root style for strategy selection.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/strategy/VersionEntrypoint.java:41-92` - shared runtime seam exact versions implement behind the API.

  **Acceptance Criteria** (agent-executable only):
  - [ ] One shared runtime path exists for goal ownership and queued mutation handling.
  - [ ] Attached entities snapshot recognized vanilla goals without disturbing unknown goals.
  - [ ] Spawned entities and attached entities both resolve through the same runtime goal-management orchestration.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Shared runtime goal orchestration passes contract tests
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/runtime -am test`
    Expected: Runtime tests pass with one shared goal-management path for spawn and attach
    Evidence: .sisyphus/evidence/task-4-runtime-goal-core.txt

  Scenario: Unknown attached goals remain untouched
    Tool: Bash
    Steps: run the dedicated attach-ownership test via `mvnw.cmd -pl versions/runtime -am -Dtest=*AttachedEntity*Goal* test`
    Expected: Recognized goals are managed; unknown goals remain present and unmodified
    Evidence: .sisyphus/evidence/task-4-runtime-goal-core-ownership.txt
  ```

  **Commit**: YES | Message: `feat(versions-runtime): add shared entity goal management core` | Files: `versions/api/src/main/java/**`, `versions/runtime/src/main/java/**`, `versions/runtime/src/test/java/**`

- [x] 5. Extend the cross-version SPI and strategy selection for goals support

  **What to do**: Add the exact internal seam needed for goals support to the versions SPI/runtime strategy system. Extend `VersionAdapter` and `VersionEntrypoint` through goal-support provider interfaces or adjacent runtime bridges so exact-version modules can advertise supported vanilla-goal mappings, attach snapshot logic, and selector mutation executors without changing the public API shape. Add strategy/family selection for goals alongside the current spawn/replacement selection pattern.
  **Must NOT do**: Do not shove exact-version selector reflection directly into `VersionedPlatform`; do not create a separate public API per server flavor.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task defines how all exact-version modules plug into the shared runtime.
  - Skills: `[]` - no extra skill required.
  - Omitted: `ai-slop-remover` - not a cleanup task.

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: `6, 7, 8, 9` | Blocked By: `1, 4`

  **References** (executor has NO interview context - be exhaustive):
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/spi/VersionAdapter.java:41-102` - existing spawn/attach SPI boundary.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/strategy/VersionEntrypoint.java:41-92` - existing exact-version runtime seam.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/strategy/EntityStrategyBundle.java:34-94` - current composition-root design to mirror for goals support.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:117-123` - current capability/binding/strategy resolution path.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11.java:115-147` - version-local capabilities/bindings/strategy selection pattern.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The shared runtime can resolve goals support from exact-version modules without using public API conditionals.
  - [ ] Goals support selection follows the existing strategy-family composition style already used for spawn/replacement.
  - [ ] Exact-version modules can declare supported keys and mutation executors independently from the public API.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Shared SPI compiles across all version modules
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions -am test`
    Expected: All existing exact-version modules compile against the new goals seam
    Evidence: .sisyphus/evidence/task-5-goal-spi-seam.txt

  Scenario: Runtime selection resolves a goals backend per version family
    Tool: Bash
    Steps: run the strategy-selection tests via `mvnw.cmd -pl versions/runtime -am -Dtest="*Goal*Selector*,*Goal*Strategy*" test`
    Expected: Runtime chooses the expected goals backend for legacy, transitional, modern, and latest families
    Evidence: .sisyphus/evidence/task-5-goal-spi-seam-selection.txt
  ```

  **Commit**: NO | Message: `feat(versions-runtime): add goals SPI bridge` | Files: `versions/api/src/main/java/**`, `versions/runtime/src/main/java/**`

- [x] 6. Implement legacy-family goal support for `1.8.8` and `1.13.2`

  **What to do**: Implement the legacy selector backend for `versions/1.8.8` and `versions/1.13.2`, including recognized vanilla-goal key mapping, attach snapshot of recognized goals, add/remove operations for the v1 catalog, and custom-goal wrapping. Keep the public contract identical to newer versions while isolating all legacy selector details in the family backend and exact-version glue.
  **Must NOT do**: Do not expand the v1 catalog beyond the seven frozen keys; do not attempt arbitrary unknown-goal introspection in legacy modules.

  **Recommended Agent Profile**:
  - Category: `ultrabrain` - Reason: `1.8.8`/`1.13.2` legacy selector handling is the most fragile cross-version family.
  - Skills: `[]` - no extra skill required.
  - Omitted: `writing` - this is implementation-heavy adapter work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: `10, 11` | Blocked By: `5`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/EntityFactoryV1_8_8.java` - exact-version legacy entrypoint to extend.
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/SpigotVersionAdapterV1_8_8.java` - legacy adapter boundary.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2.java` - transition-edge exact-version entrypoint.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityHookBinderV1_13_2.java` - existing legacy/mid hook-binder example.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/SpigotVersionAdapterV1_8_8Test.java:48-154` - current legacy metadata/provider test style.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2ReplacementBridgeTest.java` - attach/replacement test pattern to extend.
  - External: `https://github.com/squallblade/Spigot/blob/master/src/main/java/net/minecraft/server/PathfinderGoalSelector.java` - legacy selector grounding only.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `1.8.8` and `1.13.2` support add/remove for the frozen vanilla-goal catalog and custom goals through the shared runtime contract.
  - [ ] Attached zombies on both exact versions can remove recognized vanilla keys while preserving unknown/unmanaged goals.
  - [ ] Legacy family tests pass without changing the public API contract used by newer versions.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Legacy family regression tests pass
    Tool: Bash
    Steps: run `mvnw.cmd -pl "versions/1.8.8,versions/1.13.2" -am test`
    Expected: Both exact-version modules pass goal support tests and existing adapter tests
    Evidence: .sisyphus/evidence/task-6-legacy-goals.txt

  Scenario: Legacy attached-zombie goal removal preserves unknown goals
    Tool: Bash
    Steps: run the dedicated legacy attach tests via `mvnw.cmd -pl "versions/1.8.8,versions/1.13.2" -am -Dtest="*Goal*Attach*,*ReplacementBridge*" test`
    Expected: Recognized vanilla goal removed by key; unknown goal remains untouched
    Evidence: .sisyphus/evidence/task-6-legacy-goals-attach.txt
  ```

  **Commit**: NO | Message: `feat(versions-legacy): add legacy entity goals support` | Files: `versions/1.8.8/src/main/java/**`, `versions/1.13.2/src/main/java/**`, `versions/1.8.8/src/test/java/**`, `versions/1.13.2/src/test/java/**`

- [x] 7. Implement transitional goal support for `1.16.5`

  **What to do**: Add the transitional pre-mojmap backend for `1.16.5`, wiring the exact same frozen key catalog and mutation semantics into the `1.16.5` exact-version entrypoint and tests. Reuse the shared runtime contract from Tasks 4-5 instead of embedding selector ownership directly in the factory.
  **Must NOT do**: Do not special-case `1.16.5` in the public API; do not bypass the shared runtime goal queue.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: `1.16.5` is the bridge between legacy and modern internals and must prove family abstraction quality.
  - Skills: `[]` - no extra skill required.
  - Omitted: `playwright` - not needed.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: `10, 11` | Blocked By: `5`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5.java` - exact-version entrypoint.
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/SpigotVersionAdapterV1_16_5.java` - adapter surface.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5Test.java:57-158` - current factory test pattern using zombie fixtures.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5ReplacementBridgeTest.java` - attach/replacement bridge pattern to extend.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `1.16.5` passes the same public contract tests as other versions for builder-time and attached-entity goal mutation.
  - [ ] Zombie-based tests verify both normal and target goals through the shared runtime contract.
  - [ ] Transitional-family code remains behind exact-version/runtime support classes rather than leaking into `versions/api`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Transitional family tests pass on 1.16.5
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/1.16.5 -am test`
    Expected: 1.16.5 exact-version tests pass, including new goal support tests
    Evidence: .sisyphus/evidence/task-7-1165-goals.txt

  Scenario: Zombie builder + attach goal mutations work on 1.16.5
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/1.16.5 -am -Dtest="*Goal*,*EntityFactoryV1_16_5*" test`
    Expected: builder-time and attached-entity mutations both pass with identical semantics
    Evidence: .sisyphus/evidence/task-7-1165-goals-zombie.txt
  ```

  **Commit**: NO | Message: `feat(versions-1.16.5): add entity goals support` | Files: `versions/1.16.5/src/main/java/**`, `versions/1.16.5/src/test/java/**`

- [x] 8. Implement modern goal support for `1.17.1` and `1.19.2`

  **What to do**: Implement the modern mojang-mapped family backend across `1.17.1` and `1.19.2`, using the same frozen key catalog, attach ownership model, and queue semantics. Keep modern exact-version code responsible only for exact mappings and hook integration, while the shared runtime owns public behavior.
  **Must NOT do**: Do not add separate semantics for `1.17.1` vs `1.19.2`; differences must stay internal to exact-version glue or family support classes.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this family proves the contract across multiple mojang-mapped exact versions.
  - Skills: `[]` - no extra skill required.
  - Omitted: `writing` - not documentation work.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: `10, 11` | Blocked By: `5`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1.java` - early mojang-mapped exact-version entrypoint.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityHookBinderV1_17_1.java` - hook-binding example.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2.java` - later modern exact-version entrypoint.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityHookBinderV1_19_2.java` - hook-binding example.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1Test.java` - exact-version factory test pattern.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java:59-158` - attach/replacement bridge test pattern.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `1.17.1` and `1.19.2` pass the same add/remove builder/attach goal contract as legacy and transitional versions.
  - [ ] Recognized vanilla-goal removal by stable key works in both normal and target selectors for zombie fixtures.
  - [ ] Unknown goals remain untouched after attach-time mutations.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Modern family regression tests pass
    Tool: Bash
    Steps: run `mvnw.cmd -pl "versions/1.17.1,versions/1.19.2" -am test`
    Expected: Both modules pass goal support tests and existing exact-version tests
    Evidence: .sisyphus/evidence/task-8-modern-goals.txt

  Scenario: Attach semantics preserve unmanaged goals on modern family
    Tool: Bash
    Steps: run `mvnw.cmd -pl "versions/1.17.1,versions/1.19.2" -am -Dtest="*Goal*Attach*,*ReplacementBridge*" test`
    Expected: Recognized key removal succeeds and unknown goals remain untouched
    Evidence: .sisyphus/evidence/task-8-modern-goals-attach.txt
  ```

  **Commit**: YES | Message: `feat(versions): add cross-version entity goals backends` | Files: `versions/runtime/src/main/java/**`, `versions/1.17.1/src/main/java/**`, `versions/1.19.2/src/main/java/**`, `versions/1.8.8/src/main/java/**`, `versions/1.13.2/src/main/java/**`, `versions/1.16.5/src/main/java/**`, corresponding test files

- [x] 9. Implement latest-family goal support for `1.21.11` with Paper-backed internals only where needed

  **What to do**: Add the latest-family backend to `1.21.11`, using internal Paper/NMS conveniences only behind the shared goals seam. Keep the exact same public contract, catalog, attach semantics, and queue behavior used by all earlier versions. This task must prove that the runtime can use newer backend affordances without changing downstream plugin code.
  **Must NOT do**: Do not expose `Pathfinder`, `PaperPathfinder`, or any Paper-specific type in the public API; do not fork semantics from the older versions.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: newest backend needs to exploit modern internals while staying contract-compatible.
  - Skills: `[]` - no extra skill required.
  - Omitted: `playwright` - not applicable.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: `10, 11` | Blocked By: `5`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11.java:95-209` - exact-version entrypoint for capabilities/spawn/attach.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/SpigotVersionAdapterV1_21_11.java` - latest adapter boundary.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/strategy/EntityStrategyBundle.java:34-94` - shared strategy composition expected by the runtime.
  - External: `https://github.com/PaperMC/Paper/blob/37e02efd956478fc93a9516ee232c8d379dc30cb/paper-server/src/main/java/com/destroystokyo/paper/entity/PaperPathfinder.java` - internal inspiration only.
  - External: `https://github.com/PaperMC/Paper/pull/5629` - mutation-safety precedent for not mutating selectors mid-tick.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `1.21.11` exposes the exact same public goal behavior as all older families.
  - [ ] Latest-family tests verify attach-time recognized-goal removal, custom-goal add/remove, and queued mutation timing.
  - [ ] Any Paper-specific helper remains internal to the exact-version/runtime backend.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Latest family tests pass on 1.21.11
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/1.21.11 -am test`
    Expected: 1.21.11 goal support tests and existing exact-version tests pass
    Evidence: .sisyphus/evidence/task-9-12111-goals.txt

  Scenario: Latest family mutation timing matches the shared contract
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/1.21.11 -am -Dtest="*Goal*Queue*,*Goal*Attach*,*EntityFactoryV1_21_11*" test`
    Expected: add/remove operations are queued to a safe tick and attach semantics preserve unmanaged goals
    Evidence: .sisyphus/evidence/task-9-12111-goals-queue.txt
  ```

  **Commit**: NO | Message: `feat(versions-1.21.11): add latest entity goals support` | Files: `versions/1.21.11/src/main/java/**`, `versions/1.21.11/src/test/java/**`

- [x] 10. Add public-contract and runtime regression tests

  **What to do**: Add focused tests in the shared API/runtime layers that lock the public behavior regardless of backend. Cover builder-time configuration, spawn-time override merging, attached-entity ownership rules, duplicate add semantics, remove-all-by-key semantics, unsupported entity/key/version failures, supported-but-absent removal returning zero, and next-safe-tick mutation visibility. Use zombie fixtures for positive cases and `ArmorStand` for unsupported-entity cases.
  **Must NOT do**: Do not rely only on exact-version tests; the shared public contract must be protected independently.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: broad regression coverage across API/runtime behavior.
  - Skills: `[]` - no extra skill required.
  - Omitted: `writing` - this task is test implementation, not prose.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: `11` | Blocked By: `1, 2, 3, 4, 6, 7, 8, 9`

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:327-455` - spawn entrypoints and customizer path.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:263-303` - attached-entity flow to exercise in tests.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5Test.java:73-157` - zombie-based fixture style.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java:72-158` - attached-entity bridge test style.
  - Test infra: `pom.xml:205-258` - Surefire + JUnit 5 + Mockito.
  - Test infra: `platform-spigot/pom.xml:45-58`, `test-plugin/pom.xml:96-171` - MockBukkit availability.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Shared tests cover the full public contract for builder-time, runtime mutation, ownership, and unsupported behavior.
  - [ ] A zombie happy-path test proves one normal goal and one target goal can be added then removed by stable key.
  - [ ] An `ArmorStand` negative test proves unsupported entities fail with the documented goals exception contract.
  - [ ] A duplicate/idempotency test proves adding the same managed key replaces the old entry and repeated removal returns `removed > 0` then `0`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Public contract tests pass
    Tool: Bash
    Steps: run `mvnw.cmd -pl versions/runtime -am -Dtest="*Goal*,*VersionedPlatform*" test`
    Expected: Contract tests pass for builder-time, attach-time, unsupported, duplicate, and queue semantics
    Evidence: .sisyphus/evidence/task-10-goal-contract-tests.txt

  Scenario: Full repository suite still passes
    Tool: Bash
    Steps: run `mvnw.cmd clean test`
    Expected: BUILD SUCCESS across all modules
    Evidence: .sisyphus/evidence/task-10-goal-contract-tests-full-suite.txt
  ```

  **Commit**: NO | Message: `test(versions-runtime): add entity goals contract coverage` | Files: `versions/runtime/src/test/java/**`, related shared test helpers

- [x] 11. Add exact-version verification coverage across every supported versions module

  **What to do**: Add or update exact-version tests for every versions module currently in the repository (`1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, `1.21.11`) so each one proves the frozen goal catalog, attach ownership rules, and queue semantics work on that backend.
  **Must NOT do**: Do not assume shared runtime tests are sufficient; each exact-version module must prove goal support on its own backend.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: broad repo-wide verification and sample usage validation.
  - Skills: `[]` - no extra skill required.
  - Omitted: `frontend-ui-ux` - not relevant.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: none | Blocked By: `6, 7, 8, 9, 10`

  **References** (executor has NO interview context - be exhaustive):
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/v1_8_8/entity/SpigotVersionAdapterV1_8_8Test.java:48-154` - legacy test style.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/v1_13_2/entity/EntityFactoryV1_13_2Test.java` - exact-version factory test surface.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/v1_16_5/entity/EntityFactoryV1_16_5Test.java:57-158` - zombie fixture example.
  - Test: `versions/1.17.1/src/test/java/tech/guilhermekaua/spigotboot/v1_17_1/entity/EntityFactoryV1_17_1Test.java` - modern family exact-version test surface.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/v1_19_2/entity/EntityFactoryV1_19_2ReplacementBridgeTest.java:59-158` - attach bridge example.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/v1_21_11/entity/EntityFactoryV1_21_11Test.java` - latest exact-version test surface.
  - Test infra: `AGENTS.md:6-12` - canonical repo test/package commands.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Every exact-version module in `versions/` has goal coverage proving the frozen contract on that backend.
  - [ ] Exact-version tests explicitly cover attach-time recognized-goal removal and safe-tick mutation timing.
  - [ ] `mvnw.cmd clean test` remains green after all exact-version coverage is added.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Every exact-version module passes goal coverage
    Tool: Bash
    Steps: run `mvnw.cmd -pl "versions/1.8.8,versions/1.13.2,versions/1.16.5,versions/1.17.1,versions/1.19.2,versions/1.21.11" -am test`
    Expected: All exact-version modules pass goal tests and existing adapter/factory tests
    Evidence: .sisyphus/evidence/task-11-version-family-goals.txt
  ```

  **Commit**: NO | Message: `test(versions): cover entity goals across exact-version modules` | Files: `versions/*/src/test/java/**`, any shared test helpers

- [x] 12. Add sample-plugin exercise path and final end-to-end verification

  **What to do**: Add one minimal `test-plugin` usage path showing builder-time goal configuration for a spawned entity and one minimal path showing attached-entity mutation for an existing entity. Keep it small and purpose-built for regression verification, then run final repo-wide/package verification including the sample plugin packaging command.
  **Must NOT do**: Do not turn the sample plugin into a showcase rewrite; only add the smallest surface needed to exercise the new API in-repo.

  **Recommended Agent Profile**:
  - Category: `writing` - Reason: this is a thin user-facing exercise surface plus final verification packaging.
  - Skills: `[]` - no extra skill required.
  - Omitted: `frontend-ui-ux` - no UI work involved.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: none | Blocked By: `6, 7, 8, 9, 10`

  **References** (executor has NO interview context - be exhaustive):
  - Sample surface: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java` - existing entity demo command to extend minimally.
  - Sample surface: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java` - existing entity demo service to extend minimally.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:393-455` - builder-time spawn customizer path the sample should exercise.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/versions/runtime/VersionedPlatform.java:263-303` - attach/get path the sample should exercise.
  - Test infra: `AGENTS.md:6-12` - canonical repo test/package commands.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The sample plugin contains one minimal usage path for builder-time goal configuration and one for attached-entity mutation.
  - [ ] The sample path uses only the new public goals API, not internal runtime classes.
  - [ ] `mvnw.cmd clean test` and `mvnw.cmd -pl test-plugin -am package` both succeed after the sample exercise path is added.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Sample plugin packages with new goals API usage
    Tool: Bash
    Steps: run `mvnw.cmd -pl test-plugin -am package`
    Expected: The sample plugin packages successfully with the new goal API usage path
    Evidence: .sisyphus/evidence/task-12-sample-plugin-goals-package.txt

  Scenario: End-to-end repo verification passes
    Tool: Bash
    Steps: run `mvnw.cmd clean test`
    Expected: BUILD SUCCESS across the full repository after sample-plugin integration
    Evidence: .sisyphus/evidence/task-12-sample-plugin-goals-e2e.txt
  ```

  **Commit**: YES | Message: `test(test-plugin): exercise entity goals api` | Files: `test-plugin/src/main/java/**`, any final verification test helpers

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
- [x] F2. Code Quality Review — unspecified-high
- [x] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [x] F4. Scope Fidelity Check — deep

## Commit Strategy
- One commit after Wave 1 foundation, one after Wave 2 version coverage, one after Wave 3 test completion.
- Commit style: Conventional Commits.
- No commit should mix public API design changes with unrelated cleanup.

## Success Criteria
- The executor can implement the API without making new product decisions.
- The public API does not expose NMS or Paper classes.
- The behavior contract is identical for custom-entity spawn flow and attached-entity flow.
- Unknown third-party goals survive attach/mutation unchanged.
- All supported versions remain green under automated tests.
