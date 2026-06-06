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
- `includeJavaHome=false`: animal-sniffer cannot harvest the JDK API on modern
  JDKs (9+ have no boot classpath — verified empirically on JDK 21 with plugin
  1.27, which skips generation with a warning). The signature therefore covers
  spigot-api 1.8.8 (and its transitives) only; the consuming checks ignore
  `java.*`/`javax.*` instead (see below), consistent with JDK-level enforcement
  being a non-goal.
- Listed **first** in the inventory-api parent `<modules>` so the sequential
  reactor builds it before `api`/`nms-api`/`nms` (no dependency edge exists; the
  build is not parallelized).
- `<maven.deploy.skip>true</maven.deploy.skip>` plus
  `central-publishing-maven-plugin` `skipPublishing=true`: build-internal
  artifact, never published. Both are needed — the release workflow runs
  `mvn clean deploy` through the central-publishing extension, which does not
  honor `maven.deploy.skip`. `install` stays enabled so single-module `-pl`
  workflows work after one `install` of this module (a plain reactor `test`
  build attaches in-session but does not install).

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
            <excludeDependency>com.github.seeseemelk:*</excludeDependency>
        </excludeDependencies>
        <ignores>
            <ignore>java.*</ignore>
            <ignore>javax.*</ignore>
        </ignores>
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
    <ignores combine.children="append">
        <ignore>org.bukkit.inventory.InventoryView</ignore>
    </ignores>
</configuration>
```

(`combine.children="append"` is required: Maven's default configuration merge
would otherwise *replace* the managed `java.*`/`javax.*` ignores with this
module-level list.) This suppresses the intentional, version-gated
`InventoryView#setTitle` (1.20+) call in
`BukkitInventoryTitleUpdater` — selected at runtime only when the server minor
version is >= 20.

The version-specific `nms-1_8_R3` … `nms-1_19_R3` modules do not declare the
plugin and are unaffected. The signature module declares it for the `build`
goal only and defends against `pluginManagement` bleed-through: it pins its
configuration with `combine.self="override"` (the managed checker
configuration must not merge into the `build` goal, where
`excludeDependencies` would silently shrink the generated signature) and
unbinds the inherited check execution (`<phase>none</phase>` for
`check-spigot-188-api` — a self-referential check on a classless module).

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

MockBukkit must be excluded for the same reason: the check resolves test-scope
dependencies (`ResolutionScope.TEST`), and `MockBukkit-v1.20-3.20.2.jar` bundles
classes in `org.bukkit.command` and `org.bukkit.plugin.java` (verified by jar
inspection on 2026-06-05), which would otherwise put those packages on the
ignore list and mask future main-source references into them. Excluding it has
no downside because `checkTestClasses=false` — main classes never reference
MockBukkit. The exclude uses the group wildcard `com.github.seeseemelk:*` so an
artifactId bump (e.g. `MockBukkit-v1.21`) cannot silently re-enable the
masking.

The managed `java.*`/`javax.*` ignores are the flip side of the signature not
containing the JDK API (see the module section above): JDK classes are not
Maven dependencies, so without these ignores every `java.lang.*` reference
would be reported as undefined. This expresses the JDK-enforcement non-goal in
config.

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
- Because the signature lacks JDK supertypes, methods inherited from
  `java.lang.Object`/`java.lang.Enum` but invoked through a Bukkit-typed
  receiver (e.g. `InventoryType#equals`) are reported as undefined references —
  a false positive. Rewrite such call sites (enums: use `==`; other inherited
  methods: hoist the receiver into an `Object`-typed local) or add a targeted
  ignore. Occurrences found and rewritten during rollout:
  `CustomInventoryListener` (`InventoryType#equals` → `==`) and `InventoryApiNMS`
  (`Bukkit.getServer().getClass()` twice → `Object`-typed hoists with
  why-comments).
- Other root modules (`core`, `commands`, …) are out of scope.

## Failure mode and developer experience

A violation fails `mvn test` at `process-test-classes` with
`Undefined reference: <class>#<member>` plus the offending class. Identical
behavior locally (`mvnw.cmd test` from root or `modules/inventory-api`).

Caveat (documented as a pom comment): a single-module run such as
`mvnw.cmd -pl modules/inventory-api/api test` — and any `-am` build that pulls a
consumer into the reactor (e.g. `mvnw.cmd -pl test-plugin -am package`) — has no
reactor signature module and no dependency edge for `-am` to follow; the
signature resolves from the local repository. A reactor `test` build only
attaches in-session and does NOT install, so the working remedies are
`mvnw.cmd -f modules/inventory-api/pom.xml -pl spigot-api-1_8-signature install`
(once per version bump) or skipping via `-Danimal.sniffer.skip=true`. Giving
consumers a real reactor edge (a `type=signature` dependency) is a possible
follow-up but needs verification that it stays off classpaths and out of the
checker's dependency-ignore scan.

## Verification plan

1. **Green**: purge `tech/guilhermekaua/spigot-boot/spigot-boot-spigot-api-1_8-signature`
   from `~/.m2`, run `mvnw.cmd -f modules/inventory-api/pom.xml test` (or the
   full `mvnw.cmd test -B`) — proves in-reactor generation, resolution, and
   passing checks. (Do NOT use `-pl modules/inventory-api -am`: selecting an
   aggregator with `-pl` does not include its children, so no checks would run.)
2. **Red canary (reverted afterwards)**: add a temporary
   `inventory.getStorageContents()` (1.9+) call in `api` — the build must fail
   with the undefined-reference error.
3. **Ignore correctness**: `nms` stays green with its `InventoryView` ignore while
   `InventoryApiNMS` (`Bukkit.getBukkitVersion()` etc.) remains checked.
