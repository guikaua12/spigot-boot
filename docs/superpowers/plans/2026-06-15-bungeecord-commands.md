# BungeeCord commands-bungee Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `platform-bungee/commands-bungee` adapter so a BungeeCord plugin using the Spigot Boot container can declare `@RootCommand`/`@Command` handler beans and have them registered with BungeeCord automatically, with argument binding, `@Permission`, tab completion, and `ProxiedPlayer`/`ServerInfo` resolvers.

**Architecture:** Mirror `platform-spigot/commands-spigot` as a sibling `platform-bungee/commands-bungee` tree. The generic `commands` module (and its `CommandsConfiguration` with all 13 platform-neutral beans) is reused unchanged; `commands-bungee` adds the thin Bungee adapters (`BungeeCommandPlatformSupport`, `BungeeCommandSender`, `BungeeBootCommand extends Command implements TabExecutor`, `BungeeCommandRegistrar`, `RegisteredCommandSet`, `BungeeCommandsContextReadyRegistrar`), the Bungee `ProxiedPlayer`/`ServerInfo` resolvers + `onlinePlayers`/`servers` completions, the `BungeeCommandsModule` + marker, and a `@Configuration` (`BungeeCommandsConfiguration`) that re-declares the 8 beans that depend on `CommandPlatformSupport` plus the Bungee glue. It depends on `core-bungee` (which supplies the injectable native `Plugin`).

**Tech Stack:** Java 8 bytecode (tests Java 17), Maven, `net.md-5:bungeecord-api:1.21-R0.3` (provided, Maven Central, inherited from `platform-bungee/pom.xml`), JUnit 5 + Mockito 5 (inline mock maker, inherited from the root pom). Spec: `docs/superpowers/specs/2026-06-15-bungeecord-commands-design.md`.

**Build/JDK notes (read once):**
- Build with **JDK 21** (`JAVA_HOME` must point at JDK 21, e.g. `C:/Users/Guilherme/.jdks/ms-21.0.10`, not 25 — Lombok 1.18.36 crashes on 25).
- All commands run from the worktree root: `C:\Users\Guilherme\IdeaProjects\spigot-boot\.claude\worktrees\bungeecord-commands`, using `mvnw.cmd`.
- Pass `-Danimal.sniffer.skip=true` on every build so a stale Spigot 1.8.8 signature in an upstream reactor module never blocks the build. `commands-bungee` itself declares no animal-sniffer check.
- For filtered test runs (`-pl ... -am -Dtest=Xxx`), also pass `-Dsurefire.failIfNoSpecifiedTests=false` so the upstream `-am` modules (which have no class matching the `-Dtest` filter) do not fail.
- Every new `.java` file MUST begin with the project's MIT license header (the block shown verbatim in Task 2, `Copyright © 2025 Guilherme Kauã da Silva`, matching the existing `core-bungee` files and the root `license-maven-plugin` config). Subsequent tasks say "begin with the standard MIT header" — use the exact block from Task 2.

---

## File Structure

**Created (all under `platform-bungee/commands-bungee/`):**
- `pom.xml` — adapter module pom; compiler + annotation-processor wiring mirroring `core-bungee`, minus shade/animal-sniffer/MockBukkit.
- `src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandSender.java` — `CommandSenderHandle` over a Bungee `CommandSender`.
- `.../bungee/BungeeCommandPlatformSupport.java` — `CommandPlatformSupport` creating `BungeeCommandSender`.
- `.../bungee/BungeeBootCommand.java` — `Command implements TabExecutor`, bridges to the generic dispatcher.
- `.../bungee/RegisteredCommandSet.java` — immutable snapshot of registered commands.
- `.../bungee/BungeeCommandRegistrar.java` — registers/unregisters via `PluginManager`.
- `.../bungee/BungeeCommandsContextReadyRegistrar.java` — `ContextReadyListener, Ordered` that compiles + registers.
- `.../bungee/resolve/BungeeProxiedPlayerArgumentResolver.java` — `CommandArgumentResolver<ProxiedPlayer>`.
- `.../bungee/resolve/BungeeServerArgumentResolver.java` — `CommandArgumentResolver<ServerInfo>`.
- `.../bungee/completion/BungeeCommandCompletionRegistryCustomizer.java` — `onlinePlayers` + `servers` named completions.
- `.../bungee/BungeeCommandsModule.java` — near-no-op `Module`.
- `.../bungee/configuration/BungeeCommandsConfiguration.java` — `@Configuration` wiring.
- `src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsModule` — empty discovery marker.
- Tests under `src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/`: `BungeeCommandSenderTest`, `BungeeCommandPlatformSupportTest`, `BungeeBootCommandTest`, `BungeeCommandRegistrarTest`, `BungeeCommandsContextReadyRegistrarTest`, `BungeeCommandsModuleDiscoveryTest`, `BungeeBootCommandEndToEndTest`, `resolve/BungeeProxiedPlayerArgumentResolverTest`, `resolve/BungeeServerArgumentResolverTest`, `completion/BungeeCommandCompletionRegistryCustomizerTest`.

**Modified:**
- `platform-bungee/pom.xml` — add `<module>commands-bungee</module>`. **Shared file:** the sibling `config-bungee` effort edits the same `<modules>` list; this is the expected trivial merge conflict (keep both `<module>` lines).

---

## Task 1: Scaffold the `commands-bungee` Maven module

**Files:**
- Create: `platform-bungee/commands-bungee/pom.xml`
- Modify: `platform-bungee/pom.xml`

- [ ] **Step 1: Create `platform-bungee/commands-bungee/pom.xml`**

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
    <description>Runtime commands module for Spigot Boot on BungeeCord.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-commands-bungee</artifactId>

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
            <artifactId>spigot-boot-commands</artifactId>
            <version>${project.version}</version>
        </dependency>

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

Note: `bungeecord-api` needs no `<version>`/`<scope>` — both (`1.21-R0.3`, `provided`) are inherited from `platform-bungee/pom.xml` dependency-management. JUnit 5 + Mockito are `test`-scoped in the root pom and inherited. `Plugin`, `ProxyServer`, `ProxyUtils`, the generic command classes, and `BungeeBootPlugin`/`Context` arrive transitively through `spigot-boot-commands` and `spigot-boot-core-bungee`.

- [ ] **Step 2: Register the module in `platform-bungee/pom.xml`**

In `platform-bungee/pom.xml`, change the `<modules>` block to list `commands-bungee` after `core-bungee`:

```xml
    <modules>
        <module>core-bungee</module>
        <module>commands-bungee</module>
    </modules>
```

(If a concurrent branch already added `<module>config-bungee</module>`, keep all three lines — this is the expected merge point.)

- [ ] **Step 3: Verify the scaffolding resolves and compiles**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. The module has no sources yet, so the compiler reports "No sources to compile" — that is fine. This proves `bungeecord-api`, `spigot-boot-commands`, and `spigot-boot-core-bungee` all resolve and the reactor wiring is correct. (`compile`, not `package`, avoids the javadoc/source jars that would choke on an empty module.)

- [ ] **Step 4: Commit**

```bash
git add platform-bungee/pom.xml platform-bungee/commands-bungee/pom.xml
git commit -m "build(bungee): scaffold commands-bungee module"
```

---

## Task 2: `BungeeCommandSender`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandSender.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandSenderTest.java`

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
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandSenderTest {

    @Test
    void playerIdentityIsUuid() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getName()).thenReturn("Alex");
        when(player.getUniqueId()).thenReturn(uuid);

        BungeeCommandSender handle = new BungeeCommandSender(player);

        assertEquals("Alex", handle.getName());
        assertEquals(uuid.toString(), handle.getIdentity());
    }

    @Test
    void consoleIdentityFallsBackToClassAndName() {
        CommandSender console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");

        BungeeCommandSender handle = new BungeeCommandSender(console);

        assertTrue(handle.getIdentity().endsWith(":CONSOLE"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void delegatesPermissionAndMessage() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("a.b")).thenReturn(true);

        BungeeCommandSender handle = new BungeeCommandSender(sender);

        assertTrue(handle.hasPermission("a.b"));
        handle.sendMessage("hi");
        verify(sender).sendMessage("hi");
    }

    @Test
    void unwrapReturnsSenderForAssignableTypeAndEmptyOtherwise() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        BungeeCommandSender handle = new BungeeCommandSender(player);

        Optional<ProxiedPlayer> asPlayer = handle.unwrap(ProxiedPlayer.class);
        Optional<CommandSender> asSender = handle.unwrap(CommandSender.class);
        Optional<String> asString = handle.unwrap(String.class);

        assertSame(player, asPlayer.orElse(null));
        assertSame(player, asSender.orElse(null));
        assertFalse(asString.isPresent());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandSenderTest`
Expected: COMPILE FAILURE — `BungeeCommandSender` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
/* ... standard MIT header from Task 2 ... */
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

import java.util.Objects;
import java.util.Optional;

/**
 * Adapts a BungeeCord {@link CommandSender} to the platform-neutral {@link CommandSenderHandle}.
 * Mirrors the Spigot {@code BukkitCommandSender}.
 */
public class BungeeCommandSender implements CommandSenderHandle {
    private final CommandSender sender;

    public BungeeCommandSender(CommandSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender cannot be null.");
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public String getIdentity() {
        if (sender instanceof ProxiedPlayer) {
            return ((ProxiedPlayer) sender).getUniqueId().toString();
        }
        // BungeeCord exposes no console sender type in its API; identify non-players by class + name.
        String name = sender.getName();
        return sender.getClass().getName() + ":" + (name == null ? "" : name);
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void sendMessage(String message) {
        // the String overload is deprecated in favour of BaseComponent, but the CommandSenderHandle
        // contract is String-based; the legacy overload renders legacy colour codes correctly.
        sender.sendMessage(message);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type == null || !type.isInstance(sender)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(sender));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandSenderTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandSender.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandSenderTest.java
git commit -m "feat(bungee): add BungeeCommandSender handle"
```

---

## Task 3: `BungeeCommandPlatformSupport`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandPlatformSupport.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandPlatformSupportTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeCommandPlatformSupportTest {
    private final BungeeCommandPlatformSupport support = new BungeeCommandPlatformSupport();

    @Test
    void createSenderWrapsBungeeSender() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("Alex");

        CommandSenderHandle handle = support.createSender(player);

        assertEquals("Alex", handle.getName());
        assertSame(player, handle.unwrap(ProxiedPlayer.class).orElse(null));
    }

    @Test
    void createSenderRejectsNonBungeeSender() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> support.createSender("not a sender"));
        assertTrue(ex.getMessage().contains("Expected a BungeeCord CommandSender"));
    }

    @Test
    void isSenderTypeMatchesCommandSenderHierarchy() {
        assertTrue(support.isSenderType(CommandSender.class));
        assertTrue(support.isSenderType(ProxiedPlayer.class));
        assertFalse(support.isSenderType(String.class));
        assertFalse(support.isSenderType(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandPlatformSupportTest`
Expected: COMPILE FAILURE — `BungeeCommandPlatformSupport` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

/**
 * Wraps a BungeeCord {@link CommandSender} into a {@link CommandSenderHandle}. Mirrors the Spigot
 * {@code BukkitCommandPlatformSupport}. {@code ProxiedPlayer} is a {@code CommandSender}, so it is
 * recognised by {@link #isSenderType(Class)} too.
 */
public class BungeeCommandPlatformSupport implements CommandPlatformSupport {

    @Override
    public CommandSenderHandle createSender(Object nativeSender) {
        if (!(nativeSender instanceof CommandSender)) {
            throw new IllegalArgumentException("Expected a BungeeCord CommandSender but got " +
                    (nativeSender == null ? "null" : nativeSender.getClass().getName()) + ".");
        }
        return new BungeeCommandSender((CommandSender) nativeSender);
    }

    @Override
    public boolean isSenderType(Class<?> type) {
        return type != null && CommandSender.class.isAssignableFrom(type);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandPlatformSupportTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandPlatformSupport.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandPlatformSupportTest.java
git commit -m "feat(bungee): add BungeeCommandPlatformSupport"
```

---

## Task 4: `BungeeBootCommand`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommand.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommandTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeBootCommandTest {

    private CompiledRootCommand rootNamed(String primary) {
        CommandAliasSet aliases = mock(CommandAliasSet.class);
        when(aliases.getPrimary()).thenReturn(primary);
        when(aliases.getAliases()).thenReturn(Collections.emptyList());
        CompiledRootCommand root = mock(CompiledRootCommand.class);
        when(root.getAliases()).thenReturn(aliases);
        return root;
    }

    @Test
    void baseCommandExposesNoPermissionSoDispatcherOwnsIt() {
        BungeeBootCommand command = new BungeeBootCommand(
                mock(Context.class), rootNamed("server"), mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        assertNull(command.getPermission());
    }

    @Test
    void executeDelegatesToDispatcher() {
        Context context = mock(Context.class);
        CompiledRootCommand root = rootNamed("server");
        CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        CommandPlatformSupport support = mock(CommandPlatformSupport.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        CommandSenderHandle handle = mock(CommandSenderHandle.class);
        when(support.createSender(sender)).thenReturn(handle);

        BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, support);
        String[] args = {"send", "Target"};
        command.execute(sender, args);

        verify(dispatcher).dispatch(context, root, handle, "server", args);
    }

    @Test
    void onTabCompleteDelegatesToDispatcherAndIsNullSafe() {
        Context context = mock(Context.class);
        CompiledRootCommand root = rootNamed("server");
        CommandDispatcher dispatcher = mock(CommandDispatcher.class);
        CommandPlatformSupport support = mock(CommandPlatformSupport.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        CommandSenderHandle handle = mock(CommandSenderHandle.class);
        when(support.createSender(sender)).thenReturn(handle);
        when(dispatcher.complete(context, root, handle, "server", new String[]{"send", "T"}))
                .thenReturn(Collections.singletonList("Target"));

        BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, support);

        Iterable<String> result = command.onTabComplete(sender, new String[]{"send", "T"});
        List<String> values = new ArrayList<>();
        result.forEach(values::add);
        assertEquals(Collections.singletonList("Target"), values);

        when(dispatcher.complete(context, root, handle, "server", new String[]{"x"})).thenReturn(null);
        assertFalse(command.onTabComplete(sender, new String[]{"x"}).iterator().hasNext());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeBootCommandTest`
Expected: COMPILE FAILURE — `BungeeBootCommand` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.Collections;
import java.util.List;

/**
 * A BungeeCord {@link Command} that bridges a compiled root command to the generic
 * {@link CommandDispatcher}. Mirrors the Spigot {@code SpigotBootCommand}.
 *
 * <p>The base command is constructed with a {@code null} permission: BungeeCord's
 * {@code PluginManager} only blocks execution when {@code hasPermission} fails, so leaving it null
 * guarantees {@link #execute}/{@link #onTabComplete} are always reached and the dispatcher enforces
 * per-route {@code @Permission} for both — the same end-state as the Spigot adapter.
 *
 * <p>{@link TabExecutor} is implemented because BungeeCord's base {@link Command} has no tab-complete
 * method; the dispatcher invokes {@link #onTabComplete} via {@code instanceof TabExecutor}.
 */
public class BungeeBootCommand extends Command implements TabExecutor {
    private final Context context;
    private final CompiledRootCommand rootCommand;
    private final CommandDispatcher dispatcher;
    private final CommandPlatformSupport commandPlatformSupport;

    public BungeeBootCommand(Context context,
                             CompiledRootCommand rootCommand,
                             CommandDispatcher dispatcher,
                             CommandPlatformSupport commandPlatformSupport) {
        super(rootCommand.getAliases().getPrimary(), null,
                rootCommand.getAliases().getAliases().toArray(new String[0]));
        this.context = context;
        this.rootCommand = rootCommand;
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // BungeeCord's execute() is void and provides no label; the primary name is used as the label.
        dispatcher.dispatch(context, rootCommand, commandPlatformSupport.createSender(sender), getName(), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = dispatcher.complete(context, rootCommand,
                commandPlatformSupport.createSender(sender), getName(), args);
        return completions == null ? Collections.emptyList() : completions;
    }

    public CompiledRootCommand getRootCommand() {
        return rootCommand;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeBootCommandTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommand.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommandTest.java
git commit -m "feat(bungee): add BungeeBootCommand TabExecutor bridge"
```

---

## Task 5: `RegisteredCommandSet` + `BungeeCommandRegistrar`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/RegisteredCommandSet.java`
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandRegistrar.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandRegistrarTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandRegistrarTest {

    private CompiledRootCommand rootNamed(String primary) {
        CommandAliasSet aliases = mock(CommandAliasSet.class);
        when(aliases.getPrimary()).thenReturn(primary);
        when(aliases.getAliases()).thenReturn(Collections.emptyList());
        CompiledRootCommand root = mock(CompiledRootCommand.class);
        when(root.getAliases()).thenReturn(aliases);
        return root;
    }

    @Test
    void registersEachRootAndUnregistersThem() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPluginManager()).thenReturn(pluginManager);

        Context context = mock(Context.class);
        when(context.getBean(Plugin.class)).thenReturn(plugin);

        BungeeCommandRegistrar registrar = new BungeeCommandRegistrar(
                mock(CommandDispatcher.class), mock(CommandPlatformSupport.class));

        RegisteredCommandSet set = registrar.register(context, Collections.singletonList(rootNamed("server")));

        assertEquals(1, set.getCommands().size());
        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(pluginManager).registerCommand(eq(plugin), captor.capture());
        assertEquals("server", captor.getValue().getName());

        registrar.unregister(context, set);
        verify(pluginManager).unregisterCommand(set.getCommands().get(0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandRegistrarTest`
Expected: COMPILE FAILURE — `BungeeCommandRegistrar`/`RegisteredCommandSet` do not exist yet.

- [ ] **Step 3: Write `RegisteredCommandSet`** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the {@link BungeeBootCommand}s registered for a context, carried by the
 * shutdown hook so exactly those commands are unregistered. Mirrors the Spigot
 * {@code RegisteredCommandSet}.
 */
public final class RegisteredCommandSet {
    private final List<BungeeBootCommand> commands;

    public RegisteredCommandSet(List<BungeeBootCommand> commands) {
        this.commands = Collections.unmodifiableList(commands);
    }

    public List<BungeeBootCommand> getCommands() {
        return commands;
    }
}
```

- [ ] **Step 4: Write `BungeeCommandRegistrar`** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers compiled commands with BungeeCord through its public {@link PluginManager} API. Unlike
 * the Spigot registrar there is no CommandMap reflection: {@code registerCommand}/{@code
 * unregisterCommand} are public. BungeeCord's command map is private and last-wins, so no collision
 * detection is performed (see the design spec).
 */
public class BungeeCommandRegistrar {
    private final CommandDispatcher dispatcher;
    private final CommandPlatformSupport commandPlatformSupport;

    public BungeeCommandRegistrar(CommandDispatcher dispatcher,
                                  CommandPlatformSupport commandPlatformSupport) {
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    public RegisteredCommandSet register(Context context, List<CompiledRootCommand> roots) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();

        List<BungeeBootCommand> commands = new ArrayList<>();
        for (CompiledRootCommand root : roots) {
            BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, commandPlatformSupport);
            pluginManager.registerCommand(plugin, command);
            commands.add(command);
        }
        return new RegisteredCommandSet(commands);
    }

    public void unregister(Context context, RegisteredCommandSet registeredCommandSet) {
        Plugin plugin = context.getBean(Plugin.class);
        PluginManager pluginManager = plugin.getProxy().getPluginManager();
        for (BungeeBootCommand command : registeredCommandSet.getCommands()) {
            pluginManager.unregisterCommand(command);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandRegistrarTest`
Expected: PASS (1 test).

- [ ] **Step 6: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/RegisteredCommandSet.java platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandRegistrar.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandRegistrarTest.java
git commit -m "feat(bungee): add BungeeCommandRegistrar and RegisteredCommandSet"
```

---

## Task 6: `BungeeCommandsContextReadyRegistrar`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsContextReadyRegistrar.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsContextReadyRegistrarTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandsContextReadyRegistrarTest {

    @Test
    void compilesRegistersAndUnregistersOnShutdown() {
        CommandRootCompiler compiler = mock(CommandRootCompiler.class);
        BungeeCommandRegistrar registrar = mock(BungeeCommandRegistrar.class);
        CommandReplacementRegistry replacementRegistry = mock(CommandReplacementRegistry.class);

        Context context = mock(Context.class);
        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getName()).thenReturn("TestPlugin");
        when(context.getPlugin()).thenReturn(bootPlugin);

        CompiledRootCommand root = mock(CompiledRootCommand.class);
        List<CompiledRootCommand> roots = Collections.singletonList(root);
        when(compiler.compile(context)).thenReturn(roots);
        RegisteredCommandSet set = new RegisteredCommandSet(Collections.emptyList());
        when(registrar.register(context, roots)).thenReturn(set);

        new BungeeCommandsContextReadyRegistrar(compiler, registrar, replacementRegistry).onContextReady(context);

        verify(replacementRegistry).register("plugin.name", "TestPlugin");
        verify(replacementRegistry).register("plugin", "TestPlugin");
        verify(registrar).register(context, roots);

        ArgumentCaptor<Runnable> hook = ArgumentCaptor.forClass(Runnable.class);
        verify(context).registerShutdownHook(hook.capture());
        hook.getValue().run();
        verify(registrar).unregister(context, set);
    }

    @Test
    void registersNothingWhenNoCommands() {
        CommandRootCompiler compiler = mock(CommandRootCompiler.class);
        BungeeCommandRegistrar registrar = mock(BungeeCommandRegistrar.class);
        CommandReplacementRegistry replacementRegistry = mock(CommandReplacementRegistry.class);

        Context context = mock(Context.class);
        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getName()).thenReturn("TestPlugin");
        when(context.getPlugin()).thenReturn(bootPlugin);
        when(compiler.compile(context)).thenReturn(Collections.emptyList());

        new BungeeCommandsContextReadyRegistrar(compiler, registrar, replacementRegistry).onContextReady(context);

        verify(registrar, never()).register(any(), any());
        verify(context, never()).registerShutdownHook(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandsContextReadyRegistrarTest`
Expected: COMPILE FAILURE — `BungeeCommandsContextReadyRegistrar` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;

import java.util.List;

/**
 * Compiles every command handler bean and registers the results with BungeeCord when the context is
 * ready, unregistering them on shutdown. Mirrors the Spigot {@code CommandsContextReadyRegistrar}.
 */
public class BungeeCommandsContextReadyRegistrar implements ContextReadyListener, Ordered {
    private final CommandRootCompiler commandRootCompiler;
    private final BungeeCommandRegistrar bungeeCommandRegistrar;
    private final CommandReplacementRegistry replacementRegistry;

    public BungeeCommandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                               BungeeCommandRegistrar bungeeCommandRegistrar,
                                               CommandReplacementRegistry replacementRegistry) {
        this.commandRootCompiler = commandRootCompiler;
        this.bungeeCommandRegistrar = bungeeCommandRegistrar;
        this.replacementRegistry = replacementRegistry;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public void onContextReady(Context context) {
        replacementRegistry.register("plugin.name", context.getPlugin().getName());
        replacementRegistry.register("plugin", context.getPlugin().getName());

        List<CompiledRootCommand> roots = commandRootCompiler.compile(context);
        if (roots.isEmpty()) {
            return;
        }

        final RegisteredCommandSet registered = bungeeCommandRegistrar.register(context, roots);
        context.registerShutdownHook(() -> bungeeCommandRegistrar.unregister(context, registered));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandsContextReadyRegistrarTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsContextReadyRegistrar.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsContextReadyRegistrarTest.java
git commit -m "feat(bungee): register commands on context ready"
```

---

## Task 7: `BungeeProxiedPlayerArgumentResolver`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeProxiedPlayerArgumentResolver.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeProxiedPlayerArgumentResolverTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.resolve;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeProxiedPlayerArgumentResolverTest {
    private final Plugin plugin = mock(Plugin.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final BungeeProxiedPlayerArgumentResolver resolver = new BungeeProxiedPlayerArgumentResolver(plugin);
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "target", "target", ProxiedPlayer.class, ProxiedPlayer.class, false, false, false, null, null);

    @BeforeEach
    void setUp() {
        when(plugin.getProxy()).thenReturn(proxy);
    }

    @Test
    void supportsOnlyProxiedPlayer() {
        assertTrue(resolver.supports(parameter));
        CommandParameterMetadata stringParam = new CommandParameterMetadata(
                null, 0, "s", "s", String.class, String.class, false, false, false, null, null);
        assertFalse(resolver.supports(stringParam));
    }

    @Test
    void resolveReturnsOnlinePlayer() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(proxy.getPlayer("Alex")).thenReturn(player);
        assertSame(player, resolver.resolve(context, parameter, "Alex"));
    }

    @Test
    void resolveRejectsUnknownPlayer() {
        when(proxy.getPlayer("Ghost")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(context, parameter, "Ghost"));
    }

    @Test
    void defaultCompletionProviderListsOnlinePlayers() {
        ProxiedPlayer a = mock(ProxiedPlayer.class);
        ProxiedPlayer b = mock(ProxiedPlayer.class);
        when(a.getName()).thenReturn("Alex");
        when(b.getName()).thenReturn("Bob");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(a, b));

        List<String> names = resolver.defaultCompletionProvider().complete(context, parameter, "");
        assertEquals(Arrays.asList("Alex", "Bob"), names);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeProxiedPlayerArgumentResolverTest`
Expected: COMPILE FAILURE — `BungeeProxiedPlayerArgumentResolver` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.resolve;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a {@code ProxiedPlayer} command argument by online name, with online-player tab
 * completion. The Bungee analogue of {@code BukkitPlayerArgumentResolver}; reaches the proxy via the
 * injected {@link Plugin}. Prefix filtering of completions is applied downstream by the framework's
 * {@code CompletionResolver}.
 */
public class BungeeProxiedPlayerArgumentResolver implements CommandArgumentResolver<ProxiedPlayer>, Ordered {
    private final Plugin plugin;

    public BungeeProxiedPlayerArgumentResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return ProxiedPlayer.class.equals(parameter.getValueType());
    }

    @Override
    public ProxiedPlayer resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(input);
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + input);
        }
        return player;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                values.add(player.getName());
            }
            return values;
        };
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeProxiedPlayerArgumentResolverTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeProxiedPlayerArgumentResolver.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeProxiedPlayerArgumentResolverTest.java
git commit -m "feat(bungee): add ProxiedPlayer argument resolver"
```

---

## Task 8: `BungeeServerArgumentResolver`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeServerArgumentResolver.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeServerArgumentResolverTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.resolve;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeServerArgumentResolverTest {
    private final Plugin plugin = mock(Plugin.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final BungeeServerArgumentResolver resolver = new BungeeServerArgumentResolver(plugin);
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "server", "server", ServerInfo.class, ServerInfo.class, false, false, false, null, null);

    @BeforeEach
    void setUp() {
        when(plugin.getProxy()).thenReturn(proxy);
    }

    @Test
    void supportsOnlyServerInfo() {
        assertTrue(resolver.supports(parameter));
    }

    @Test
    void resolveReturnsServer() {
        ServerInfo lobby = mock(ServerInfo.class);
        when(proxy.getServerInfo("lobby")).thenReturn(lobby);
        assertSame(lobby, resolver.resolve(context, parameter, "lobby"));
    }

    @Test
    void resolveRejectsUnknownServer() {
        when(proxy.getServerInfo("void")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(context, parameter, "void"));
    }

    @Test
    void defaultCompletionProviderListsServers() {
        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        servers.put("lobby", mock(ServerInfo.class));
        servers.put("survival", mock(ServerInfo.class));
        when(proxy.getServers()).thenReturn(servers);

        List<String> names = resolver.defaultCompletionProvider().complete(context, parameter, "");
        assertTrue(names.contains("lobby"));
        assertTrue(names.contains("survival"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeServerArgumentResolverTest`
Expected: COMPILE FAILURE — `BungeeServerArgumentResolver` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.resolve;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;

/**
 * Resolves a {@code ServerInfo} command argument by configured server name, with server-name tab
 * completion. A proxy-specific resolver with no Bukkit analogue; reaches the proxy via the injected
 * {@link Plugin}. Prefix filtering of completions is applied downstream by {@code CompletionResolver}.
 */
public class BungeeServerArgumentResolver implements CommandArgumentResolver<ServerInfo>, Ordered {
    private final Plugin plugin;

    public BungeeServerArgumentResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return ServerInfo.class.equals(parameter.getValueType());
    }

    @Override
    public ServerInfo resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        ServerInfo server = plugin.getProxy().getServerInfo(input);
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + input);
        }
        return server;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> new ArrayList<>(plugin.getProxy().getServers().keySet());
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeServerArgumentResolverTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeServerArgumentResolver.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/resolve/BungeeServerArgumentResolverTest.java
git commit -m "feat(bungee): add ServerInfo argument resolver"
```

---

## Task 9: `BungeeCommandCompletionRegistryCustomizer`

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/completion/BungeeCommandCompletionRegistryCustomizer.java`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/completion/BungeeCommandCompletionRegistryCustomizerTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.completion;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeCommandCompletionRegistryCustomizerTest {

    @Test
    void registersOnlinePlayersAndServersWithPrefixFiltering() {
        Plugin plugin = mock(Plugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(plugin.getProxy()).thenReturn(proxy);

        ProxiedPlayer alex = mock(ProxiedPlayer.class);
        ProxiedPlayer bob = mock(ProxiedPlayer.class);
        when(alex.getName()).thenReturn("Alex");
        when(bob.getName()).thenReturn("Bob");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(alex, bob));

        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        servers.put("lobby", mock(ServerInfo.class));
        servers.put("survival", mock(ServerInfo.class));
        when(proxy.getServers()).thenReturn(servers);

        Map<String, CommandCompletionProvider> registered = new HashMap<>();
        CommandCompletionRegistry registry = new CommandCompletionRegistry() {
            @Override
            public void register(String id, CommandCompletionProvider provider) {
                registered.put(id, provider);
            }

            @Override
            public Optional<CommandCompletionProvider> resolve(String id) {
                return Optional.ofNullable(registered.get(id));
            }
        };

        new BungeeCommandCompletionRegistryCustomizer(plugin).customize(registry);

        assertTrue(registered.containsKey("onlinePlayers"));
        assertTrue(registered.containsKey("servers"));

        CommandExecutionContext ctx = mock(CommandExecutionContext.class);
        assertEquals(Collections.singletonList("Alex"),
                registered.get("onlinePlayers").complete(ctx, null, "a"));
        assertEquals(Collections.singletonList("lobby"),
                registered.get("servers").complete(ctx, null, "l"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandCompletionRegistryCustomizerTest`
Expected: COMPILE FAILURE — `BungeeCommandCompletionRegistryCustomizer` does not exist yet.

- [ ] **Step 3: Write the implementation** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.completion;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registers the proxy-specific named completions {@code onlinePlayers} and {@code servers}, referenced
 * from handlers via {@code @Completion("onlinePlayers")} / {@code @Completion("servers")}. Mirrors the
 * Spigot {@code BukkitCommandCompletionRegistryCustomizer} ({@code worlds}/{@code materials} have no
 * proxy analogue). Reaches the proxy via the injected {@link Plugin}.
 */
public class BungeeCommandCompletionRegistryCustomizer implements CommandCompletionRegistryCustomizer {
    private final Plugin plugin;

    public BungeeCommandCompletionRegistryCustomizer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void customize(CommandCompletionRegistry registry) {
        registry.register("onlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                if (!lowerInput.isEmpty() && !player.getName().toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(player.getName());
            }
            return values;
        });
        registry.register("servers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (String serverName : plugin.getProxy().getServers().keySet()) {
                if (!lowerInput.isEmpty() && !serverName.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(serverName);
            }
            return values;
        });
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandCompletionRegistryCustomizerTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/completion/BungeeCommandCompletionRegistryCustomizer.java platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/completion/BungeeCommandCompletionRegistryCustomizerTest.java
git commit -m "feat(bungee): add onlinePlayers and servers completions"
```

---

## Task 10: `BungeeCommandsModule` + discovery marker

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsModule.java`
- Create: `platform-bungee/commands-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsModule`
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsModuleDiscoveryTest.java`

- [ ] **Step 1: Write the failing test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleDiscovery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeeCommandsModuleDiscoveryTest {

    // proves the META-INF/spigot-boot/modules marker resource is present and names the module correctly,
    // so the framework auto-discovers it exactly as it does BungeeCoreModule.
    @Test
    void bungeeCommandsModuleIsAutoDiscoverable() {
        List<Class<? extends Module>> modules =
                new ModuleDiscovery(getClass().getClassLoader()).discover();

        assertTrue(modules.contains(BungeeCommandsModule.class),
                "BungeeCommandsModule must be discoverable via its META-INF/spigot-boot/modules marker");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandsModuleDiscoveryTest`
Expected: COMPILE FAILURE — `BungeeCommandsModule` does not exist yet.

- [ ] **Step 3: Write the `BungeeCommandsModule`** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.module.Module;

/**
 * Marks the Bungee commands module for discovery. The bean wiring lives in
 * {@code BungeeCommandsConfiguration}; this module only logs initialisation, mirroring the Spigot
 * {@code SpigotCommandsModule}.
 */
public class BungeeCommandsModule implements Module {
    @Override
    public void onInitialize(Context context) {
        context.getPlugin().getLogger().fine("Initializing Bungee commands module.");
    }
}
```

- [ ] **Step 4: Create the module marker resource**

File: `platform-bungee/commands-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsModule`

Content: an empty file (zero bytes). The file *name* is the module FQCN; `ModuleDiscovery` reads the name, not the contents.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeCommandsModuleDiscoveryTest`
Expected: PASS (1 test). If it fails, confirm the marker file landed in `target/classes/META-INF/spigot-boot/modules/` with the exact FQCN as its name (no extension, no trailing newline in the name).

- [ ] **Step 6: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsModule.java "platform-bungee/commands-bungee/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsModule" platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeCommandsModuleDiscoveryTest.java
git commit -m "feat(bungee): add discoverable BungeeCommandsModule"
```

---

## Task 11: `BungeeCommandsConfiguration` (`@Configuration` wiring)

**Files:**
- Create: `platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/configuration/BungeeCommandsConfiguration.java`

This wires the 8 beans that depend on `CommandPlatformSupport` (mirroring `SpigotCommandsConfiguration`, minus the CommandMap accessor) plus the Bungee glue. There is no isolated unit test — it is validated by the reactor build here and the end-to-end test in Task 12.

- [ ] **Step 1: Write the configuration** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee.configuration;

import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolverChain;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandRegistrar;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsContextReadyRegistrar;
import tech.guilhermekaua.spigotboot.commands.bungee.completion.BungeeCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeProxiedPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeServerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;

/**
 * Wires the BungeeCord command pipeline. The 13 platform-neutral beans come from the generic
 * {@code CommandsConfiguration}; this configuration declares only the beans that depend on the
 * platform {@link CommandPlatformSupport}, plus the Bungee registrar, context-ready registrar,
 * argument resolvers, and completion customizer. Mirrors {@code SpigotCommandsConfiguration} (without
 * the CommandMap accessor, which BungeeCord does not need).
 */
@Configuration
public class BungeeCommandsConfiguration {

    @Bean
    public CommandPlatformSupport commandPlatformSupport() {
        return new BungeeCommandPlatformSupport();
    }

    @Bean
    public CommandParameterRoleResolver commandParameterRoleResolver(CommandPlatformSupport commandPlatformSupport) {
        return new CommandParameterRoleResolver(commandPlatformSupport);
    }

    @Bean
    public CommandInvocationFactory commandInvocationFactory(CommandParameterRoleResolver commandParameterRoleResolver) {
        return new CommandInvocationFactory(commandParameterRoleResolver);
    }

    @Bean
    public CommandRouteFactory commandRouteFactory(CommandPatternParser commandPatternParser,
                                                   CommandReplacementRegistry commandReplacementRegistry,
                                                   CommandTextResolverChain commandTextResolverChain,
                                                   CommandInvocationFactory commandInvocationFactory) {
        return new CommandRouteFactory(
                commandPatternParser,
                commandReplacementRegistry,
                commandTextResolverChain,
                commandInvocationFactory
        );
    }

    @Bean
    public CommandRootCompiler commandRootCompiler(CommandHandlerIntrospector commandHandlerIntrospector,
                                                   CommandRouteFactory commandRouteFactory,
                                                   CommandRouteValidator commandRouteValidator,
                                                   CommandInterceptorChain commandInterceptorChain) {
        return new CommandRootCompiler(
                commandHandlerIntrospector,
                commandRouteFactory,
                commandRouteValidator,
                commandInterceptorChain
        );
    }

    @Bean
    public CommandParameterBinder commandParameterBinder(CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CommandParameterBinder(commandArgumentResolverRegistry);
    }

    @Bean
    public CommandDispatcher commandDispatcher(CommandParameterBinder commandParameterBinder,
                                               CommandInvocationExecutor commandInvocationExecutor,
                                               CommandMessagesProvider commandMessagesProvider,
                                               CompletionResolver completionResolver,
                                               CommandInterceptorChain commandInterceptorChain) {
        return new CommandDispatcher(
                commandParameterBinder,
                commandInvocationExecutor,
                commandMessagesProvider,
                completionResolver,
                commandInterceptorChain
        );
    }

    @Bean
    public CommandCooldownInterceptor commandCooldownInterceptor(CooldownManager cooldownManager,
                                                                 CommandMessagesProvider commandMessagesProvider) {
        return new CommandCooldownInterceptor(cooldownManager, commandMessagesProvider);
    }

    @Bean
    public BungeeCommandRegistrar bungeeCommandRegistrar(CommandDispatcher commandDispatcher,
                                                         CommandPlatformSupport commandPlatformSupport) {
        return new BungeeCommandRegistrar(commandDispatcher, commandPlatformSupport);
    }

    @Bean
    public BungeeCommandsContextReadyRegistrar bungeeCommandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                                                                   BungeeCommandRegistrar bungeeCommandRegistrar,
                                                                                   CommandReplacementRegistry commandReplacementRegistry) {
        return new BungeeCommandsContextReadyRegistrar(
                commandRootCompiler,
                bungeeCommandRegistrar,
                commandReplacementRegistry
        );
    }

    @Bean
    public CommandCompletionRegistryCustomizer bungeeCommandCompletionRegistryCustomizer(Plugin plugin) {
        return new BungeeCommandCompletionRegistryCustomizer(plugin);
    }

    @Bean
    public CommandArgumentResolver<?> bungeeProxiedPlayerArgumentResolver(Plugin plugin) {
        return new BungeeProxiedPlayerArgumentResolver(plugin);
    }

    @Bean
    public CommandArgumentResolver<?> bungeeServerArgumentResolver(Plugin plugin) {
        return new BungeeServerArgumentResolver(plugin);
    }
}
```

- [ ] **Step 2: Compile to verify the wiring is type-correct**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. This proves every `@Bean` method's parameters and return types resolve against the generic command classes and the Bungee adapters. (Full DI graph resolution is exercised by Task 12.)

- [ ] **Step 3: Commit**

```bash
git add platform-bungee/commands-bungee/src/main/java/tech/guilhermekaua/spigotboot/commands/bungee/configuration/BungeeCommandsConfiguration.java
git commit -m "feat(bungee): wire BungeeCommandsConfiguration"
```

---

## Task 12: End-to-end test + full-module & reactor verification

**Files:**
- Test: `platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommandEndToEndTest.java`

This is the strongest single proof: a real `@RootCommand` handler is compiled through the real `CommandRootCompiler` pipeline and executed through a fully-wired `CommandDispatcher` (using the Bungee platform support, resolvers, and completion customizer), asserting sender binding, `ProxiedPlayer`/`ServerInfo` argument resolution, and tab completion. Mirrors `SpigotBootCommandTest` with pure Mockito (no MockBungee).

- [ ] **Step 1: Write the test** (begin with the standard MIT header from Task 2)

```java
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.completion.BungeeCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeProxiedPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeServerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBootPlugin;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeBootCommandEndToEndTest {
    private final BungeeCommandPlatformSupport platformSupport = new BungeeCommandPlatformSupport();

    private Plugin mockPlugin(ProxyServer proxy) {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getName()).thenReturn("TestPlugin");
        when(plugin.getDescription()).thenReturn(description);
        when(plugin.getProxy()).thenReturn(proxy);
        return plugin;
    }

    private Context newContext(Plugin plugin) {
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new BungeeBootPlugin(plugin));
        when(context.getDependencyManager()).thenReturn(new DependencyManager());
        return context;
    }

    private CompiledRootCommand compileRoot(Object handler) {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher(Plugin plugin) {
        DefaultCommandArgumentResolverRegistry resolverRegistry = new DefaultCommandArgumentResolverRegistry(
                Arrays.asList(new BungeeProxiedPlayerArgumentResolver(plugin), new BungeeServerArgumentResolver(plugin)),
                Collections.emptyList()
        );
        DefaultCommandCompletionRegistry completionRegistry = new DefaultCommandCompletionRegistry(
                Collections.singletonList(new BungeeCommandCompletionRegistryCustomizer(plugin))
        );
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(completionRegistry, resolverRegistry)
        );
    }

    @Test
    void executesWithSenderBindingAndProxiedPlayerAndServerResolution() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        ProxiedPlayer target = mock(ProxiedPlayer.class);
        ServerInfo lobby = mock(ServerInfo.class);
        when(sender.getName()).thenReturn("Sender");
        when(target.getName()).thenReturn("Target");
        when(lobby.getName()).thenReturn("lobby");
        when(proxy.getPlayer("Target")).thenReturn(target);
        when(proxy.getServerInfo("lobby")).thenReturn(lobby);

        ServerCommands handler = new ServerCommands();
        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(handler), newDispatcher(plugin), platformSupport);

        command.execute(sender, new String[]{"send", "Target", "lobby"});

        assertSame(sender, handler.lastSender);
        assertSame(target, handler.lastTarget);
        assertSame(lobby, handler.lastServer);
    }

    @Test
    void tabCompleteSurfacesPlayerNames() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        ProxiedPlayer target = mock(ProxiedPlayer.class);
        when(sender.getName()).thenReturn("Sender");
        when(target.getName()).thenReturn("Target");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(sender, target));

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new ServerCommands()), newDispatcher(plugin), platformSupport);

        List<String> values = new ArrayList<>();
        command.onTabComplete(sender, new String[]{"send", "T"}).forEach(values::add);

        assertEquals(Collections.singletonList("Target"), values);
    }

    @CommandHandler
    @RootCommand("server")
    static class ServerCommands {
        private ProxiedPlayer lastSender;
        private ProxiedPlayer lastTarget;
        private ServerInfo lastServer;

        @Command("send <target> <server>")
        public void send(@Sender ProxiedPlayer sender, ProxiedPlayer target, ServerInfo server) {
            this.lastSender = sender;
            this.lastTarget = target;
            this.lastServer = server;
        }
    }
}
```

- [ ] **Step 2: Run the end-to-end test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false test -Dtest=BungeeBootCommandEndToEndTest`
Expected: PASS (2 tests). If `tabCompleteSurfacesPlayerNames` returns more than `["Target"]`, confirm the `<target>` parameter is typed `ProxiedPlayer` (so the resolver's online-player completion is used) and that `CompletionResolver` is filtering by the `"T"` prefix.

- [ ] **Step 3: Run the full module test suite**

Run: `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true test`
Expected: PASS — all `commands-bungee` tests green across the 10 test classes (`BungeeCommandSenderTest` 4, `BungeeCommandPlatformSupportTest` 3, `BungeeBootCommandTest` 3, `BungeeCommandRegistrarTest` 1, `BungeeCommandsContextReadyRegistrarTest` 2, `BungeeProxiedPlayerArgumentResolverTest` 4, `BungeeServerArgumentResolverTest` 4, `BungeeCommandCompletionRegistryCustomizerTest` 1, `BungeeCommandsModuleDiscoveryTest` 1, `BungeeBootCommandEndToEndTest` 2 = 25 tests, 0 failures).

- [ ] **Step 4: Verify the whole reactor still builds**

Run: `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests`
Expected: `BUILD SUCCESS` for every module including `spigot-boot-commands-bungee`. Confirms adding the new module did not disturb the existing reactor.

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/commands-bungee/src/test/java/tech/guilhermekaua/spigotboot/commands/bungee/BungeeBootCommandEndToEndTest.java
git commit -m "test(bungee): end-to-end command dispatch and completion"
```

---

## Done criteria

- `mvnw.cmd -pl platform-bungee/commands-bungee -am -Danimal.sniffer.skip=true test` is green (25 tests across 10 test classes).
- `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests` builds the full reactor including `spigot-boot-commands-bungee`.
- A BungeeCord plugin author can declare a `@RootCommand`/`@Command` handler bean and have it registered automatically on context-ready, with `@Permission` enforced, `@Sender CommandSender`/`ProxiedPlayer` binding, `ProxiedPlayer`/`ServerInfo` argument resolution, and `onlinePlayers`/`servers` tab completion — without writing any registration code.
- `commands-bungee` ships no `maven-shade-plugin`, no javassist relocation, no `animal-sniffer` check, and no MockBukkit.

## Out of scope (later slices, separate plans)

- `commands-config-bungee` (the `CommandTextResolver` → config bridge; needs a future `config-bungee`).
- Hoisting the 8 platform-support-dependent pipeline beans into the generic `CommandsConfiguration` to remove the Spigot/Bungee duplication (a deliberate follow-up refactor, after this module is merged and green).
- A `bungee.yml` descriptor generator and any runnable BungeeCord sample/test plugin or on-server end-to-end validation.
