# Repo-Wide spigot-api 1.8.8 Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the existing 1.8.8 animal-sniffer enforcement to repo-level infrastructure and activate it in the 6 runtime platform-spigot modules (`core-spigot`, `commands-spigot`, `commands-config-spigot`, `config-spigot`, `pmc`, `placeholder`).

**Architecture:** The signature-generator module moves from `modules/inventory-api/` to the repo root and is listed FIRST in the root reactor (platform-spigot builds before `modules/`, so the old location cannot serve it in-reactor). The shared `pluginManagement` moves from the inventory-api parent to the root pom; the established 3-line opt-in activates the check per module. Rollout per module follows the proven red→triage→green discipline.

**Tech Stack:** Maven 3.9.9 (wrapper), animal-sniffer-maven-plugin 1.27 (managed), spigot-api 1.8.8-R0.1-SNAPSHOT.

**Spec:** `docs/superpowers/specs/2026-06-06-repo-wide-188-enforcement-design.md`

**Environment:** Every Maven command MUST run with JDK 21 (`$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"`; shell-default JDK 25 crashes Lombok). All commands are PowerShell from the repo root `C:\Users\Guilherme\IdeaProjects\spigot-boot`.

**Violation triage policy (applies in Tasks 2–3):** classify every `Undefined reference` error:
1. **JDK-supertype false positive** — an inherited `java.lang.Object`/`Enum` member invoked through a Bukkit-typed receiver (e.g. `boolean org.bukkit.X.equals(Object)`, `Class org.bukkit.X.getClass()`). Fix at source in a `refactor(...)` commit placed BEFORE the enforcement commit: enums compare with `==`; other receivers hoist into an `Object`-typed local with the two-line why-comment (precedents: `CustomInventoryListener`, `InventoryApiNMS`).
2. **Dependency mask** — a NEW groupId:artifactId (not already excluded) bundling `org.bukkit` classes, found by the mask scan. Add it to the managed `excludeDependencies` in the root pom.
3. **Genuine newer-API usage** — a real Bukkit/Paper member that does not exist in 1.8.8. STOP and report it (file, line, member, whether it appears version-gated). Do NOT add ignores or rewrite on your own — the user decides gate-vs-fix.

---

## File Structure

- Move: `modules/inventory-api/spigot-api-1_8-signature/` → `spigot-api-1_8-signature/` (git mv; pom reparented + repo added)
- Modify: `pom.xml` (root — modules list + pluginManagement)
- Modify: `modules/inventory-api/pom.xml` (remove signature module entry + entire `<build>`)
- Modify: `modules/inventory-api/{api,nms-api,nms}/pom.xml` (activation comment wording only)
- Modify: `platform-spigot/{core-spigot,commands-spigot,commands-config-spigot,config-spigot,placeholder}/pom.xml` (activation inside existing `<build><plugins>`)
- Modify: `platform-spigot/pmc/pom.xml` (new `<build>` section — it has none)
- Modify: `CLAUDE.md` (install remedy path)
- Possibly modify (triage outcomes only): Java sources in platform-spigot modules
- Temporary (never committed): `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/Compat188Canary.java`

NOT touched: `platform-spigot/annotation-processor`, `test-plugin`, `nms-1_*` modules.

---

### Task 1: Promote signature module + root wiring

**Files:**
- Move+modify: `spigot-api-1_8-signature/pom.xml`
- Modify: `pom.xml` (root, `<modules>` at lines 38–47, `<build>` opens line 55)
- Modify: `modules/inventory-api/pom.xml` (modules list lines 18–31; whole `<build>` lines 60–114)
- Modify: `modules/inventory-api/api/pom.xml`, `modules/inventory-api/nms-api/pom.xml`, `modules/inventory-api/nms/pom.xml` (comment wording)

- [x] **Step 1.1: Move the module**

```powershell
git mv modules/inventory-api/spigot-api-1_8-signature spigot-api-1_8-signature
```

- [x] **Step 1.2: Reparent and re-describe the moved pom**

In `spigot-api-1_8-signature/pom.xml`:

(a) Replace the parent block:

```xml
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot-inventory-api-parent</artifactId>
        <version>2.0.2</version>
    </parent>
```

with:

```xml
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot</artifactId>
        <version>2.0.2</version>
    </parent>
```

(b) Replace the description line with:

```xml
    <description>Generates the spigot-api 1.8.8 animal-sniffer signature used to enforce API compatibility in every Bukkit/Paper-facing Spigot Boot module.</description>
```

(c) Insert directly after the `</properties>` line:

```xml

    <repositories>
        <!-- the root pom declares no repositories; only this module needs the spigot snapshots -->
        <repository>
            <id>spigot-repo</id>
            <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
        </repository>
    </repositories>
```

Nothing else in the file changes.

- [x] **Step 1.3: Root pom — modules list**

In `pom.xml`, replace:

```xml
    <modules>
        <module>core</module>
```

with:

```xml
    <modules>
        <!-- listed first: generates the 1.8.8 signature every check consumes in-reactor
             (declaration order guarantees this only for sequential builds; -T is not supported) -->
        <module>spigot-api-1_8-signature</module>
        <module>core</module>
```

- [x] **Step 1.4: Root pom — pluginManagement**

In `pom.xml`, insert directly after the `<build>` line (before `<plugins>`):

```xml
        <pluginManagement>
            <plugins>
                <!-- fails the build when a main-source class references Bukkit/Paper API that
                     does not exist in spigot-api 1.8.8. Modules opt in by declaring this plugin.
                     excludeDependencies is load-bearing: animal-sniffer ignores classes found in
                     dependencies BEFORE consulting the signature, so paper-api (compile API) and
                     MockBukkit (bundles org.bukkit.command / org.bukkit.plugin.java classes,
                     resolved via test scope) must not contribute to the ignore list (group
                     wildcard: an artifactId bump like MockBukkit-v1.21 must not silently
                     re-enable the masking).
                     Reactor runs that exclude the signature module (single-module -pl runs, -am
                     builds, or -f subtree builds like -f modules/inventory-api/pom.xml) resolve
                     the signature from the local repository: run
                     mvnw -pl spigot-api-1_8-signature install
                     once (and again after version bumps), or skip the check with
                     -Danimal.sniffer.skip=true. -->
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
                        <!-- the signature cannot contain the JDK API (see the signature module pom);
                             JDK classes are not dependencies either, so ignore them explicitly.
                             limitation: methods inherited from JDK supertypes but invoked on a
                             Bukkit-typed receiver (e.g. InventoryType.equals) cannot resolve and are
                             reported undefined - rewrite the call site (enums: use ==) or add a
                             targeted ignore -->
                        <ignores>
                            <ignore>java.*</ignore>
                            <ignore>javax.*</ignore>
                        </ignores>
                    </configuration>
                    <executions>
                        <execution>
                            <id>check-spigot-188-api</id>
                            <goals>
                                <goal>check</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
```

- [x] **Step 1.5: Strip the inventory-api parent**

In `modules/inventory-api/pom.xml`:

(a) Replace:

```xml
    <modules>
        <!-- listed first: generates the 1.8.8 signature the sibling checks consume in-reactor
             (declaration order guarantees this only for sequential builds; -T is not supported) -->
        <module>spigot-api-1_8-signature</module>
        <module>api</module>
```

with:

```xml
    <modules>
        <module>api</module>
```

(b) Delete the ENTIRE `<build>...</build>` section (lines 60–114 — it contains only the animal-sniffer `<pluginManagement>`, which now lives in the root pom).

- [x] **Step 1.6: Update the three inventory-api activation comments**

In each of `modules/inventory-api/api/pom.xml`, `modules/inventory-api/nms-api/pom.xml`, `modules/inventory-api/nms/pom.xml`, replace:

```xml
            <!-- activates the managed spigot-api 1.8.8 check (config in the inventory-api parent) -->
```

with:

```xml
            <!-- activates the managed spigot-api 1.8.8 check (config in the root pom) -->
```

- [x] **Step 1.7: Verify relocation — generation, cross-subtree in-reactor resolution, remedy**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
# cold local repo for the signature
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\tech\guilhermekaua\spigot-boot\spigot-boot-spigot-api-1_8-signature" -ErrorAction SilentlyContinue
# in-reactor resolution from the ROOT reactor (signature + an inventory-api consumer)
.\mvnw.cmd -pl spigot-api-1_8-signature,modules/inventory-api/api test
```

Expected: `BUILD SUCCESS`; log shows `animal-sniffer:1.27:build (generate-spigot-188-signature)` for the signature module, then `animal-sniffer:1.27:check (check-spigot-188-api) @ spigot-boot-inventory-api`, and `Tests run: 128, Failures: 0`.

```powershell
# the new documented remedy works and leaves the signature installed for subtree workflows
.\mvnw.cmd -pl spigot-api-1_8-signature install
Get-ChildItem "$env:USERPROFILE\.m2\repository\tech\guilhermekaua\spigot-boot\spigot-boot-spigot-api-1_8-signature\2.0.2" -Name
# the inventory-api subtree build (no signature module in its reactor anymore) resolves from local repo
.\mvnw.cmd -f modules\inventory-api\pom.xml test
```

Expected: install `BUILD SUCCESS` with the `.signature` file listed; subtree build `BUILD SUCCESS` with all three inventory-api checks executing.

- [x] **Step 1.8: Commit**

```powershell
git status --short
git add pom.xml modules/inventory-api/pom.xml modules/inventory-api/api/pom.xml modules/inventory-api/nms-api/pom.xml modules/inventory-api/nms/pom.xml spigot-api-1_8-signature modules/inventory-api/spigot-api-1_8-signature
git commit -m "build: promote 1.8.8 signature module and check config to repo root"
```

(`git status` first: the move must show as rename `R`; nothing unrelated staged.)

---

### Task 2: core-spigot rollout with canary red proof

**Files:**
- Modify: `platform-spigot/core-spigot/pom.xml` (`<build><plugins>` ends `</plugins>` at line 51, after maven-compiler-plugin)
- Temporary: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/Compat188Canary.java`
- Possibly modify (triage): Java sources under `platform-spigot/core-spigot/src/main/java`

- [x] **Step 2.1: Mask scan**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -q -pl platform-spigot/core-spigot dependency:build-classpath "-Dmdep.outputFile=target\cp-scan.txt"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = (Get-Content "platform-spigot\core-spigot\target\cp-scan.txt" -Raw).Trim() -split ';'
foreach ($j in $jars) {
    $name = Split-Path $j -Leaf
    try {
        $zip = [IO.Compression.ZipFile]::OpenRead($j)
        $hit = $zip.Entries | Where-Object { $_.FullName -like 'org/bukkit/*' -and $_.FullName -like '*.class' } | Select-Object -First 1
        $zip.Dispose()
        if ($hit) { Write-Output "BUNDLES org.bukkit: $name" }
    } catch {}
}
```

Expected output: only jars whose groupId is ALREADY excluded (`paper-api-*` from `io.papermc.paper`, `MockBukkit-*` from `com.github.seeseemelk`). Any OTHER hit is a dependency mask (triage category 2): identify its groupId:artifactId via the path in cp-scan.txt and add an `<excludeDependency>` entry to the root pom's managed `excludeDependencies` (keep the list alphabetical by groupId), noting it in the commit message.

- [x] **Step 2.2: Activate the check**

In `platform-spigot/core-spigot/pom.xml`, add after the maven-compiler-plugin's closing `</plugin>` (inside `<build><plugins>`):

```xml
            <!-- activates the managed spigot-api 1.8.8 check (config in the root pom) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
```

- [x] **Step 2.3: Red canary (temporary, never committed)**

Create `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/Compat188Canary.java`:

```java
package tech.guilhermekaua.spigotboot.core.spigot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

// temporary red-canary for the animal-sniffer 1.8.8 check - DELETE BEFORE COMMITTING.
// getStorageContents() exists in paper-api 1.20.1 (so javac passes) but not in
// spigot-api 1.8.8 (so the check must fail).
final class Compat188Canary {
    private Compat188Canary() {
    }

    static ItemStack[] storage(Inventory inventory) {
        return inventory.getStorageContents();
    }
}
```

- [x] **Step 2.4: Run the check — the canary MUST fail it**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl spigot-api-1_8-signature,platform-spigot/core-spigot process-test-classes
```

Expected: `BUILD FAILURE` including:

```
[ERROR] ...Compat188Canary.java... Undefined reference: org.bukkit.inventory.ItemStack[] org.bukkit.inventory.Inventory.getStorageContents()
```

**CRITICAL GATE:** if the build PASSES, STOP — the relocated wiring is vacuous for platform-spigot. Report BLOCKED with the full output; commit nothing.

Collect any OTHER `Undefined reference` errors in the output — those are core-spigot's real violation list for triage.

- [x] **Step 2.5: Delete the canary, triage remaining violations**

```powershell
Remove-Item platform-spigot\core-spigot\src\main\java\tech\guilhermekaua\spigotboot\core\spigot\Compat188Canary.java
```

Apply the **Violation triage policy** from the plan header to every error collected in Step 2.4. Category-1 fixes go in their own `refactor(platform-spigot): ...` commit(s) BEFORE Step 2.7's commit. Category 3 → STOP and report.

- [x] **Step 2.6: Green run**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl spigot-api-1_8-signature,platform-spigot/core-spigot test
```

Expected: `BUILD SUCCESS` with `animal-sniffer:1.27:check (check-spigot-188-api) @ spigot-boot-core-spigot` and existing tests passing.

- [x] **Step 2.7: Commit**

```powershell
git status --short platform-spigot
git add platform-spigot/core-spigot/pom.xml
git commit -m "build(platform-spigot): enforce spigot-api 1.8.8 compatibility in core-spigot"
```

(Include the root pom too if Step 2.1 added an exclude. Canary must be gone.)

---

### Task 3: Remaining five modules

**Files:**
- Modify: `platform-spigot/commands-spigot/pom.xml`, `platform-spigot/commands-config-spigot/pom.xml`, `platform-spigot/config-spigot/pom.xml`, `platform-spigot/placeholder/pom.xml` (each: `<build><plugins>` after maven-compiler-plugin, same shape as core-spigot)
- Modify: `platform-spigot/pmc/pom.xml` (NO `<build>` exists — insert a full section before `</project>`)
- Possibly modify (triage): Java sources in those modules

For EACH module `M` in `commands-spigot`, `commands-config-spigot`, `config-spigot`, `pmc`, `placeholder`, repeat:

- [x] **Step 3.1 (×5): Mask scan for M**

Same script as Step 2.1 with `$m = "platform-spigot\M"` (and `-pl platform-spigot/M`). Same expected outcome and exclude-handling.

- [x] **Step 3.2 (×5): Activate in M**

For the four modules WITH an existing `<build><plugins>` (commands-spigot, commands-config-spigot, config-spigot, placeholder), add after the maven-compiler-plugin's closing `</plugin>`:

```xml
            <!-- activates the managed spigot-api 1.8.8 check (config in the root pom) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
```

For `pmc` (no `<build>` section), insert before `</project>`:

```xml
    <build>
        <plugins>
            <!-- activates the managed spigot-api 1.8.8 check (config in the root pom) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

- [x] **Step 3.3: Combined red probe across all five**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl "spigot-api-1_8-signature,platform-spigot/commands-spigot,platform-spigot/commands-config-spigot,platform-spigot/config-spigot,platform-spigot/pmc,platform-spigot/placeholder" process-test-classes
```

Outcome A — `BUILD SUCCESS`: all five are already 1.8.8-clean; proceed to Step 3.5.
Outcome B — `BUILD FAILURE` with `Undefined reference` errors: collect them per module and triage per the policy (category-1 fixes in `refactor(platform-spigot): ...` commits BEFORE Step 3.6; category 3 → STOP and report). Note: Maven stops at the first failing module — after fixing it, re-run this step until the whole list is green so later modules get probed too.

- [x] **Step 3.4: (only if triage produced source fixes) Commit each fix**

One `refactor(platform-spigot): <what>` commit per logical fix, each with a body explaining the animal-sniffer rationale (see `git log` for the two precedents: `compare InventoryType by identity in listener`, `hoist server to Object receiver in nms selector`).

- [x] **Step 3.5: Green run with tests across all five**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl "spigot-api-1_8-signature,platform-spigot/commands-spigot,platform-spigot/commands-config-spigot,platform-spigot/config-spigot,platform-spigot/pmc,platform-spigot/placeholder" test
```

Expected: `BUILD SUCCESS` with five `check (check-spigot-188-api)` execution lines (one per module).

- [x] **Step 3.6: Commit**

```powershell
git status --short platform-spigot
git add platform-spigot/commands-spigot/pom.xml platform-spigot/commands-config-spigot/pom.xml platform-spigot/config-spigot/pom.xml platform-spigot/pmc/pom.xml platform-spigot/placeholder/pom.xml
git commit -m "build(platform-spigot): enforce spigot-api 1.8.8 compatibility in remaining runtime modules"
```

(Include the root pom if any Step 3.1 scan added excludes.)

---

### Task 4: Docs + cold-cache CI-parity verification

**Files:**
- Modify: `CLAUDE.md` (Build commands section)

- [x] **Step 4.1: Update the CLAUDE.md remedy path**

Replace:

```markdown
On a fresh clone, first run `mvnw.cmd -f modules/inventory-api/pom.xml -pl spigot-api-1_8-signature install` (the spigot-api 1.8.8 API-check signature is build-internal, never published), or skip the check with `-Danimal.sniffer.skip=true`.
```

with:

```markdown
On a fresh clone, first run `mvnw.cmd -pl spigot-api-1_8-signature install` (the spigot-api 1.8.8 API-check signature is build-internal, never published), or skip the check with `-Danimal.sniffer.skip=true`.
```

- [x] **Step 4.2: Cold-cache full-reactor verification (the CI command)**

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\tech\guilhermekaua\spigot-boot\spigot-boot-spigot-api-1_8-signature" -ErrorAction SilentlyContinue
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd test -B 2>&1 | Tee-Object -FilePath target\repo-wide-188.log | Select-Object -Last 15
Select-String -Path target\repo-wide-188.log -Pattern "animal-sniffer"
Select-String -Path target\repo-wide-188.log -Pattern "Undefined reference"
```

Expected: `BUILD SUCCESS`; exactly ONE `build (generate-spigot-188-signature)` line appearing FIRST (the signature module is the first reactor entry); exactly NINE `check (check-spigot-188-api)` lines — for `spigot-boot-inventory-api`, `spigot-boot-inventory-api-nms-api`, `spigot-boot-inventory-api-nms`, `spigot-boot-core-spigot`, `spigot-boot-commands-spigot`, `spigot-boot-commands-config-spigot`, `spigot-boot-config-spigot`, `spigot-boot-plugin-messaging-spigot`, `spigot-boot-placeholder-spigot` (all artifactIds verified against the poms) — and none for any other module; zero `Undefined reference` lines.

- [x] **Step 4.3: Restore the local install**

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl spigot-api-1_8-signature install
```

Expected: `BUILD SUCCESS`.

- [x] **Step 4.4: Commit**

```powershell
git status --short
git add CLAUDE.md
git commit -m "docs: update 1.8.8 signature install remedy for repo-root location"
```

---

## Execution record (2026-06-06)

All four tasks executed and verified; cold-cache `mvnw test -B` produced 1 signature
generation + exactly 9 checks with 0 undefined references, BUILD SUCCESS.

Findings from the red runs, triaged per the policy:

- **Leftover user canary (api module):** Task 1's verification caught a
  `Player#sendTitle(String,String,int,int,int)` (1.11+) line in
  `PapiPlaceholderApplier` — the user's own manual test of the check, still in the
  working tree; removed with their knowledge (accidental end-to-end proof of the
  relocated wiring).
- **core-spigot:** `UnsafeValues#fromLegacy` (1.13+) in `TypeUtil#convertFromLegacy`
  is data-gated by `LEGACY_*` materials (unreachable on 1.8.8) → targeted
  `org.bukkit.UnsafeValues` ignore. `SkullMeta#setOwningPlayer` (1.12.1+) in
  `ItemUtils#getHeadByUuid` was a genuine ungated 1.8.8 crash → runtime
  `NoSuchMethodError` fallback to `setOwner` (commit `ec2a967`) + targeted
  `SkullMeta` ignore (`40b3d53`).
- **commands-spigot:** 4 JDK-supertype false positives (`Material.name()`,
  `getClass()` ×3) → Enum/Object receiver retypes with why-comments.
- **config-spigot:** `Sound.name()`/`Material.name()` + `getClass()` ×2 → same
  retypes (all in `088cdbc`). `ParticleSerializer` referenced `org.bukkit.Particle`
  (class itself is 1.9+), crashing `BukkitSerializers#registerAll` on 1.8.8 —
  user decided to DELETE the serializer (`6e11be5`).
- **commands-config-spigot, pmc, placeholder:** clean on first probe.
- All five mask scans found only the already-excluded paper-api and MockBukkit jars.
