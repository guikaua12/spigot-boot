# Design: animal-sniffer spigot-api 1.8.8 enforcement for inventory-api

Date: 2026-06-05
Status: approved
Scope: `modules/inventory-api` (submodules `api`, `nms-api`, `nms`)

## Problem

The version-independent inventory-api submodules compile against paper-api 1.20.1
but must only use Bukkit API that exists in spigot-api 1.8.8 (the oldest supported
server, targeted by `nms-1_8_R3`). Nothing enforces this today: a 1.9+ method
reference would compile fine and only fail at runtime on a 1.8.8 server. A manual
recompile-against-1.8.8 review (2026-06-05) confirmed the modules are currently
clean; this design locks that in.

## Goal

CI (`mvn test -B`, JDK 17, Maven 3.9.9) fails the build whenever a main-source
class in `api`, `nms-api`, or `nms` references a Bukkit/Paper class or member that
does not exist in spigot-api 1.8.8 — with no changes to `ci.yml`.

## Mechanism: animal-sniffer-maven-plugin 1.27

### 1. Signature-generator module

New module `modules/inventory-api/spigot-api-1_8-signature/`:

- artifactId `spigot-boot-spigot-api-1_8-signature`, `<packaging>pom</packaging>`
  (same shape as the official `org.codehaus.mojo.signature` projects), parent
  `spigot-boot-inventory-api-parent`.
- Single dependency: `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` (compile scope;
  resolvable via the SpigotMC snapshot repository already declared in the
  inventory-api parent — `nms-1_8_R3` uses the same-era artifact).
- animal-sniffer `build` goal bound explicitly to **`generate-resources`**. The
  mojo has no default phase; binding early means a plain `mvn test` reactor run
  generates the signature before any consumer's check executes.
- The mojo attaches the result via `projectHelper.attachArtifact(project,
  "signature", …)`; Maven's ReactorReader serves attached artifacts in-session,
  so consumers resolve it mid-build without `install`.
- `includeJavaHome=true` (default): the signature contains the building JDK's
  API plus the spigot-api 1.8.8 API, keeping `java.*` references resolvable.
- Listed **first** in the inventory-api parent `<modules>` so the sequential
  reactor builds it before `api`/`nms-api`/`nms` (no dependency edge exists; the
  build is not parallelized).
- `<maven.deploy.skip>true</maven.deploy.skip>`: build-internal artifact, not
  published by release workflows. `install` stays enabled so single-module `-pl`
  workflows work after one root build.

### 2. Check executions on api, nms-api, nms

Shared setup in the inventory-api parent `<pluginManagement>`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>animal-sniffer-maven-plugin</artifactId>
    <version>1.27</version>
    <configuration>
        <signature>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-spigot-api-1_8-signature</artifactId>
            <version>${project.version}</version>
        </signature>
        <excludeDependencies>
            <excludeDependency>io.papermc.paper:paper-api</excludeDependency>
        </excludeDependencies>
    </configuration>
    <executions>
        <execution>
            <id>check-spigot-188-api</id>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

Each of `api`, `nms-api`, `nms` declares the plugin (no version or configuration
needed — both are managed) in `<build><plugins>` to activate the managed
execution. `nms` additionally sets:

```xml
<configuration>
    <ignores>
        <ignore>org.bukkit.inventory.InventoryView</ignore>
    </ignores>
</configuration>
```

for the intentional, version-gated `InventoryView#setTitle` (1.20+) call in
`BukkitInventoryTitleUpdater` — selected at runtime only when the server minor
version is >= 20.

The version-specific `nms-1_8_R3` … `nms-1_19_R3` modules and the signature
module itself do not declare the plugin and are unaffected.

### Why `excludeDependencies` is load-bearing

Verified in plugin source (`SignatureChecker.shouldBeIgnored` runs **before**
signature lookup): with the default `ignoreDependencies=true`, every dependency's
classes join an ignore list that overrides the signature. paper-api 1.20.1 stays a
compile dependency, so without exclusion all `org.bukkit.*` references would be
ignored and the check would pass vacuously. Excluding `io.papermc.paper:paper-api`
from the ignore list means:

- references into paper-api classes are strictly checked against the 1.8.8
  signature (missing members fail), and
- paper-only classes (`io.papermc.*`, `com.destroystokyo.*`) fail outright —
  neither in the signature nor ignored.

The check goal's defaults do the rest: phase `process-test-classes` (runs under
`mvn test`), `checkTestClasses=false` (MockBukkit-1.20 tests untouched),
`failOnError=true`.

## Coverage and non-goals

- Main classes only. Test sources intentionally target MockBukkit v1.20.
- Other dependencies (spigot-boot-core, placeholderapi, nms submodules, adventure)
  remain dependency-ignored; their internals are not this module's contract. As of
  2026-06-05 there are zero `net.kyori`/`net.md_5` imports in main sources.
- JDK-8-API enforcement is a non-goal (CI has no JDK 8 toolchain; `source/target
  1.8` stays as is).
- Other root modules (`core`, `commands`, …) are out of scope.

## Failure mode and developer experience

A violation fails `mvn test` at `process-test-classes` with
`Undefined reference: <class>#<member>` plus the offending class. Identical
behavior locally (`mvnw.cmd test` from root or `modules/inventory-api`).

Caveat (documented as a pom comment): a single-module run such as
`mvnw.cmd -pl modules/inventory-api/api test` has no reactor signature module and
no dependency edge for `-am` to follow; it resolves the signature from the local
repository, requiring one prior root/aggregator build or
`mvnw.cmd -pl modules/inventory-api/spigot-api-1_8-signature install`.

## Verification plan

1. **Green**: purge `tech/guilhermekaua/spigot-boot/spigot-boot-spigot-api-1_8-signature`
   from `~/.m2`, run `mvnw.cmd -pl modules/inventory-api -am test` — proves
   in-reactor generation, resolution, and passing checks.
2. **Red canary (reverted afterwards)**: add a temporary
   `inventory.getStorageContents()` (1.9+) call in `api` — the build must fail
   with the undefined-reference error.
3. **Ignore correctness**: `nms` stays green with its `InventoryView` ignore while
   `InventoryApiNMS` (`Bukkit.getBukkitVersion()` etc.) remains checked.
