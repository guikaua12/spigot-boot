# Work Plan: Entity Version Handler Architecture

## Objective

Refactor `spigot-boot` entity version support into a BKCommonLib-inspired hybrid model where exact-version adapters remain the SPI boundary, while fresh spawn, replacement, world registration, and tracking behavior move into shared runtime strategies grouped by version range and capability family.

## Desired Outcome

- `SpigotEntityAdapterV1_8_8` and `SpigotEntityAdapterV1_21_11` stay as thin entrypoints.
- `EntityFactoryV1_8_8` and `EntityFactoryV1_21_11` stop owning end-to-end lifecycle logic directly.
- Constructor-first fresh spawn becomes the shared default path for both supported versions.
- Replacement stays a dedicated fallback path for already-existing entities rather than the normal fresh-spawn path.
- Shared runtime strategies make future fixes land once per behavior family instead of once per exact version.

## Defaults Applied

- Treat Paper-specific handling as first-class for `1.21+`, but isolate it behind runtime strategy selection rather than adapter-local branching.
- Keep public/internal SPI contracts stable unless a breaking change is explicitly required.
- Do **not** add automatic “fresh-spawn failed -> silently fall back to replacement” behavior in the initial refactor; fail fast and cleanly instead.
- Use existing test infrastructure (`JUnit 5`, Mockito, MockBukkit where available) and add focused regression tests plus sample-plugin QA.

## Guardrails / Non-Goals

- Do not redesign service loading, bootstrap, or adapter discovery beyond what is required for strategy delegation.
- Do not expand supported Minecraft versions during this refactor.
- Do not fold unrelated reflection/class-generation cleanup into this work.
- Do not change sample-plugin behavior except where needed to verify the refactor.
- Do not force shared abstractions across incompatible NMS eras when version-local shims are still required.

## Repository Anchors

- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/RuntimeNativeEntityLifecycle.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/AbstractRuntimeControlledEntity.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/EntityAdapterDiscovery.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/registry/EntityAdapterRegistry.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/nativebridge/GeneratedNativeEntityClassFactory.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/nativebridge/ReflectionSupport.java`
- `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/nativebridge/FieldCopySupport.java`
- `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java`
- `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8.java`
- `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11.java`
- `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/SpigotEntityAdapterV1_21_11.java`
- `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatformTest.java`
- `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/lifecycle/RuntimeNativeEntityLifecycleTest.java`
- `versions/1.8.8/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8Test.java`
- `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11Test.java`
- `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/commands/EntityDemoCommand.java`
- `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java`

## Proposed Runtime Architecture

### Exact-Version Boundary

Keep exact-version modules responsible for:

- adapter registration and version matching
- version-specific NMS type resolution/bindings
- capability declaration
- delegating into runtime strategies

### Shared Runtime Strategy Families

- `FreshSpawnStrategy`
  - `LegacyFreshSpawnStrategy_1_8_to_1_12`
  - `ModernFreshSpawnStrategy_1_13_to_1_20`
  - `PaperFreshSpawnStrategy_1_21_plus`
- `EntityReplacementStrategy`
  - `LegacyReplacementStrategy_1_8_to_1_13`
  - `ModernReplacementStrategy_1_14_to_1_20`
  - `PaperChunkSystemReplacementStrategy_1_21_plus`
- `WorldAddStrategy`
  - `LegacyWorldAddStrategy`
  - `ModernWorldAddStrategy`
  - `PaperWorldAddStrategy_1_21_plus`
- `TrackingBindingStrategy`
  - `LegacyTrackingBindingStrategy`
  - `ModernTrackingBindingStrategy`
  - `PaperTrackingBindingStrategy_1_21_plus`

### Shared Runtime Models

- `VersionCapabilities`
  - `hasWorldCoordConstructor`
  - `usesEntityTypeConstructor`
  - `hasSeparateTrackerState`
  - `hasPaperChunkSystem`
  - `supportsSectionEntityStorage`
- `VersionBindings` or equivalent bundle for reflection/NMS handles required by strategies
- `StrategySelectionContext` or equivalent runtime selector input
- `EntityStrategyBundle` or equivalent composition root used by factories/adapters

## Acceptance Gates

1. Fresh spawn on both `1.8.8` and `1.21.11` delegates through shared runtime strategies, not version-local end-to-end logic.
2. Constructor-first spawn remains the shared default path for fresh entities on both supported versions.
3. Replacement remains available for attach/existing-entity flows and is not silently reused as the normal fresh-spawn path.
4. Adapter discovery/bootstrapping still works for both supported versions.
5. No regression in sample-plugin runtime behavior for:
   - `/entitydemo orbit`
   - `/entitydemo deathfx cow`
6. AI verification passes:
   - reactive mob behavior still occurs after spawn and damage
   - no double-add / double-track / duplicate registration errors
7. Tests and packaging commands succeed.

## Execution Strategy

Implement the refactor in waves so the project stays shippable after each phase. Keep version adapters thin early, then move behavior into shared runtime strategies one family at a time.

---

## Wave 1 - Runtime seams and delegation scaffolding

- [x] T01 - Introduce capability and binding models
- **Goal:** Create shared runtime models that describe version capabilities and package version-specific handles/bindings for downstream strategies.
- **References:** `VersionedEntityPlatform.java`, `ReflectionSupport.java`, both version factories.
- **Likely files:** new files under `versions/runtime/.../capability/` and `.../model/`; small adapter/factory touchpoints.
- **Parallelizable:** Yes, with T03.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** Shared models exist, compile cleanly, and can express the current `1.8.8` and `1.21.11` constructor/tracker/world differences without adapter-local ad hoc flags.

- [x] T02 - Introduce strategy interfaces and selection/composition scaffolding
- **Goal:** Add runtime abstractions for spawn/replacement/world/tracking plus a single place that selects strategy bundles from capabilities.
- **References:** `SpigotEntityBootstrap.java`, `EntityAdapterDiscovery.java`, `EntityAdapterRegistry.java`, `VersionedEntityPlatform.java`.
- **Likely files:** new files under `versions/runtime/.../strategy/`, `.../selection/`, `.../factory/`.
- **Parallelizable:** Starts after T01 shape is agreed; can proceed while T03 runs.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** One central composition/selection path exists; no strategy selection logic is duplicated inside exact-version adapters.

- [x] T03 - Add runtime test seams and fixtures for strategy-level verification
- **Goal:** Extend runtime tests so strategy selection/delegation can be verified without requiring full NMS execution.
- **References:** `VersionedEntityPlatformTest.java`, `RuntimeNativeEntityLifecycleTest.java`, `SpigotEntityBootstrapTest.java`.
- **Likely files:** existing runtime tests plus helper fixtures/support classes.
- **Parallelizable:** Yes.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** Tests can assert which strategy bundle is selected for `1.8.8` vs `1.21.11` and protect against adapter rediscovering old inline logic.

- [x] T04 - Thin exact-version adapters/factories to orchestration entrypoints
- **Goal:** Refactor `SpigotEntityAdapterV1_8_8`, `SpigotEntityAdapterV1_21_11`, and their factories so they expose capabilities/bindings and delegate orchestration instead of owning every lifecycle step.
- **References:** both adapter files, both factory files.
- **Dependencies:** T01, T02.
- **Parallelizable:** No, but `1.8.8` and `1.21.11` substeps can be split across agents once the contract is fixed.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** Factories/adapters compile and still route existing behavior through the new orchestration seam even before later extraction is complete.

---

## Wave 2 - Fresh spawn migration (highest-value behavior)

- [x] T05 - Extract `1.21.11` fresh spawn into `PaperFreshSpawnStrategy_1_21_plus`
- **Goal:** Move the current constructor-first modern spawn path into a shared Paper-oriented strategy.
- **References:** `EntityFactoryV1_21_11.java`, `GeneratedNativeEntityClassFactory.java`, `RuntimeNativeEntityLifecycle.java`.
- **Dependencies:** T01, T02, T04.
- **Parallelizable:** Yes, with T06.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** `1.21.11` fresh spawn uses the shared Paper strategy; no fresh-spawn path relies on post-spawn handle swapping; orbit/deathfx behavior remains intact.

- [x] T06 - Extract `1.8.8` fresh spawn into `LegacyFreshSpawnStrategy_1_8_to_1_12`
- **Goal:** Port the BKCommonLib-style constructor-first spawn model to legacy support and stop using replacement as the normal fresh-spawn path.
- **References:** `EntityFactoryV1_8_8.java`, `wiki/wiki/CustomEntities_AILossInvestigation.md`, current `1.21.11` constructor-first implementation as the in-repo modern reference point.
- **Dependencies:** T01, T02, T04.
- **Parallelizable:** Yes, with T05.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** `1.8.8` fresh spawn delegates through the shared legacy strategy and preserves reactive AI for representative mobs.

- [x] T07 - Wire spawn strategy selection end to end
- **Goal:** Make exact-version adapters/factories consume the selected shared fresh-spawn strategies and remove stale inline spawn branches.
- **References:** `VersionedEntityPlatform.java`, both adapters, both factories.
- **Dependencies:** T05, T06.
- **Parallelizable:** Limited.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** A single strategy-selection path determines fresh spawn behavior for both supported versions; no duplicate factory-local spawn orchestration remains.

- [x] T08 - Add fresh-spawn regression coverage and sample-plugin QA notes
- **Goal:** Protect the constructor-first behavior and document exact QA scenarios that prove AI/regression safety.
- **References:** runtime tests, `EntityFactoryV1_21_11Test.java`, `SpigotEntityAdapterV1_8_8Test.java`, sample plugin command/service files.
- **Dependencies:** T05, T06, T07.
- **Parallelizable:** Yes, tests and QA-note updates can split.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** Regression tests cover fresh-spawn delegation; QA script includes `deathfx cow` and `orbit` on both versions with expected outcomes.

---

## Wave 3 - Replacement, world add, and tracking extraction

- [x] T09 - Extract replacement strategy family
- **Goal:** Move attach/existing-entity replacement logic out of version factories into shared replacement strategies by era.
- **References:** `FieldCopySupport.java`, `EntityFactoryV1_8_8.java`, `EntityFactoryV1_21_11.java`, attached lifecycle classes.
- **Dependencies:** T01, T02, T04.
- **Parallelizable:** Yes, with T10/T11 once contracts are fixed.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** Replacement remains available for attach paths, clearly separated from fresh spawn, and version-local replacement behavior is expressed through strategy implementations.

- [x] T10 - Extract world-add strategy family
- **Goal:** Encapsulate the actual server/world registration flow by era rather than leaving it embedded inside factories.
- **References:** current factory add-to-world logic, lifecycle classes, bootstrap/runtime platform touchpoints.
- **Dependencies:** T01, T02, T04.
- **Parallelizable:** Yes.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** World-add behavior is owned by dedicated strategies; 1.8-era and 1.21 Paper-era differences are isolated and testable.

- [x] T11 - Extract tracking binding strategy family
- **Goal:** Isolate tracker entry/state rebinding and modern Paper-specific tracker handling from the rest of lifecycle orchestration.
- **References:** current `1.21.11` tracker/state handling, runtime network state classes, `VersionedEntityPlatform.java`.
- **Dependencies:** T01, T02, T04.
- **Parallelizable:** Yes.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** Tracker binding is no longer coupled to spawn/replacement logic and can be validated independently for both supported versions.

- [x] T12 - Compose final strategy bundles and remove duplicated version-local orchestration
- **Goal:** Finish the transition so factories/adapters primarily assemble bindings and delegate to a composed runtime strategy bundle.
- **References:** all strategy families and both version factories/adapters.
- **Dependencies:** T09, T10, T11.
- **Parallelizable:** No.
- **Recommended agent profile:** `deep`
- **Acceptance criteria:** Duplicated lifecycle logic has been removed or reduced to version-specific binding shims; orchestration lives in shared runtime code.

- [x] T13 - Add replacement/world/tracking regression tests
- **Goal:** Cover attach/existing-entity behavior, registration safety, and tracking invariants after extraction.
- **References:** runtime tests, adapter tests, factory tests.
- **Dependencies:** T09, T10, T11, T12.
- **Parallelizable:** Yes.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** Tests fail if replacement becomes the default fresh-spawn path again, if world-add duplicates occur, or if tracker binding regresses.

---

## Wave 4 - Documentation, verification, cleanup

- [x] T14 - Update architecture documentation and migration notes
- **Goal:** Document the new hybrid architecture, strategy families, capability model, and explicit boundary between fresh spawn and replacement.
- **References:** `.sisyphus/drafts/entity-version-handler-architecture.md`, `wiki/wiki/CustomEntities_AILossInvestigation.md`, any runtime docs worth preserving.
- **Dependencies:** T12.
- **Parallelizable:** Yes, with late test stabilization.
- **Recommended agent profile:** `writing`
- **Acceptance criteria:** A maintainer can locate where to add support for a new era/capability family without rediscovering factory internals.

- [ ] T15 - Run verification matrix and sample-plugin QA
- **Goal:** Execute the planned build/test/runtime checks and ensure both supported versions behave correctly.
- **Dependencies:** T08, T13, T14.
- **Parallelizable:** Partially; unit/build runs can parallelize, manual QA remains sequential.
- **Recommended agent profile:** `unspecified-high`
- **Acceptance criteria:** All planned commands pass; sample-plugin QA succeeds on both versions; no double-add, missing adapter, or AI-loss regressions remain.

- [ ] T16 - Final cleanup and commit batching
- **Goal:** Remove dead duplicated helpers only after verification, then stage implementation into logical commits.
- **Dependencies:** T15.
- **Parallelizable:** No.
- **Recommended agent profile:** `quick`
- **Acceptance criteria:** Final diff is structurally coherent, no broken dead code remains, and commit boundaries reflect architecture vs behavior vs tests/docs.

---

## Dependency Matrix

| Task | Depends On | Can Run In Parallel With | Outputs |
|---|---|---|---|
| T01 | — | T03 | capability + binding model |
| T02 | T01 (contract shape) | T03 | strategy interfaces + selector |
| T03 | — | T01, T02 | runtime strategy test seams |
| T04 | T01, T02 | version-specific substeps can split | thin adapters/factories |
| T05 | T01, T02, T04 | T06 | shared Paper fresh-spawn strategy |
| T06 | T01, T02, T04 | T05 | shared legacy fresh-spawn strategy |
| T07 | T05, T06 | limited | end-to-end spawn delegation |
| T08 | T05, T06, T07 | docs/test substeps | fresh-spawn regression coverage |
| T09 | T01, T02, T04 | T10, T11 | replacement strategy family |
| T10 | T01, T02, T04 | T09, T11 | world-add strategy family |
| T11 | T01, T02, T04 | T09, T10 | tracking strategy family |
| T12 | T09, T10, T11 | — | final runtime strategy composition |
| T13 | T09, T10, T11, T12 | test slices | replacement/world/tracking regressions |
| T14 | T12 | T15 prep | architecture docs |
| T15 | T08, T13, T14 | build/test splits only | verification evidence |
| T16 | T15 | — | cleanup + commit-ready diff |

## Task-Level QA Map

| Task | Tool / Command | Steps | Expected Result |
|---|---|---|---|
| T01 | `mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest test` | add capability/binding models, then run focused runtime tests | new models compile; focused runtime tests stay green |
| T02 | `mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest test` | add strategy interfaces/selection path and rerun runtime tests | selection scaffolding compiles and adapter/runtime tests still pass |
| T03 | `mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest,RuntimeNativeEntityLifecycleTest,SpigotEntityBootstrapTest test` | add test seams/fixtures and run only affected runtime tests | new seams work without requiring NMS-heavy integration setup |
| T04 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` | thin adapters/factories, then run runtime + version-module tests | both exact-version modules still resolve/bootstrap through the new orchestration seam |
| T05 | `mvnw.cmd -pl versions/1.21.11 -am -Dtest=EntityFactoryV1_21_11Test test` | move modern fresh spawn into Paper strategy, then run focused 1.21.11 tests | 1.21.11 constructor-first behavior remains green in focused tests |
| T06 | `mvnw.cmd -pl versions/1.8.8 -am -Dtest=SpigotEntityAdapterV1_8_8Test test` | implement legacy constructor-first fresh spawn, then run focused 1.8.8 tests | 1.8.8 adapter/factory behavior compiles and focused tests stay green |
| T07 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` | wire end-to-end spawn selection and run combined module tests | shared spawn selection path is active for both supported versions |
| T08 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` plus sample-plugin QA | add fresh-spawn regressions; manually run `/entitydemo orbit` and `/entitydemo deathfx cow` on both versions | tests pass; sample-plugin commands succeed; reactive AI remains intact |
| T09 | `mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest test` | extract replacement strategies and rerun attach/cache lifecycle tests | attach/replacement still passes `shouldCacheAttachedEntitiesByBukkitIdentity` and `shouldEvictRemovedAttachedEntitiesAndAttachAgain` |
| T10 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` | extract world-add strategies and rerun affected module tests | no world-add regressions or compile failures in version modules |
| T11 | `mvnw.cmd -pl versions/runtime,versions/1.21.11 -am test` | extract tracking strategies and rerun runtime + 1.21.11 tests | tracker binding remains correct and 1.21.11 tests stay green |
| T12 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` | compose final strategy bundles and remove duplicated orchestration | all three modules still pass after final wiring |
| T13 | `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` | add replacement/world/tracking regressions and execute full targeted suite | regressions fail on duplicate registration/tracking drift and pass on intended behavior |
| T14 | documentation review + `mvnw.cmd -pl versions/runtime -am test` | update docs, then rerun a quick safety test slice | docs match implementation naming and no accidental code drift was introduced |
| T15 | `mvnw.cmd clean test` and `mvnw.cmd -pl test-plugin -am package -DskipTests` plus manual QA | run full test suite, package sample plugin, then manually verify `orbit` and `deathfx cow` on `1.8.8` and `1.21.11` | full suite passes; packaging succeeds; sample-plugin runtime behavior is correct on both versions |
| T16 | `git diff --stat` + rerun failing/last-changed test command from T15 if needed | remove dead code only after green verification and inspect final diff scope | final diff is coherent, no dead branches remain, and verification stays green |

## Verification Matrix

### Unit / module tests
- `mvnw.cmd -pl versions/runtime -am test`
- `mvnw.cmd -pl versions/1.8.8 -am test`
- `mvnw.cmd -pl versions/1.21.11 -am test`
- Add/adjust any strategy-selection and regression tests introduced by the refactor.

### Packaging / integration checks
- `mvnw.cmd -pl test-plugin -am package -DskipTests`
- before merge: `mvnw.cmd clean test`

### Manual QA scenarios
- `1.21.11`: `/entitydemo orbit` spawns once, no double-add/tracking errors.
- `1.21.11`: `/entitydemo deathfx cow` cow still reacts/panics after damage.
- `1.8.8`: `/entitydemo orbit` spawns once and adapter routing still works.
- `1.8.8`: `/entitydemo deathfx cow` reactive AI remains intact after damage.
- attach/existing-entity flow is verified through `VersionedEntityPlatformTest` (`shouldCacheAttachedEntitiesByBukkitIdentity`, `shouldEvictRemovedAttachedEntitiesAndAttachAgain`) plus any new attach regressions added in T13.

## Suggested Commit Strategy

1. `refactor(entity-runtime): add capability and strategy selection scaffolding`
2. `refactor(entity-runtime): migrate shared fresh spawn strategies for 1.8.8 and 1.21.11`
3. `refactor(entity-runtime): extract replacement world-add and tracking strategies`
4. `test(entity-runtime): add regression coverage for spawn replacement and tracking`
5. `docs(entity-runtime): document version handler architecture`

## Risks to Watch During Execution

- Over-abstracting incompatible legacy/modern behavior into one strategy implementation.
- Reintroducing post-spawn replacement as the normal fresh-spawn path on legacy versions.
- Paper-specific tracker or chunk-system handling leaking into generic modern strategies.
- Breaking adapter discovery/bootstrapping while thinning factories.
- Hidden coupling between world-add and tracking that only surfaces during manual QA.

## Explicit Exceptions / Known Open Questions

- `1.8.8` and `1.12` may not fully share every implementation detail even if they share the same strategy family; allow version-local binding shims.
- If world-add and tracking prove inseparable in practice, combine them under a single registration strategy **after** proving the simpler split is invalid.
- Keep any exact-version-only helper that is truly required by mapping differences, but force lifecycle orchestration to stay shared.
