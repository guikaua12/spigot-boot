# BungeeCord config-bungee (v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `platform-bungee/config-bungee` adapter module that gives BungeeCord plugins the full Spigot Boot configuration framework — `@Config` live-reload, `@ConfigValue`, `@OnConfigReload`, `@FolderConfig`, and `${...}` references — at full parity with `config-spigot`, in a relocation-safe shaded jar.

**Architecture:** The generic `spigot-boot-config` core (annotations, `Binder`, refs, node tree, `ConfigLoader`/`ConfigSource`, serializer registry) is reused unchanged. `config-bungee` is a 1:1 port of `platform-spigot/config-spigot`'s platform layer into the `tech.guilhermekaua.spigotboot.config.bungee` package: SnakeYAML-direct loader/node, the javassist `ConfigProxy`, the manager + registry + folder + injector + reference + reload subsystems, DI wiring, and a `Module` + discovery marker. The **only** platform delta is the plugin seam (`net.md_5.bungee.api.plugin.Plugin` instead of `org.bukkit.plugin.Plugin`, `getResourceAsStream` instead of `getResource`) and dropping the Bukkit serializers (keeping only `Duration`). Because `ConfigProxy` uses the javassist API directly, the module shades `javassist` -> the relocated package and proves relocation safety with a packaging-phase integration test.

**Tech Stack:** Java 8 bytecode (tests Java 17), Maven. `tech.guilhermekaua.spigot-boot:spigot-boot-config` + `spigot-boot-core-bungee` (compile), `org.javassist:javassist:3.30.2-GA` (provided), `net.md-5:bungeecord-api:1.21-R0.3` (provided, inherited). SnakeYAML is transitively present (no new dependency). JUnit 5 + Mockito 5 inherited from the root pom. Spec: `docs/superpowers/specs/2026-06-15-bungeecord-config-design.md`.

**Build/JDK notes (read once):**
- Build with **JDK 21** (`JAVA_HOME` must point at JDK 21, not 25 — Lombok 1.18.36 crashes on 25).
- All commands run from the worktree root: `C:\Users\Guilherme\IdeaProjects\spigot-boot\.claude\worktrees\bungeecord-config`.
- Always pass `-Danimal.sniffer.skip=true`: `config-bungee` does not declare the animal-sniffer 1.8.8 check, but `-am` pulls in upstream Bukkit-facing modules that do, and a stale 1.8.8 signature must never block the build.
- For filtered `-Dtest=...` runs that may match no tests in some upstream module, add `-Dsurefire.failIfNoSpecifiedTests=false`.
- The root `license-maven-plugin` enforces the MIT header. **Every new `.java` file MUST begin with the MIT header** (the `Copyright © 2025 Guilherme Kauã da Silva` block, present verbatim atop every source file in `config-spigot`). All ported files already carry it; for the few new files written here, the header is shown inline.
- `config-bungee` and `core-bungee` already exist in this worktree (the reactor builds them). The sibling `commands-bungee` effort edits the same `platform-bungee/pom.xml` `<modules>` list — a trivial merge conflict there is expected.

## The standard Bungee port transform

Most of this module is a **verbatim port** of `config-spigot` source files. Re-listing
~5000 lines inline would invite transcription errors; instead each port task names the
exact source file(s) under `platform-spigot/config-spigot/src/...` and the exact target
path(s) under `platform-bungee/config-bungee/src/...`, and you apply this transform. It is
exhaustive — there is nothing else to change. Every port task ends with a compile + test
run + a leftover-reference grep that catches any miss.

Throughout this plan, `…/config/spigot/` is shorthand for
`platform-spigot/config-spigot/src/{main,test}/java/tech/guilhermekaua/spigotboot/config/spigot/`
and `…/config/bungee/` for
`platform-bungee/config-bungee/src/{main,test}/java/tech/guilhermekaua/spigotboot/config/bungee/`.
The exact full paths appear in the `File Structure` section and in every `git add` command.

Apply to every copied file (main and test):

1. **Package + imports:** replace `tech.guilhermekaua.spigotboot.config.spigot` with
   `tech.guilhermekaua.spigotboot.config.bungee` in the `package` statement and in every
   `import` (and in the directory path, so the file lands in the mirrored package).
2. **Class renames** (declaration, filename, and every reference across all files):
   - `SpigotConfigManager` -> `BungeeConfigManager`
   - `SpigotConfigReferenceLookup` -> `BungeeConfigReferenceLookup`
   - `SpigotConfigModule` -> `BungeeConfigModule`
   - `BukkitSerializers` -> `BungeeConfigSerializers`
3. **Plugin type** (appears in exactly three main files — `ConfigConfiguration`,
   `SpigotConfigManager`, `FolderConfigEntry` — and in the tests that mock it): replace
   `import org.bukkit.plugin.Plugin;` with `import net.md_5.bungee.api.plugin.Plugin;`.
   The `Plugin` simple name and all its uses (`getDataFolder()`, `getLogger()`,
   `getClassLoader()`) stay identical — both APIs share those signatures.
4. **Plugin resource method** (exactly two call-sites: `SpigotConfigManager` and
   `FolderConfigEntry`): change `plugin.getResource(` to `plugin.getResourceAsStream(`.
   **Do NOT** change `...getClassLoader().getResource(` in `FolderConfigEntry` — that is
   the `ClassLoader` API (returns a `URL`) and is unchanged.
5. **MIT header:** keep it verbatim. The one source file that lacks it
   (`ConfigConfiguration.java`) gets the header added in its target (shown in Task 9).

Files NOT ported (they cover dropped Bukkit serializers): `serialization/MaterialSerializer.java`,
`serialization/SoundSerializer.java`, `serialization/WorldSerializer.java`,
`serialization/LocationSerializer.java`, and the tests
`serialization/SoundSerializerTest.java`, `test/binding/MaterialSerializerBindingTest.java`.

---

## File Structure

**Created (module + new files):**
- `platform-bungee/config-bungee/pom.xml` — module pom: compiler (main 1.8 / tests 17), the javassist relocation shade execution, the failsafe relocation guard, and dependencies.
- `platform-bungee/config-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigModule` — empty discovery marker.
- `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/BungeeConfigSerializers.java` — Duration-only serializer registrar (replaces `BukkitSerializers`).
- `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/serialization/DurationSerializerTest.java` — new unit test for the ported `DurationSerializer`.
- `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeModuleDiscoveryTest.java` — proves the marker resolves `BungeeConfigModule`.
- `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxyRelocationIT.java` — failsafe packaging-phase relocation guard.

**Created (ported 1:1 from `config-spigot`, package `…config.bungee`):**
- main: `BungeeConfigManager` (was `SpigotConfigManager`), `ConfigBindingCoordinator`, `ConfigEntryAccessor`, `BungeeConfigModule` (was `SpigotConfigModule`), `configuration/ConfigConfiguration`, `loader/YamlConfigLoader`, `node/YamlConfigNode`, `proxy/ConfigProxy`, `registry/ConfigRegistry`, `folder/FolderConfigEntry`, `folder/DefaultFolderConfigRef`, `folder/DefaultFolderConfigEditor`, `folder/DefaultFolderConfigSnapshot`, `injector/ConfigRefInjector`, `injector/FolderConfigInjector`, `injector/ConfigValueInjector`, `injector/ConfigValueResolver`, `reference/ConfigReferenceManager`, `reference/ReferenceResolvingPreprocessor`, `reference/BungeeConfigReferenceLookup` (was `SpigotConfigReferenceLookup`), `reload/OnConfigReloadProcessor`, `reload/OnConfigReloadBinder`, `reload/OnConfigReloadInvoker`, `serialization/DurationSerializer`.
- tests: `test/node/YamlConfigNodeTest`, `test/loader/YamlConfigLoaderTest`, `test/proxy/ConfigProxyTest`, `test/folder/FolderConfigEntryTest`, `test/manager/SpigotConfigManagerTest` -> `BungeeConfigManagerTest`, `test/manager/SpigotConfigManagerValueAccessTest` -> `BungeeConfigManagerValueAccessTest`, `test/manager/SpigotConfigManagerFolderItemTypesTest` -> `BungeeConfigManagerFolderItemTypesTest`, `test/binding/ConfigEnumListBindingTest`, `test/registry/ConfigRegistryTest`, `test/injector/ConfigValueInjectorTest`, `test/injector/ConfigValueResolverTest`, `test/reference/ReferenceResolvingPreprocessorTest`, `test/reload/OnConfigReloadBinderTest`, `test/reload/OnConfigReloadInvokerTest`, `test/reload/OnConfigReloadProcessorTest`, `test/reload/OnConfigReloadProcessorIntegrationTest`, `test/configuration/ConfigConfigurationTest`.

**Modified:**
- `platform-bungee/pom.xml` — add `<module>config-bungee</module>` to `<modules>`.

---

## Task 1: Scaffold the `config-bungee` module

**Files:**
- Create: `platform-bungee/config-bungee/pom.xml`
- Modify: `platform-bungee/pom.xml` — add the module

- [ ] **Step 1: Create the module pom `platform-bungee/config-bungee/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot-platform-bungee</artifactId>
        <version>3.2.1-SNAPSHOT</version>
    </parent>

    <name>${project.artifactId}</name>
    <description>BungeeCord implementation of Spigot Boot configuration system.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-config-bungee</artifactId>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <testSource>17</testSource>
                    <testTarget>17</testTarget>
                    <compilerArgs>
                        <arg>-parameters</arg>
                    </compilerArgs>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.36</version>
                        </path>
                        <path>
                            <groupId>tech.guilhermekaua.spigot-boot</groupId>
                            <artifactId>spigot-boot-annotation-processor-spigot</artifactId>
                            <version>${project.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>

            <!-- ConfigProxy uses the javassist proxy API directly. javassist ships only as the
                 relocated copy bundled inside spigot-boot-core, so this module's own bytecode must
                 reference the shaded names too; otherwise it throws NoClassDefFoundError:
                 javassist/util/proxy/* at runtime. nothing is bundled here (core owns the shaded
                 javassist) — this execution only rewrites this module's references. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <!-- version managed by the parent pluginManagement (3.5.1) -->
                <executions>
                    <execution>
                        <id>relocate-javassist-references</id>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>true</createDependencyReducedPom>
                            <!-- include only this module's own classes so nothing is bundled; the execution
                                 exists purely to rewrite this module's javassist references to the shaded package. -->
                            <artifactSet>
                                <includes>
                                    <include>tech.guilhermekaua.spigot-boot:spigot-boot-config-bungee</include>
                                </includes>
                            </artifactSet>
                            <relocations>
                                <relocation>
                                    <pattern>javassist</pattern>
                                    <shadedPattern>tech.guilhermekaua.spigotboot.shaded.javassist</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!-- runs *IT tests in the integration-test phase (after package/shade) so the relocation
                 guard can inspect the SHADED jar; surefire (test phase) excludes *IT by default. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.5.3</version>
                <configuration>
                    <systemPropertyVariables>
                        <project.build.directory>${project.build.directory}</project.build.directory>
                        <project.build.finalName>${project.build.finalName}</project.build.finalName>
                    </systemPropertyVariables>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <!-- ConfigProxy uses the javassist proxy API; provided (never bundled) because the relocated
             copy is supplied at runtime by spigot-boot-core and this module's references are rewritten
             to the shaded package by the shade execution above. -->
        <dependency>
            <groupId>org.javassist</groupId>
            <artifactId>javassist</artifactId>
            <version>3.30.2-GA</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>net.md-5</groupId>
            <artifactId>bungeecord-api</artifactId>
            <!-- version + provided scope inherited from the platform-bungee parent's dependencyManagement -->
        </dependency>

        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-config</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- runtime pairing: a plugin pulling in config-bungee also gets the Bungee bootstrap and the
             Plugin bean producer (BungeeCoreModule). Mirrors config-spigot depending on core-spigot. -->
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-core-bungee</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-annotation-processor-spigot</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

Note: JUnit 5 + Mockito and the surefire 3.5.3 config (with the junit5 tree reporter) are
inherited from the **root** pom — do not redeclare them. `javassist` resolves at `provided`
scope for the IT/tests too.

- [ ] **Step 2: Register the module in `platform-bungee/pom.xml`**

In `platform-bungee/pom.xml`, add `config-bungee` to the `<modules>` block, after
`core-bungee`:

```xml
    <modules>
        <module>core-bungee</module>
        <module>config-bungee</module>
    </modules>
```

(If the sibling `commands-bungee` effort already added its module here, keep all entries —
this is the expected trivial merge point.)

- [ ] **Step 3: Verify the scaffolding resolves and compiles**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. The module has no sources yet, so the compiler reports "No sources to compile" — that is fine. This proves `javassist`, `bungeecord-api`, `spigot-boot-config`, and `spigot-boot-core-bungee` all resolve and the reactor wiring is correct. The `compile` phase does not run the shade/failsafe executions (bound to `package`/`verify`), so an empty module is fine here.

If `bungeecord-api:1.21-R0.3` fails to resolve (it should not — `core-bungee` already consumes it in this reactor), fall back to the Sonatype snapshot repo: add a `<repositories>` entry to `platform-bungee/pom.xml` with id `sonatype-oss-snapshots`, url `https://oss.sonatype.org/content/repositories/snapshots`, and change the managed version to `1.21-R0.1-SNAPSHOT`. Re-run.

- [ ] **Step 4: Commit**

```bash
git add platform-bungee/pom.xml platform-bungee/config-bungee/pom.xml
git commit -m "build(bungee): scaffold config-bungee module with javassist shade + failsafe"
```

---

## Task 2: Port the YAML loader + node

**Files:**
- Create: `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/node/YamlConfigNode.java`
- Create: `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/loader/YamlConfigLoader.java`
- Test: `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/node/YamlConfigNodeTest.java`
- Test: `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/loader/YamlConfigLoaderTest.java`

- [ ] **Step 1: Port the two test files (apply the standard transform)**

Copy, applying the standard port transform (package only — these are platform-neutral):
- `…/config/spigot/test/node/YamlConfigNodeTest.java` -> `…/config/bungee/test/node/YamlConfigNodeTest.java`
- `…/config/spigot/test/loader/YamlConfigLoaderTest.java` -> `…/config/bungee/test/loader/YamlConfigLoaderTest.java`

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=YamlConfigNodeTest,YamlConfigLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `YamlConfigNode` / `YamlConfigLoader` do not exist yet.

- [ ] **Step 3: Port the two implementation files (apply the standard transform)**

Copy, applying the standard port transform (package only — no `Plugin`/`org.bukkit` here):
- `…/config/spigot/node/YamlConfigNode.java` -> `…/config/bungee/node/YamlConfigNode.java`
- `…/config/spigot/loader/YamlConfigLoader.java` -> `…/config/bungee/loader/YamlConfigLoader.java`

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=YamlConfigNodeTest,YamlConfigLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests in both ported classes green.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/node/YamlConfigNode.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/loader/YamlConfigLoader.java platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/node/YamlConfigNodeTest.java platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/loader/YamlConfigLoaderTest.java
git commit -m "feat(bungee): port YAML config loader and node to config-bungee"
```

---

## Task 3: Port `ConfigProxy` (javassist)

**Files:**
- Create: `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxy.java`
- Test: `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/proxy/ConfigProxyTest.java`

- [ ] **Step 1: Port the test (apply the standard transform)**

Copy, applying the standard port transform (package only):
- `…/config/spigot/test/proxy/ConfigProxyTest.java` -> `…/config/bungee/test/proxy/ConfigProxyTest.java`

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigProxyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `ConfigProxy` does not exist yet.

- [ ] **Step 3: Port the implementation (apply the standard transform)**

Copy, applying the standard port transform (package only — the `import javassist.util.proxy.*` lines stay **unchanged** in source; they are rewritten to the shaded package later, at `package` phase, by the shade execution):
- `…/config/spigot/proxy/ConfigProxy.java` -> `…/config/bungee/proxy/ConfigProxy.java`

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigProxyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests green. (At this phase `javassist` is on the test classpath as the original package, so the proxy works in tests; relocation safety is proven separately in Task 10.)

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxy.java platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/proxy/ConfigProxyTest.java
git commit -m "feat(bungee): port ConfigProxy (javassist) to config-bungee"
```

---

## Task 4: Duration serializer + `BungeeConfigSerializers`

**Files:**
- Create: `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/DurationSerializer.java`
- Create: `platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/BungeeConfigSerializers.java`
- Test: `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/serialization/DurationSerializerTest.java`

- [ ] **Step 1: Write the failing test**

`platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/serialization/DurationSerializerTest.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee.test.serialization;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.bungee.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.config.bungee.serialization.DurationSerializer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationSerializerTest {

    private final DurationSerializer serializer = new DurationSerializer();

    @Test
    void deserializesHumanReadableUnits() {
        assertEquals(Duration.ofMillis(500), serializer.deserialize(new YamlConfigNode("500ms"), Duration.class));
        assertEquals(Duration.ofSeconds(30), serializer.deserialize(new YamlConfigNode("30s"), Duration.class));
        assertEquals(Duration.ofMinutes(5), serializer.deserialize(new YamlConfigNode("5m"), Duration.class));
        assertEquals(Duration.ofHours(2), serializer.deserialize(new YamlConfigNode("2h"), Duration.class));
        assertEquals(Duration.ofDays(1), serializer.deserialize(new YamlConfigNode("1d"), Duration.class));
        assertEquals(Duration.ofDays(7), serializer.deserialize(new YamlConfigNode("1w"), Duration.class));
    }

    @Test
    void deserializesPlainNumberAsSeconds() {
        assertEquals(Duration.ofSeconds(45), serializer.deserialize(new YamlConfigNode(45), Duration.class));
    }

    @Test
    void rejectsInvalidFormat() {
        assertThrows(SerializationException.class,
                () -> serializer.deserialize(new YamlConfigNode("not-a-duration"), Duration.class));
    }

    @Test
    void serializesToCompactUnit() {
        YamlConfigNode node = new YamlConfigNode();
        serializer.serialize(Duration.ofMinutes(5), node);
        assertEquals("5m", node.raw());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=DurationSerializerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `DurationSerializer` does not exist yet.

- [ ] **Step 3: Port `DurationSerializer` (apply the standard transform)**

Copy, applying the standard port transform (package only — it is platform-neutral):
- `…/config/spigot/serialization/DurationSerializer.java` -> `…/config/bungee/serialization/DurationSerializer.java`

- [ ] **Step 4: Write `BungeeConfigSerializers` (replaces `BukkitSerializers`)**

`platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/BungeeConfigSerializers.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee.serialization;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.time.Duration;
import java.util.Objects;

/**
 * Registers BungeeCord-appropriate type serializers. BungeeCord has no Material/Sound/World/Location
 * equivalents, so unlike the Spigot {@code BukkitSerializers} only the platform-neutral
 * {@link Duration} serializer is registered.
 */
public final class BungeeConfigSerializers {

    private BungeeConfigSerializers() {
    }

    /**
     * Registers all BungeeCord serializers to the given registry.
     *
     * @param registry the registry to populate
     */
    public static void registerAll(@NotNull TypeSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        registry.register(Duration.class, new DurationSerializer());
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=DurationSerializerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/DurationSerializer.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/serialization/BungeeConfigSerializers.java platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/serialization/DurationSerializerTest.java
git commit -m "feat(bungee): add Duration serializer and BungeeConfigSerializers"
```

---

## Task 5: Port the folder-config subsystem

**Files:**
- Create: `…/config/bungee/folder/FolderConfigEntry.java` (seam: `Plugin` + `getResourceAsStream`)
- Create: `…/config/bungee/folder/DefaultFolderConfigRef.java`
- Create: `…/config/bungee/folder/DefaultFolderConfigEditor.java`
- Create: `…/config/bungee/folder/DefaultFolderConfigSnapshot.java`
- Test: `…/config/bungee/test/folder/FolderConfigEntryTest.java`

(All paths under `platform-bungee/config-bungee/src/...`.)

- [ ] **Step 1: Port the test (apply the standard transform)**

Copy, applying the standard port transform. `FolderConfigEntryTest` mocks the plugin, so
apply transform rule 3 (`org.bukkit.plugin.Plugin` -> `net.md_5.bungee.api.plugin.Plugin`):
- `…/config/spigot/test/folder/FolderConfigEntryTest.java` -> `…/config/bungee/test/folder/FolderConfigEntryTest.java`

Note: if any test stub calls `plugin.getResource(...)`, change it to
`plugin.getResourceAsStream(...)` (transform rule 4). Leave any
`getClassLoader().getResource(...)` untouched.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=FolderConfigEntryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — the `folder` classes do not exist yet.

- [ ] **Step 3: Port the four implementation files (apply the standard transform)**

Copy, applying the standard port transform:
- `…/config/spigot/folder/DefaultFolderConfigSnapshot.java` -> `…/config/bungee/folder/DefaultFolderConfigSnapshot.java` (package only)
- `…/config/spigot/folder/DefaultFolderConfigEditor.java` -> `…/config/bungee/folder/DefaultFolderConfigEditor.java` (package only)
- `…/config/spigot/folder/DefaultFolderConfigRef.java` -> `…/config/bungee/folder/DefaultFolderConfigRef.java` (package only)
- `…/config/spigot/folder/FolderConfigEntry.java` -> `…/config/bungee/folder/FolderConfigEntry.java` — **seam**: apply transform rules 3 (Plugin type) and 4 (`plugin.getResource(` -> `plugin.getResourceAsStream(` at the single `plugin.getResource` call-site). The `pluginObject.getClass().getClassLoader().getResource(resourcePath)` call stays unchanged.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=FolderConfigEntryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests in `FolderConfigEntryTest` green.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/folder/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/folder/
git commit -m "feat(bungee): port folder-config support to config-bungee"
```

---

## Task 6: Port the config manager + reference engine

**Files:**
- Create: `…/config/bungee/BungeeConfigManager.java` (was `SpigotConfigManager`; seam: `Plugin` + `getResourceAsStream`)
- Create: `…/config/bungee/ConfigBindingCoordinator.java`
- Create: `…/config/bungee/ConfigEntryAccessor.java`
- Create: `…/config/bungee/reference/ConfigReferenceManager.java`
- Create: `…/config/bungee/reference/ReferenceResolvingPreprocessor.java`
- Create: `…/config/bungee/reference/BungeeConfigReferenceLookup.java` (was `SpigotConfigReferenceLookup`)
- Test: `…/config/bungee/test/manager/BungeeConfigManagerTest.java`
- Test: `…/config/bungee/test/manager/BungeeConfigManagerValueAccessTest.java`
- Test: `…/config/bungee/test/manager/BungeeConfigManagerFolderItemTypesTest.java`
- Test: `…/config/bungee/test/reference/ReferenceResolvingPreprocessorTest.java`

(`ConfigEnumListBindingTest` is ported later, in Task 7, where the full manager + injector
stack it may touch is present.)

- [ ] **Step 1: Port the test files (apply the standard transform)**

Copy, applying the standard port transform. The three manager tests are renamed
(`SpigotConfigManager*Test` -> `BungeeConfigManager*Test`, transform rule 2) and their
`Plugin` mocks swapped (rule 3):
- `…/config/spigot/test/manager/SpigotConfigManagerTest.java` -> `…/config/bungee/test/manager/BungeeConfigManagerTest.java`
- `…/config/spigot/test/manager/SpigotConfigManagerValueAccessTest.java` -> `…/config/bungee/test/manager/BungeeConfigManagerValueAccessTest.java`
- `…/config/spigot/test/manager/SpigotConfigManagerFolderItemTypesTest.java` -> `…/config/bungee/test/manager/BungeeConfigManagerFolderItemTypesTest.java`
- `…/config/spigot/test/reference/ReferenceResolvingPreprocessorTest.java` -> `…/config/bungee/test/reference/ReferenceResolvingPreprocessorTest.java`

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeConfigManagerTest,BungeeConfigManagerValueAccessTest,BungeeConfigManagerFolderItemTypesTest,ReferenceResolvingPreprocessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `BungeeConfigManager` and the reference classes do not exist yet.

- [ ] **Step 3: Port the implementation files (apply the standard transform)**

Copy, applying the standard port transform:
- `…/config/spigot/ConfigEntryAccessor.java` -> `…/config/bungee/ConfigEntryAccessor.java` (package + rule 2 renames where `SpigotConfigManager` is referenced)
- `…/config/spigot/ConfigBindingCoordinator.java` -> `…/config/bungee/ConfigBindingCoordinator.java` (package + rule 2)
- `…/config/spigot/reference/ReferenceResolvingPreprocessor.java` -> `…/config/bungee/reference/ReferenceResolvingPreprocessor.java` (package + rule 2)
- `…/config/spigot/reference/SpigotConfigReferenceLookup.java` -> `…/config/bungee/reference/BungeeConfigReferenceLookup.java` (package + rule 2: rename the class to `BungeeConfigReferenceLookup` and all references)
- `…/config/spigot/reference/ConfigReferenceManager.java` -> `…/config/bungee/reference/ConfigReferenceManager.java` (package + rule 2; it references the lookup type)
- `…/config/spigot/SpigotConfigManager.java` -> `…/config/bungee/BungeeConfigManager.java` — **seam**: apply rule 2 (class rename), rule 3 (Plugin type: import + field + all three constructors), and rule 4 (`plugin.getResource(` -> `plugin.getResourceAsStream(` at the single call-site). `plugin.getDataFolder()` is unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeConfigManagerTest,BungeeConfigManagerValueAccessTest,BungeeConfigManagerFolderItemTypesTest,ReferenceResolvingPreprocessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests across the four classes green.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeConfigManager.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/ConfigBindingCoordinator.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/ConfigEntryAccessor.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/reference/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/manager/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/reference/
git commit -m "feat(bungee): port BungeeConfigManager and reference engine"
```

---

## Task 7: Port the config registry + DI injectors

**Files:**
- Create: `…/config/bungee/registry/ConfigRegistry.java`
- Create: `…/config/bungee/injector/ConfigRefInjector.java`
- Create: `…/config/bungee/injector/FolderConfigInjector.java`
- Create: `…/config/bungee/injector/ConfigValueInjector.java`
- Create: `…/config/bungee/injector/ConfigValueResolver.java`
- Test: `…/config/bungee/test/registry/ConfigRegistryTest.java`
- Test: `…/config/bungee/test/injector/ConfigValueInjectorTest.java`
- Test: `…/config/bungee/test/injector/ConfigValueResolverTest.java`
- Test: `…/config/bungee/test/binding/ConfigEnumListBindingTest.java`

- [ ] **Step 1: Port the test files (apply the standard transform)**

Copy, applying the standard port transform (package + rule 2 renames; the injector tests
mock the `Plugin`, so apply rule 3). `ConfigEnumListBindingTest` only needs the `Plugin`
import swap (rule 3) — its enum is a plain Java enum:
- `…/config/spigot/test/registry/ConfigRegistryTest.java` -> `…/config/bungee/test/registry/ConfigRegistryTest.java`
- `…/config/spigot/test/injector/ConfigValueInjectorTest.java` -> `…/config/bungee/test/injector/ConfigValueInjectorTest.java`
- `…/config/spigot/test/injector/ConfigValueResolverTest.java` -> `…/config/bungee/test/injector/ConfigValueResolverTest.java`
- `…/config/spigot/test/binding/ConfigEnumListBindingTest.java` -> `…/config/bungee/test/binding/ConfigEnumListBindingTest.java`

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigRegistryTest,ConfigValueInjectorTest,ConfigValueResolverTest,ConfigEnumListBindingTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — the registry/injector classes do not exist yet.

- [ ] **Step 3: Port the implementation files (apply the standard transform)**

Copy, applying the standard port transform (package + rule 2; none of these reference
`Plugin` directly — `ConfigRegistry` uses `Context.getPlugin()`):
- `…/config/spigot/injector/ConfigRefInjector.java` -> `…/config/bungee/injector/ConfigRefInjector.java`
- `…/config/spigot/injector/FolderConfigInjector.java` -> `…/config/bungee/injector/FolderConfigInjector.java`
- `…/config/spigot/injector/ConfigValueResolver.java` -> `…/config/bungee/injector/ConfigValueResolver.java`
- `…/config/spigot/injector/ConfigValueInjector.java` -> `…/config/bungee/injector/ConfigValueInjector.java`
- `…/config/spigot/registry/ConfigRegistry.java` -> `…/config/bungee/registry/ConfigRegistry.java`

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigRegistryTest,ConfigValueInjectorTest,ConfigValueResolverTest,ConfigEnumListBindingTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests across the four classes green.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/registry/ platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/injector/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/registry/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/injector/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/binding/
git commit -m "feat(bungee): port config registry and DI injectors"
```

---

## Task 8: Port the `@OnConfigReload` subsystem

**Files:**
- Create: `…/config/bungee/reload/OnConfigReloadProcessor.java`
- Create: `…/config/bungee/reload/OnConfigReloadBinder.java`
- Create: `…/config/bungee/reload/OnConfigReloadInvoker.java`
- Test: `…/config/bungee/test/reload/OnConfigReloadBinderTest.java`
- Test: `…/config/bungee/test/reload/OnConfigReloadInvokerTest.java`
- Test: `…/config/bungee/test/reload/OnConfigReloadProcessorTest.java`
- Test: `…/config/bungee/test/reload/OnConfigReloadProcessorIntegrationTest.java`

- [ ] **Step 1: Port the test files (apply the standard transform)**

Copy, applying the standard port transform (package + rule 2;
`OnConfigReloadProcessorIntegrationTest` mocks the `Plugin`, so apply rule 3):
- `…/config/spigot/test/reload/OnConfigReloadBinderTest.java` -> `…/config/bungee/test/reload/OnConfigReloadBinderTest.java`
- `…/config/spigot/test/reload/OnConfigReloadInvokerTest.java` -> `…/config/bungee/test/reload/OnConfigReloadInvokerTest.java`
- `…/config/spigot/test/reload/OnConfigReloadProcessorTest.java` -> `…/config/bungee/test/reload/OnConfigReloadProcessorTest.java`
- `…/config/spigot/test/reload/OnConfigReloadProcessorIntegrationTest.java` -> `…/config/bungee/test/reload/OnConfigReloadProcessorIntegrationTest.java`

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=OnConfigReloadBinderTest,OnConfigReloadInvokerTest,OnConfigReloadProcessorTest,OnConfigReloadProcessorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — the reload classes do not exist yet.

- [ ] **Step 3: Port the implementation files (apply the standard transform)**

Copy, applying the standard port transform (package + rule 2; `OnConfigReloadProcessor`
takes a `Logger`, not a `Plugin`, so no Plugin-type change is needed inside these files):
- `…/config/spigot/reload/OnConfigReloadInvoker.java` -> `…/config/bungee/reload/OnConfigReloadInvoker.java`
- `…/config/spigot/reload/OnConfigReloadBinder.java` -> `…/config/bungee/reload/OnConfigReloadBinder.java`
- `…/config/spigot/reload/OnConfigReloadProcessor.java` -> `…/config/bungee/reload/OnConfigReloadProcessor.java`

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=OnConfigReloadBinderTest,OnConfigReloadInvokerTest,OnConfigReloadProcessorTest,OnConfigReloadProcessorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — all tests across the four classes green.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/reload/ platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/reload/
git commit -m "feat(bungee): port @OnConfigReload support to config-bungee"
```

---

## Task 9: DI configuration + module + discovery marker

**Files:**
- Create: `…/config/bungee/configuration/ConfigConfiguration.java` (seam: Plugin type + Duration serializer bean)
- Create: `…/config/bungee/BungeeConfigModule.java` (was `SpigotConfigModule`)
- Create: `…/config/bungee/../resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigModule` (empty marker)
- Test: `…/config/bungee/test/configuration/ConfigConfigurationTest.java`
- Test: `…/config/bungee/BungeeModuleDiscoveryTest.java`

- [ ] **Step 1: Write the failing tests**

Port `ConfigConfigurationTest`, applying the standard transform (package + rule 2 renames
`SpigotConfigManager` -> `BungeeConfigManager`; rule 3 swaps the `Plugin` mock):
- `…/config/spigot/test/configuration/ConfigConfigurationTest.java` -> `…/config/bungee/test/configuration/ConfigConfigurationTest.java`

Then write the new module-discovery test (mirrors core-bungee's `BungeeModuleDiscoveryTest`):

`platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeModuleDiscoveryTest.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleDiscovery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeeModuleDiscoveryTest {

    // proves the META-INF/spigot-boot/modules marker resource is present and names the module correctly,
    // so SpigotBootBuilder.autoDiscover() picks it up exactly as it does SpigotConfigModule on Spigot.
    @Test
    void bungeeConfigModuleIsAutoDiscoverable() {
        List<Class<? extends Module>> modules =
                new ModuleDiscovery(getClass().getClassLoader()).discover();

        assertTrue(modules.contains(BungeeConfigModule.class),
                "BungeeConfigModule must be discoverable via its META-INF/spigot-boot/modules marker");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigConfigurationTest,BungeeModuleDiscoveryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `ConfigConfiguration` / `BungeeConfigModule` do not exist yet.

- [ ] **Step 3: Write `BungeeConfigModule` (port of `SpigotConfigModule`)**

`platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeConfigModule.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.bungee.registry.ConfigRegistry;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.logging.Logger;

/**
 * Spigot Boot module providing configuration management on BungeeCord.
 * <p>
 * Scans for {@code @Config} and {@code @FolderConfig} annotated classes, loads configs, and
 * registers them as beans. Mirrors the Spigot {@code SpigotConfigModule}.
 * <p>
 * Runs early (after {@code BungeeCoreModule}, which registers the plugin at {@code @Order(-1000)},
 * but before default-order modules) so that {@code @Config} beans are registered before any later
 * module resolves a component that depends on them.
 */
@Order(-500)
public class BungeeConfigModule implements Module {

    @Override
    public void onInitialize(@NotNull Context context) throws Exception {
        Logger logger = context.getPlugin().getLogger();

        logger.info("Initializing Config Module...");

        context.getBean(ConfigRegistry.class)
                .registerConfigs(context);

        logger.info("Config Module initialized.");

        context.registerShutdownHook(() -> {
            logger.info("Config Module shutting down...");
        });
    }
}
```

- [ ] **Step 4: Write `ConfigConfiguration` (port of the Spigot one, Plugin + Duration deltas)**

`platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/configuration/ConfigConfiguration.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee.configuration;

import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistryCustomizer;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigRefInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.config.bungee.injector.FolderConfigInjector;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.config.bungee.serialization.BungeeConfigSerializers;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistryCustomizer;
import tech.guilhermekaua.spigotboot.core.context.dependency.postprocessor.BeanPostProcessorRegistryCustomizer;

import java.util.List;

@Configuration
public class ConfigConfiguration {
    @Bean
    public BungeeConfigManager configManager(
            Plugin plugin,
            @Nullable ConfigReferenceErrorHandler errorHandler,
            List<TypeSerializerRegistryCustomizer> serializerCustomizers
    ) {
        return new BungeeConfigManager(plugin, errorHandler, serializerCustomizers);
    }

    @Bean
    public CustomInjectorRegistryCustomizer configInjectors(BungeeConfigManager configManager) {
        return (registry) -> {
            registry.register(new FolderConfigInjector(configManager));
            registry.register(new ConfigRefInjector(configManager));
            registry.register(new ConfigValueInjector(configManager));
        };
    }

    @Bean
    public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(BungeeConfigManager configManager, Plugin plugin) {
        return registry -> registry.register(new OnConfigReloadProcessor(configManager, plugin.getLogger()));
    }

    @Bean
    public TypeSerializerRegistryCustomizer bungeeTypeSerializers() {
        return new TypeSerializerRegistryCustomizer() {
            @Override
            public void customize(@NotNull TypeSerializerRegistry registry) {
                BungeeConfigSerializers.registerAll(registry);
            }

            @Override
            public int getOrder() {
                return -100;
            }
        };
    }
}
```

- [ ] **Step 5: Create the empty discovery marker**

File: `platform-bungee/config-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigModule`

Content: an empty file (zero bytes). The file *name* is the module FQCN; `ModuleDiscovery`
reads the name, not the contents.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true test -Dtest=ConfigConfigurationTest,BungeeModuleDiscoveryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS — `ConfigConfigurationTest` (2 tests) + `BungeeModuleDiscoveryTest` (1 test). If `BungeeModuleDiscoveryTest` fails, confirm the marker file landed in `target/classes/META-INF/spigot-boot/modules/` with the exact FQCN as its name (no extension, no trailing newline in the name).

- [ ] **Step 7: Commit**

```bash
git add platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/configuration/ConfigConfiguration.java platform-bungee/config-bungee/src/main/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeConfigModule.java "platform-bungee/config-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigModule" platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/test/configuration/ConfigConfigurationTest.java platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/BungeeModuleDiscoveryTest.java
git commit -m "feat(bungee): wire config-bungee DI configuration and module discovery"
```

---

## Task 10: Relocation guard — prove the shaded jar is javassist-safe

**Files:**
- Test: `platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxyRelocationIT.java`

- [ ] **Step 1: Write the failsafe integration test**

`platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxyRelocationIT.java`:

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee.proxy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the shaded-javassist NoClassDefFoundError. {@code ConfigProxy} uses the
 * javassist proxy API directly, so its pre-shade bytecode legitimately references {@code javassist/};
 * only the PACKAGED (shaded) jar must be clean. A normal surefire test runs against the un-shaded
 * {@code target/classes} and cannot catch this — so this is a failsafe {@code *IT} bound to the
 * {@code verify} phase (after {@code package}/shade), inspecting the produced jar.
 */
class ConfigProxyRelocationIT {

    private static final String SHADED_PREFIX = "tech/guilhermekaua/spigotboot/shaded/javassist/";
    private static final String CONFIG_PROXY_ENTRY =
            "tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxy.class";

    @Test
    void packagedConfigProxyReferencesOnlyShadedJavassist() throws IOException {
        String buildDir = System.getProperty("project.build.directory");
        String finalName = System.getProperty("project.build.finalName");
        assertNotNull(buildDir, "project.build.directory must be supplied by the failsafe plugin");
        assertNotNull(finalName, "project.build.finalName must be supplied by the failsafe plugin");

        Path jar = Path.of(buildDir, finalName + ".jar");
        assertTrue(Files.exists(jar),
                "shaded jar not found at " + jar + " — run the package/verify phase, not just test");

        byte[] bytes;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(CONFIG_PROXY_ENTRY);
            assertNotNull(entry, CONFIG_PROXY_ENTRY + " not found inside " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
        }

        // constant-pool type references use the slashed internal form (javassist/util/proxy/...);
        // decode byte-preserving so they appear verbatim as substrings.
        String pool = new String(bytes, StandardCharsets.ISO_8859_1);

        assertTrue(pool.contains(SHADED_PREFIX),
                "ConfigProxy.class must reference the relocated javassist package " + SHADED_PREFIX
                        + " in the packaged jar (relocation did not run)");

        // strip the shaded prefix first: the relocated form ITSELF contains the substring "javassist/",
        // so a naive contains("javassist/") would false-match. what must not remain is any un-relocated ref.
        String withoutShaded = pool.replace(SHADED_PREFIX, "");
        assertFalse(withoutShaded.contains("javassist/"),
                "ConfigProxy.class still references the un-relocated javassist package in the packaged jar");
    }
}
```

- [ ] **Step 2: Run the packaging build so the IT executes against the shaded jar**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS`. The `package` phase runs surefire (all unit tests green) then the `relocate-javassist-references` shade execution; the `integration-test`/`verify` phases then run `ConfigProxyRelocationIT` against `target/spigot-boot-config-bungee-3.2.1-SNAPSHOT.jar`, which passes only because `ConfigProxy.class` inside the jar references `tech/guilhermekaua/spigotboot/shaded/javassist/` and nothing un-relocated.

Sanity check it actually guards: temporarily remove the `<relocations>` block from the shade execution in `platform-bungee/config-bungee/pom.xml` and re-run the command — `ConfigProxyRelocationIT` must now FAIL (proving the guard has teeth). Restore the `<relocations>` block before committing.

- [ ] **Step 3: Commit**

```bash
git add platform-bungee/config-bungee/src/test/java/tech/guilhermekaua/spigotboot/config/bungee/proxy/ConfigProxyRelocationIT.java
git commit -m "test(bungee): guard ConfigProxy javassist relocation in the shaded jar"
```

---

## Task 11: Full-module + reactor verification

**Files:** none (verification gate; all code already committed).

- [ ] **Step 1: Run the full module suite (unit + integration)**

Run: `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true verify`
Expected: `BUILD SUCCESS` — every ported unit test green and `ConfigProxyRelocationIT` green.

- [ ] **Step 2: Confirm no un-ported references leaked into the new subtree**

Run: `git grep -nE "config\.spigot|org\.bukkit" -- platform-bungee/config-bungee/src`
Expected: **no output**. Any hit means a copied file kept a `config.spigot` import/reference or an `org.bukkit` import — fix it (it indicates a missed transform rule 1, 2, or 3) and re-run Step 1.

- [ ] **Step 3: Verify the whole reactor still builds and installs**

Run: `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests`
Expected: `BUILD SUCCESS` for every module including `spigot-boot-config-bungee`. The shade execution runs during `install` and produces the relocation-safe jar in the local repo. This confirms adding the new module did not disturb the existing reactor.

---

## Done criteria

- `mvnw.cmd -pl platform-bungee/config-bungee -am -Danimal.sniffer.skip=true verify` is green — all ported unit tests pass AND `ConfigProxyRelocationIT` proves the packaged `ConfigProxy.class` references only the shaded javassist package.
- `git grep -nE "config\.spigot|org\.bukkit" -- platform-bungee/config-bungee/src` returns nothing (the port is clean: no leftover Spigot package refs, no Bukkit API).
- `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests` builds and installs the full reactor including `spigot-boot-config-bungee`.
- A BungeeCord plugin author (already booting via `core-bungee`) can declare `@Config`, `@FolderConfig`, `@ConfigValue`, and `@OnConfigReload` classes and have them loaded, injected, live-reloaded, and reference-resolved exactly as on Spigot — using SnakeYAML-backed YAML files under the plugin's data folder, with the Duration serializer available.

## Out of scope (later slices, separate plans)

- Extracting a shared `config-platform-common` module to de-duplicate the platform-neutral classes copied between config-spigot and config-bungee.
- Bungee-specific serializers (`ServerInfo`, chat `BaseComponent`/`ChatColor`).
- On-proxy end-to-end validation inside a runnable BungeeCord sample plugin (depends on the `bungee.yml` descriptor-generator slice).
