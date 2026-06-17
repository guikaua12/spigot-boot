# BungeeCord Sample Plugin (`test-plugin-bungee`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a runnable BungeeCord sample plugin, `test-plugin-bungee`, that exercises the full proxy-side surface of Spigot Boot (descriptor generation, shared config, commands, listeners, services, DI, and `TaskScheduler`).

**Architecture:** A new top-level Maven consumer module that depends on `core-bungee`, `commands-bungee`, and the shared `config` module. Its main class declares `@BungeePlugin` (generates `bungee.yml`) and boots the DI container via `BungeeBoot.initialize(...)`. All beans (configs, services, commands, listeners, scheduler) are auto-discovered through the generated `DiscoveryIndex` and wired by constructor injection.

**Tech Stack:** Java 8 (source/target 1.8), Lombok 1.18.36, Maven shade (`minimizeJar`), `net.md-5:bungeecord-api:1.21-R0.3` (provided), Spigot Boot 3.2.1-SNAPSHOT modules.

## Global Constraints

These apply to **every** task below.

- **Build JDK is 21, not the shell default.** Before any Maven command: `$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"` (PowerShell). JDK 25/26 crash Lombok 1.18.36 with `TypeTag :: UNKNOWN`.
- **Module identity:** parent `tech.guilhermekaua.spigot-boot:spigot-boot:3.2.1-SNAPSHOT`; `artifactId` = `test-plugin-bungee`; base package `tech.guilhermekaua.spigotboot.testPluginBungee`.
- **Java level:** `source`/`target` = `1.8`; compile with `-parameters`.
- **No automated tests** — this module is a pure runnable demo. Do not create `src/test`. Verification is build success + generated-artifact inspection.
- **License header:** every `.java` file starts with the repo's MIT license header (copy verbatim from any existing file, e.g. `test-plugin/src/main/java/.../Main.java`). Normal comments start lowercase; public/protected methods get Javadoc.
- **DI idiom:** constructor injection needs **no** `@Inject`; use Lombok `@RequiredArgsConstructor` over `private final` fields (as in `JoinListener`). `Plugin` and `TaskScheduler` are injectable beans; **`ProxyServer` is not** — inject `Plugin` and call `getProxy()`.
- **Verification build command** (per task): `.\mvnw.cmd -pl test-plugin-bungee -am package`. If a `spigot-api-1_8-signature` / animal-sniffer error appears (none expected, since no upstream dependency opts in), append `-Danimal.sniffer.skip=true`.
- **Commit discipline:** one commit per task, Conventional Commit prefix. The work happens on the current branch `feat/bungee-shared-config-integration`.
- **Do not modify any framework module** other than the single `<module>` line added to the root `pom.xml`.

---

### Task 1: Scaffold module — pom, reactor wiring, minimal boot

Stands up the module so it compiles, registers in the reactor, and the annotation processor emits `bungee.yml`. `Main` boots the container but reads no beans yet (configs arrive in Task 2), keeping this task independently buildable.

**Files:**
- Create: `test-plugin-bungee/pom.xml`
- Modify: `pom.xml` (root reactor `<modules>`)
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/Main.java`

**Interfaces:**
- Consumes: `BungeeBoot.initialize(BootPlugin)` / `BungeeBoot.onDisable(BootPlugin)`; `new BungeeBootPlugin(Plugin)`; `@BungeePlugin(name, version, author, description)` from `tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations`.
- Produces: the `test-plugin-bungee` module and `Main` (base package `tech.guilhermekaua.spigotboot.testPluginBungee`) that later tasks add classes beside.

- [ ] **Step 1: Create `test-plugin-bungee/pom.xml`**

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
    <artifactId>test-plugin-bungee</artifactId>
    <description>Sample BungeeCord plugin demonstrating Spigot Boot on a proxy.</description>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.deploy.skip>true</maven.deploy.skip>
        <skipPublishing>true</skipPublishing>
        <bungee.plugins.dir>${project.build.directory}</bungee.plugins.dir>
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
                        <path>
                            <groupId>tech.guilhermekaua.spigot-boot</groupId>
                            <artifactId>spigot-boot-annotation-processor-bungee</artifactId>
                            <version>${project.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <minimizeJar>true</minimizeJar>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>2.3.1</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>jar</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <outputDirectory>${bungee.plugins.dir}</outputDirectory>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-core-bungee</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-commands-bungee</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-config</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-annotation-processor-bungee</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>net.md-5</groupId>
            <artifactId>bungeecord-api</artifactId>
            <version>1.21-R0.3</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add the module to the root reactor**

In `pom.xml`, inside `<modules>`, add the new module on the line **after** `<module>test-plugin</module>`:

```xml
        <module>test-plugin</module>
        <module>test-plugin-bungee</module>
    </modules>
```

- [ ] **Step 3: Create `Main.java` (minimal boot)**

Path: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/Main.java`. Prefix with the MIT license header, then:

```java
package tech.guilhermekaua.spigotboot.testPluginBungee;

import lombok.Getter;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBoot;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBootPlugin;
import tech.guilhermekaua.spigotboot.core.context.Context;

/**
 * Sample BungeeCord plugin main class. Declares the descriptor via {@link BungeePlugin} (which
 * generates {@code bungee.yml}) and boots the Spigot Boot DI container on enable.
 */
@Getter
@BungeePlugin(
        name = "NetworkManager",
        version = "1.0.0",
        author = "Approximations",
        description = "A sample BungeeCord plugin for the Spigot Boot framework."
)
public class Main extends Plugin {
    private BungeeBootPlugin bootPlugin;
    private Context context;

    @Override
    public void onEnable() {
        bootPlugin = new BungeeBootPlugin(this);
        context = BungeeBoot.initialize(bootPlugin);
        getLogger().info("NetworkManager enabled.");
    }

    @Override
    public void onDisable() {
        BungeeBoot.onDisable(bootPlugin);
    }
}
```

- [ ] **Step 4: Build and verify it compiles and packages**

Run:
```
$env:JAVA_HOME = "C:\Users\Guilherme\.jdks\ms-21.0.10"
.\mvnw.cmd -pl test-plugin-bungee -am package
```
Expected: `BUILD SUCCESS`, and `test-plugin-bungee` is in the reactor list.

- [ ] **Step 5: Verify `bungee.yml` was generated**

Inspect `test-plugin-bungee/target/classes/bungee.yml`. Expected content:
```yaml
name: NetworkManager
main: tech.guilhermekaua.spigotboot.testPluginBungee.Main
version: 1.0.0
author: Approximations
description: A sample BungeeCord plugin for the Spigot Boot framework.
```
(Key order may vary; all five keys must be present with these values.)

- [ ] **Step 6: Commit**

```
git add pom.xml test-plugin-bungee/pom.xml test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/Main.java
git commit -m "feat(bungee): scaffold test-plugin-bungee sample module"
```

---

### Task 2: Config classes + boot-time config read

Adds the two `@Config` classes (one with a nested POJO, `@Range`, and a `Duration`) and wires `Main` to read a config bean at boot.

**Files:**
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/config/NetworkConfig.java`
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/config/MessagesConfig.java`
- Modify: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/Main.java` (onEnable reads `NetworkConfig`)

**Interfaces:**
- Consumes: `@Config(String)`, `@Comment(String)` (`tech.guilhermekaua.spigotboot.config.annotation`); `@Range(min,max)` (`tech.guilhermekaua.spigotboot.core.validation.annotation`); `Context.getBean(Class)`.
- Produces: `NetworkConfig` with `getDefaultServer():String`, `getMaxNetworkPlayers():int`, `getAnnouncements():NetworkConfig.AnnouncementsConfig`; nested `AnnouncementsConfig` with `isEnabled():boolean`, `getInterval():Duration`, `getMessages():List<String>`. `MessagesConfig` with `getPrefix()`, `getWelcome()`, `getDisconnect()`, `getServerSwitch()` (all `String`).

- [ ] **Step 1: Create `config/NetworkConfig.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.config;

import lombok.Data;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.core.validation.annotation.Range;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Primary plugin configuration, backed by {@code config.yml}. Demonstrates comments, range
 * validation, a nested config POJO, and a {@link Duration} field (cross-config Duration support).
 */
@Config("config.yml")
@Data
public class NetworkConfig {
    @Comment("Server players are sent to by /lobby")
    private String defaultServer = "lobby";

    @Comment("Maximum players allowed across the whole network")
    @Range(min = 1, max = 1000)
    private int maxNetworkPlayers = 500;

    @Comment("Periodic broadcast announcement settings")
    private AnnouncementsConfig announcements = new AnnouncementsConfig();

    /**
     * nested configuration block for periodic announcements.
     */
    @Data
    public static class AnnouncementsConfig {
        @Comment("Whether periodic announcements are broadcast")
        private boolean enabled = true;

        @Comment("Delay between announcements")
        private Duration interval = Duration.ofMinutes(5);

        @Comment("Messages cycled through, one per interval; supports & colour codes")
        private List<String> messages = Arrays.asList(
                "&7Welcome to the network! Use &b/server &7to move around.",
                "&7Type &b/lobby &7to return to the main hub."
        );
    }
}
```

- [ ] **Step 2: Create `config/MessagesConfig.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.config;

import lombok.Data;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;
import tech.guilhermekaua.spigotboot.config.annotation.Config;

/**
 * Player-facing message templates, backed by {@code messages.yml}. Placeholders {@code %player%}
 * and {@code %server%} are substituted by the consuming services and listeners.
 */
@Config("messages.yml")
@Data
public class MessagesConfig {
    @Comment("Prefix prepended to every plugin message; supports & colour codes")
    private String prefix = "&7[&bNetwork&7] ";

    @Comment("Sent to a player when they join; %player% is replaced")
    private String welcome = "&aWelcome, %player%&a!";

    @Comment("Broadcast when a player leaves; %player% is replaced")
    private String disconnect = "&7%player% left the network.";

    @Comment("Broadcast when a player changes server; %player% and %server% are replaced")
    private String serverSwitch = "&7%player% moved to &b%server%&7.";
}
```

- [ ] **Step 3: Update `Main.onEnable` to read `NetworkConfig`**

Replace the body of `onEnable()` in `Main.java` with:

```java
    @Override
    public void onEnable() {
        bootPlugin = new BungeeBootPlugin(this);
        context = BungeeBoot.initialize(bootPlugin);

        NetworkConfig config = context.getBean(NetworkConfig.class);
        getLogger().info("NetworkManager enabled. Default server: " + config.getDefaultServer()
                + ", max network players: " + config.getMaxNetworkPlayers() + ".");
    }
```

Add the import at the top of `Main.java`:
```java
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;
```

- [ ] **Step 4: Build**

Run: `.\mvnw.cmd -pl test-plugin-bungee -am package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Verify the configs are in the generated discovery index**

Search `test-plugin-bungee/target/generated-sources/annotations/tech/guilhermekaua/spigotboot/generated/` (file `DiscoveryIndex_*.java`) for `NetworkConfig` and `MessagesConfig`. Expected: both class names appear (the `config` category lists them). If they do not appear, the `annotation-processor-spigot` path is misconfigured — recheck Task 1 Step 1's `annotationProcessorPaths`.

- [ ] **Step 6: Commit**

```
git add test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/config test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/Main.java
git commit -m "feat(bungee): add NetworkConfig and MessagesConfig to sample plugin"
```

---

### Task 3: Service beans

Adds two `@Service` beans demonstrating constructor DI (`Plugin` + config), inter-bean reuse, and `@OnConfigReload` live-reload.

**Files:**
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/service/BroadcastService.java`
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/service/ConnectionService.java`

**Interfaces:**
- Consumes: `@Service` (`tech.guilhermekaua.spigotboot.core.context.annotations`); `@OnConfigReload(Class<?>...)` (`tech.guilhermekaua.spigotboot.config.annotation`); `NetworkConfig`, `MessagesConfig`; Bungee `Plugin`, `ProxiedPlayer`, `ServerInfo`, `ChatColor`, `TextComponent`.
- Produces: `BroadcastService.broadcast(String)`; `ConnectionService.send(ProxiedPlayer, ServerInfo)`, `ConnectionService.locate(String):Optional<ServerInfo>`, `ConnectionService.defaultServer():ServerInfo`.

- [ ] **Step 1: Create `service/BroadcastService.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.service;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;

/**
 * Sends prefixed, colour-translated broadcasts to the whole proxy. Reaches the proxy through the
 * injected {@link Plugin} ({@code ProxyServer} is not an injectable bean).
 */
@Service
@RequiredArgsConstructor
public class BroadcastService {
    private final Plugin plugin;
    private final MessagesConfig messages;

    /**
     * broadcasts a message to every player on the proxy.
     *
     * @param message the message body; {@code &} colour codes are translated and the configured
     *                prefix is prepended.
     */
    public void broadcast(String message) {
        String rendered = ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message);
        plugin.getProxy().broadcast(TextComponent.fromLegacyText(rendered));
    }

    /**
     * logs when {@code messages.yml} is reloaded, demonstrating live config reload callbacks.
     */
    @OnConfigReload(MessagesConfig.class)
    public void onMessagesReload() {
        plugin.getLogger().info("messages.yml reloaded.");
    }
}
```

- [ ] **Step 2: Create `service/ConnectionService.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.service;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;

import java.util.Optional;

/**
 * Encapsulates proxy connection actions: sending players to servers and locating where a player is.
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {
    private final Plugin plugin;
    private final NetworkConfig config;

    /**
     * connects a player to the given server.
     *
     * @param player the player to move.
     * @param server the destination server.
     */
    public void send(@NotNull ProxiedPlayer player, @NotNull ServerInfo server) {
        player.connect(server);
    }

    /**
     * locates the server an online player is currently connected to.
     *
     * @param playerName the player name to look up.
     * @return the player's current server, or empty if the player is offline or not on a server.
     */
    public Optional<ServerInfo> locate(@NotNull String playerName) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null || player.getServer() == null) {
            return Optional.empty();
        }
        return Optional.of(player.getServer().getInfo());
    }

    /**
     * resolves the configured default ("lobby") server.
     *
     * @return the default {@link ServerInfo}, or {@code null} if it is not configured on the proxy.
     */
    public ServerInfo defaultServer() {
        return plugin.getProxy().getServerInfo(config.getDefaultServer());
    }
}
```

- [ ] **Step 3: Build**

Run: `.\mvnw.cmd -pl test-plugin-bungee -am package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify services are discovered**

Search the `DiscoveryIndex_*.java` (path as in Task 2 Step 5) for `BroadcastService` and `ConnectionService`. Expected: both appear (under the `component` category, where `@Service` beans are indexed).

- [ ] **Step 5: Commit**

```
git add test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/service
git commit -m "feat(bungee): add BroadcastService and ConnectionService to sample plugin"
```

---

### Task 4: Command handlers

Adds two command handlers exercising `@RootCommand`/`@Command`/`@DefaultCommand`, `@Permission`, the `ProxiedPlayer`/`ServerInfo` argument resolvers, and the named completions `onlinePlayers`/`servers`.

**Files:**
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/command/ServerCommands.java`
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/command/LobbyCommand.java`

**Interfaces:**
- Consumes: command annotations (`tech.guilhermekaua.spigotboot.commands.annotations`): `@CommandHandler`, `@RootCommand(value, aliases)`, `@Command(value)`, `@DefaultCommand`, `@Permission(value)`, `@Sender`, `@Completion(value)`; `ConnectionService`, `MessagesConfig`; Bungee `Plugin`, `CommandSender`, `ProxiedPlayer`, `ServerInfo`, `ChatColor`, `TextComponent`.
- Produces: registered commands `/server` (`send`, `find`, `list`, default `info`) and `/lobby`.

- [ ] **Step 1: Create `command/ServerCommands.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.command;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Completion;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Permission;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.ConnectionService;

import java.util.Optional;

/**
 * {@code /server} command tree: move players between servers, find where a player is, and list
 * configured servers. Argument types {@link ProxiedPlayer} and {@link ServerInfo} are resolved by
 * the Bungee argument resolvers; the {@code onlinePlayers}/{@code servers} completions are provided
 * by the Bungee completion customizer.
 */
@CommandHandler
@RootCommand(value = "server", aliases = "srv")
@RequiredArgsConstructor
public class ServerCommands {
    private final ConnectionService connections;
    private final MessagesConfig messages;
    private final Plugin plugin;

    @DefaultCommand
    public void info(@Sender CommandSender sender) {
        reply(sender, "&7Usage: &b/server send <player> <server>&7, &b/server find <player>&7, &b/server list");
    }

    @Command("send <target> <server>")
    @Permission("network.server.send")
    public void send(@Sender CommandSender sender,
                     @Completion("onlinePlayers") ProxiedPlayer target,
                     @Completion("servers") ServerInfo server) {
        connections.send(target, server);
        reply(sender, "&aSent &b" + target.getName() + " &ato &b" + server.getName() + "&a.");
    }

    @Command("find <target>")
    public void find(@Sender CommandSender sender,
                     @Completion("onlinePlayers") ProxiedPlayer target) {
        Optional<ServerInfo> server = connections.locate(target.getName());
        if (server.isPresent()) {
            reply(sender, "&b" + target.getName() + " &7is on &b" + server.get().getName() + "&7.");
        } else {
            reply(sender, "&c" + target.getName() + " is not connected to any server.");
        }
    }

    @Command("list")
    public void list(@Sender CommandSender sender) {
        String servers = String.join("&7, &b", plugin.getProxy().getServers().keySet());
        reply(sender, "&7Servers: &b" + servers);
    }

    private void reply(CommandSender sender, String message) {
        sender.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message)));
    }
}
```

- [ ] **Step 2: Create `command/LobbyCommand.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.command;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.ConnectionService;

/**
 * {@code /lobby} command: sends the caller to the configured default server. A config-driven
 * command that injects only the collaborators it needs.
 */
@CommandHandler
@RootCommand("lobby")
@RequiredArgsConstructor
public class LobbyCommand {
    private final ConnectionService connections;
    private final MessagesConfig messages;

    @DefaultCommand
    public void lobby(@Sender ProxiedPlayer sender) {
        ServerInfo lobby = connections.defaultServer();
        if (lobby == null) {
            send(sender, "&cThe lobby server is not configured.");
            return;
        }
        connections.send(sender, lobby);
        send(sender, "&aSending you to the lobby...");
    }

    private void send(ProxiedPlayer player, String message) {
        player.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + message)));
    }
}
```

- [ ] **Step 3: Build**

Run: `.\mvnw.cmd -pl test-plugin-bungee -am package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify command handlers are discovered**

Search the `DiscoveryIndex_*.java` for `ServerCommands` and `LobbyCommand`. Expected: both appear (`@CommandHandler` is `@Component`-meta-annotated, so they index under `component`).

- [ ] **Step 5: Commit**

```
git add test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/command
git commit -m "feat(bungee): add /server and /lobby commands to sample plugin"
```

---

### Task 5: Event listeners

Adds two auto-registered Bungee `Listener` `@Component` beans (login/disconnect and server-switch), wired to `BroadcastService`/`MessagesConfig`.

**Files:**
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/listener/ConnectionListener.java`
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/listener/ServerSwitchListener.java`

**Interfaces:**
- Consumes: `@Component`; Bungee `Listener` (`net.md_5.bungee.api.plugin.Listener`), `@EventHandler` (`net.md_5.bungee.event.EventHandler`), events `PostLoginEvent`, `PlayerDisconnectEvent`, `ServerSwitchEvent` (`net.md_5.bungee.api.event`); `BroadcastService`, `MessagesConfig`.
- Produces: nothing consumed by later tasks (terminal beans, auto-registered by `BungeeListenerAutoRegistrar` on context-ready).

- [ ] **Step 1: Create `listener/ConnectionListener.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.listener;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.BroadcastService;

/**
 * Welcomes joining players and broadcasts disconnects. Auto-registered with BungeeCord by the
 * framework once the context is ready.
 */
@Component
@RequiredArgsConstructor
public class ConnectionListener implements Listener {
    private final BroadcastService broadcast;
    private final MessagesConfig messages;

    @EventHandler
    public void onLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String welcome = messages.getWelcome().replace("%player%", player.getName());
        player.sendMessage(TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes('&', messages.getPrefix() + welcome)));
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        broadcast.broadcast(messages.getDisconnect().replace("%player%", event.getPlayer().getName()));
    }
}
```

- [ ] **Step 2: Create `listener/ServerSwitchListener.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.listener;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.MessagesConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.BroadcastService;

/**
 * Announces when a player changes backend server.
 */
@Component
@RequiredArgsConstructor
public class ServerSwitchListener implements Listener {
    private final BroadcastService broadcast;
    private final MessagesConfig messages;

    @EventHandler
    public void onSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player.getServer() == null) {
            return;
        }
        String server = player.getServer().getInfo().getName();
        broadcast.broadcast(messages.getServerSwitch()
                .replace("%player%", player.getName())
                .replace("%server%", server));
    }
}
```

- [ ] **Step 3: Build**

Run: `.\mvnw.cmd -pl test-plugin-bungee -am package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify listeners are discovered**

Search the `DiscoveryIndex_*.java` for `ConnectionListener` and `ServerSwitchListener`. Expected: both appear under `component`.

- [ ] **Step 5: Commit**

```
git add test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/listener
git commit -m "feat(bungee): add connection and server-switch listeners to sample plugin"
```

---

### Task 6: Scheduled announcements + final full-reactor verification

Adds the `TaskScheduler`-driven announcement component (the one feature beyond `test-plugin`'s surface) and runs a full-reactor build to confirm the whole project stays green and the shaded jar + descriptor are correct.

**Files:**
- Create: `test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/task/AnnouncementScheduler.java`

**Interfaces:**
- Consumes: `ContextReadyListener.onContextReady(Context)` (`tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners`); `@Component`; Bungee `TaskScheduler` (`net.md_5.bungee.api.scheduler`) — `schedule(Plugin, Runnable, long delay, long period, TimeUnit)`; `Plugin`; `NetworkConfig`, `BroadcastService`.
- Produces: nothing consumed downstream (terminal component).

- [ ] **Step 1: Create `task/AnnouncementScheduler.java`** (license header, then:)

```java
package tech.guilhermekaua.spigotboot.testPluginBungee.task;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.BroadcastService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules periodic announcements on the native {@link TaskScheduler} once the context is ready,
 * cycling through the messages configured in {@link NetworkConfig.AnnouncementsConfig}. Demonstrates
 * injecting the proxy scheduler bean and the {@link ContextReadyListener} extension point.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementScheduler implements ContextReadyListener {
    private final TaskScheduler scheduler;
    private final Plugin plugin;
    private final NetworkConfig config;
    private final BroadcastService broadcast;

    @Override
    public void onContextReady(@NotNull Context context) {
        NetworkConfig.AnnouncementsConfig announcements = config.getAnnouncements();
        if (!announcements.isEnabled() || announcements.getMessages().isEmpty()) {
            return;
        }

        long seconds = Math.max(1, announcements.getInterval().getSeconds());
        List<String> messages = announcements.getMessages();
        AtomicInteger cursor = new AtomicInteger();

        scheduler.schedule(plugin, () -> {
            String message = messages.get(cursor.getAndIncrement() % messages.size());
            broadcast.broadcast(message);
        }, seconds, seconds, TimeUnit.SECONDS);
    }
}
```

- [ ] **Step 2: Build the module**

Run: `.\mvnw.cmd -pl test-plugin-bungee -am package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Verify the scheduler is discovered**

Search the `DiscoveryIndex_*.java` for `AnnouncementScheduler`. Expected: it appears under `component`.

- [ ] **Step 4: Verify the shaded jar and descriptor**

- Confirm `test-plugin-bungee/target/test-plugin-bungee-3.2.1-SNAPSHOT.jar` exists.
- Confirm `bungee.yml` is at the jar root:
  ```
  jar tf test-plugin-bungee/target/test-plugin-bungee-3.2.1-SNAPSHOT.jar | findstr "bungee.yml"
  ```
  Expected: a line `bungee.yml`.

- [ ] **Step 5: Full-reactor build to confirm nothing regressed**

Run: `.\mvnw.cmd test -B`
Expected: `BUILD SUCCESS` across all modules (the new module has no tests, so it only compiles/packages; every other module's existing tests stay green).

- [ ] **Step 6: Commit**

```
git add test-plugin-bungee/src/main/java/tech/guilhermekaua/spigotboot/testPluginBungee/task
git commit -m "feat(bungee): add scheduled announcements to sample plugin"
```

---

## Manual On-Proxy Verification (post-implementation, optional)

Not part of the automated cycle — documented for a human to validate runtime behavior:

1. Drop `test-plugin-bungee-3.2.1-SNAPSHOT.jar` into a BungeeCord 1.21 proxy's `plugins/` folder with at least one backend server named `lobby` configured.
2. Start the proxy. Expected: plugin loads from the generated `bungee.yml`; `config.yml` and `messages.yml` are written with the annotated defaults; the console logs `NetworkManager enabled. Default server: lobby, ...`.
3. Join: expect the welcome message. Switch servers: expect a switch broadcast. Disconnect: expect a leave broadcast.
4. Run `/server` (usage), `/server list`, `/server send <player> <server>` (with tab-completion on both args), `/server find <player>`, `/lobby`.
5. Edit `messages.yml`, trigger a reload: expect `messages.yml reloaded.` in the console and updated message text.
6. Wait one announcement interval: expect a broadcast cycling through the configured messages.

## Self-Review Notes

- **Spec coverage:** every row of the spec's Feature Coverage Matrix maps to a task — descriptor/lifecycle (T1), config + nested + `@Range` + `Duration` (T2), `@OnConfigReload` + `@Service` + DI (T3), commands + `@Permission` + resolvers + completions (T4), auto-registered listeners (T5), `TaskScheduler` + `ContextReadyListener` (T6).
- **Type consistency:** `ConnectionService.locate` returns `Optional<ServerInfo>` and is consumed as such in `ServerCommands.find`; `defaultServer()` returns `ServerInfo` (nullable) and `LobbyCommand` null-checks it; `NetworkConfig.AnnouncementsConfig` getters (`isEnabled`/`getInterval`/`getMessages`) match `AnnouncementScheduler`'s usage.
- **Known risk (carried from spec):** `DiscoveryIndex` generation for this module is verified explicitly in Tasks 2–6 Step "verify discovered"; if a class is missing there, fix the `annotationProcessorPaths` before proceeding rather than discovering it only at runtime.
