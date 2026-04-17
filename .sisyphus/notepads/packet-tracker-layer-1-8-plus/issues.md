## Initialization

## 2026-04-13 task 1
- `lsp_diagnostics` could not run for Java because `jdtls` is not installed in this environment, so validation relied on the required Maven test command instead.
- The first Maven run exposed a test-design mismatch: the default probe registry intentionally marks Paper `1.19.2+` as chunk-system-capable via version baseline, so the Moonrise marker test had to use `1.18.2` to isolate the reflective marker path.

## 2026-04-13 task 2
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so the task relied on the required Maven verification command plus focused selector/platform tests for validation.

## 2026-04-13 task 3
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so validation again relied on the required focused Maven slice: `cmd /c "mvnw.cmd -pl versions/runtime -am clean -Dtest=EntityTransportContractTest,RuntimeNativeEntityLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test"`.

## 2026-04-13 task 11
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so validation relied on Maven test runs instead of Java LSP output.
- The required broad check `./mvnw.cmd -pl versions -am test` succeeds against the aggregator pom without compiling the concrete family modules, so an additional module-targeted reactor test run was necessary to validate the newly added version skeletons and their tests.

## 2026-04-13 task 4
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so validation again had to rely on Maven test execution.
- The exact required root-level verification command is currently blocked by unrelated reactor state: `versions/pom.xml` references missing `1.13.2`, `1.16.5`, `1.17.1`, and `1.19.2` child modules, so Maven fails during project loading before it reaches `versions/runtime`.
- Runtime validation still succeeded by targeting `versions/runtime/pom.xml` directly with the same test filter, which confirmed the new contract compiles and both `NetworkMetadataContractTest` and `EntityTransportContractTest` pass in the module itself.

## 2026-04-13 task 5
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so the task still depends on the required focused Maven verification command for the real completion gate.

## 2026-04-13 task 8
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so validation used a clean cross-module compile plus the exact required Maven reactor test slice instead of Java LSP output.

## 2026-04-13 task 7
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so validation again relied on the required Maven reactor test run instead of Java LSP output.

## 2026-04-13 task 6
- Java LSP diagnostics again reported unresolved imports/methods for the new legacy tracker-hook bridge inside the version modules, but the required reactor verification compiled `versions/runtime`, `versions/1.8.8`, and `versions/1.13.2` successfully and all targeted tests passed; for this task, Maven remained the reliable truth source over editor diagnostics.

## 2026-04-13 task 10
- `lsp_diagnostics` is still unavailable for Java because `jdtls` is not installed in this environment, so the task again had to rely on the required Maven reactor verification as the real compile/test gate.

## 2026-04-13 task 9
- `lsp_diagnostics` is still unavailable for Java because `jdtls` is not installed in this environment, so the required reactor command remained the only trustworthy completion gate for the legacy transport work.
- Mockito inline mocks on this JDK cannot implement non-public extra interfaces from another package, so the new version-module packet tests had to expose their `HandleCarrier` / `PassengerCarrier` contracts publicly before mocked Bukkit entities and players could surface reflective `getHandle()` / `getPassengers()` methods for the fake legacy NMS packet fixtures.

## 2026-04-13 task 12
- `lsp_diagnostics` remains unavailable for Java because `jdtls` is not installed in this environment, so Maven stayed the reliable validation gate here too.
- The first module-targeted adapter test run reused stale compiled outputs in `versions/*/target`, which surfaced old unresolved-compilation artifacts instead of the new assertions; rerunning the same module slice with `clean` forced a fresh compile and produced the real pass/fail signal.

## 2026-04-13 task 15 blocker fix
- The first task-15 enforcement pass was too eager: putting `RuntimeSupportMatrix.requireSupported(...)` inside `VersionedEntityPlatform` broke existing direct fake-adapter tests with errors such as `metadata backend contract is unspecified` and `modern fresh-spawn runtime bridge is missing`, and it also blocked broader reactor verification needed by task 13.
- `lsp_diagnostics` is still unavailable for Java because `jdtls` is not installed, so the fix was validated entirely through the required Maven runtime slice plus the `test-plugin` package/test commands.

## 2026-04-13 task 13
- `lsp_diagnostics` is still unavailable for Java because `jdtls` is not installed in this environment, so the task again relied on Maven reactor compilation plus the exact required focused test command for validation.
- On Windows PowerShell, the Maven wrapper and `-D...` flags had to be invoked via `cmd /c ".\mvnw.cmd ..."`; calling `mvnw.cmd` directly or passing the `-D` flags unquoted caused shell-level failures before Maven reached compilation.

## 2026-04-13 task 8 publication regression follow-up
- The inherited Atlas report about `EntityPublicationFamilyTest.shouldPreferModernFreshAddMethodDuringFreshPublication` no longer reproduces on the current branch: `cmd /c ".\mvnw.cmd -pl versions/runtime -am test -Dtest=EntityPublicationFamilyTest -Dsurefire.failIfNoSpecifiedTests=false"` passes, and the `test-plugin` reactor also runs past the runtime publication tests.
- `cmd /c ".\mvnw.cmd -pl test-plugin -am package"` is still failing in this environment, but the failure has moved to the later `versions/runtime` `maven-javadoc-plugin:jar` step rather than the runtime publication test; that remaining blocker is outside this narrow publication regression scope.

## 2026-04-13 task 15 javadoc package fix
- Java LSP remains unavailable because `jdtls` is not installed, so the packaging fix had to be validated by Maven package runs rather than editor diagnostics.
- After the Javadoc correction, no additional directly related runtime Javadoc blocker appeared: `cmd /c ".\mvnw.cmd -pl versions/runtime -am package -DskipTests"` and `cmd /c ".\mvnw.cmd -pl test-plugin -am package"` both completed successfully.

## 2026-04-13 task 8 deterministic publication fix
- Java LSP remains unavailable because `jdtls` is not installed, so the regression fix was validated entirely through Maven: the focused runtime publication slice and the full `test-plugin` package reactor both passed after the reflection-order change.

## 2026-04-13 task 14 live autorun fix
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so the task again had to rely on the real Maven/package run plus live matrix execution instead of editor diagnostics.
- The first live `spigot-1.8.8` autorun attempt exposed a runtime compatibility bug rather than a runner bug: headless scenario initialization was calling newer Bukkit `Zombie` methods that do not exist on 1.8.8, which threw `NoSuchMethodError` from the scheduled autorun task before any artifacts were written.
- The first `paper-1.21.11` rerun exposed a PowerShell script bug after successful provisioning: checking `$LASTEXITCODE` after invoking another PowerShell script is invalid on that path, so the guard had to be removed before the warmed Paper cache could proceed into the live run.

## 2026-04-13 task 14 boundary packaging follow-up
- `lsp_diagnostics` is still unavailable for Java because `jdtls` is not installed, so the packaging/discovery follow-up again had to rely on the real shaded-plugin package run and live matrix executions.
- The original `spigot-1.17.1` failure was correctly rooted in packaging (`EntityAdapterNotFoundException`), but once the missing mid-family module jars were added to `test-plugin`, the next live run immediately exposed a deeper family-local blocker: the `1.17-1.18.2` replacement scaffold still throws `UnsupportedOperationException` for `current native handle resolution` on attach.
- The current green `spigot-1.17.1` boundary result therefore proves packaging/discovery coverage and matrix autorun/output behavior, but it still records an `attach-fallback` trace event because the underlying `1.17.1` replacement implementation is not yet complete in the version module itself.
- A separate verification-only issue appeared during reruns: the package helper in `scripts/entity-matrix/common.ps1` reused a single `test-plugin-package.log` file and hit Windows file-locking when an earlier process had not released it. Using a per-process log filename fixed the rerun path.

## 2026-04-13 paper 1.19.2 fresh-spawn fix
- `lsp_diagnostics` remains unavailable for Java because `jdtls` is not installed, so this task again relied on Maven plus the live matrix runner as the real validation gate.
- The first direct `versions/1.19.2` module test run produced stale `Unresolved compilation problems` class artifacts because Maven skipped recompiling changed main sources; rerunning the same slice with `clean` forced a fresh compile and produced the real signal.
- After the fresh-spawn bridge stopped throwing, the live Paper `1.19.2` matrix exposed a second genuine runtime gap: `AbstractEntityPublicationBackend` could not find the fresh-add method on `net.minecraft.server.level.WorldServer` because this era uses the Bukkit `SpawnReason` overload. Supporting that overload in the shared backend was required for a real green matrix result.

## 2026-04-13 task F3 manual qa
- The QA blocker is semantic, not infrastructural: `spigot-1.17.1/attach-existing-zombie` writes `pass=true` even when the trace shows `attach-fallback`, because `EntityDemoService.attachHeadlessExistingZombieScenario(...)` catches the known unsupported attach gap and fills in success-shaped assertions instead of surfacing a degraded or failed result.
## 2026-04-13 task 1.17.1 attach replacement fix
- A non-clean `mvnw.cmd -pl versions/1.17.1 -am test` rerun surfaced stale `Unresolved compilation problems` artifacts in `SpigotEntityAdapterV1_17_1Test` even though the actual sources compiled; the reliable gate was the clean rerun (`clean test`), which rebuilt the module and passed.
- The first live matrix rerun proved the old `current native handle resolution` blocker was gone and exposed the next genuine runtime bug instead: `rewireModernVehicleAndPassengerReferencesInternal(...)` was reading the wrong obfuscated field shape and threw `ClassCastException` until the lookup was pinned to the base NMS `Entity` fields.

## 2026-04-13 task 1.19.2 attach replacement fix
- `lsp_diagnostics` remains unavailable for Java in this environment because `jdtls` is not installed, so the 1.19.2 attach/replacement bridge had to be validated through the required clean Maven reactor test run instead of Java LSP output.

## 2026-04-14 task 1.19.2 generated replacement bridge follow-up
- The first real generated-bridge test pass surfaced a packaging seam immediately: `EntityFactoryV1_19_2` now loads `GeneratedNativeEntityClassFactory` during replacement preparation, so without a direct Javassist dependency the module fails before attach work begins even though the runtime module already shades Javassist for packaged jars.
- The first clean generated-subclass tests also exposed a real JVM-level collision: caching generated replacement metadata per factory instance let separate factories try to define the same generated bridge name twice, so the cache had to move to a family-static map before the clean `versions/1.19.2` slice would pass reliably.

## 2026-04-14 task 1.13.2 attach replacement fix
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so the reliable completion gate for the `1.13.2` bridge remained the required clean Maven reactor command instead of Java LSP output.

## 2026-04-14T01:18:06.9689887-03:00 task F3 manual qa rerun
- The retained .tools/entity-matrix/*/logs/latest.log files are not sufficient evidence on their own for QA approval because they mostly show startup plus autorun scheduling; the authoritative pass/fail evidence is the fresh 	arget/entity-matrix/.../{trace,assertions}.json output produced by the runner.

## 2026-04-14 task 1.19.2 cow support contract correction
- `lsp_diagnostics` is still unavailable for Java in this environment because `jdtls` is not installed, so the reliable validation gate remained the required clean Maven slice plus the live matrix rerun.
- Live `spigot-1.19.2` inspection showed the cow mismatch was not just the typed `EntityCow(EntityTypes, World)` constructor path: the current `1.19.2` hook binder also exposes no supported fresh-spawn hooks for real cow handles, so the family could not honestly keep advertising `COW` support.
- The smallest honest fix was therefore contract-side in `versions/1.19.2`: stop registering `CustomEntityBaseType.COW` in the family metadata registry and update `EntityFactoryV1_19_2Test` accordingly. After `cmd /c ".\mvnw.cmd -pl versions/1.19.2 -am clean test"` passed, the live `spigot-1.19.2/deathfx-cow` artifacts changed from constructor-resolution failure to an immediate unsupported-type rejection: `The active adapter does not support base type 'COW'.`

## 2026-04-14T05:00:21.3733069-03:00 task F3 manual qa contract-correction sanity
- After focused reruns, the workspace still retained only a partial representative artifact set under 	arget/entity-matrix (spigot-1.17.1/attach-existing-zombie and paper-1.21.11/metadata-dirty-zombie), while the expected current retained outputs for 1.8.8, 1.13.2, 1.16.5, and paper-1.19.2 were not present to inspect directly.
- Because the representative evidence set is incomplete on disk, QA cannot fully re-prove release coherence from artifacts alone even though the non-representative spigot-1.19.2/deathfx-cow semantics are now honest.
- The retained extra artifact currently uses the spigot-1.19.2 server label rather than the representative paper-1.19.2 label, so it should not be confused with the release matrix row.

## 2026-04-14T05:13:50.4257278-03:00 task F3 final representative recheck
- The rebuilt on-disk representative set is still inconsistent with the claimed release evidence layout: spigot-1.8.8, spigot-1.13.2, spigot-1.16.5, and paper-1.21.11 expose inspectable ssertions.json and 	race.json, but spigot-1.17.1/attach-existing-zombie currently retains only server.log and no machine-readable artifacts, and paper-1.19.2/viewer-cycle-zombie does not exist on disk at all.
- The only retained 1.19.2 artifact is the non-representative spigot-1.19.2/deathfx-cow, whose trace still uses the utorun-failure event name even though the message now honestly reports unsupported COW capability rather than a hidden claimed-success path.
