# Versions Module Neutralization and Shared Dependency Refactor

## TL;DR
> **Summary**: Neutralize the versions subsystem so its public package/artifact/runtime naming is no longer entity-centric, move each implementation module under `tech.guilhermekaua.spigotboot.v1_x_x.entity`, and introduce one consumer-facing shared module that transitively brings in api, runtime, and all version providers.
> **Deliverables**:
> - renamed versions parent, api, runtime, provider, and shared-module Maven coordinates
> - renamed API/runtime infrastructure packages and types
> - six migrated version modules under `tech.guilhermekaua.spigotboot.v1_x_x.entity`
> - `test-plugin` consuming one shared versions dependency instead of eight direct versions dependencies
> - updated automated regression coverage for discovery, selection, and packaging
> **Effort**: Large
> **Parallel**: YES - 3 waves
> **Critical Path**: 1 → 2 → 4 → 6-11 → 12 → 14

## Context
### Original Request
Refactor the `versions` API/module naming and package structure so it is not entity-only, specifically moving version-local entity packages from `tech.guilhermekaua.spigotboot.entity.v1_8_8`-style roots to `tech.guilhermekaua.spigotboot.v1_8_8.entity`, and make plugin consumers depend on one shared versions module instead of adding every version artifact individually.

### Interview Summary
- User selected a **full neutral rename** for the versions subsystem: package roots, public runtime/SPI types, and artifactIds all move away from `entity`-centric naming.
- User selected a **transitive shared jar** model, not a bundled fat jar: one dependency coordinate for consumers, with api/runtime/provider modules remaining separate published jars under the hood.
- User approved a **breaking change** with **tests-after** execution policy.
- Default applied: neutralize **versioning infrastructure** names, but keep **entity-domain nouns** when they still describe the domain (`ControlledEntity`, `EntityTemplate`, `SpawnedEntity`, metadata payload/value types, per-version `EntityFactory*`, etc.).
- Default applied: API/runtime package roots become `tech.guilhermekaua.spigotboot.versions.api` and `tech.guilhermekaua.spigotboot.versions.runtime`; version-local implementations move to `tech.guilhermekaua.spigotboot.v1_x_x.entity`.

### Metis Review (gaps addressed)
- Preserve both adapter discovery paths: `ServiceLoader` and the explicit adapter registry.
- Do not let the new shared module inherit fat-jar behavior from the root `maven-shade-plugin`; it must remain thin.
- Rename **service descriptor filenames and contents**, not just Java symbols.
- Keep docs/CI migration work out of scope unless needed to keep build/test sources valid.
- Treat `test-plugin` as the proof consumer because it is the only pom that currently lists api/runtime/all six version artifacts directly.

## Work Objectives
### Core Objective
Deliver a breaking but internally consistent rename/repackaging of the versions subsystem so the public versioning surface is no longer entity-branded, while preserving runtime adapter discovery/selection behavior and reducing plugin consumption to one shared dependency.

### Deliverables
- `versions/pom.xml` converted from public consumer coordinate to parent-only coordinate `spigot-boot-versions-parent`
- new `versions/shared` module publishing consumer-facing artifact `spigot-boot-versions`
- renamed shared artifacts:
  - `spigot-boot-entity-api` → `spigot-boot-versions-api`
  - `spigot-boot-entity-runtime` → `spigot-boot-versions-runtime`
  - `spigot-boot-entity-v1_8_8` → `spigot-boot-versions-v1_8_8`
  - `spigot-boot-entity-v1_13_2` → `spigot-boot-versions-v1_13_2`
  - `spigot-boot-entity-v1_16_5` → `spigot-boot-versions-v1_16_5`
  - `spigot-boot-entity-v1_17_1` → `spigot-boot-versions-v1_17_1`
  - `spigot-boot-entity-v1_19_2` → `spigot-boot-versions-v1_19_2`
  - `spigot-boot-entity-v1_21_11` → `spigot-boot-versions-v1_21_11`
- renamed infrastructure types:
  - `EntityVersionAdapter` → `VersionAdapter`
  - `SpigotEntityBootstrap` → `SpigotVersionBootstrap`
  - `VersionedEntityPlatform` → `VersionedPlatform`
  - `EntityRuntimeProfile` → `VersionRuntimeProfile`
  - `EntityAdapterDiscovery` → `VersionAdapterDiscovery`
  - `EntityAdapterRegistry` → `VersionAdapterRegistry`
  - `EntityAdapterNotFoundException` → `VersionAdapterNotFoundException`
  - `EntityVersionCapabilities` → `VersionCapabilities`
  - `EntityVersionBindings` → `VersionBindings`
  - `EntityVersionMetadataProvider` → `VersionMetadataProvider`
  - `EntityVersionNetworkMetadataProvider` → `VersionNetworkMetadataProvider`
  - `EntityVersionTransportProvider` → `VersionTransportProvider`
  - `EntityVersionEntrypoint` → `VersionEntrypoint`
- version-local implementation package roots moved to:
  - `tech.guilhermekaua.spigotboot.v1_8_8.entity`
  - `tech.guilhermekaua.spigotboot.v1_13_2.entity`
  - `tech.guilhermekaua.spigotboot.v1_16_5.entity`
  - `tech.guilhermekaua.spigotboot.v1_17_1.entity`
  - `tech.guilhermekaua.spigotboot.v1_19_2.entity`
  - `tech.guilhermekaua.spigotboot.v1_21_11.entity`
- `test-plugin` updated to consume only `spigot-boot-versions` for the versions subsystem

### Definition of Done (verifiable conditions with commands)
- `mvnw.cmd -pl versions -am test` returns `BUILD SUCCESS`.
- `mvnw.cmd -pl test-plugin -am test` returns `BUILD SUCCESS`.
- `mvnw.cmd -pl test-plugin -am package` returns `BUILD SUCCESS`.
- `Select-String -Path (Get-ChildItem -Recurse -File versions,test-plugin -Include *.java,*.xml | ForEach-Object FullName) -Pattern 'spigot-boot-entity-|tech\.guilhermekaua\.spigotboot\.entity\.(api|runtime|v1_)'` returns no matches.
- `Get-ChildItem -Recurse versions -Filter 'tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter'` returns no files.
- Inspecting `test-plugin/target/*.jar` shows exactly one merged `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` file with six provider lines.
- Inspecting `versions/shared/target/*.jar` shows a thin jar containing only shared-module classes/resources, not shaded copies of api/runtime/provider classes.

### Must Have
- Preserve runtime detection and adapter selection flow from `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:108-230`.
- Preserve adapter discovery through both `ServiceLoader` and explicit registry from `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/EntityAdapterDiscovery.java:41-56` and `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/registry/EntityAdapterRegistry.java:38-108`.
- Preserve Javassist relocation in `versions/runtime/pom.xml:17-109`.
- Preserve generated-native class package derivation behavior in `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/nativebridge/GeneratedNativeEntityClassFactory.java:51-58` after the runtime package rename.
- Keep per-version provider modules as separate artifacts; the shared module is a dependency fan-in jar, not a code monolith.

### Must NOT Have (guardrails, AI slop patterns, scope boundaries)
- Must NOT rename entity-domain API nouns unless they are explicitly listed in the deliverables above.
- Must NOT add backward-compatibility bridge classes, alias artifacts, or duplicate service descriptors.
- Must NOT collapse all provider implementations into one fat shared artifact.
- Must NOT change `.github/workflows/*.yml` in this refactor.
- Must NOT expand into README/changelog/migration-doc updates; only fix build/test source references inside the repo.
- Must NOT leave inherited package-phase shading enabled in api/shared/provider modules.

## Verification Strategy
> ZERO HUMAN INTERVENTION - all verification is agent-executed.
- Test decision: tests-after with existing JUnit 5/Mockito/MockBukkit infrastructure.
- QA policy: Every task includes agent-executed happy-path and failure-path scenarios.
- Evidence: `.sisyphus/evidence/task-{N}-{slug}.{ext}`
- Mandatory command set:
  - `mvnw.cmd -pl versions -am test`
  - `mvnw.cmd -pl test-plugin -am test`
  - `mvnw.cmd -pl test-plugin -am package`
  - PowerShell scans for old coordinates/package roots/service-resource paths
  - PowerShell jar inspection of `versions/shared/target/*.jar` and `test-plugin/target/*.jar`

## Execution Strategy
### Parallel Execution Waves
> Target: 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks for max parallelism.

Wave 1: foundation topology + API/runtime neutralization + runtime regression updates (Tasks 1-5)

Wave 2: six independent provider-module migrations (Tasks 6-11)

Wave 3: consumer migration + integration packaging coverage (Tasks 12-14)

### Dependency Matrix (full, all tasks)
- 1 blocks 2-14
- 2 blocks 12-14
- 3 blocks 4-14
- 4 blocks 5-14
- 5 validates 3-4 and must complete before 12-14
- 6, 7, 8, 9, 10, 11 each depend on 1, 3, and 4; they do not depend on each other
- 12 depends on 1-11
- 13 depends on 3, 4, and 12
- 14 depends on 5-13

### Agent Dispatch Summary (wave → task count → categories)
- Wave 1 → 5 tasks → `deep` for runtime core, `unspecified-high` for pom/api/test tasks
- Wave 2 → 6 tasks → `unspecified-high` per version module
- Wave 3 → 3 tasks → `unspecified-high` for dependency/test migration, `quick` only for small import/test follow-ups inside the same wave when scope shrinks

## TODOs
> Implementation + Test = ONE task. Never separate.
> EVERY task MUST have: Agent Profile + Parallelization + QA Scenarios.

- [x] 1. Rename the versions parent coordinate and provider artifactIds

  **What to do**: Change `versions/pom.xml` from public coordinate `spigot-boot-versions` to parent-only coordinate `spigot-boot-versions-parent`; update every child pom under `versions/*` to use the new parent artifactId; rename api/runtime/provider artifactIds to the `spigot-boot-versions-*` family; add the `shared` module entry to the reactor and keep it listed after the six provider modules; in `versions/api/pom.xml` and every provider pom (`versions/1.8.8` through `versions/1.21.11`), add a module-local `maven-shade-plugin` override with `<skip>true</skip>` so only `versions/runtime` keeps active shading.
  **Must NOT do**: Do not repurpose the parent pom into the consumer artifact; do not change module directory names (`1.8.8`, `1.13.2`, etc. stay as-is); do not touch `.github/workflows/*.yml`.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: multi-pom refactor with repo-wide coordinate consistency requirements.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 2-14 | Blocked By: none

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/pom.xml:15-27` - current parent coordinate and reactor modules; rename artifactId and append `shared` as the consumer module.
  - Pattern: `versions/api/pom.xml:6-16` - child-parent relationship and current api artifactId.
  - Pattern: `versions/runtime/pom.xml:6-16` - child-parent relationship and current runtime artifactId.
  - Pattern: `versions/1.21.11/pom.xml:6-27` - representative provider pom; apply same rename pattern to all six provider modules.
  - Pattern: `test-plugin/pom.xml:160-254` - downstream consumer currently references all old coordinates; task 1 must prepare the coordinate map that Task 12 will consume.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/pom.xml` publishes `spigot-boot-versions-parent` and lists `shared` in `<modules>` after `1.21.11`.
  - [ ] All child poms under `versions/*/pom.xml` point at parent artifactId `spigot-boot-versions-parent`.
  - [ ] API/runtime/provider artifactIds all use the `spigot-boot-versions-*` prefix and no `spigot-boot-entity-*` artifactId remains inside `versions/**/pom.xml`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Versions reactor coordinates are internally consistent
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions -am -DskipTests validate`; then run a PowerShell scan over `versions/**/pom.xml` for `spigot-boot-entity-` and old parent artifactId references.
    Expected: Maven validation succeeds; scans return zero old entity-prefixed artifactIds in `versions/**/pom.xml` and zero child-parent references to `spigot-boot-versions`.
    Evidence: .sisyphus/evidence/task-1-versions-parent-topology.txt

  Scenario: Parent pom no longer claims the public consumer coordinate
    Tool: Bash
    Steps: Inspect `versions/pom.xml` after the rename and run a PowerShell count of `<artifactId>spigot-boot-versions</artifactId>` in that file only.
    Expected: The parent pom contains zero occurrences of the public consumer coordinate and exactly one occurrence of `spigot-boot-versions-parent`.
    Evidence: .sisyphus/evidence/task-1-versions-parent-topology-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): neutralize shared api and runtime naming` | Files: `versions/pom.xml`, `versions/*/pom.xml`

- [x] 2. Create the `versions/shared` consumer module as a thin transitive jar

  **What to do**: Add module directory `versions/shared` with artifactId `spigot-boot-versions`; declare direct compile-scope dependencies on `spigot-boot-versions-api`, `spigot-boot-versions-runtime`, and all six renamed provider artifacts; add one minimal marker type (for example `tech.guilhermekaua.spigotboot.versions.shared.SharedVersionsModule`) so the module ships a non-empty jar; explicitly disable inherited shade execution in this module with a module-local `maven-shade-plugin` configuration containing `<skip>true</skip>` so it stays thin.
  **Must NOT do**: Do not shade provider/runtime/api classes into the shared jar; do not add ServiceLoader metadata to the shared module; do not skip the module-local guardrail against inherited `maven-shade-plugin` behavior.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: Maven packaging and publication wiring with plugin inheritance guardrails.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 12-14 | Blocked By: 1

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/pom.xml:18-27` - `shared` must be a reactor child, listed after the six provider modules.
  - Pattern: `pom.xml:96-113` - root build inherits package-phase `maven-shade-plugin`; shared must override this or it becomes a fat jar.
  - Pattern: `pom.xml:134-190` - source/javadoc/publish plugins are inherited; the marker class ensures shared publishes a real non-empty artifact.
  - Pattern: `versions/runtime/pom.xml:17-87` - example of module-local shade configuration; shared needs the opposite behavior (skip, not shade).
  - Pattern: `test-plugin/pom.xml:52-67` - downstream plugin shading already merges services; shared must rely on downstream merging rather than shipping its own service file.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/shared/pom.xml` exists, publishes artifactId `spigot-boot-versions`, and directly depends on api/runtime/all six provider artifacts.
  - [ ] `versions/shared/src/main/java/tech/guilhermekaua/spigotboot/versions/shared/SharedVersionsModule.java` (or an equivalent single marker type) exists.
  - [ ] Packaging `versions/shared` produces a thin jar that does not contain provider implementation classes or old/new ServiceLoader provider files.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Shared module packages as a thin fan-in artifact
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/shared -am package -DskipTests`; inspect the primary jar in `versions/shared/target` with `jar tf`.
    Expected: Packaging succeeds; jar contains the marker class/package and pom metadata, but does not contain `tech/guilhermekaua/spigotboot/v1_*/entity/` classes, runtime shaded Javassist classes, or `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter`.
    Evidence: .sisyphus/evidence/task-2-shared-module-thin-jar.txt

  Scenario: Shared module does not inherit fat-jar behavior
    Tool: Bash
    Steps: Use PowerShell to search the packaged shared jar listing for `shaded/javassist`, `SpigotVersionAdapterV1_`, and `EntityFactoryV1_` entries.
    Expected: Zero matches are found.
    Evidence: .sisyphus/evidence/task-2-shared-module-thin-jar-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): neutralize shared api and runtime naming` | Files: `versions/shared/pom.xml`, `versions/shared/src/main/java/**`, `versions/pom.xml`

- [x] 3. Rename the shared API package root and SPI surface

  **What to do**: Move the API package root from `tech.guilhermekaua.spigotboot.entity.api` to `tech.guilhermekaua.spigotboot.versions.api`; rename SPI interface `EntityVersionAdapter` to `VersionAdapter`; move the SPI resource package namespace to `tech.guilhermekaua.spigotboot.versions.api.spi`; update imports inside api sources accordingly; keep entity-domain type names unchanged unless they are explicitly listed in the plan deliverables.
  **Must NOT do**: Do not rename domain types such as `ControlledEntity`, `CustomEntityBaseType`, `EntityTemplate`, `SpawnOptions`, or `SpawnedEntity`; do not leave any source file in `versions/api` under the old `entity.api` package root.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: broad public API package move with one critical SPI rename and strong rename-boundary guardrails.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 4-14 | Blocked By: 1

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/api/pom.xml:12-24` - shared API artifact and dependency baseline.
  - API/Type: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/spi/EntityVersionAdapter.java:23-103` - rename to `VersionAdapter` under the new `versions.api.spi` package.
  - Pattern: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:41-58` - downstream code imports the api root as a wildcard today; package rename must keep those domain types accessible from the new root.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:28-35` - runtime tests already depend on the SPI type and api root, so the rename must preserve contracts.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/api` compiles with package root `tech.guilhermekaua.spigotboot.versions.api` and SPI type `VersionAdapter`.
  - [ ] No file under `versions/api/src/main/java` declares `package tech.guilhermekaua.spigotboot.entity.api` or `package tech.guilhermekaua.spigotboot.entity.api.spi`.
  - [ ] Domain type names remain unchanged while their package root moves to `tech.guilhermekaua.spigotboot.versions.api`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: API module compiles with the new namespace
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/api -am -DskipTests compile`; then scan `versions/api/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.api` and `interface EntityVersionAdapter`.
    Expected: Compile succeeds; both scans return zero matches.
    Evidence: .sisyphus/evidence/task-3-versions-api-rename.txt

  Scenario: Rename boundary preserves entity-domain nouns
    Tool: Bash
    Steps: Run PowerShell scans in `versions/api/src/main/java` for `class ControlledEntity`, `class EntityTemplate`, `class SpawnedEntity`, and `class SpawnOptions` (or equivalent declarations/records/interfaces).
    Expected: These domain types still exist under the new `tech.guilhermekaua.spigotboot.versions.api` root and were not mechanically renamed away.
    Evidence: .sisyphus/evidence/task-3-versions-api-rename-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): neutralize shared api and runtime naming` | Files: `versions/api/pom.xml`, `versions/api/src/main/java/**`

- [x] 4. Rename runtime infrastructure packages and public versioning types

  **What to do**: Move the runtime package root from `tech.guilhermekaua.spigotboot.entity.runtime` to `tech.guilhermekaua.spigotboot.versions.runtime`; rename the infrastructure types listed in Deliverables (`SpigotEntityBootstrap`, `VersionedEntityPlatform`, `EntityRuntimeProfile`, `EntityAdapterDiscovery`, `EntityAdapterRegistry`, `EntityAdapterNotFoundException`, `EntityVersionCapabilities`, `EntityVersionBindings`, `EntityVersionMetadataProvider`, `EntityVersionNetworkMetadataProvider`, `EntityVersionTransportProvider`, `EntityVersionEntrypoint`); update all imports/usages in runtime main sources; keep domain-oriented transport/publication/metadata/entity model class names unchanged unless they are one of the explicit deliverable renames; update generated-native package derivation so generated classes still live under the renamed runtime package tree.
  **Must NOT do**: Do not remove `ServiceLoader` discovery; do not remove explicit registry fallback; do not modify Javassist relocation; do not rename domain model types merely because they contain the word `Entity`.

  **Recommended Agent Profile**:
  - Category: `deep` - Reason: high-coupling runtime refactor spanning bootstrap, discovery, registry, selection, and generated-class behavior.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 5-14 | Blocked By: 1, 3

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/runtime/pom.xml:15-109` - runtime artifactId and Javassist shading/relocation that must remain intact.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:108-230` - preserve boot/discovery/select flow while renaming bootstrap/profile/adapter types and message text.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/EntityAdapterDiscovery.java:41-56` - preserve `ServiceLoader` plus registry merging.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/registry/EntityAdapterRegistry.java:38-108` - preserve explicit registration semantics.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:76-127` - rename platform/profile/capability/binding/provider dependencies while keeping runtime behavior unchanged.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/EntityRuntimeProfile.java:35-145` - rename to `VersionRuntimeProfile` without changing equality/toString semantics.
  - API/Type: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/nativebridge/GeneratedNativeEntityClassFactory.java:51-58` - update generated package constant behavior to follow the new runtime root.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/runtime` main sources compile under `tech.guilhermekaua.spigotboot.versions.runtime` with the renamed infrastructure types.
  - [ ] `SpigotVersionBootstrap.boot(...)` preserves discovery and selection semantics from the current runtime implementation.
  - [ ] `VersionAdapterRegistry` still supports explicit registration, replacement-by-implementation-class, snapshot reads, and clear semantics.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Runtime main sources compile with preserved wiring
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am -DskipTests compile`; then run PowerShell scans over `versions/runtime/src/main/java` for `class SpigotEntityBootstrap`, `class VersionedEntityPlatform`, `class EntityRuntimeProfile`, and `class EntityAdapterRegistry`.
    Expected: Compile succeeds; all scanned old infrastructure type names are absent from runtime main sources.
    Evidence: .sisyphus/evidence/task-4-runtime-infrastructure-rename.txt

  Scenario: Runtime still exposes both adapter discovery paths
    Tool: Bash
    Steps: Scan `versions/runtime/src/main/java` to confirm `ServiceLoader.load(VersionAdapter.class, classLoader)` still exists in the renamed discovery class and that registry iteration still merges `VersionAdapterRegistry.registeredAdapters()`.
    Expected: Both discovery paths are present exactly once in the renamed runtime discovery flow.
    Evidence: .sisyphus/evidence/task-4-runtime-infrastructure-rename-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): neutralize shared api and runtime naming` | Files: `versions/runtime/pom.xml`, `versions/runtime/src/main/java/**`

- [x] 5. Update runtime tests and test resources for the renamed SPI/bootstrap surface

  **What to do**: Rename runtime test packages/imports from `entity.runtime` to `versions.runtime`; rename `SpigotEntityBootstrapTest` to `SpigotVersionBootstrapTest`; update references to `VersionAdapter`, `SpigotVersionBootstrap`, `VersionedPlatform`, `VersionRuntimeProfile`, and `VersionAdapterRegistry`; rename the runtime test ServiceLoader resource file from `META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` to `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter`; update assertions that mention adapter-not-found and unsupported-version error text so they expect `version adapter` wording, not `entity adapter` wording.
  **Must NOT do**: Do not weaken or remove the existing runtime-selection, classloader-discovery, registry, or failure-path assertions.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: broad test/resource rename tightly coupled to runtime core behavior.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 12-14 | Blocked By: 4

  **References** (executor has NO interview context - be exhaustive):
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:74-246` - preserve selection, discovery, classloader, and failure-path assertions while renaming symbols.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/RuntimeProfileSelectionTest.java` - update runtime-profile type/import names without changing deterministic family coverage.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/RuntimeSupportMatrixTest.java` - keep support-matrix validation green after renames.
  - Pattern: `versions/runtime/src/test/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter:1` - rename the service-resource filename and update the provider FQN inside it.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:118-126` - current error message wording that tests validate.

  **Acceptance Criteria** (agent-executable only):
  - [ ] Runtime test sources compile and pass against the renamed runtime/api symbols.
  - [ ] The old runtime test service-resource path no longer exists and the new `VersionAdapter` resource path exists exactly once under `versions/runtime/src/test/resources`.
  - [ ] Failure-path assertions still verify no-adapter and unsupported-version behavior using renamed message text.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Core runtime regression suite passes after the rename
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/runtime -am test -Dtest=SpigotVersionBootstrapTest,RuntimeProfileSelectionTest,RuntimeSupportMatrixTest` after renaming the bootstrap test class/package.
    Expected: BUILD SUCCESS; tests covering discovery, deterministic selection, support matrix, and failure paths all pass.
    Evidence: .sisyphus/evidence/task-5-runtime-tests-rename.txt

  Scenario: Old runtime test SPI resource path is fully removed
    Tool: Bash
    Steps: Use PowerShell to locate files named `tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` under `versions/runtime/src/test/resources` and files named `tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` under the same tree.
    Expected: Zero old-path files; exactly one new-path file whose content points at the renamed runtime test adapter support class.
    Evidence: .sisyphus/evidence/task-5-runtime-tests-rename-error.txt
  ```

  **Commit**: YES | Message: `refactor(versions): neutralize shared api and runtime naming` | Files: `versions/api/**`, `versions/runtime/**`, `versions/shared/**`, `versions/pom.xml`

- [x] 6. Migrate the 1.8.8 provider module to `tech.guilhermekaua.spigotboot.v1_8_8.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_8_8`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_8_8` to `tech.guilhermekaua.spigotboot.v1_8_8.entity`; rename `SpigotEntityAdapterV1_8_8` to `SpigotVersionAdapterV1_8_8`; keep `EntityFactoryV1_8_8` and other entity-domain helpers named as entities but under the new package root; update the ServiceLoader file to `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` with content `tech.guilhermekaua.spigotboot.v1_8_8.entity.SpigotVersionAdapterV1_8_8`.
  **Must NOT do**: Do not collapse helper classes into the adapter; do not leave any source/resource in the old `entity.v1_8_8` package/resource path.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with resource/package/class rename coupling.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.8.8/pom.xml` - rename the provider artifactId and parent coordinate usage.
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/SpigotEntityAdapterV1_8_8.java` - rename adapter class and package root.
  - Pattern: `versions/1.8.8/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_8_8/EntityFactoryV1_8_8.java` - keep domain helper naming while relocating package.
  - Pattern: `versions/1.8.8/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - rename resource filename and provider FQN.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:95-121` - runtime selection still expects the legacy-family provider range to resolve uniquely.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.8.8` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_8_8.entity` and adapter type `SpigotVersionAdapterV1_8_8`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.8.8`.
  - [ ] The renamed service descriptor points exactly at `tech.guilhermekaua.spigotboot.v1_8_8.entity.SpigotVersionAdapterV1_8_8`.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.8.8 provider builds cleanly after the package move
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.8.8 -am test`; then scan `versions/1.8.8/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_8_8`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-6-provider-v1_8_8.txt

  Scenario: 1.8.8 service descriptor targets the renamed adapter
    Tool: Bash
    Steps: Verify the old service filename is absent and the new `VersionAdapter` service file exists with one line: `tech.guilhermekaua.spigotboot.v1_8_8.entity.SpigotVersionAdapterV1_8_8`.
    Expected: Exactly one new service file exists with the expected FQN and zero old-path files remain.
    Evidence: .sisyphus/evidence/task-6-provider-v1_8_8-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.8.8/**`

- [x] 7. Migrate the 1.13.2 provider module to `tech.guilhermekaua.spigotboot.v1_13_2.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_13_2`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_13_2` to `tech.guilhermekaua.spigotboot.v1_13_2.entity`; rename `SpigotEntityAdapterV1_13_2` to `SpigotVersionAdapterV1_13_2`; keep `EntityFactoryV1_13_2` and `EntityHookBinderV1_13_2` named as entity-domain helpers but under the new package root; rename the ServiceLoader file to the new SPI path and update its content to `tech.guilhermekaua.spigotboot.v1_13_2.entity.SpigotVersionAdapterV1_13_2`.
  **Must NOT do**: Do not change the provider’s supported version range or legacy transport behavior; do not leave stale `entity.v1_13_2` imports/resources.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with representative tests available.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.13.2/pom.xml` - rename the provider artifactId and parent coordinate usage.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_13_2/SpigotEntityAdapterV1_13_2.java` - rename adapter class and package root.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_13_2/EntityFactoryV1_13_2.java` - relocate the factory while keeping domain naming.
  - Pattern: `versions/1.13.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_13_2/EntityHookBinderV1_13_2.java` - relocate binder/helper code under the new root.
  - Pattern: `versions/1.13.2/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - rename resource filename and provider FQN.
  - Test: `versions/1.13.2/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_13_2/LegacyPacketTransportV1_8_to_1_13_2Test.java` - preserve legacy transport behavior after relocation.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.13.2` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_13_2.entity` and adapter type `SpigotVersionAdapterV1_13_2`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.13.2`.
  - [ ] Legacy transport tests still pass under the new package/type names.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.13.2 provider regression test stays green
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.13.2 -am test -Dtest=LegacyPacketTransportV1_8_to_1_13_2Test`; then scan `versions/1.13.2/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_13_2`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-7-provider-v1_13_2.txt

  Scenario: 1.13.2 service descriptor points at the renamed provider class
    Tool: Bash
    Steps: Verify the new service file exists and contains only `tech.guilhermekaua.spigotboot.v1_13_2.entity.SpigotVersionAdapterV1_13_2`; verify the old filename path is gone.
    Expected: One correct new service file; zero old-path files.
    Evidence: .sisyphus/evidence/task-7-provider-v1_13_2-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.13.2/**`

- [x] 8. Migrate the 1.16.5 provider module to `tech.guilhermekaua.spigotboot.v1_16_5.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_16_5`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_16_5` to `tech.guilhermekaua.spigotboot.v1_16_5.entity`; rename `SpigotEntityAdapterV1_16_5` to `SpigotVersionAdapterV1_16_5`; keep `EntityFactoryV1_16_5` and `EntityHookBinderV1_16_5` named as entity-domain helpers under the new package root; rename the ServiceLoader file to the new SPI path and update its content accordingly.
  **Must NOT do**: Do not change the provider’s supported range or modern transport/publication behavior; do not leave stale `entity.v1_16_5` imports/resources.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with representative unit coverage.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.16.5/pom.xml` - rename the provider artifactId and parent coordinate usage.
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_16_5/SpigotEntityAdapterV1_16_5.java` - rename adapter class and package root.
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_16_5/EntityFactoryV1_16_5.java` - relocate the factory while keeping domain naming.
  - Pattern: `versions/1.16.5/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_16_5/EntityHookBinderV1_16_5.java` - relocate binder/helper code under the new root.
  - Pattern: `versions/1.16.5/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - rename resource filename and provider FQN.
  - Test: `versions/1.16.5/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_16_5/EntityFactoryV1_16_5Test.java` - preserve factory/bridge behavior after relocation.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.16.5` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_16_5.entity` and adapter type `SpigotVersionAdapterV1_16_5`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.16.5`.
  - [ ] Factory tests still pass under the new package/type names.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.16.5 provider unit regression passes after relocation
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.16.5 -am test -Dtest=EntityFactoryV1_16_5Test`; then scan `versions/1.16.5/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_16_5`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-8-provider-v1_16_5.txt

  Scenario: 1.16.5 service descriptor points at the renamed provider class
    Tool: Bash
    Steps: Verify the new service file exists and contains only `tech.guilhermekaua.spigotboot.v1_16_5.entity.SpigotVersionAdapterV1_16_5`; verify the old filename path is gone.
    Expected: One correct new service file; zero old-path files.
    Evidence: .sisyphus/evidence/task-8-provider-v1_16_5-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.16.5/**`

- [x] 9. Migrate the 1.17.1 provider module to `tech.guilhermekaua.spigotboot.v1_17_1.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_17_1`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_17_1` to `tech.guilhermekaua.spigotboot.v1_17_1.entity`; rename `SpigotEntityAdapterV1_17_1` to `SpigotVersionAdapterV1_17_1`; keep entity-domain helpers such as `EntityFactoryV1_17_1` and `EntityHookBinderV1_17_1` named as entities under the new package root; rename the ServiceLoader file to the new SPI path and update its content accordingly.
  **Must NOT do**: Do not change the provider’s supported range or runtime wiring; do not leave stale `entity.v1_17_1` imports/resources.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with the same coupling pattern as the other modern families.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.17.1/pom.xml` - rename the provider artifactId and parent coordinate usage.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_17_1/SpigotEntityAdapterV1_17_1.java` - rename adapter class and package root.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_17_1/EntityFactoryV1_17_1.java` - relocate the factory while keeping domain naming.
  - Pattern: `versions/1.17.1/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_17_1/EntityHookBinderV1_17_1.java` - relocate binder/helper code under the new root.
  - Pattern: `versions/1.17.1/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - rename resource filename and provider FQN.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:98-121` - runtime selection still expects the 1.17.1 family range to resolve uniquely.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.17.1` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_17_1.entity` and adapter type `SpigotVersionAdapterV1_17_1`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.17.1`.
  - [ ] Runtime selection tests continue to resolve the 1.17-1.18.2 family through the renamed provider.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.17.1 provider builds cleanly after the package move
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.17.1 -am test`; then scan `versions/1.17.1/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_17_1`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-9-provider-v1_17_1.txt

  Scenario: 1.17.1 service descriptor targets the renamed adapter
    Tool: Bash
    Steps: Verify the new service file exists and contains only `tech.guilhermekaua.spigotboot.v1_17_1.entity.SpigotVersionAdapterV1_17_1`; verify the old filename path is gone.
    Expected: One correct new service file; zero old-path files.
    Evidence: .sisyphus/evidence/task-9-provider-v1_17_1-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.17.1/**`

- [x] 10. Migrate the 1.19.2 provider module to `tech.guilhermekaua.spigotboot.v1_19_2.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_19_2`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_19_2` to `tech.guilhermekaua.spigotboot.v1_19_2.entity`; rename `SpigotEntityAdapterV1_19_2` to `SpigotVersionAdapterV1_19_2`; keep entity-domain helpers such as `EntityFactoryV1_19_2` and `EntityHookBinderV1_19_2` named as entities under the new package root; rename the ServiceLoader file to the new SPI path and update its content accordingly.
  **Must NOT do**: Do not change the provider’s supported range or publication-family behavior; do not leave stale `entity.v1_19_2` imports/resources.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with representative publication-family coverage.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.19.2/pom.xml:15-25` - provider artifactId plus api/runtime dependency coordinates.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_19_2/SpigotEntityAdapterV1_19_2.java` - rename adapter class and package root.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_19_2/EntityFactoryV1_19_2.java` - relocate the factory while keeping domain naming.
  - Pattern: `versions/1.19.2/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_19_2/EntityHookBinderV1_19_2.java` - relocate binder/helper code under the new root.
  - Pattern: `versions/1.19.2/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter` - rename resource filename and provider FQN.
  - Test: `versions/1.19.2/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_19_2/EntityPublicationFamilyTest.java` - preserve publication-family behavior after relocation.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.19.2` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_19_2.entity` and adapter type `SpigotVersionAdapterV1_19_2`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.19.2`.
  - [ ] Publication-family tests still pass under the new package/type names.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.19.2 provider publication regression passes after relocation
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.19.2 -am test -Dtest=EntityPublicationFamilyTest`; then scan `versions/1.19.2/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_19_2`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-10-provider-v1_19_2.txt

  Scenario: 1.19.2 service descriptor points at the renamed provider class
    Tool: Bash
    Steps: Verify the new service file exists and contains only `tech.guilhermekaua.spigotboot.v1_19_2.entity.SpigotVersionAdapterV1_19_2`; verify the old filename path is gone.
    Expected: One correct new service file; zero old-path files.
    Evidence: .sisyphus/evidence/task-10-provider-v1_19_2-error.txt
  ```

  **Commit**: NO | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.19.2/**`

- [x] 11. Migrate the 1.21.11 provider module to `tech.guilhermekaua.spigotboot.v1_21_11.entity`

  **What to do**: Rename artifactId `spigot-boot-versions-v1_21_11`; move all implementation classes from `tech.guilhermekaua.spigotboot.entity.v1_21_11` to `tech.guilhermekaua.spigotboot.v1_21_11.entity`; rename `SpigotEntityAdapterV1_21_11` to `SpigotVersionAdapterV1_21_11`; keep entity-domain helpers such as `EntityFactoryV1_21_11` named as entities under the new package root; update imports to the renamed runtime/api infrastructure types; rename the ServiceLoader file to the new SPI path with content `tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11`.
  **Must NOT do**: Do not change the provider’s 1.21.x version family semantics, network metadata contract wiring, or transport-family behavior; do not leave stale `entity.v1_21_11` imports/resources.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: self-contained provider-module migration with the richest current adapter surface and newest-family test coverage.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 12-14 | Blocked By: 1, 3, 4

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `versions/1.21.11/pom.xml:15-33` - provider artifactId plus api/runtime dependency coordinates.
  - Pattern: `versions/1.21.11/src/main/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/SpigotEntityAdapterV1_21_11.java:23-75` - rename adapter class/package/imports while preserving version constants and metadata/transport families.
  - Pattern: `versions/1.21.11/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter:1` - rename resource filename and provider FQN.
  - Test: `versions/1.21.11/src/test/java/tech/guilhermekaua/spigotboot/entity/v1_21_11/EntityFactoryV1_21_11Test.java` - preserve newest-family metadata/bridge behavior after relocation.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:100-121` - runtime selection still expects the latest provider family to resolve uniquely.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `versions/1.21.11` compiles/tests with package root `tech.guilhermekaua.spigotboot.v1_21_11.entity` and adapter type `SpigotVersionAdapterV1_21_11`.
  - [ ] The old provider package root and old SPI resource filename are absent from `versions/1.21.11`.
  - [ ] Newest-family adapter tests and runtime family-selection tests still pass.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: 1.21.11 provider regression stays green after relocation
    Tool: Bash
    Steps: Run `mvnw.cmd -pl versions/1.21.11 -am test -Dtest=EntityFactoryV1_21_11Test`; then scan `versions/1.21.11/src/main/java` for `package tech.guilhermekaua.spigotboot.entity.v1_21_11`.
    Expected: BUILD SUCCESS; zero old package declarations remain.
    Evidence: .sisyphus/evidence/task-11-provider-v1_21_11.txt

  Scenario: 1.21.11 service descriptor points at the renamed provider class
    Tool: Bash
    Steps: Verify the new service file exists and contains only `tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11`; verify the old filename path is gone.
    Expected: One correct new service file; zero old-path files.
    Evidence: .sisyphus/evidence/task-11-provider-v1_21_11-error.txt
  ```

  **Commit**: YES | Message: `refactor(versions): migrate provider modules to shared package layout` | Files: `versions/1.8.8/**`, `versions/1.13.2/**`, `versions/1.16.5/**`, `versions/1.17.1/**`, `versions/1.19.2/**`, `versions/1.21.11/**`

- [x] 12. Replace direct versions dependencies in `test-plugin` with the shared module

  **What to do**: In `test-plugin/pom.xml`, remove the direct dependencies on `spigot-boot-entity-api`, `spigot-boot-entity-runtime`, and all six `spigot-boot-entity-v1_*` artifacts; add one dependency on `spigot-boot-versions`; keep the existing `maven-shade-plugin` `ServicesResourceTransformer`; do not add exclusions on the shared dependency.
  **Must NOT do**: Do not keep any direct api/runtime/provider dependency in `test-plugin/pom.xml`; do not remove `ServicesResourceTransformer`; do not point `test-plugin` at the parent-only `spigot-boot-versions-parent` coordinate; do not add exclusions on `spigot-boot-versions`.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: consumer dependency graph migration with classpath/shading consequences.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: 13-14 | Blocked By: 2, 6, 7, 8, 9, 10, 11

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `test-plugin/pom.xml:52-67` - keep `ServicesResourceTransformer` exactly in place for downstream service descriptor merging.
  - Pattern: `test-plugin/pom.xml:160-254` - remove the eight direct versions-related dependencies and replace them with one shared dependency.
  - Pattern: `versions/pom.xml:15-27` - parent coordinate is no longer the consumer artifact; shared module is the consumer-facing one.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `test-plugin/pom.xml` contains exactly one versions-subsystem dependency: `tech.guilhermekaua.spigot-boot:spigot-boot-versions:${project.version}`.
  - [ ] `test-plugin/pom.xml` contains zero direct dependencies on `spigot-boot-versions-api`, `spigot-boot-versions-runtime`, or any `spigot-boot-versions-v1_*` artifact.
  - [ ] `ServicesResourceTransformer` remains configured in the test-plugin shade plugin.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Test plugin compiles against the shared versions coordinate
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am -DskipTests validate`; then scan `test-plugin/pom.xml` for `spigot-boot-versions-api`, `spigot-boot-versions-runtime`, and `spigot-boot-versions-v1_`.
    Expected: Maven validation succeeds; the scans return zero direct versions-subsystem implementation dependencies.
    Evidence: .sisyphus/evidence/task-12-test-plugin-shared-dependency.txt

  Scenario: Downstream shade service merging remains configured
    Tool: Bash
    Steps: Inspect `test-plugin/pom.xml` for `ServicesResourceTransformer` after the dependency rewrite.
    Expected: Exactly one `ServicesResourceTransformer` entry remains in the test-plugin shade configuration.
    Evidence: .sisyphus/evidence/task-12-test-plugin-shared-dependency-error.txt
  ```

  **Commit**: NO | Message: `refactor(test-plugin): consume shared versions dependency` | Files: `test-plugin/pom.xml`

- [x] 13. Update `test-plugin` code to the renamed API/runtime surface

  **What to do**: Update all `test-plugin` Java sources to import `tech.guilhermekaua.spigotboot.versions.api.*` and `tech.guilhermekaua.spigotboot.versions.runtime.*`; replace `SpigotEntityBootstrap` with `SpigotVersionBootstrap`, `VersionedEntityPlatform` with `VersionedPlatform`, and any other renamed infrastructure types; keep entity-domain API type names unchanged after the package-root move.
  **Must NOT do**: Do not rename sample-plugin domain concepts that are outside the versions infrastructure; do not leave any `tech.guilhermekaua.spigotboot.entity.api` or `.entity.runtime` imports in `test-plugin/src/main/java`.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: repo consumer-source migration with a broad but mechanical import rename and a few public type renames.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 14 | Blocked By: 3, 4, 12

  **References** (executor has NO interview context - be exhaustive):
  - Pattern: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/services/EntityDemoService.java:41-58` - current wildcard api import plus bootstrap/platform imports that must move to the new packages/names.
  - Pattern: `versions/api/src/main/java/tech/guilhermekaua/spigotboot/entity/api/spi/EntityVersionAdapter.java:23-103` - api root/SPI rename boundary; domain types keep names while package roots move.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/bootstrap/SpigotEntityBootstrap.java:108-150` - new bootstrap entrypoint retains the same overload shape.
  - Pattern: `versions/runtime/src/main/java/tech/guilhermekaua/spigotboot/entity/runtime/VersionedEntityPlatform.java:76-145` - downstream sample code now targets `VersionedPlatform`.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `test-plugin/src/main/java` compiles with the new versions api/runtime package roots and renamed infrastructure types.
  - [ ] No old `tech.guilhermekaua.spigotboot.entity.api` or `tech.guilhermekaua.spigotboot.entity.runtime` imports remain in `test-plugin/src/main/java`.
  - [ ] Entity-domain api type names stay unchanged after the package-root migration.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Test plugin main sources compile against the renamed surface
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am -DskipTests compile`; then scan `test-plugin/src/main/java` for `tech.guilhermekaua.spigotboot.entity.api` and `tech.guilhermekaua.spigotboot.entity.runtime`.
    Expected: Compile succeeds; zero old imports/package references remain.
    Evidence: .sisyphus/evidence/task-13-test-plugin-source-rename.txt

  Scenario: Sample plugin now targets the renamed bootstrap/platform types
    Tool: Bash
    Steps: Inspect `EntityDemoService.java` (and any additional matching files) for `SpigotVersionBootstrap` and `VersionedPlatform`, and scan for `SpigotEntityBootstrap` / `VersionedEntityPlatform`.
    Expected: New type names are present where needed; old type names are absent from `test-plugin/src/main/java`.
    Evidence: .sisyphus/evidence/task-13-test-plugin-source-rename-error.txt
  ```

  **Commit**: NO | Message: `refactor(test-plugin): consume shared versions dependency` | Files: `test-plugin/src/main/java/**`

- [x] 14. Add integration and packaging regression coverage for the shared dependency model

  **What to do**: Add/update `test-plugin` integration tests so the consumer proves the new model works from a single shared dependency; create a dedicated test class (name it `VersionsSharedDependencyIntegrationTest`) under `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/` that boots the renamed runtime through `SpigotVersionBootstrap.boot("1.21.11")`, asserts the resolved adapter class is `tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11`, and verifies the classpath sees the new SPI type; keep `AutoDiscoveryIntegrationTest` green; use package-phase QA to inspect the built shared jar and shaded test-plugin jar.
  **Must NOT do**: Do not rely on manual jar browsing or manual server startup; do not skip package-phase inspection; do not leave any old SPI resource path in compiled outputs.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` - Reason: integration-test addition plus package-phase verification of transitive-dependency and service-merging behavior.
  - Skills: `[]` - No extra skill injection required.
  - Omitted: `[]` - N/A.

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: Final Verification Wave | Blocked By: 5, 12, 13

  **References** (executor has NO interview context - be exhaustive):
  - Test: `test-plugin/src/test/java/tech/guilhermekaua/spigotboot/testPlugin/test/AutoDiscoveryIntegrationTest.java:34-53` - keep existing integration smoke coverage green.
  - Test: `versions/runtime/src/test/java/tech/guilhermekaua/spigotboot/entity/runtime/SpigotEntityBootstrapTest.java:125-129,216-246` - preserve service-loaded discovery, classloader fallback, and failure-path semantics in the consumer-facing integration layer.
  - Pattern: `test-plugin/pom.xml:52-67` - packaged plugin jar already merges services through `ServicesResourceTransformer`.
  - Pattern: `versions/1.21.11/src/main/resources/META-INF/services/tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter:1` - each provider contributes one service entry; the shaded plugin jar must merge all six under the new SPI path.

  **Acceptance Criteria** (agent-executable only):
  - [ ] `VersionsSharedDependencyIntegrationTest` exists and passes while `test-plugin` depends only on `spigot-boot-versions`.
  - [ ] `mvnw.cmd -pl test-plugin -am package` succeeds.
  - [ ] The built `versions/shared` jar is thin, and the built `test-plugin` jar contains exactly one merged `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` file with six provider lines.
  - [ ] Booting `SpigotVersionBootstrap.boot("1.21.11")` from the consumer test suite resolves `SpigotVersionAdapterV1_21_11` without explicit registry registration.

  **QA Scenarios** (MANDATORY - task incomplete without these):
  ```
  Scenario: Shared dependency integration tests pass end-to-end
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am test -Dtest=AutoDiscoveryIntegrationTest,VersionsSharedDependencyIntegrationTest`.
    Expected: BUILD SUCCESS; one test class validates module auto-discovery and the other validates that `SpigotVersionBootstrap.boot("1.21.11")` resolves `tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11` through ServiceLoader-backed discovery.
    Evidence: .sisyphus/evidence/task-14-shared-dependency-integration.txt

  Scenario: Package-phase artifacts prove thin shared jar and merged provider metadata
    Tool: Bash
    Steps: Run `mvnw.cmd -pl test-plugin -am package`; inspect `versions/shared/target/*.jar` with `jar tf`; inspect `test-plugin/target/*.jar` and read `META-INF/services/tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter` from the shaded jar.
    Expected: Shared jar contains no provider/runtime shaded classes; shaded test-plugin jar contains exactly one new SPI service file with six provider entries and no old SPI service path.
    Evidence: .sisyphus/evidence/task-14-shared-dependency-integration-error.txt
  ```

  **Commit**: YES | Message: `refactor(test-plugin): consume shared versions dependency` | Files: `test-plugin/pom.xml`, `test-plugin/src/main/java/**`, `test-plugin/src/test/java/**`

<!-- TASKS INSERTED ABOVE FINAL VERIFICATION WAVE -->

## Final Verification Wave (MANDATORY — after ALL implementation tasks)
> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**
> **Never mark F1-F4 as checked before getting user's okay.** Rejection or user feedback -> fix -> re-run -> present again -> wait for okay.
- [x] F1. Plan Compliance Audit — oracle
- [x] F2. Code Quality Review — unspecified-high
- [x] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [x] F4. Scope Fidelity Check — deep

## Commit Strategy
- Commit 1 after Task 5: `refactor(versions): neutralize shared api and runtime naming`
- Commit 2 after Task 11: `refactor(versions): migrate provider modules to shared package layout`
- Commit 3 after Task 14: `refactor(test-plugin): consume shared versions dependency`

## Success Criteria
- One consumer-facing coordinate (`spigot-boot-versions`) replaces direct api/runtime/provider declarations in `test-plugin/pom.xml`.
- The repo builds/tests/packages successfully with the renamed versions namespace and coordinates.
- Adapter discovery still works through both ServiceLoader metadata and explicit runtime registration.
- Each version family still resolves exactly one provider for its declared version range.
- No legacy `spigot-boot-entity-*` coordinates, old SPI service-resource filenames, or `tech.guilhermekaua.spigotboot.entity.(api|runtime|v1_*)` package roots remain in build/test sources.
