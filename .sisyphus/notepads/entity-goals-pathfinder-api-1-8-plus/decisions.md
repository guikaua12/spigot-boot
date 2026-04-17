# Decisions

## 2026-04-15 Task: T1 public API defaults
- The new package should live under `versions/api/src/main/java/tech/guilhermekaua/spigotboot/versions/api/goal/`.
- Public contract should mirror existing API style: final immutable core type(s), typed enums/keys, explicit result and exception types, static factory/builder entry points, and no NMS/Paper imports.
- Stable vanilla goal identifiers will be library-owned (`VanillaGoalKey`) rather than inherited from upstream APIs.
- Runtime-facing mutation semantics should be represented by explicit public result/exception objects rather than exposing selector internals.

## 2026-04-15 Task: T1 implementation choices
- `VanillaGoalKey` was frozen to exactly seven v1 entries: `FLOAT`, `MELEE_ATTACK`, `RANDOM_STROLL_LAND`, `LOOK_AT_PLAYER`, `RANDOM_LOOK_AROUND`, `HURT_BY_TARGET`, and `NEAREST_ATTACKABLE_TARGET`.
- `CustomGoalKey` uses `namespace:value` formatting and validation to match existing immutable id-style contracts without leaking implementation details.
- `GoalProfile.Builder` replaces prior managed entries only within the same selector and key family, allowing later runtime code to consume a stable deduplicated snapshot.
- `GoalOperationException` / `UnsupportedGoalOperationException` carry selector, managed key, optional Bukkit entity type, and optional `MinecraftVersion` so unsupported entity/version/key failures can stay explicit without exposing NMS classes.

## 2026-04-15 Task: T4 runtime-core choices
- The real runtime-backed `ControlledEntity.goalManager()` should be overridden in `AbstractRuntimeControlledEntity`, not reintroduced through public API changes, so spawned and attached handles automatically converge on one internal orchestration path.
- Task 4 stops at a backend-ready seam: `RuntimeGoalMutationExecutor` provides an initial recognized snapshot plus deferred batch execution, while exact-version selector snapshotting and mutation backends remain for Task 5+.
- Spawned lifecycles initialize the shared manager from `template.goalProfile()`, while attached lifecycles default to an empty recognized snapshot unless a runtime executor supplies a real attached snapshot.

## 2026-04-15 Task: T5 seam wiring choices
- The optional cross-version seam lives entirely under `versions/runtime` through `VersionGoalSupportProvider`, `VersionGoalSupportMetadata`, and `EntityGoalSupportBundle*` types; `versions/api` and exact-version modules stay unchanged in this task.
- `EntityGoalSupportBundle` owns the fallback behavior for spawned and attached executors so `VersionedPlatform` can stay a composition root that resolves once and delegates, rather than reintroducing `RuntimeGoalMutationExecutor.noop(...)` conditionals at every lifecycle creation site.
- Goal-support family ids are version-range driven only when a provider exists; missing providers resolve to `UNSPECIFIED`, which preserves backward compatibility for all current adapters until family backends arrive in later tasks.

## 2026-04-15 Task: T7 transitional-family 1.16.5 choices
- The `1.16.5` backend stays version-local through `EntityGoalSupportV1_16_5`, which owns supported-key metadata, attached snapshots, native-handle binding, and reflective selector mutation without changing `versions/api`.
- `EntityFactoryV1_16_5` now binds goal support at the same exact-version seam as hook binding: freshly spawned generated handles and attached replacement handles both hand their native handle to the shared-runtime executor before runtime mutations flush.
- The new zombie regression covers both selectors through the shared runtime contract: builder-time spawn applies normal `FLOAT` plus target `HURT_BY_TARGET`, while attached mutation removes a normal recognized vanilla goal, preserves an unknown external goal, and adds a managed custom target goal.

## 2026-04-15 Task: T8 modern-family 1.17.1 + 1.19.2 choices
- The modern family stays inside exact-version scope: both adapters now publish goal-support metadata/executor factories, and both factories keep selector reflection, managed snapshotting, and builder-time application internal without touching the shared runtime architecture.
- The new *GoalAttach* regressions assert queued managed state through RuntimeAttachedEntityLifecycle.goalManager() but simulate the version-local safe-tick application with the factory helper that the executor delegates to, which keeps the test focused on Task 8’s exact-version glue instead of re-testing the shared runtime queue contract already covered in ersions/runtime.

## 2026-04-15 Task: T6 legacy-family 1.8.8 + 1.13.2 choices
- Both exact-version adapters now opt into Task 5 through `VersionGoalSupportProvider`; the shared runtime still owns queueing and deterministic managed snapshots, while each legacy module owns reflective selector sync locally.
- The legacy backends synchronize live selectors to the runtime batch’s final `managedGoals()` snapshot instead of replaying version-local queue logic, which lets add/remove/clear preserve unknown goals while still replacing stale recognized entries by key.
- Custom goal support stays internal to the legacy modules through generated no-op `PathfinderGoal` subclasses that carry only the managed `CustomGoalKey`, keeping the public API unchanged and avoiding selector collisions with the frozen seven vanilla mappings.

## 2026-04-15 Task: T12 sample-plugin exercise path choices
- Keep Task 12 scoped to `test-plugin` by adding only one new discoverable command (`goal-builder-zombie`) and reusing `attach-existing-zombie` as the attached-entity mutation exercise path.
- Make the player-facing attach demo goal-focused by removing controller-driven movement from that path and demonstrating a clear public API mutation sequence (`addVanilla` -> `removeVanilla` -> `addVanilla`) on the attached zombie.
- Preserve the existing matrix/headless attach scenario unchanged so the regression-only sample change does not reshape unrelated demo or test infrastructure.

## 2026-04-15 Task: T12 verification unblock choices
- Fix the legacy package blocker in `versions/runtime/pom.xml` by keeping the core relocated Javassist API classes instead of weakening or skipping `versions-v1_13_2` tests.
- Fix `commands-config-spigot` clean-test reliability by declaring `spigot-boot-config` directly in `platform-spigot/commands-config-spigot/pom.xml`, matching the module's real compile/runtime usage of config-core types.
- Treat the required Task 12 verifier commands as sequential operations on one workspace, not parallel commands, because `clean test` can invalidate another in-flight reactor build's outputs.

## 2026-04-15 Task: T8 modern-family spawn hook fix choices
- ResolvedSpawnMetadata in 1.19.2 now carries generated bridge metadata (ResolvedReplacementMetadata) so the fresh-spawn path can instantiate the generated subtype and bind hook interception before applying managed goals, matching the working 1.17.1 lifecycle behavior without changing shared runtime APIs.
- The new 1.19.2 spawned-goal regression lives in the exact-version factory test and checks native selector state before and after a spawned 	ick() call, which verifies live flush behavior at the exact-version seam instead of only reasserting shared runtime queue semantics.
