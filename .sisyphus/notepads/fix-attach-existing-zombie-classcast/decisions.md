# Decisions — fix-attach-existing-zombie-classcast

## [plan] Architecture decisions locked in before execution

- **Fix mechanism**: Additive type-filtered `findFieldOfType`/`requireFieldOfType` in `ReflectionSupport` (no behavior change to existing `findField`/`requireField`).
- **ReflectionSupport iteration order**: UNCHANGED. Per-class outer × per-name inner preserved.
- **ScenarioRecorder fix**: Producer-side only. Delete `Boolean.FALSE` pre-populations at `EntityDemoService:473,504` + `putIfAbsent("controllerTickObserved", Boolean.FALSE)` in `scheduleCompletion` before `recorder.complete()`.
- **`containsFailureSignal`**: NEVER modified. Preserves `entityIdStable`/`trackerRebound`/`aiReactedAfterHit`/`goalMutationReplacedExistingEntry` contracts.
- **Deadline widening**: `scheduleCompletion(recorder, 10L)` at `attach-existing-zombie` site → `30L` (matches viewer-cycle line 455). ONLY that one call site.
- **Vehicle type filter in EntityFactoryV1_16_5**: Resolve `net.minecraft.server.v1_16_R3.Entity` via `Class.forName` with `null` → `Object.class` fallback in tests (where NMS jar is absent).
- **Vehicle type filter in EntityFactoryV1_19_2**: Same pattern, class `net.minecraft.world.entity.Entity` (Mojang-mapped).
- **Out-of-scope factories**: 1.13.2 (deliberate 1.8→1.9 fallback), 1.17.1 (already safe), 1.21.11 (single Mojang), 1.8.8 (no reflection). NEVER touch in this plan.

## [plan] Commit strategy

Four commits, one per implementation task:
1. `feat(runtime): add type-filtered field lookup helpers to ReflectionSupport`
2. `fix(test-plugin): prevent ScenarioRecorder observation-marker trap`
3. `fix(1.16.5): resolve passengers/vehicle fields by type to avoid DataWatcherObject collision`
4. `fix(1.19.2): resolve passengers/vehicle fields by type to avoid au-token collision`
