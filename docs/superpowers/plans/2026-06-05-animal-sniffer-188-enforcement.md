# Animal-Sniffer spigot-api 1.8.8 Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `mvn test -B` (exactly what CI runs) fail whenever a main-source class in `modules/inventory-api`'s `api`, `nms-api`, or `nms` submodules references a Bukkit/Paper class or member that does not exist in spigot-api 1.8.8.

**Architecture:** A new pom-packaged module generates a `.signature` artifact from `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT` at `generate-resources` (attached in-session, served to sibling modules by Maven's ReactorReader). The three version-independent modules run the animal-sniffer `check` goal (default phase `process-test-classes`) against it. `excludeDependencies` keeps paper-api 1.20.1 and MockBukkit out of the checker's dependency-ignore list — without this the check passes vacuously because ignores take precedence over the signature.

**Tech Stack:** Maven 3.9.9 (wrapper), animal-sniffer-maven-plugin 1.27, spigot-api 1.8.8-R0.1-SNAPSHOT (already in `~/.m2`; SpigotMC snapshot repo declared in the inventory-api parent).

**Spec:** `docs/superpowers/specs/2026-06-05-animal-sniffer-188-design.md`

**Important environment note:** Every Maven command below MUST run with JDK 21 (Lombok 1.18.36 crashes on the shell-default JDK 25). Each Run step sets `$env:JAVA_HOME` explicitly. All commands are PowerShell, run from the repo root `C:\Users\Guilherme\IdeaProjects\spigot-boot`.

---

## File Structure

- Create: `modules/inventory-api/spigot-api-1_8-signature/pom.xml` — signature-generator module (pom packaging, not deployed)
- Modify: `modules/inventory-api/pom.xml` — register new module first; add `<pluginManagement>` with shared check config
- Modify: `modules/inventory-api/api/pom.xml` — activate the managed check
- Modify: `modules/inventory-api/nms-api/pom.xml` — activate the managed check
- Modify: `modules/inventory-api/nms/pom.xml` — activate the managed check + `InventoryView` ignore
- Temporary (never committed): `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/Compat188Canary.java` — red-canary proving the check bites

The `nms-1_8_R3` … `nms-1_19_R3` modules are NOT touched.

---

### Task 1: Signature-generator module

**Files:**
- Create: `modules/inventory-api/spigot-api-1_8-signature/pom.xml`
- Modify: `modules/inventory-api/pom.xml` (modules list, currently lines 18–28)

- [ ] **Step 1.1: Create the module pom**

Create `modules/inventory-api/spigot-api-1_8-signature/pom.xml` with exactly:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot-inventory-api-parent</artifactId>
        <version>2.0.2</version>
    </parent>

    <name>${project.artifactId}</name>
    <description>Generates the spigot-api 1.8.8 animal-sniffer signature used to enforce API compatibility in the version-independent inventory API modules.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-spigot-api-1_8-signature</artifactId>
    <packaging>pom</packaging>

    <properties>
        <!-- build-internal artifact: consumed in-reactor (or from the local repo), never published -->
        <maven.deploy.skip>true</maven.deploy.skip>
    </properties>

    <dependencies>
        <!-- compile scope on purpose: the animal-sniffer build goal only collects
             signatures from classpath dependencies -->
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.8.8-R0.1-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
                <version>1.27</version>
                <configuration>
                    <!-- modern JDKs (9+) have no boot classpath, so animal-sniffer cannot harvest the
                         JDK API here; the signature covers spigot-api (and its transitives) only and
                         the consuming checks ignore java.* / javax.* instead (JDK-level enforcement
                         is a non-goal) -->
                    <includeJavaHome>false</includeJavaHome>
                </configuration>
                <executions>
                    <execution>
                        <id>generate-spigot-188-signature</id>
                        <!-- bound early so a plain `mvn test` reactor run (what CI executes)
                             attaches the signature before sibling checks need it -->
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>build</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.sonatype.central</groupId>
                <artifactId>central-publishing-maven-plugin</artifactId>
                <configuration>
                    <!-- maven.deploy.skip does not cover the central-publishing extension used by the
                         release workflow; skip explicitly so this build-internal artifact never ships -->
                    <skipPublishing>true</skipPublishing>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

- [ ] **Step 1.2: Register the module first in the inventory-api parent**

In `modules/inventory-api/pom.xml`, the modules list currently reads:

```xml
    <modules>
        <module>api</module>
```

Change to (new module listed FIRST — there is no dependency edge, so sequential reactor order comes from declaration order):

```xml
    <modules>
        <!-- listed first: generates the 1.8.8 signature the sibling checks consume in-reactor
             (declaration order guarantees this only for sequential builds; -T is not supported) -->
        <module>spigot-api-1_8-signature</module>
        <module>api</module>
```

- [ ] **Step 1.3: Verify the signature generates and attaches**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature generate-resources
Test-Path modules\inventory-api\spigot-api-1_8-signature\target\spigot-boot-spigot-api-1_8-signature-2.0.2.signature
```

Expected: `BUILD SUCCESS` with `[INFO] Wrote signatures for 5556 classes.` (spigot-api 1.8.8 plus its transitives; the JDK is intentionally absent — see the pom comment), and the `Test-Path` prints `True`.

- [ ] **Step 1.4: Commit**

```powershell
git add modules/inventory-api/spigot-api-1_8-signature/pom.xml modules/inventory-api/pom.xml
git commit -m "build(inventory-api): add spigot-api 1.8 signature generator module"
```

---

### Task 2: Shared check config + api module enforcement (red → green)

**Files:**
- Modify: `modules/inventory-api/pom.xml` (add `<build><pluginManagement>` after `</repositories>`)
- Modify: `modules/inventory-api/api/pom.xml` (add plugin to existing `<build><plugins>`, currently lines 21–49)
- Temporary: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/Compat188Canary.java`

- [ ] **Step 2.1: Add pluginManagement to the inventory-api parent**

In `modules/inventory-api/pom.xml`, insert between `</repositories>` and `</project>`:

```xml
    <build>
        <pluginManagement>
            <plugins>
                <!-- fails the build when a main-source class references Bukkit/Paper API that
                     does not exist in spigot-api 1.8.8. Modules opt in by declaring this plugin.
                     excludeDependencies is load-bearing: animal-sniffer ignores classes found in
                     dependencies BEFORE consulting the signature, so paper-api (compile API) and
                     MockBukkit (bundles org.bukkit.command / org.bukkit.plugin.java classes,
                     resolved via test scope) must not contribute to the ignore list.
                     Single-module runs (e.g. mvnw -pl modules/inventory-api/api test) resolve the
                     signature from the local repository: run a modules/inventory-api reactor build
                     (or install the spigot-api-1_8-signature module) once first. -->
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
                            <excludeDependency>com.github.seeseemelk:MockBukkit-v1.20</excludeDependency>
                        </excludeDependencies>
                        <!-- the signature cannot contain the JDK API (see the signature module pom);
                             JDK classes are not dependencies either, so ignore them explicitly -->
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
    </build>
```

- [ ] **Step 2.2: Activate the check in the api module**

In `modules/inventory-api/api/pom.xml`, the `<build><plugins>` section currently contains only `maven-compiler-plugin` (ends `</plugin>` line 48 followed by `</plugins>` line 49). Add after the compiler plugin's closing `</plugin>`:

```xml
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
```

(No version/configuration — both come from the parent `pluginManagement`.)

- [ ] **Step 2.3: Write the red canary (temporary, never committed)**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/Compat188Canary.java`:

```java
package tech.guilhermekaua.spigotboot.inventoryapi;

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

- [ ] **Step 2.4: Run the check — expect FAILURE on the canary**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature,api process-test-classes
```

Expected: `BUILD FAILURE`. The animal-sniffer output must contain an undefined-reference error naming the canary, similar to:

```
[ERROR] ...Compat188Canary.java... Undefined reference: org.bukkit.inventory.ItemStack[] org.bukkit.inventory.Inventory.getStorageContents()
```

If this step PASSES instead, STOP — the check is vacuous (most likely the `excludeDependencies` config is wrong) and the plan must not proceed until the failure reproduces.

> **Execution note (2026-06-06):** the red run also surfaced one pre-existing violation of the
> JDK-supertype false-positive class: `CustomInventoryListener.java:92` called
> `InventoryType#equals`, unresolvable because the signature lacks `java.lang.Enum`/`Object`.
> Fixed by switching to identity comparison (`==`) in its own commit
> (`refactor(inventory-api): compare InventoryType by identity in listener`) placed BEFORE the
> build-enforcement commit so every commit stays green.

- [ ] **Step 2.5: Delete the canary**

```powershell
Remove-Item modules\inventory-api\api\src\main\java\tech\guilhermekaua\spigotboot\inventoryapi\Compat188Canary.java
```

- [ ] **Step 2.6: Re-run with full tests — expect GREEN**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature,api test
```

Expected: `BUILD SUCCESS`; the log shows the check ran (a `animal-sniffer:1.27:check (check-spigot-188-api)` line for the api module) and the existing unit tests pass.

- [ ] **Step 2.7: Commit**

```powershell
git status --short modules/inventory-api
git add modules/inventory-api/pom.xml modules/inventory-api/api/pom.xml
git commit -m "build(inventory-api): enforce spigot-api 1.8.8 compatibility in api module"
```

(`git status` first: confirm the canary file is gone and only the two poms are staged.)

- [x] **Step 2.8 (added by code review): hardening commit**

> **Execution note (2026-06-06, commit `3a1e293`):** quality review of Task 2 found two Important
> issues, fixed in a follow-up commit touching all three poms:
> 1. The signature module inherited the managed `check-spigot-188-api` execution AND the managed
>    checker `<configuration>` merged into its `build` goal — where `excludeDependencies` is a
>    real parameter that could silently shrink the generated signature. Fixed by
>    `combine.self="override"` on the module's plugin configuration and an unbind execution
>    (`<id>check-spigot-188-api</id><phase>none</phase>`); its explicit `<version>1.27</version>`
>    was dropped (now managed by the parent).
> 2. The single-module-run pom comment prescribed a non-working remedy (a reactor `test` build
>    attaches but never installs), and `-am` builds pulling consumers into the reactor (e.g.
>    `mvnw -pl test-plugin -am package`) broke on cold cache. Comment now names the working
>    remedy (`mvnw -f modules/inventory-api/pom.xml -pl spigot-api-1_8-signature install`, or
>    `-Danimal.sniffer.skip=true`), and the signature was installed locally.
> Also: MockBukkit exclude widened to `com.github.seeseemelk:*` (artifactId bumps must not
> re-enable masking) and the api opt-in entry gained an explanatory comment. Red canary re-run
> after the changes: still fails as designed.

---

### Task 3: nms-api and nms enforcement (nms proves red before the ignore)

**Files:**
- Modify: `modules/inventory-api/nms-api/pom.xml` (no `<build>` section exists; add after `</dependencies>` line 24)
- Modify: `modules/inventory-api/nms/pom.xml` (no `<build>` section exists; add after `</dependencies>` line 61)

- [ ] **Step 3.1: Activate the check in nms-api**

In `modules/inventory-api/nms-api/pom.xml`, insert between `</dependencies>` and `</project>`:

```xml
    <build>
        <plugins>
            <!-- activates the managed spigot-api 1.8.8 check (config in the inventory-api parent) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

- [ ] **Step 3.2: Verify nms-api is green**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature,nms-api test
```

Expected: `BUILD SUCCESS` with the `check-spigot-188-api` execution visible for nms-api.

- [ ] **Step 3.3: Activate the check in nms WITHOUT the ignore — expect FAILURE**

In `modules/inventory-api/nms/pom.xml`, insert between `</dependencies>` and `</project>`:

```xml
    <build>
        <plugins>
            <!-- activates the managed spigot-api 1.8.8 check (config in the inventory-api parent) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature,nms process-test-classes
```

Expected: `BUILD FAILURE` with an undefined-reference error in `BukkitInventoryTitleUpdater` for `org.bukkit.inventory.InventoryView.setTitle(java.lang.String)` — this proves the nms check actually bites before we suppress the one intentional, version-gated usage.

- [ ] **Step 3.4: Add the InventoryView ignore — expect GREEN**

In `modules/inventory-api/nms/pom.xml`, extend the plugin declaration from Step 3.3 to:

```xml
    <build>
        <plugins>
            <!-- activates the managed spigot-api 1.8.8 check (config in the inventory-api parent) -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>animal-sniffer-maven-plugin</artifactId>
                <configuration>
                    <!-- BukkitInventoryTitleUpdater intentionally calls InventoryView#setTitle (1.20+);
                         InventoryApiNMS only selects it when the server minor version is >= 20.
                         combine.children="append" merges with the managed java.*/javax.* ignores
                         instead of replacing them -->
                    <ignores combine.children="append">
                        <ignore>org.bukkit.inventory.InventoryView</ignore>
                    </ignores>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

Run:

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -f modules\inventory-api\pom.xml -pl spigot-api-1_8-signature,nms test
```

Expected: `BUILD SUCCESS` (the rest of the nms module — e.g. `InventoryApiNMS`'s `Bukkit.getBukkitVersion()` — is still checked; only `InventoryView` references are bypassed).

- [x] **Step 3.5: Commit**

```powershell
git add modules/inventory-api/nms-api/pom.xml modules/inventory-api/nms/pom.xml
git commit -m "build(inventory-api): enforce spigot-api 1.8.8 compatibility in nms modules"
```

> **Execution note (2026-06-06):** the Step 3.3 red run surfaced, alongside the expected
> `InventoryView.setTitle` error, two more hits of the JDK-supertype false-positive class:
> `InventoryApiNMS` calling `Bukkit.getServer().getClass()` (owner `org.bukkit.Server`, lines
> 80/140). Fixed at source by hoisting the server into `Object`-typed locals so `getClass()`
> resolves via the managed `java.*` ignore — commit
> `refactor(inventory-api): hoist server to Object receiver in nms selector` (`eac3065`),
> placed before the enforcement commit (`c31d545`). Quality review added why-comments at both
> hoists (`02030fa`) so the Object type is not "simplified" away. The final `nms` pom ignores
> only `org.bukkit.inventory.InventoryView` — no `org.bukkit.Server` pom ignore was needed.

---

### Task 4: Full-reactor CI-parity verification (no commit)

**Files:** none (verification only)

- [ ] **Step 4.1: Purge the installed signature artifact to prove in-reactor resolution**

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.m2\repository\tech\guilhermekaua\spigot-boot\spigot-boot-spigot-api-1_8-signature" -ErrorAction SilentlyContinue
```

(CI starts with this artifact absent from cache; this reproduces that state.)

- [ ] **Step 4.2: Run the exact CI command across the full reactor**

Run (expect several minutes — all modules build and test):

```powershell
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd test -B
```

Expected: `BUILD SUCCESS`. The log contains the signature generation (`animal-sniffer-maven-plugin:1.27:build (generate-spigot-188-signature)`) before the three `check (check-spigot-188-api)` executions (api, nms-api, nms), and every module's tests pass — proving CI (`mvn test -B`) enforces the check with zero workflow changes.

If resolution of `spigot-boot-spigot-api-1_8-signature` fails here, the reactor ordering is broken (signature module must be first in the parent's `<modules>` list) — fix that rather than installing the artifact manually.
