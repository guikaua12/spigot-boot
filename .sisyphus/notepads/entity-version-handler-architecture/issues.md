## 2026-04-12T23:34:23.210Z Task: startup-analysis
Primary hidden coupling: 1.8.8 can change entity identity mid-flow during replacement, so any new abstraction that exposes a pre-swap entity instance risks stale references and lost state.
Secondary risk: shared imports or early binding resolution could accidentally touch wrong-version classes before bootstrap has selected the adapter.

## 2026-04-12T20:58:09-03:00 Task: T01
Java LSP diagnostics could not run in this environment because jdtls is not installed; Maven test/build remains the available verification path.

## 2026-04-12T23:59:00.000Z Task: T01-capability-binding-seam
Java LSP diagnostics could not be used in this environment because jdtls is not installed, so verification relied on clean Maven compilation plus targeted tests instead.
The requested Maven selector `mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest test` currently fails in the upstream `versions/api` module because Surefire treats missing matching tests there as fatal; runtime verification succeeded with the same selector plus `-Dsurefire.failIfNoSpecifiedTests=false`.

## 2026-04-12T21:14:12-03:00 Task: T02
Focused test reruns can report stale `NoClassDefFoundError` if runtime classes were not recompiled before Surefire executes; a package/recompile or subsequent rerun resolves this in the current Maven flow.

## 2026-04-13T00:10:00.000Z Task: T02-strategy-selection-scaffolding
Java LSP diagnostics are still unavailable in this environment because `jdtls` is not installed, so validation again had to rely on Maven compile/test/package results.
On PowerShell, the focused Maven selector and Surefire reactor guard must be passed through `mvnw.cmd --% ...` or the shell rewrites the comma-separated `-Dtest=...` argument and the dotted `-Dsurefire.failIfNoSpecifiedTests=false` property before Maven sees them.
The runtime-slice package build generated a root-level `core/dependency-reduced-pom.xml` artifact as a side effect; it was removed so the task leaves only intentional scaffolding changes behind.

## 2026-04-12T21:21:58-03:00 Task: T03
Java LSP diagnostics remain unavailable in this environment because `jdtls` is not installed; test verification continues to rely on Maven only.

## 2026-04-13T01:16:30-03:00 Task: T15
Hands-on sample-plugin QA cannot be executed in this environment because there are no local server runtime artifacts or directories for either supported version (no Paper/Spigot jars, no `server.properties`, no `plugins/` server tree).

## 2026-04-13T00:20:00.000Z Task: T03-runtime-test-seams-and-fixtures
Java LSP diagnostics remain unavailable here because `jdtls` is not installed, so test validation again depended on targeted Maven execution and a runtime-module package build.
The focused selector still needs the Surefire reactor guard in this multi-module build; the passing invocation was `cmd /c "mvnw.cmd -pl versions/runtime -am -Dtest=VersionedEntityPlatformTest,RuntimeNativeEntityLifecycleTest,SpigotEntityBootstrapTest -Dsurefire.failIfNoSpecifiedTests=false test"`.

## 2026-04-13T00:33:30.000Z Task: T04-thin-exact-version-entrypoints
Java LSP diagnostics are still unavailable in this environment because `jdtls` is not installed, so verification again depended on Maven results instead.
After introducing the new shared runtime entrypoint type, an incremental `mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test` run initially hit stale 1.21.11 test/runtime output; `cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am clean test"` refreshed the slice, and the exact required test command then passed cleanly on rerun.

## 2026-04-13T00:43:20.000Z Task: T04-non-clean-verification-fix
The runtime module still had a generated `versions/runtime/dependency-reduced-pom.xml` from prior package work, which is the kind of stale shade side effect that can destabilize later incremental/non-clean verification even when the module dependency graph itself is correct.
Validation for this fix explicitly covered the package-then-test scenario: `cmd /c "mvnw.cmd -pl versions/runtime -am package -DskipTests"` followed by `cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"`, and both commands passed.

## 2026-04-13T01:10:00.000Z Task: T05-paper-fresh-spawn-extraction
Java LSP diagnostics remain unavailable here because `jdtls` is not installed, so Java verification for this task again depended on Maven test/package results instead of language-server diagnostics.
The targeted `versions/1.21.11` rerun initially picked up stale incremental output after source edits and produced unresolved-compilation-problem failures at test runtime; `./mvnw.cmd --% -pl versions/runtime,versions/1.21.11 -am clean -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest,RuntimeNativeEntityLifecycleTest,EntityFactoryV1_21_11Test,SpigotEntityAdapterV1_21_11Test -Dsurefire.failIfNoSpecifiedTests=false test` refreshed the slice and passed.
This working tree already contains unrelated pre-existing changes outside the T05 scope, so implementation review and verification had to stay focused on the runtime paper fresh-spawn files and their targeted tests rather than the full local diff.

## 2026-04-12T22:39:00-03:00 Task: T06-legacy-fresh-spawn-extraction
Java LSP diagnostics remain unavailable here because `jdtls` is not installed, so validation again relied on Maven compilation and test runs instead of language-server diagnostics.
The guarded legacy fallback cannot safely extend past pre-registration preparation/native creation: once `bindRuntimeLifecycle(...)` binds the hook binder into the runtime handle, retrying the same lifecycle through the replacement path would violate the one-shot binder contract.
Passing verification for this task required both the focused legacy/runtime slice (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8 -am clean -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest,RuntimeNativeEntityLifecycleTest,SpigotEntityAdapterV1_8_8Test -Dsurefire.failIfNoSpecifiedTests=false test"`) and the requested cross-module command (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"`).

## 2026-04-13T02:12:00.000Z Task: T07-wire-spawn-strategy-selection-end-to-end
Java LSP diagnostics are still unavailable here because `jdtls` is not installed, so this task again depended on Maven for verification.
The new fallback regression test initially failed because the legacy recovery path still triggers the template network controller's Bukkit bind callback before initializer/spawn; the assertion had to reflect that existing lifecycle order rather than assuming the fallback skipped `bind-bukkit`.

## 2026-04-13T02:52:00.000Z Task: T09-replacement-orchestration-extraction
Java LSP diagnostics are still unavailable in this environment because `jdtls` is not installed; explicit `lsp_diagnostics` calls against the modified Java source trees failed for that reason, so verification again relied on Maven compilation and tests instead.
Passing verification for this task required both the focused clean reactor slice (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am clean -Dtest=VersionedEntityPlatformTest,RuntimeNativeEntityLifecycleTest,SpigotEntityBootstrapTest,SpigotEntityAdapterV1_8_8Test,SpigotEntityAdapterV1_21_11Test,EntityFactoryV1_21_11Test -Dsurefire.failIfNoSpecifiedTests=false test"`) and the requested broad cross-module command (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"`).

## 2026-04-13T03:22:00.000Z Task: T10-world-add-strategy-extraction
Java LSP diagnostics remain unavailable here because `jdtls` is not installed, so the required diagnostics step again failed at the tooling level and verification had to rely on Maven compilation plus targeted and broad reactor tests instead.
This working tree still contains unrelated pre-existing edits outside T10 scope, so git diff output was noisy during review/context gathering; verification and implementation checks stayed focused on the runtime/entity strategy files touched for world-add extraction.

## 2026-04-13T00:41:30-03:00 Task: T11-tracking-strategy-extraction
Java LSP diagnostics remain unavailable in this environment because `jdtls` is not installed, so the required diagnostics step again failed at the tooling level and verification relied on Maven instead.
Passing verification for T11 used both a focused clean reactor slice (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am clean -Dtest=VersionedEntityPlatformTest,RuntimeNativeEntityLifecycleTest,SpigotEntityBootstrapTest,SpigotEntityAdapterV1_8_8Test,SpigotEntityAdapterV1_21_11Test -Dsurefire.failIfNoSpecifiedTests=false test"`) and the required broad command (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"`), and both passed.

## 2026-04-13T00:57:00-03:00 Task: T12-final-orchestration-consolidation
Java LSP diagnostics still could not run because `jdtls` is not installed, so edited-file diagnostics failed at the tooling layer and validation again relied on Maven clean test, the required broad reactor test command, and a follow-up package build.
The selector/factory consolidation itself stayed low risk: focused verification passed with `cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am clean -Dtest=VersionedEntityPlatformTest,RuntimeNativeEntityLifecycleTest,SpigotEntityBootstrapTest,SpigotEntityAdapterV1_8_8Test,SpigotEntityAdapterV1_21_11Test,EntityFactoryV1_21_11Test -Dsurefire.failIfNoSpecifiedTests=false test"`, and the required `cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"` command remained green.

## 2026-04-13T01:05:00-03:00 Task: T13-replacement-world-tracking-regressions
Java LSP diagnostics remain unavailable in this environment because `jdtls` is not installed, so the mandatory edited-file diagnostics step still failed at the tooling layer and verification relied on Maven instead.
Passing verification for T13 used both a focused clean slice (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.21.11 -am clean -Dtest=VersionedEntityPlatformTest,SpigotEntityBootstrapTest,EntityFactoryV1_21_11Test -Dsurefire.failIfNoSpecifiedTests=false test"`) and the required broad cross-module command (`cmd /c "mvnw.cmd -pl versions/runtime,versions/1.8.8,versions/1.21.11 -am test"`).

## 2026-04-13T12:00:00.000Z Task: T14-architecture-documentation-and-migration-notes
This task was documentation-only, so code-oriented verification steps such as Java LSP diagnostics and Maven reactor tests were not applicable to the changed files.
The main risk was documentation drift against the final T12 architecture, which was checked by reading the selector, capability and binding models, shared strategy classes, and exact-version adapters before editing the wiki page.

## 2026-04-13T11:58:00-03:00 Task: remove-ai-slops
Java LSP diagnostics still could not run because `jdtls` is not installed, so edited-file diagnostics again failed at the tooling layer and verification relied on targeted Maven tests plus a package build.
The focused Maven test selector worked through `cmd /c "mvnw.cmd -pl versions/runtime,versions/api -am clean -Dtest=CustomEntityDefinitionTest,VersionedEntityPlatformTest -Dsurefire.failIfNoSpecifiedTests=false test"`; the earlier attempt to pass PowerShell's `--%` through `cmd /c` failed because Maven received it as an unknown argument.
