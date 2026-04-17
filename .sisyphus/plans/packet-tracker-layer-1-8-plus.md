# BKCommonLib-Style Packet/Tracker Control Layer for Custom Entities (1.8+ Spigot/Paper)

## TL;DR
> **Summary**: Add a real packet/tracker + metadata synchronization layer to `versions/` by keeping the current adapter/bootstrap architecture, introducing a runtime profile model, splitting backend selection by subsystem, and filling the missing 1.8+ version families with explicit adapters and transport backends.
> **Deliverables**:
> - runtime profile detection (`minecraftVersion + serverFlavor + feature probes`)
> - subsystem selectors for tracker hooks, publication/add-remove, packet transport, and metadata sync
> - exact version-family adapter modules covering the full 1.8+ range
> - packet/metadata parity for spawn, destroy, movement, rotation, velocity, metadata init/delta, attributes, equipment, effects, and passenger/vehicle sync
> - automated unit + matrix verification with evidence capture
> **Effort**: XL
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 2 → 5 → 8/9/10 → 11 → 14/15

## Context
### Original Request
- Implement the BKCommonLib-style packet/tracker layer for custom entities.
- Support all versions `1.8+`.

### Interview Summary
- Scope is literal full `1.8+` support now, not just the current `1.8.8` and `1.21.11` modules.
- Parity target is packet/tracker **plus** metadata parity.
- Server matrix is **Spigot + Paper**.
- Test strategy is **tests-after**.
- Default structural choice is to preserve the repo’s adapter/bootstrap pattern and keep version-sensitive internals local to version modules.

### Metis Review (gaps addressed)
- Added a required runtime profile model instead of version-only detection.
- Split architecture by subsystem family instead of deepening the current global two-bucket selector.
- Locked the parity contract to include metadata init/delta plus living-entity attribute/equipment/effect initialization.
- Added explicit version-family coverage and fail-fast support declarations so “supported” cannot mean “boots with handles only.”
- Added real matrix verification requirements because the current repo has no live packet-level validation.

## Work Objectives
### Core Objective
Implement a BKCommonLib-like transport architecture for custom entities that preserves the current `SpigotEntityBootstrap -> EntityAdapterDiscovery -> VersionedEntityPlatform` flow while adding real viewer-owned packet/tracker and metadata synchronization behavior across the full `1.8+` Spigot/Paper range.

### Deliverables
- `versions/runtime` runtime profile detection and subsystem bundle selection
- internal tracker-hook, publication, transport, and metadata synchronization SPIs
- version-family adapters/modules covering all `1.8+` support ranges
- real viewer add/remove and late-viewer full snapshot behavior for both fresh spawn and attach/replacement paths
- matrix runner, trace/assertion schema, and per-family support gating

### Definition of Done (verifiable conditions with commands)
- `mvnw.cmd -pl versions/runtime -am test -Dtest=SpigotEntityBootstrapTest,VersionedEntityPlatformTest,RuntimeProfileSelectionTest,EntityNetworkRuntimeBundleSelectorTest` exits `0`
- `mvnw.cmd -pl versions -am test` exits `0`
- `mvnw.cmd -pl test-plugin -am package` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.8.8" -Scenario "viewer-cycle-zombie"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.13.2" -Scenario "metadata-dirty-zombie"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.16.5" -Scenario "orbit"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.17.1" -Scenario "attach-existing-zombie"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.19.2" -Scenario "deathfx-cow"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "paper-1.19.2" -Scenario "viewer-cycle-zombie"` exits `0`
- `pwsh -File "scripts/run-entity-matrix.ps1" -Server "paper-1.21.11" -Scenario "metadata-dirty-zombie"` exits `0`

### Must Have
- Runtime profile selection based on version + flavor + feature probes
- Non-overlapping adapter coverage for all supported version families
- Separate subsystem selectors for tracker hooks, publication/add-remove, transport, and metadata sync
- Fresh-spawn and attach/replacement parity
- Metadata parity covering initial snapshot and dirty updates
- Living-entity initialization parity covering attributes, equipment, and effects
- Explicit support declarations and fail-fast behavior for unsupported profiles

### Must NOT Have
- No new public API that leaks raw packet/NMS/tracker classes from `versions/api`
- No claim of support for a version/fork combination without an explicit adapter + backend assignment
- No broad “generic packet library” scope expansion
- No hidden dependence on Paper-only classes from shared API/runtime modules
- No silent conflation of existing type metadata (`EntityFactory...` metadata registries) with network metadata/watchers

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: **tests-after** using JUnit 5 + Mockito + existing runtime tests, with a new matrix runner for real server/fork smoke validation
- QA policy: Every task includes agent-executed unit or matrix scenarios
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}` plus matrix traces in `target/entity-matrix/<server>/<scenario>/`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: runtime profile, subsystem selectors, module skeleton coverage, transport contract, metadata contract, runtime viewer lifecycle

Wave 2: legacy hook family, modern hook family, publication/add-remove families, legacy transport backend, modern transport backends

Wave 3: unit tests, demo scenarios, matrix runner, support gating

### Support Matrix (planned)
- `versions/1.8.8`: `1.8.8 - 1.12.2` Spigot/Paper legacy entry-hook family
- `versions/1.13.2`: `1.13 - 1.13.2` Spigot/Paper transitional legacy-entry family
- `versions/1.16.5`: `1.14 - 1.16.5` Spigot/Paper entry+state + entities-by-UUID publication family
- `versions/1.17.1`: `1.17 - 1.18.2` Spigot/Paper section-manager publication family
- `versions/1.19.2`: `1.19.2 - 1.20.6` Spigot section-manager + Paper chunk-system overlay family
- `versions/1.21.11`: `1.21 - latest supported 1.21.x` Spigot/Paper latest family

### Dependency Matrix (full, all tasks)
- 1 blocks 2, 11, 12, 15
- 2 blocks 3, 4, 5, 11
- 3 blocks 5, 9, 10, 12, 13, 14
- 4 blocks 9, 10, 12, 13, 14
- 5 blocks 6, 7, 8, 9, 10, 13, 14
- 6 blocks 9, 14
- 7 blocks 10, 14
- 8 blocks 14, 15
- 9 blocks 14, 15
- 10 blocks 14, 15
- 11 blocks 6, 7, 8, 9, 10, 12, 14, 15
- 12 blocks 15
- 13 blocks 14
- 14 blocks 15
- 15 blocks Final Verification Wave

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 6 tasks → `deep` x5, `unspecified-high` x1
- Wave 2 → 5 tasks → `deep` x5
- Wave 3 → 4 tasks → `deep` x1, `unspecified-high` x3

### Scenario Assertion Contract (exact keys)
- `orbit` → `pass`, `spawnCount`, `duplicateRegistrationErrors`, `movementSyncObserved`
- `deathfx-cow` → `pass`, `aiReactedAfterHit`, `deathEffectCount`, `duplicateRegistrationErrors`
- `metadata-dirty-zombie` → `pass`, `metadataInitCount`, `metadataDeltaCount`, `attributeInitCount`, `equipmentInitCount`, `effectInitCount`
- `viewer-cycle-zombie` → `pass`, `viewerAddCount`, `viewerRemoveCount`, `spawnCount`, `destroyCount`
- `attach-existing-zombie` → `pass`, `attachCount`, `duplicateSpawnCount`, `entityIdStable`, `trackerRebound`

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Add runtime profile detection and deterministic adapter precedence

  **What to do**: Introduce `EntityRuntimeProfile` in `versions/runtime` with three required dimensions: `MinecraftVersion`, `ServerFlavor` (`SPIGOT`, `PAPER`), and feature probes (`trackerState`, `paperChunkSystem`, `paperMoonriseChunkSystem`). Add `RuntimeServerFlavorDetector`/`RuntimeFeatureProbeRegistry` beside the existing bootstrap detector, update `SpigotEntityBootstrap` to resolve a runtime profile before adapter selection, and replace the current version-only match scoring with deterministic precedence: exact profile match > exact flavor-neutral range match > lower-priority fallback; reject overlapping adapters at bootstrap with an explicit exception and test coverage.
  **Must NOT do**: Do not change `versions/api` to expose raw runtime-profile internals; do not keep the current "highest minimum version wins" rule once adapters overlap by range; do not hard-reference Paper-only classes from shared runtime code.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: bootstrap, range precedence, and cross-fork detection are architecture-critical and easy to get subtly wrong.
  - Skills: `[]` - No extra skill is required beyond the repo-local architecture and test stack.
  - Omitted: [`playwright`, `git-master`] - No browser work is involved, and no git action is part of the implementation slice.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 2, 11, 12, 15 | Blocked By: none

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:110-134` - current adapter selection is version-only and must become runtime-profile-aware.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/RuntimeMinecraftVersionDetector.java:32-34` - current detector only resolves Bukkit version and must remain the version source.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/EntityAdapterDiscovery.java` - preserve ServiceLoader + registry discovery flow.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/registry/EntityAdapterRegistry.java` - explicit registration seam must keep working after precedence changes.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/spi/EntityVersionAdapter.java` - adapter contract must stay the bootstrap boundary.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java` - extend for profile-based adapter selection and overlap rejection.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/component/LibraryComponentSelector.java:17-27, 138-200` - reference for first-supported runtime-family selection discipline.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `RuntimeProfileSelectionTest` exists and covers Spigot vs Paper profile selection, feature-probe detection, and overlap rejection.
  - [ ] `SpigotEntityBootstrapTest` verifies that non-overlapping family-range adapters are selected deterministically for `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11`.
  - [ ] Bootstrap throws a stable, test-asserted exception when two adapters claim the same runtime profile.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Runtime profile resolves correct adapter
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=SpigotEntityBootstrapTest,RuntimeProfileSelectionTest`
    Expected: Exit code 0; the suites cover Spigot and Paper profile cases and no adapter-overlap failures remain.
    Evidence: .sisyphus/evidence/task-1-runtime-profile.txt

  Scenario: Overlapping adapter declarations fail fast
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=RuntimeProfileSelectionTest`
    Expected: Exit code 0; the test case for overlapping profile coverage passes by asserting the expected bootstrap exception text.
    Evidence: .sisyphus/evidence/task-1-runtime-profile-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): add runtime profile selection for entity adapters` | Files: `versions/runtime/**`, `versions/runtime/src/test/**`

- [x] 2. Replace the global two-family selector with subsystem bundle selection

  **What to do**: Keep `EntityStrategyBundle` for spawn/replacement concerns, but add a new internal `EntityNetworkRuntimeBundle` composed by separate selectors for tracker hooks, publication/add-remove, packet transport, and metadata synchronization. Replace the global assumption inside `EntityStrategyBundleSelector` that all transport concerns collapse to “legacy” or “paper-like”; create explicit selectors under `versions/runtime/.../selection/` that each classify a runtime profile into one backend family, then attach the resulting `EntityNetworkRuntimeBundle` to `VersionedEntityPlatform`.
  **Must NOT do**: Do not deepen the current `EntityStrategyBundleSelector` with more nested `if`/`else` family checks; do not create one giant enum that mixes tracker hooks, publication, and packet transport in a single axis.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task defines the internal architecture that every later backend plugs into.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not a browser or git task.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 3, 4, 5, 6, 7, 8, 9, 10, 11 | Blocked By: 1

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/selection/EntityStrategyBundleSelector.java:73-99` - current global bundle composition must remain intact for spawn/replacement while transport moves to a separate bundle.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/selection/EntityStrategyBundleSelector.java:178-205` - current fresh-spawn family classification logic shows the hardcoded family assumption that must not be copied.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/selection/EntityStrategyBundleSelector.java:207-220` - replacement selection has the same coupling problem.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java` - this is the runtime composition root and must own the additional bundle.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/model/EntityVersionMetadataProvider.java` - continue using provider metadata as the selection seam.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/model/EntityVersionBindings.java` - preserve the existing binding model for spawn/replacement while adding a parallel network binding model.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/logic/EntityTypingHandler.java:18-23` - tracker hook selection is one independent axis.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/logic/EntityAddRemoveHandler.java:41-60` - publication/add-remove selection is another independent axis.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `EntityNetworkRuntimeBundle` exists and is resolved separately from the current spawn/replacement bundle.
  - [ ] New selector tests cover at least six family assignments: `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2` Spigot/Paper split, and `1.21.11`.
  - [ ] No task after this one needs to branch on a single combined “legacy vs paper-like” transport family.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Subsystem selectors compose a runtime bundle
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityNetworkRuntimeBundleSelectorTest,VersionedEntityPlatformTest`
    Expected: Exit code 0; composed bundles are asserted for all planned version/fork families.
    Evidence: .sisyphus/evidence/task-2-subsystem-selectors.txt

  Scenario: Legacy-only global selector assumptions are removed
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityNetworkRuntimeBundleSelectorTest`
    Expected: Exit code 0; tests asserting distinct tracker/publication/transport families all pass.
    Evidence: .sisyphus/evidence/task-2-subsystem-selectors-error.txt
  ```

  **Commit**: YES | Message: `refactor(runtime): split entity transport selection by subsystem` | Files: `versions/runtime/**`

- [x] 3. Introduce the internal packet transport contract and operation pipeline

  **What to do**: Add an internal `versions/runtime/.../network/transport/` package that defines the semantic operations the runtime can request without leaking raw packets: `spawnForViewer`, `destroyForViewer`, `syncRelativeMove`, `syncAbsoluteMove`, `syncRotation`, `syncHeadRotation`, `syncVelocity`, `syncPassengersOrVehicle`, `sendInitialMetadataSnapshot`, `sendDirtyMetadataDelta`, and `sendLivingInitialization`. Wire `AbstractRuntimeControlledEntity` and the runtime-owned network controller path to route through this transport SPI rather than only comparing thresholds and mutating `EntityNetworkState`.
  **Must NOT do**: Do not expose packet classes or packet builders through `versions/api`; do not put transport operations directly on the public `EntityNetworkController` type.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this defines the semantic parity contract that later family backends must implement consistently.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 5, 9, 10, 12, 13, 14 | Blocked By: 2

  **References** (executor has NO interview context - be exhaustive):
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkController.java:35-97` - preserve the public sync-decision surface and back it with real transport work.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkState.java:38-104` - continue using this as runtime bookkeeping, not as the transport backend.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/AbstractRuntimeControlledEntity.java:226-235` - current tick path only refreshes state and calls `onTick`; this is where transport orchestration begins.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:580-663` - semantic parity for initial viewer synchronization.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:687-735` - semantic parity for per-tick movement/velocity/metadata/head-rotation sync.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/utils/PacketUtil.java:79-123` - reference for the shared transport dispatch role; adapt the idea, not the concrete API.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The runtime has a single internal transport SPI that represents every required parity operation semantically.
  - [ ] `AbstractRuntimeControlledEntity` can drive transport operations for late-viewer snapshot and periodic sync without exposing raw packet types to plugin code.
  - [ ] New contract tests validate operation ordering for spawn, destroy, movement, and metadata flows.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Transport operations are dispatched in the expected order
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityTransportContractTest,RuntimeNativeEntityLifecycleTest`
    Expected: Exit code 0; tests verify spawn -> metadata init -> living init order and tick-time delta dispatch ordering.
    Evidence: .sisyphus/evidence/task-3-transport-contract.txt

  Scenario: No raw packet API leaks into public packages
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityTransportContractTest`
    Expected: Exit code 0; tests/reflection assertions confirm transport implementations remain internal to `versions/runtime`.
    Evidence: .sisyphus/evidence/task-3-transport-contract-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): add semantic entity transport contract` | Files: `versions/runtime/**`, `versions/runtime/src/test/**`

- [x] 4. Add a dedicated network metadata model and watcher synchronization contract

  **What to do**: Add an internal `versions/runtime/.../network/metadata/` package that is distinct from existing entity-type metadata. Define network metadata descriptors for: generic watcher payload, dirty watcher delta, living attributes, equipment snapshot, active effects snapshot, head rotation value, and passenger/vehicle state. The runtime contract must support both initial snapshot generation and incremental delta generation, and version modules must publish metadata capabilities independently from the existing `EntityFactory...` type metadata registries.
  **Must NOT do**: Do not rename or overload the current `EntityMetadata` records inside `EntityFactoryV1_8_8`/`EntityFactoryV1_21_11`; do not treat attributes/equipment/effects as out of scope for metadata parity.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: metadata parity is the easiest place to fake support, and the naming collision already exists.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 9, 10, 12, 13, 14 | Blocked By: 2

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java:711-726` - current `EntityMetadata` naming is already used for type/base-type resolution; keep this separate.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11.java:704-719` - same naming constraint on the modern side.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/wrappers/DataWatcher.java:320-398` - reference for all-items, changed-items, and non-default packing behavior.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/wrappers/DataWatcher.java:455-538` - reference for reusable watcher prototype configuration.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/wrappers/DataWatcher.java:705-788` - reference for packed metadata item/value semantics.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:631-661` - living-entity initialization parity includes attributes, equipment, and effects.

  **Acceptance Criteria** (agent-executable only):
  - [ ] A separate network metadata model exists and is not conflated with type metadata resolution.
  - [ ] The transport contract can request both initial metadata snapshots and dirty metadata deltas.
  - [ ] Living-entity initialization has explicit metadata contracts for attributes, equipment, and effects.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Metadata contracts cover init and delta paths
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=NetworkMetadataContractTest,EntityTransportContractTest`
    Expected: Exit code 0; tests cover initial snapshot generation, dirty delta generation, and living initialization payload assembly.
    Evidence: .sisyphus/evidence/task-4-network-metadata.txt

  Scenario: Type metadata and network metadata remain distinct
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=NetworkMetadataContractTest`
    Expected: Exit code 0; tests/reflection assertions confirm no reuse of `EntityFactory...EntityMetadata` types for network metadata.
    Evidence: .sisyphus/evidence/task-4-network-metadata-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): add network metadata contract for entity transport` | Files: `versions/runtime/**`, `versions/runtime/src/test/**`

- [x] 5. Extend runtime lifecycle to own viewers and late-viewer full snapshots

  **What to do**: Extend the runtime-controlled entity lifecycle so the runtime, not just the public `EntityNetworkController`, owns viewer membership, bind/unbind callbacks, late-viewer snapshot generation, and attach/replacement parity. Add internal hooks so both fresh-spawn and `platform().get(entity)` attach paths can register viewers, emit a full spawn snapshot on first visibility, emit dirty deltas on later ticks, and flush destroy packets/state on removal or unbind.
  **Must NOT do**: Do not leave viewer lifecycle as a no-op callback surface; do not make fresh-spawn the only path that receives network state.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: this task is broad runtime wiring with high regression risk but less architecture invention than tasks 1-4.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 6, 7, 8, 9, 10, 13, 14 | Blocked By: 2, 3

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/AbstractRuntimeControlledEntity.java:103-104` - current bind path is too thin and must own real network lifecycle state.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/AbstractRuntimeControlledEntity.java:226-235` - current tick path is the insertion point for viewer-aware transport dispatch.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkController.java:45-58` - keep `onBind`, `onUnbind`, `onViewerAdded`, and `onViewerRemoved` as the public extension seam.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkState.java:96-103` - reuse viewer set bookkeeping but stop treating it as the whole solution.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:432-467` - reference viewer add/remove semantics.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:580-619` - reference late-viewer full snapshot behavior.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Fresh-spawn and attach/replacement runtime entities both maintain viewer membership and emit full late-viewer snapshots.
  - [ ] Removal/unbind clears viewers and triggers transport destroy behavior exactly once per viewer.
  - [ ] New lifecycle tests cover bind, unbind, viewer add, viewer remove, and attach parity.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Viewer lifecycle works for spawned and attached entities
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=RuntimeNativeEntityLifecycleTest,EntityViewerLifecycleTest`
    Expected: Exit code 0; both fresh-spawn and attach paths pass viewer add/remove and late-snapshot assertions.
    Evidence: .sisyphus/evidence/task-5-viewer-lifecycle.txt

  Scenario: Removal/unbind does not double-destroy viewers
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityViewerLifecycleTest`
    Expected: Exit code 0; tests assert one destroy/unbind path per viewer and no duplicate callbacks.
    Evidence: .sisyphus/evidence/task-5-viewer-lifecycle-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): wire viewer lifecycle into controlled entities` | Files: `versions/runtime/**`, `versions/runtime/src/test/**`

- [x] 6. Implement the legacy tracker-hook family for 1.8.8-1.13.2

  **What to do**: Add the legacy tracker-hook backend for the `1.8.8 - 1.13.2` range, modeled on BKCommonLib’s entry-hook family. The backend must own tracker-entry interception, per-viewer viewability decisions, tick callbacks, and viewer add/remove handoff into the runtime transport pipeline; for `1.13 - 1.13.2`, keep the same hook family but allow a distinct entity-type/metadata descriptor overlay where required.
  **Must NOT do**: Do not reuse the modern entry+state hook path for legacy versions; do not skip explicit viewability logic for pre-1.14 viewers.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: legacy tracker semantics and viewability rules are version-sensitive and regress easily.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 9, 14 | Blocked By: 2, 5, 11

  **References** (executor has NO interview context - be exhaustive):
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/logic/EntityTypingHandler.java:18-23` - legacy tracker hook family is selected independently from newer families.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/hooks/EntityTrackerEntryHook_1_8_to_1_13_2.java:40-109` - required hook responsibilities: `track`, `updatePlayer`, `clear`, `removeViewer`.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/hooks/EntityTrackerEntryHook_1_8_to_1_13_2.java:112-192` - required legacy viewability logic inputs.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/strategy/LegacyTrackingBindingStrategy_1_8_to_1_12.java:100-143` - current legacy runtime only stores tracker entry handles; this task must turn that into an active hook backend.
  - API/Type: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java` - legacy version-local support bridge lives here and in the new `1.13.2` module.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8Test.java` - extend with active tracker-hook assertions.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The legacy family backend actively intercepts tracker-entry tick and viewer lifecycle callbacks.
  - [ ] Legacy viewability rules are encapsulated inside the backend and tested.
  - [ ] `1.8.8` and `1.13.2` family adapters both bind to the legacy hook backend without branching through modern state handles.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Legacy tracker hook dispatches viewer and tick callbacks
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.8.8 -am test -Dtest=SpigotEntityAdapterV1_8_8Test,LegacyTrackerHookSupportTest`
    Expected: Exit code 0; tests assert viewer add/remove and tick dispatch through the legacy hook path.
    Evidence: .sisyphus/evidence/task-6-legacy-hook.txt

  Scenario: Transitional 1.13 family still uses the legacy hook model safely
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.13.2 -am test -Dtest=SpigotEntityAdapterV1_13_2Test,LegacyTrackerHookSupportTest`
    Expected: Exit code 0; tests prove the 1.13 family resolves through legacy entry hooks without state-handle assumptions.
    Evidence: .sisyphus/evidence/task-6-legacy-hook-error.txt
  ```

  **Commit**: YES | Message: `feat(legacy): add tracker hook backend for 1.8-1.13` | Files: `versions/runtime/**`, `versions/1.8.8/**`, `versions/1.13.2/**`

- [x] 7. Implement the modern tracker-hook family for 1.14+

  **What to do**: Add the modern tracker-hook backend for `1.14+`, modeled on BKCommonLib’s entry+state split. The backend must hook both the tracker entry and tracker state paths, route `addPairing`/`removePairing`-style viewer transitions into runtime transport, and preserve separate handling for entry state, broadcast consumers, passenger snapshots, and tick callbacks.
  **Must NOT do**: Do not collapse modern behavior into an entry-only hook; do not ignore tracker-state-specific callbacks for `1.14+`.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this is the central modern hook path and spans the widest version range.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 10, 14 | Blocked By: 2, 5, 11

  **References** (executor has NO interview context - be exhaustive):
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/hooks/EntityTrackerEntryHook_1_14.java:64-117` - required modern hook behavior for `hook`, state `onTick`, `addPairing`, and `removePairing`.
  - External: `local-only/BKCommonLib/src/main/generated/com/bergerkiller/generated/net/minecraft/server/level/EntityTrackerEntryStateHandle.java:141-179` - required modern state concepts: broadcast consumer, optional passengers, optional vehicle, add/remove pairing methods, and tick counters.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/strategy/PaperTrackingBindingStrategy_1_21_plus.java:100-136` - current modern runtime only records state handles; this task upgrades that capability into an active backend.
  - API/Type: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11.java` - modern version-local support bridge starts here and is expanded by new mid-era modules.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/SpigotEntityAdapterV1_21_11Test.java` - extend with active modern hook assertions.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The modern family backend actively hooks both entry and state paths for viewer lifecycle and ticks.
  - [ ] Broadcast consumer rebinding and passenger snapshot/state preservation are covered by tests.
  - [ ] `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11` family adapters bind through this modern hook family with version-local overlays only where required.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Modern tracker entry+state hooks dispatch runtime transport callbacks
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.21.11 -am test -Dtest=SpigotEntityAdapterV1_21_11Test,ModernTrackerHookSupportTest`
    Expected: Exit code 0; tests assert state-level tick and pairing callbacks are hooked and routed into the runtime transport layer.
    Evidence: .sisyphus/evidence/task-7-modern-hook.txt

  Scenario: Mid-era modules use the same modern family without legacy assumptions
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.16.5 -am test -Dtest=SpigotEntityAdapterV1_16_5Test,ModernTrackerHookSupportTest`
    Expected: Exit code 0; tests prove entry+state hooks are used and no entry-only assumptions remain.
    Evidence: .sisyphus/evidence/task-7-modern-hook-error.txt
  ```

  **Commit**: YES | Message: `feat(modern): add tracker hook backend for 1.14+` | Files: `versions/runtime/**`, `versions/1.16.5/**`, `versions/1.17.1/**`, `versions/1.19.2/**`, `versions/1.21.11/**`

- [x] 8. Implement publication and add/remove backends across Spigot/Paper eras

  **What to do**: Add a publication subsystem that owns world registration, add/remove notification, tracker rebinding, and chunk-system integration. Implement four publication backends: `1.8.8-1.13.2` legacy world listener family, `1.14-1.16.5` entities-by-UUID family, `1.17-1.18.2` section-manager family, and `1.19.2+` dual-path family with Spigot section-manager behavior plus Paper chunk-system overlays; the latest `1.21.x` Paper path must support current callback/event mechanisms without leaking Paper classes into shared runtime code.
  **Must NOT do**: Do not assume publication logic is identical across Spigot and Paper for `1.19.2+`; do not piggyback all add/remove behavior onto spawn/replacement code only.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: publication/add-remove semantics are the second major compatibility axis after tracker hooks.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 14, 15 | Blocked By: 2, 5, 11

  **References** (executor has NO interview context - be exhaustive):
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/logic/EntityAddRemoveHandler.java:41-60` - reference family split for publication/add-remove selection.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/CommonPlugin.java:856-858, 917-919` - reference how world enable/disable hooks drive add/remove handlers.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java` - publication backends must integrate with the existing platform registries and attach/replacement paths.
  - Pattern: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11Test.java` - existing modern section-membership tests should be extended rather than replaced.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/capability/EntityWorldRegistrationMode.java` - preserve and extend this concept for publication backend selection.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Each planned version family has an explicit publication backend assignment.
  - [ ] Replacement/attach publication keeps entity registration stable and test-covered for both Spigot and Paper paths.
  - [ ] The latest Paper family uses reflective/classloader-safe feature detection from the runtime profile rather than shared compile-time Paper dependencies.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Publication backends preserve registration across replacement and attach paths
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am test -Dtest=EntityFactoryV1_21_11Test,EntityPublicationFamilyTest`
    Expected: Exit code 0; tests verify add/remove tracking and section membership rebinding across all publication families.
    Evidence: .sisyphus/evidence/task-8-publication.txt

  Scenario: Paper overlays remain classloader-safe
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=EntityPublicationFamilyTest`
    Expected: Exit code 0; tests assert feature-probe-driven activation instead of hard Paper class linkage in shared runtime code.
    Evidence: .sisyphus/evidence/task-8-publication-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): add publication backends for entity transport families` | Files: `versions/runtime/**`, `versions/*/src/test/**`

- [x] 9. Implement the legacy transport backend for 1.8.8-1.13.2

  **What to do**: Implement the legacy packet/metadata transport backend for `1.8.8 - 1.13.2`, including spawn, destroy, relative vs absolute movement, rotation/head rotation, velocity, passenger-or-vehicle sync, metadata init/delta, and living initialization parity. Use version-local support bridges in `versions/1.8.8` and `versions/1.13.2` to hide packet class/field differences; keep the runtime contract semantic and shared.
  **Must NOT do**: Do not mark the legacy family supported if metadata delta or living initialization is missing; do not assume modern passenger/state semantics on `1.8.x`.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: legacy packet layouts, vehicle/passenger differences, and metadata encoding are the hardest parity slice.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 14, 15 | Blocked By: 3, 4, 5, 6, 11

  **References** (executor has NO interview context - be exhaustive):
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:580-663` - viewer spawn/init packet responsibilities for the shared transport backend.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:687-735` - legacy family must still cover tick-time movement, velocity, metadata, and head-rotation sync.
  - External: `local-only/BKCommonLib/src/main/generated/com/bergerkiller/generated/net/minecraft/server/level/EntityTrackerEntryStateHandle.java:146-170` - note the optional passenger/vehicle differences and method availability.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/strategy/LegacyTrackingBindingStrategy_1_8_to_1_12.java:100-143` - legacy transport integrates with this tracker family.
  - API/Type: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java` - version-local bridge for `1.8.8 - 1.12.2`.
  - API/Type: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_13_2/EntityFactoryV1_13_2.java` - version-local bridge for `1.13 - 1.13.2`.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Legacy transport supports every parity operation listed in the plan for both `1.8.8` and `1.13.2` family adapters.
  - [ ] `1.8.x` vehicle/passenger semantics and `1.13.x` metadata/packet differences are covered by tests.
  - [ ] Legacy family support is disabled automatically if any required transport operation is absent.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Legacy transport covers spawn, movement, velocity, metadata, and viewer cycles
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.8.8 -am test -Dtest=LegacyPacketTransportV1_8_to_1_13_2Test,LegacyTrackerHookSupportTest`
    Expected: Exit code 0; legacy transport contract tests pass for the 1.8.8 family.
    Evidence: .sisyphus/evidence/task-9-legacy-transport.txt

  Scenario: Transitional 1.13 transport still passes the legacy contract
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.13.2 -am test -Dtest=LegacyPacketTransportV1_8_to_1_13_2Test`
    Expected: Exit code 0; 1.13.2-specific transport assertions pass without modern state-handle assumptions.
    Evidence: .sisyphus/evidence/task-9-legacy-transport-error.txt
  ```

  **Commit**: YES | Message: `feat(legacy): add packet and metadata transport backend` | Files: `versions/runtime/**`, `versions/1.8.8/**`, `versions/1.13.2/**`

- [x] 10. Implement modern transport backends and Spigot/Paper overlays for 1.14+

  **What to do**: Implement modern transport backends for `1.14 - 1.16.5`, `1.17 - 1.18.2`, `1.19.2 - 1.20.6`, and `1.21.x`, reusing the shared semantic transport SPI but allowing version-local overlays for packet shapes, tracker-state data, and publication differences. Split Spigot and Paper behavior only where runtime-profile feature probes prove a fork-specific transport/publication path is required; otherwise keep one shared backend per version family.
  **Must NOT do**: Do not create one backend per patch version where the family behavior is identical; do not merge `1.19.2+` Spigot and Paper when the runtime profile indicates chunk-system differences.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task spans the widest modern range and must keep family boundaries explicit.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 14, 15 | Blocked By: 3, 4, 5, 7, 11

  **References** (executor has NO interview context - be exhaustive):
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/internal/hooks/EntityTrackerEntryHook_1_14.java:64-117` - modern viewer lifecycle must route through state hooks.
  - External: `local-only/BKCommonLib/src/main/generated/com/bergerkiller/generated/net/minecraft/server/level/EntityTrackerEntryStateHandle.java:141-179` - modern state carries broadcast/pairing/passenger semantics that transport backends must honor.
  - External: `local-only/BKCommonLib/src/main/java/com/bergerkiller/bukkit/common/controller/EntityNetworkController.java:595-619` - passenger/vehicle/leash/head-rotation behavior is part of modern parity too.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/strategy/PaperTrackingBindingStrategy_1_21_plus.java:100-136` - modern backends continue to use entry+state handles.
  - API/Type: `versions/1.16.5/**`, `versions/1.17.1/**`, `versions/1.19.2/**`, `versions/1.21.11/**` - these modules own the version-local packet/tracker support bridges.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11Test.java` - retain and extend modern rebinding assertions.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Each modern family implements the full semantic transport contract.
  - [ ] Paper overlays are activated by runtime profile/feature probes, not by shared compile-time fork dependencies.
  - [ ] `1.16.5`, `1.17.1`, `1.19.2` Spigot, `1.19.2` Paper, and `1.21.11` Paper all have dedicated tests or matrix assertions.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Modern transport families satisfy shared parity contract
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am test -Dtest=ModernPacketTransportV1_14PlusTest,EntityFactoryV1_21_11Test`
    Expected: Exit code 0; modern contract tests pass for mid-era and latest families.
    Evidence: .sisyphus/evidence/task-10-modern-transport.txt

  Scenario: Paper overlays activate only for matching runtime profiles
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=ModernPacketTransportV1_14PlusTest`
    Expected: Exit code 0; tests assert Spigot/Paper profile splits only where configured and feature-probed.
    Evidence: .sisyphus/evidence/task-10-modern-transport-error.txt
  ```

  **Commit**: YES | Message: `feat(modern): add packet and metadata transport backends` | Files: `versions/runtime/**`, `versions/1.16.5/**`, `versions/1.17.1/**`, `versions/1.19.2/**`, `versions/1.21.11/**`

- [x] 11. Create and register the full 1.8+ version-family module skeletons early

  **What to do**: Update `versions/pom.xml` and the version subsystem early so the runtime has non-overlapping adapter module skeletons for the full planned range: keep `1.8.8` and `1.21.11`, add `1.13.2`, `1.16.5`, `1.17.1`, and `1.19.2`, and widen each module’s adapter range to the family listed in the plan’s support matrix. Each new module must be created before tasks 6-10 begin, and must include a `SpigotEntityAdapterV...`, `EntityFactoryV...`, test package, and `META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` registration; implementation details inside those modules can remain skeletal until the family backends are wired by later tasks.
  **Must NOT do**: Do not leave gaps between family ranges; do not allow overlapping version ranges across modules after task 1’s precedence changes; do not place shared transport logic inside the concrete modules.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task turns the architecture into a real full-range support matrix and is where false support claims become likely.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 6, 7, 8, 9, 10, 12, 14, 15 | Blocked By: 1, 2

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/pom.xml` - add the new version modules here.
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8.java:50-58` - preserve the existing adapter/module composition pattern.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/SpigotEntityAdapterV1_21_11.java:50-58` - preserve the same composition for new modules.
  - Pattern: `versions/1.8.8/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - every new module needs a ServiceLoader registration.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:110-134` - adapter selection must succeed cleanly with the widened family ranges.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java` - extend to cover the complete support matrix.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The `versions` parent module includes all six planned version-family modules.
  - [ ] Each module exposes one adapter, one factory, test coverage, and one service registration file.
  - [ ] No runtime profile in the planned support matrix resolves to zero adapters or more than one adapter.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Full support matrix resolves exactly one adapter per runtime profile
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=SpigotEntityBootstrapTest,RuntimeProfileSelectionTest`
    Expected: Exit code 0; all planned version/fork profiles resolve exactly one adapter.
    Evidence: .sisyphus/evidence/task-11-adapter-matrix.txt

  Scenario: Module graph builds with all new version families present
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am test`
    Expected: Exit code 0; all new modules compile and their tests run.
    Evidence: .sisyphus/evidence/task-11-adapter-matrix-error.txt
  ```

  **Commit**: YES | Message: `feat(versions): add full 1.8+ adapter family coverage` | Files: `versions/pom.xml`, `versions/1.13.2/**`, `versions/1.16.5/**`, `versions/1.17.1/**`, `versions/1.19.2/**`, `versions/1.8.8/**`, `versions/1.21.11/**`

- [x] 12. Add unit and adapter tests for selection, transport, and metadata parity contracts

  **What to do**: Expand the active test suite with new runtime and adapter tests covering runtime profile detection, subsystem family selection, transport operation ordering, metadata init/delta contracts, tracker hook support, publication backends, and support-matrix declarations. Keep tests unit-first inside active modules using JUnit 5 + Mockito; the goal is to prove selection and runtime semantics before matrix execution, not replace matrix execution.
  **Must NOT do**: Do not rely on the matrix runner as the only verification layer; do not leave middle-family modules without dedicated adapter tests.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: large but straightforward test-surface expansion spanning runtime and version modules.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 15 | Blocked By: 1, 2, 3, 4, 11

  **References** (executor has NO interview context - be exhaustive):
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java` - extend for full profile and adapter coverage.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatformTest.java` - extend for composed bundle and transport runtime assertions.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/RuntimeNativeEntityLifecycleTest.java` - extend for viewer lifecycle and transport dispatch.
  - Test: `versions/api/src/test/java/tech/guilhermekaua/spigotboot/entity/api/EntityNetworkControllerTest.java` - keep threshold logic tests and add backing transport expectations where appropriate.
  - Test: `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8Test.java` - legacy adapter assertions.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/SpigotEntityAdapterV1_21_11Test.java` - modern adapter assertions.

  **Acceptance Criteria** (agent-executable only):
  - [ ] New test classes exist for runtime profile selection, bundle selection, transport contracts, metadata contracts, tracker hooks, and publication backends.
  - [ ] Every version-family module has at least one dedicated adapter test asserting hook/transport capability exposure.
  - [ ] `mvnw.cmd -pl versions -am test` passes without using the matrix runner.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Runtime and adapter unit suites pass across the expanded matrix
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am test`
    Expected: Exit code 0; runtime, API, and every version-family module test suite pass.
    Evidence: .sisyphus/evidence/task-12-unit-tests.txt

  Scenario: Selection and transport contract suites remain isolated and reproducible
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=RuntimeProfileSelectionTest,EntityNetworkRuntimeBundleSelectorTest,EntityTransportContractTest,NetworkMetadataContractTest`
    Expected: Exit code 0; the contract-focused test slice passes independently.
    Evidence: .sisyphus/evidence/task-12-unit-tests-error.txt
  ```

  **Commit**: YES | Message: `test(versions): add transport and selection contract coverage` | Files: `versions/api/src/test/**`, `versions/runtime/src/test/**`, `versions/*/src/test/**`

- [x] 13. Add executable test-plugin scenarios for packet/tracker parity

  **What to do**: Extend `test-plugin` so matrix runs can exercise deterministic scenarios with machine-readable outputs. Keep the existing `orbit` and `deathfx` behavior, and add exactly three new command/service scenarios: `metadata-dirty-zombie`, `viewer-cycle-zombie`, and `attach-existing-zombie`; each scenario must emit trace/assertion JSON files to `target/entity-matrix/<server>/<scenario>/` with the exact keys defined in this plan.
  **Must NOT do**: Do not rely on manual `/entitydemo` observation; do not make scenario names or assertion keys drift from the ones listed in this plan.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: this is sample-plugin and automation plumbing, not core architecture work.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - No browser or git work is involved.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 14 | Blocked By: 3, 4, 5

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:53-146` - existing orbit/death-effect scenarios to keep and extend.
  - Test: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/AutoDiscoveryIntegrationTest.java` - keep test-plugin bootstrap/test discipline consistent.
  - Contract: `target/entity-matrix/<server>/<scenario>/trace.json` and `target/entity-matrix/<server>/<scenario>/assertions.json` - exact output locations required by this plan.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The test plugin exposes all five planned scenarios with stable names.
  - [ ] Each scenario writes `trace.json` and `assertions.json` to the exact directory scheme in the plan.
  - [ ] The three new scenarios emit the exact assertion keys required by the matrix acceptance criteria.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Test plugin packages with all parity scenarios available
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am package`
    Expected: Exit code 0; packaged plugin contains the scenario registration code for orbit, deathfx-cow, metadata-dirty-zombie, viewer-cycle-zombie, and attach-existing-zombie.
    Evidence: .sisyphus/evidence/task-13-demo-scenarios.txt

  Scenario: New scenarios emit trace/assertion payloads with exact keys
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am test -Dtest=EntityScenarioRegistrationTest`
    Expected: Exit code 0; test assertions confirm `viewer-cycle-zombie` emits `viewerAddCount`, `viewerRemoveCount`, `spawnCount`, `destroyCount`, and `attach-existing-zombie` emits `attachCount`, `duplicateSpawnCount`, `entityIdStable`, `trackerRebound`.
    Evidence: .sisyphus/evidence/task-13-demo-scenarios-error.txt
  ```

  **Commit**: YES | Message: `feat(test-plugin): add entity transport parity scenarios` | Files: `test-plugin/**`

- [x] 14. Implement the matrix runner and capture real family/fork evidence

  **What to do**: Add `scripts/provision-entity-matrix.ps1`, `scripts/run-entity-matrix.ps1`, and `scripts/entity-matrix/servers.json`. `servers.json` must be the single source of truth for the exact server artifacts/build strategy **and** Java runtime requirement for every server identifier, with explicit fields for `javaMajor`, `javaExecutable`, and local cache paths. `provision-entity-matrix.ps1` must provision missing server jars non-interactively into `.tools/entity-matrix/<server>/server.jar` using `BuildTools --rev <version>` for `spigot-*` entries and official Paper downloads for `paper-*` entries, and it must also provision the matching Temurin JDK into `.tools/entity-matrix/jdks/<javaMajor>/bin/java(.exe)`; use `java 8` for `spigot-1.8.8`, `spigot-1.13.2`, and `spigot-1.16.5`, `java 17` for `spigot-1.17.1`, `spigot-1.19.2`, and `paper-1.19.2`, and `java 21` for `paper-1.21.11`. `run-entity-matrix.ps1` must call the provision script automatically when either the target jar or required JDK is missing, then launch the server with the declared Java executable, execute the named scenario, and persist `trace.json` plus `assertions.json`. The minimum matrix the script must support is the plan’s representative boundary set: `spigot-1.8.8`, `spigot-1.13.2`, `spigot-1.16.5`, `spigot-1.17.1`, `spigot-1.19.2`, `paper-1.19.2`, and `paper-1.21.11`.
  **Must NOT do**: Do not mark full support complete with only endpoint runs (`1.8.8` and `1.21.11`); do not make matrix evidence optional; do not rely on a human to manually place server jars or JDKs before the script can run; do not use ambient host `java` when `servers.json` declares a different runtime.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: automation and validation work with high operational detail, but not new architecture.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - No browser or git work is involved.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 15 | Blocked By: 3, 4, 5, 6, 7, 8, 9, 10, 11, 13

  **References** (executor has NO interview context - be exhaustive):
  - Contract: `scripts/provision-entity-matrix.ps1` - must provision or verify the exact server jar for any supported server identifier.
  - Contract: `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.8.8" -Scenario "orbit"` - exact command shape the plan requires.
  - Contract: `scripts/entity-matrix/servers.json` - must map each `spigot-*`/`paper-*` identifier to a deterministic provisioning strategy and local jar path.
  - Pattern: `.github/workflows/ci.yml:16-32` - current CI only runs Maven tests; matrix automation is new and must be additive.
  - Contract: `target/entity-matrix/<server>/<scenario>/trace.json` and `target/entity-matrix/<server>/<scenario>/assertions.json` - exact runtime evidence locations.
  - Contract: assertion keys listed in this plan’s Work Objectives and task QA scenarios - keep names exact.

  **Acceptance Criteria** (agent-executable only):
  - [ ] The matrix script supports every server/scenario combination listed in the representative boundary set.
  - [ ] Running the matrix from an empty `.tools/entity-matrix/` cache provisions the required server jar and JDK automatically.
  - [ ] Each server identifier resolves to the declared Java executable from `servers.json` rather than ambient host `java`.
  - [ ] Each run writes both `trace.json` and `assertions.json`.
  - [ ] The script returns non-zero on missing traces, missing assertions, or any failed scenario assertion.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Representative matrix boundary set executes successfully
    Tool: Bash
    Steps: Run `pwsh -File "scripts/provision-entity-matrix.ps1" -Server "spigot-1.8.8"`; run `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.8.8" -Scenario "viewer-cycle-zombie"`; run `pwsh -File "scripts/run-entity-matrix.ps1" -Server "paper-1.21.11" -Scenario "metadata-dirty-zombie"`
    Expected: All commands exit 0, the 1.8.8 run uses the provisioned Java 8 executable, the 1.21.11 run uses the provisioned Java 21 executable, and both write the required trace/assertion files under `target/entity-matrix/...`.
    Evidence: .sisyphus/evidence/task-14-matrix-runner.txt

  Scenario: Matrix runner fails hard on missing assertion payloads
    Tool: Bash
    Steps: Run `pwsh -File "scripts/run-entity-matrix.ps1" -Server "spigot-1.17.1" -Scenario "attach-existing-zombie"`
    Expected: Exit code 0 only when the run uses the Java 17 executable declared for `spigot-1.17.1`, `trace.json` and `assertions.json` are both present, and all assertions pass; otherwise non-zero failure is surfaced.
    Evidence: .sisyphus/evidence/task-14-matrix-runner-error.txt
  ```

  **Commit**: YES | Message: `test(matrix): add real server verification for entity transport` | Files: `scripts/**`, `test-plugin/**`, optional CI config updates if needed

- [x] 15. Enforce support declarations, fail-fast gating, and release criteria

  **What to do**: Add a single source of truth for claimed support profiles and fail-fast behavior when a runtime profile lacks any required subsystem backend. The runtime must refuse to advertise support unless all four subsystems (tracker hook, publication, transport, metadata) are assigned and tested; wire this into bootstrap/runtime tests and matrix assertions so support claims are evidence-backed.
  **Must NOT do**: Do not leave support as an inferred side effect of adapter presence only; do not allow partial backends to fall through to silent pass-through behavior.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: this task is the final guard against false parity and unsupported configurations.
  - Skills: `[]` - No extra skill is required.
  - Omitted: [`playwright`, `git-master`] - Not relevant.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: Final Verification Wave | Blocked By: 1, 8, 9, 10, 11, 12, 14

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:110-134` - support gating must integrate at adapter/runtime resolution time.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java` - add support-declaration assertions here.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatformTest.java` - extend with fail-fast expectations for missing subsystem assignments.
  - Contract: matrix outputs under `target/entity-matrix/<server>/<scenario>/assertions.json` - these become release evidence, not optional artifacts.

  **Acceptance Criteria** (agent-executable only):
  - [ ] A runtime profile cannot report support unless every required subsystem backend is assigned.
  - [ ] Missing backend assignments fail fast with explicit, tested exception messages.
  - [ ] The representative matrix plus unit suites are the release gate for declaring the packet/tracker layer complete.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Supported profiles are declared only when fully wired
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=SpigotEntityBootstrapTest,VersionedEntityPlatformTest,RuntimeSupportMatrixTest`
    Expected: Exit code 0; tests assert that partially wired profiles fail fast and fully wired profiles report support.
    Evidence: .sisyphus/evidence/task-15-support-gating.txt

  Scenario: Release gate uses both unit tests and matrix evidence
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am test`; run `pwsh -File "scripts/run-entity-matrix.ps1" -Server "paper-1.19.2" -Scenario "viewer-cycle-zombie"`
    Expected: Both commands exit 0; no profile is marked complete without both unit and matrix success.
    Evidence: .sisyphus/evidence/task-15-support-gating-error.txt
  ```

  **Commit**: YES | Message: `feat(runtime): enforce support matrix gating for entity transport` | Files: `versions/runtime/**`, `versions/runtime/src/test/**`, matrix assertion config if introduced

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
- [x] F2. Code Quality Review — unspecified-high
- [x] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [x] F4. Scope Fidelity Check — deep

## Commit Strategy
- Commit at the end of each numbered task unless the task is explicitly marked `NO`.
- Keep commits scoped to one transport concern or one version-family slice.
- Use Conventional Commit messages.

## Success Criteria
- Every supported runtime profile has an explicit adapter/module and subsystem backend assignment.
- Fresh-spawn and attach/replacement paths both drive real viewer add/remove and packet synchronization.
- Metadata init/delta parity works across legacy and modern tracker families.
- Support claims are enforced by tests and matrix traces, not by assumptions.
