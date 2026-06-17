# BungeeCord core-bungee (v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `platform-bungee/core-bungee` adapter module so a BungeeCord plugin can boot the Spigot Boot DI container, inject the native `Plugin`/`TaskScheduler`, and auto-register Bungee event listeners.

**Architecture:** Mirror `platform-spigot/core-spigot` as a sibling `platform-bungee/core-bungee` tree. The generic `core` module (DI, lifecycle, discovery, the `SpigotBoot` bootstrap) is reused unchanged; `core-bungee` only adds thin Bungee adapters: `BungeeBootPlugin` (implements `BootPlugin`), `BungeeCoreModule` (registers native beans), `BungeeListenerAutoRegistrar` (a `ContextReadyListener`), and a `BungeeBoot` facade. No scheduler abstraction (native `TaskScheduler` is injected directly).

**Tech Stack:** Java 8 bytecode (tests Java 17), Maven, `net.md-5:bungeecord-api:1.21-R0.3` (provided, Maven Central), JUnit 5 + Mockito 5 (inline mock maker, inherited from the root pom). Spec: `docs/superpowers/specs/2026-06-15-bungeecord-core-bungee-design.md`.

**Build/JDK notes (read once):**
- Build with **JDK 21** (`JAVA_HOME` must point at JDK 21, not 25 — Lombok 1.18.36 crashes on 25).
- All commands run from the worktree root: `C:\Users\Guilherme\IdeaProjects\spigot-boot\.claude\worktrees\bungeecord-core-bungee`.
- `core-bungee` does **not** use the animal-sniffer 1.8.8 check, but its upstream reactor build is invoked with `-Danimal.sniffer.skip=true` defensively so a stale 1.8.8 signature never blocks the build.
- Every new `.java` file MUST begin with the project's MIT license header (the block shown in Task 2, `Copyright © 2025` to match the existing files and the root `license-maven-plugin` config).

---

## File Structure

**Created:**
- `platform-bungee/pom.xml` — aggregator pom (packaging `pom`); manages the `bungeecord-api` version; lists `core-bungee`.
- `platform-bungee/core-bungee/pom.xml` — the adapter module; compiler + annotation-processor wiring mirroring `core-spigot`, minus shade/animal-sniffer/MockBukkit.
- `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBootPlugin.java` — `BootPlugin` adapter over `net.md_5.bungee.api.plugin.Plugin`.
- `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeCoreModule.java` — `Module` that registers `Plugin`, the real plugin class, and `TaskScheduler` for injection.
- `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBoot.java` — bootstrap facade delegating to `SpigotBoot`.
- `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrar.java` — `@Component ContextReadyListener` that registers `Listener` beans.
- `platform-bungee/core-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.core.bungee.BungeeCoreModule` — empty marker so `ModuleDiscovery` auto-discovers the module.
- Tests under `platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/`:
  - `BungeeBootPluginTest.java`, `BungeeCoreModuleTest.java`, `BungeeModuleDiscoveryTest.java`, `integrations/BungeeListenerAutoRegistrarTest.java`.

**Modified:**
- `pom.xml` (repo root) — add `<module>platform-bungee</module>` to `<modules>`.

---

## Task 1: Scaffold the `platform-bungee` Maven module tree

**Files:**
- Create: `platform-bungee/pom.xml`
- Create: `platform-bungee/core-bungee/pom.xml`
- Modify: `pom.xml` (root) — add the module

- [ ] **Step 1: Create the aggregator pom `platform-bungee/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot</artifactId>
        <version>3.2.1-SNAPSHOT</version>
    </parent>

    <name>${project.artifactId}</name>
    <description>BungeeCord platform modules for Spigot Boot.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-platform-bungee</artifactId>
    <packaging>pom</packaging>

    <modules>
        <module>core-bungee</module>
    </modules>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>net.md-5</groupId>
                <artifactId>bungeecord-api</artifactId>
                <version>1.21-R0.3</version>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Create the module pom `platform-bungee/core-bungee/pom.xml`**

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
    <description>BungeeCord adapter for Spigot Boot core.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-core-bungee</artifactId>

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
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>net.md-5</groupId>
            <artifactId>bungeecord-api</artifactId>
        </dependency>

        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-core</artifactId>
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

Note: JUnit 5 + Mockito (core, junit-jupiter) are declared `test`-scoped in the **root** pom `<dependencies>`, so they are inherited — do not redeclare them. `ProxyUtils` and `ComponentProxy` arrive transitively through `spigot-boot-core`.

- [ ] **Step 3: Register the module in the root `pom.xml`**

In `pom.xml` (repo root), find the `<modules>` block and add `platform-bungee` immediately after `platform-spigot`:

```xml
        <module>platform-spigot</module>
        <module>platform-bungee</module>
        <module>data</module>
```

- [ ] **Step 4: Verify the scaffolding resolves and compiles**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. The module has no sources yet, so the compiler reports "No sources to compile" — that is fine. What this proves is that `net.md-5:bungeecord-api:1.21-R0.3` resolves from Maven Central (dependency resolution happens before compilation) and the reactor wiring is correct. The `compile` phase is deliberate: it does not invoke the javadoc/source jars (bound to `package`), which would choke on a module that has no classes yet.

If `bungeecord-api:1.21-R0.3` fails to resolve, fall back to the Sonatype snapshot repo: add to `platform-bungee/pom.xml` a `<repositories>` entry with id `sonatype-oss-snapshots`, url `https://oss.sonatype.org/content/repositories/snapshots`, and change the managed version to `1.21-R0.1-SNAPSHOT`. Re-run.

- [ ] **Step 5: Commit**

```bash
git add pom.xml platform-bungee/pom.xml platform-bungee/core-bungee/pom.xml
git commit -m "build(bungee): scaffold platform-bungee/core-bungee module"
```

---

## Task 2: `BungeeBootPlugin` (BootPlugin adapter)

**Files:**
- Create: `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBootPlugin.java`
- Test: `platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBootPluginTest.java`

- [ ] **Step 1: Write the failing test**

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
package tech.guilhermekaua.spigotboot.core.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeBootPluginTest {

    private Plugin mockPlugin() {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getName()).thenReturn("TestPlugin");
        when(plugin.getDescription()).thenReturn(description);
        return plugin;
    }

    @Test
    void mapsNameFromDescription() {
        Plugin plugin = mockPlugin();
        assertEquals("TestPlugin", new BungeeBootPlugin(plugin).getName());
    }

    @Test
    void delegatesLoggerDataFolderAndResource() {
        Plugin plugin = mockPlugin();
        Logger logger = Logger.getLogger("bungee-test");
        File dataFolder = new File("plugins/TestPlugin");
        InputStream resource = new ByteArrayInputStream(new byte[0]);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getResourceAsStream("config.yml")).thenReturn(resource);

        BungeeBootPlugin bootPlugin = new BungeeBootPlugin(plugin);

        assertSame(logger, bootPlugin.getLogger());
        assertSame(dataFolder, bootPlugin.getDataFolder());
        assertSame(resource, bootPlugin.getResource("config.yml"));
    }

    @Test
    void exposesNativePluginAndMainClass() {
        Plugin plugin = mockPlugin();
        BungeeBootPlugin bootPlugin = new BungeeBootPlugin(plugin);

        assertSame(plugin, bootPlugin.getNativePlugin());
        // a plain (non-proxied) plugin resolves to its own runtime class, and the classloader follows from it.
        assertEquals(plugin.getClass(), bootPlugin.getMainClass());
        assertSame(plugin.getClass().getClassLoader(), bootPlugin.getClassLoader());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeBootPluginTest`
Expected: COMPILE FAILURE — `BungeeBootPlugin` does not exist yet.

- [ ] **Step 3: Write the implementation**

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
package tech.guilhermekaua.spigotboot.core.bungee;

import lombok.EqualsAndHashCode;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Adapts a BungeeCord {@link Plugin} to the platform-neutral {@link BootPlugin} contract so the
 * Spigot Boot context can run on a proxy. Mirrors the Spigot {@code SpigotBootPlugin}.
 */
@EqualsAndHashCode(of = "plugin")
public class BungeeBootPlugin implements BootPlugin {
    private final Plugin plugin;

    public BungeeBootPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return plugin.getDescription().getName();
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public InputStream getResource(String path) {
        return plugin.getResourceAsStream(path);
    }

    @Override
    public ClassLoader getClassLoader() {
        return getMainClass().getClassLoader();
    }

    @Override
    public Class<?> getMainClass() {
        return ProxyUtils.getRealClass(plugin);
    }

    @Override
    public Object getNativePlugin() {
        return plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeBootPluginTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBootPlugin.java platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBootPluginTest.java
git commit -m "feat(bungee): add BungeeBootPlugin BootPlugin adapter"
```

---

## Task 3: `BungeeCoreModule` + module discovery marker

**Files:**
- Create: `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeCoreModule.java`
- Create: `platform-bungee/core-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.core.bungee.BungeeCoreModule`
- Test: `platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeCoreModuleTest.java`
- Test: `platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeModuleDiscoveryTest.java`

- [ ] **Step 1: Write the failing tests**

`BungeeCoreModuleTest.java` (begin with the standard MIT header from Task 2):

```java
package tech.guilhermekaua.spigotboot.core.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCoreModuleTest {

    @Test
    void registersNativePluginAndScheduler() throws Exception {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getScheduler()).thenReturn(scheduler);

        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getNativePlugin()).thenReturn(plugin);

        DependencyManager dependencyManager = mock(DependencyManager.class);
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(bootPlugin);
        when(context.getDependencyManager()).thenReturn(dependencyManager);

        new BungeeCoreModule().onInitialize(context);

        verify(dependencyManager).registerDependency(eq(Plugin.class), same(plugin), isNull(), eq(false));
        verify(dependencyManager).registerDependency(eq(TaskScheduler.class), same(scheduler), isNull(), eq(false));
    }
}
```

`BungeeModuleDiscoveryTest.java` (begin with the standard MIT header from Task 2):

```java
package tech.guilhermekaua.spigotboot.core.bungee;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleDiscovery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeeModuleDiscoveryTest {

    // proves the META-INF/spigot-boot/modules marker resource is present and names the module correctly,
    // so SpigotBootBuilder.autoDiscover() picks it up exactly as it does SpigotCoreModule on Spigot.
    @Test
    void bungeeCoreModuleIsAutoDiscoverable() {
        List<Class<? extends Module>> modules =
                new ModuleDiscovery(getClass().getClassLoader()).discover();

        assertTrue(modules.contains(BungeeCoreModule.class),
                "BungeeCoreModule must be discoverable via its META-INF/spigot-boot/modules marker");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeCoreModuleTest,BungeeModuleDiscoveryTest`
Expected: COMPILE FAILURE — `BungeeCoreModule` does not exist yet.

- [ ] **Step 3: Write the `BungeeCoreModule` implementation**

```java
/* ... standard MIT header from Task 2 ... */
package tech.guilhermekaua.spigotboot.core.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

/**
 * Registers the BungeeCord plugin and its {@link TaskScheduler} as injectable beans. Mirrors the
 * Spigot {@code SpigotCoreModule}. There is no scheduler abstraction on a proxy: authors inject the
 * native {@link TaskScheduler} directly.
 */
@Order(-1000)
public class BungeeCoreModule implements Module {

    @Override
    public void onInitialize(Context context) throws Exception {
        BootPlugin bootPlugin = context.getPlugin();
        Object nativePlugin = bootPlugin.getNativePlugin();
        DependencyManager dependencyManager = context.getDependencyManager();

        Plugin plugin = (Plugin) nativePlugin;

        dependencyManager.registerDependency(Plugin.class, plugin, null, false);
        dependencyManager.registerDependency(
                ProxyUtils.getRealClass(nativePlugin),
                nativePlugin,
                null,
                false
        );
        dependencyManager.registerDependency(TaskScheduler.class, plugin.getProxy().getScheduler(), null, false);
    }
}
```

- [ ] **Step 4: Create the module marker resource**

File: `platform-bungee/core-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.core.bungee.BungeeCoreModule`

Content: an empty file (zero bytes). The file *name* is the module FQCN; `ModuleDiscovery` reads the name, not the contents.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeCoreModuleTest,BungeeModuleDiscoveryTest`
Expected: PASS (2 tests). If `BungeeModuleDiscoveryTest` fails, confirm the marker file landed in `target/classes/META-INF/spigot-boot/modules/` with the exact FQCN as its name (no extension, no trailing newline in the name).

- [ ] **Step 6: Commit**

```bash
git add platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeCoreModule.java "platform-bungee/core-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.core.bungee.BungeeCoreModule" platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeCoreModuleTest.java platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeModuleDiscoveryTest.java
git commit -m "feat(bungee): register native Plugin and TaskScheduler via BungeeCoreModule"
```

---

## Task 4: `BungeeListenerAutoRegistrar`

**Files:**
- Create: `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrar.java`
- Test: `platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrarTest.java`

- [ ] **Step 1: Write the failing test**

`BungeeListenerAutoRegistrarTest.java` (begin with the standard MIT header from Task 2):

```java
package tech.guilhermekaua.spigotboot.core.bungee.integrations;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeListenerAutoRegistrarTest {

    public static class TestListener implements Listener {
    }

    private Context contextWith(Plugin plugin, Listener listener) {
        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getBeansByType(Listener.class)).thenReturn(Collections.singletonList(listener));
        return context;
    }

    @Test
    void registersListenerBeansAndUnregistersOnShutdown() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        TestListener listener = new TestListener();
        Context context = contextWith(plugin, listener);

        new BungeeListenerAutoRegistrar().onContextReady(context);

        verify(pluginManager).registerListener(plugin, listener);

        ArgumentCaptor<Runnable> shutdownHook = ArgumentCaptor.forClass(Runnable.class);
        verify(context).registerShutdownHook(shutdownHook.capture());
        shutdownHook.getValue().run();

        verify(pluginManager).unregisterListener(listener);
    }

    @Test
    void warnsWhenListenerIsAProxy() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getLogger()).thenReturn(logger);

        // the container hands interceptable beans back as javassist proxies; the proxy override drops the
        // @EventHandler annotation, and BungeeCord exposes no per-method registration to rebind it (v1 limitation).
        Listener proxiedListener = ComponentProxy.createProxy(
                TestListener.class, null, new Class<?>[0], new Object[0]);
        assertTrue(ProxyUtils.isProxy(proxiedListener), "precondition: the listener bean must be a javassist proxy");

        Context context = contextWith(plugin, proxiedListener);

        new BungeeListenerAutoRegistrar().onContextReady(context);

        verify(logger).warning(contains("Proxied listener"));
        verify(pluginManager).registerListener(plugin, proxiedListener);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeListenerAutoRegistrarTest`
Expected: COMPILE FAILURE — `BungeeListenerAutoRegistrar` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
/* ... standard MIT header from Task 2 ... */
package tech.guilhermekaua.spigotboot.core.bungee.integrations;

import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-registers every {@link Listener} bean with BungeeCord when the context is ready, and
 * unregisters them on shutdown. Mirrors the Spigot {@code BukkitListenerAutoRegistrar}.
 *
 * <p>v1 limitation: a proxied listener bean (one the container wrapped to intercept methods) has its
 * {@code @EventHandler} overrides stripped of the annotation, and BungeeCord exposes no per-method
 * registration to rebind them. Such a listener is registered natively but logged as a warning, since
 * its handlers may not fire. Plain (non-proxied) listeners are unaffected.
 */
@Component
public class BungeeListenerAutoRegistrar implements ContextReadyListener {
    private final List<Listener> autoRegisteredListeners = new ArrayList<>();

    @Override
    public void onContextReady(@NotNull Context context) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();

        List<Listener> listenerBeans = context.getBeansByType(Listener.class);

        for (Listener listener : listenerBeans) {
            if (ProxyUtils.isProxy(listener)) {
                plugin.getLogger().warning("Proxied listener " + ProxyUtils.getRealClass(listener).getName()
                        + " may not receive events on BungeeCord (proxied listeners are not fully supported yet).");
            }
            pluginManager.registerListener(plugin, listener);
            autoRegisteredListeners.add(listener);
        }

        context.registerShutdownHook(() -> {
            for (Listener listener : autoRegisteredListeners) {
                pluginManager.unregisterListener(listener);
            }
            autoRegisteredListeners.clear();
        });
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeeListenerAutoRegistrarTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrar.java platform-bungee/core-bungee/src/test/java/tech/guilhermekaua/spigotboot/core/bungee/integrations/BungeeListenerAutoRegistrarTest.java
git commit -m "feat(bungee): auto-register Bungee listener beans on context ready"
```

---

## Task 5: `BungeeBoot` facade + full-module verification

**Files:**
- Create: `platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBoot.java`

- [ ] **Step 1: Write the `BungeeBoot` facade**

```java
/* ... standard MIT header from Task 2 ... */
package tech.guilhermekaua.spigotboot.core.bungee;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

/**
 * BungeeCord-facing entry point for booting Spigot Boot. A thin facade over the platform-neutral
 * {@code SpigotBoot} bootstrap so Bungee plugins never import a class named "Spigot".
 */
public final class BungeeBoot {

    private BungeeBoot() {
    }

    public static Context initialize(@NotNull BootPlugin plugin) {
        return SpigotBoot.initialize(plugin);
    }

    public static Context getContext(@NotNull BootPlugin plugin) {
        return SpigotBoot.getContext(plugin);
    }

    public static void onDisable(@NotNull BootPlugin plugin) {
        SpigotBoot.onDisable(plugin);
    }
}
```

- [ ] **Step 2: Run the full module test suite**

Run: `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test`
Expected: PASS — all tests across `BungeeBootPluginTest`, `BungeeCoreModuleTest`, `BungeeModuleDiscoveryTest`, `BungeeListenerAutoRegistrarTest` (8 tests, 0 failures).

- [ ] **Step 3: Verify the whole reactor still builds**

Run: `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests`
Expected: `BUILD SUCCESS` for every module including `spigot-boot-platform-bungee` and `spigot-boot-core-bungee`. This confirms adding the new module did not disturb the existing reactor.

- [ ] **Step 4: Commit**

```bash
git add platform-bungee/core-bungee/src/main/java/tech/guilhermekaua/spigotboot/core/bungee/BungeeBoot.java
git commit -m "feat(bungee): add BungeeBoot bootstrap facade"
```

---

## Done criteria

- `mvnw.cmd -pl platform-bungee/core-bungee -am -Danimal.sniffer.skip=true test` is green (8 tests).
- `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests` builds the full reactor including the new modules.
- A Bungee plugin author can now write `new BungeeBootPlugin(this)` + `BungeeBoot.initialize(...)`, inject `Plugin`/`TaskScheduler`, and have `@Component implements net.md_5.bungee.api.plugin.Listener` beans auto-registered.

## Out of scope (later slices, separate plans)
- `bungee.yml` descriptor generation (the test plugin hand-writes one until then).
- `commands-bungee`, `config-bungee`.
- Full proxied-listener support on BungeeCord (v1 warns; a real binder is a follow-up).
- A runnable BungeeCord sample/test plugin and on-server end-to-end validation.
