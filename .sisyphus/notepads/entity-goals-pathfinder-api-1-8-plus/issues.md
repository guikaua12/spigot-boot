# Issues

## 2026-04-15 Task: T1 research caveats
- No existing in-repo goal/pathfinder public API exists yet, so Task 1 must establish naming and test patterns from adjacent API contracts rather than extending an existing goals package.
- Search follow-up found runtime/API test anchors, but direct `SpawnBuilder` / `ControlledEntity` public-contract coverage appears sparse and will likely need new tests rather than adapting many existing ones.

## 2026-04-15 Task: T1 implementation blocker
- `lsp_diagnostics` could not run for the changed Java files because `jdtls` is configured but not installed in this environment, so Maven compile/test output was used as the verification fallback.

## 2026-04-15 Task: T2 verification blocker
- lsp_diagnostics still could not run because jdtls is configured but not installed in this environment, so Maven remained the verification fallback for changed Java files.
- The first .mvnw.cmd -pl versions/api -am test attempt exposed malformed/stale classfile metadata during test compilation; rerunning with clean test rebuilt the module successfully and all tests passed, so the issue was stale build state rather than the Task 2 source changes.

## 2026-04-15 Task: T3 verification blocker
- `lsp_diagnostics` still could not run because `jdtls` is configured but not installed in this environment, so Task 3 verification relied on `./mvnw.cmd -pl versions/runtime -am test` instead.

## 2026-04-15 Task: T4 verification blocker
- `lsp_diagnostics` remains unavailable because `jdtls` is configured but not installed in this environment, so Task 4 verification relied on `./mvnw.cmd -pl versions/runtime -am test` after the changed runtime and test files compiled successfully.

## 2026-04-15 Task: T5 verification blocker
- `lsp_diagnostics` still could not run because `jdtls` is configured but not installed in this environment, so Task 5 verification relied on Maven compilation/tests instead.
- The requested `./mvnw.cmd -pl versions -am test` command succeeds only against the parent `versions` aggregator in this checkout; real source verification required the additional `./mvnw.cmd -pl versions/runtime -am test` run.

## 2026-04-15 Task: T9 review findings
- Review found a blocking semantic gap: versions/1.21.11/.../LatestEntityGoalSupportV1_21_11 advertises full latest-family goal support but both spawn and attach executor factories return RuntimeGoalMutationExecutor.noop(...), so no native selector mutations are applied for builder-time spawn goals or queued attached mutations.
- Review found a coverage gap: the new 1.21.11 tests only assert managed snapshots / queued batches, and the queue test swaps in RecordingGoalExecutor, so they do not prove real latest-family custom-goal add/remove or selector mutation behavior.


## 2026-04-15 Task: T8 verification blocker
- lsp_diagnostics / jdtls remained unavailable for the changed Java files in this environment, so Task 8 verification relied on the requested Maven test commands for both modern modules instead.

## 2026-04-15 Task: T6 verification blocker
- `lsp_diagnostics` still could not run because `jdtls` is configured but not installed in this environment, so Task 6 verification relied on the required Maven commands for the legacy modules and targeted attach/replacement tests instead.

## 2026-04-15 Cross-task blocker after Wave 2
- The root `./mvnw.cmd clean test` run is currently failing in `core` with `package tech.guilhermekaua.spigotboot.utils does not exist` even though the `utils` module still contains that package; this points to an unrelated root build/dependency wiring issue outside the goal-system change surface.
- Because Tasks 10-12 and the final wave all require a green root build, this unrelated repo-state/build blocker must be resolved before the remaining plan tasks can be truthfully completed.

## 2026-04-15 Cross-task blocker update
- The root `./mvnw.cmd clean test` reactor passed on rerun after the exact-version wave and shared-contract tests were in place, so the earlier `core` → `utils` compile failure appears to have been transient/stale build state rather than a persistent blocker.

## 2026-04-15 Task: T8 spawn hook verification blocker
- jdtls / lsp_diagnostics remained unavailable for the changed Java files, so this modern-family spawn-hook fix was verified with Maven test-compile and the two required Maven test commands instead.
