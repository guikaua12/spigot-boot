# BungeeCord Sample Plugin (`test-plugin-bungee`) — Design

**Date:** 2026-06-17
**Branch:** `feat/bungee-shared-config-integration`
**Status:** Approved, pending implementation plan

## Goal

Add a runnable BungeeCord sample plugin, `test-plugin-bungee`, that exercises the full
proxy-side surface of Spigot Boot: the `@BungeePlugin` descriptor generator, the shared
config module, the Bungee commands module (with proxy-specific argument resolvers and
completions), auto-registered Bungee listeners, services with constructor/inter-bean DI,
and native `TaskScheduler` injection.

This is the previously-deferred roadmap item from the BungeeCord specs. Every prior slice
named it as a follow-up ("a runnable Bungee sample/test plugin and on-server end-to-end
validation"), explicitly blocked on the `bungee.yml` descriptor generator. That generator
(`annotation-processor-bungee`) is now merged and reactor-green, so the blocker is cleared.

It is the proxy-idiomatic parallel to the existing Spigot `test-plugin/`, minus the
Bukkit-only concerns that have no meaning on a proxy.

## Non-Goals

- **No inventory UI** — `inventory-api` is Bukkit-world only.
- **No data-jdbc / persistence demo** — out of scope for a proxy showcase; keeps the sample
  focused on proxy concerns.
- **No Bukkit serializers** (`Material`, `Sound`, `World`, `Location`) — they do not exist on
  a proxy. Config fields use only platform-neutral types (`String`, `int`, `boolean`,
  `List`, nested POJOs, and `Duration`).
- **No automated tests** — this is a *pure runnable demo* (deliberate choice). Runtime
  behavior is validated manually on a real proxy; the build validates compilation, descriptor
  generation, discovery-index generation, and shading. Adding the module to the reactor means
  `mvn test -B` will build it (there are simply no test classes).
- **No changes to `test-plugin/`** (Spigot-only, stays so) or to any framework module. This
  slice only *adds* a new consumer module plus its single line in the root reactor.

## Theme

A small but realistic **proxy network manager**. Every demonstrated feature has a natural
reason to exist instead of being a disconnected catalog: the plugin welcomes joining players,
broadcasts leaves, lets staff move players between backend servers, gives players a `/lobby`
shortcut, and periodically announces messages from config.

## Module & Build

### Location & coordinates

- New **top-level** Maven module `test-plugin-bungee/`, paralleling the top-level
  `test-plugin/`.
- Added to the root `pom.xml` `<modules>` list **after** `test-plugin`. Maven's reactor sorts
  by the dependency graph, so position is not load-bearing, but appending keeps the consumer
  modules last and readable.
- `parent`: `tech.guilhermekaua.spigot-boot:spigot-boot:3.2.1-SNAPSHOT`
- `artifactId`: `test-plugin-bungee`
- Base package: `tech.guilhermekaua.spigotboot.testPluginBungee` (mirrors the existing
  `testPlugin` camelCase package convention).

### Dependencies

| Artifact | Scope | Why |
|----------|-------|-----|
| `tech.guilhermekaua.spigot-boot:spigot-boot-core-bungee` | compile | `BungeeBoot`, `BungeeBootPlugin`, core DI, `BungeeCoreModule` (registers `Plugin` + `TaskScheduler`), `BungeeListenerAutoRegistrar` |
| `tech.guilhermekaua.spigot-boot:spigot-boot-commands-bungee` | compile | command dispatch + `ProxiedPlayer`/`ServerInfo` resolvers + `onlinePlayers`/`servers` completions |
| `tech.guilhermekaua.spigot-boot:spigot-boot-config` | compile | shared config module (self-registers `ConfigModule` via `META-INF/spigot-boot/modules/...`); includes `DurationSerializer`. **Not** `config-spigot` — that is reduced to Bukkit serializers only |
| `tech.guilhermekaua.spigot-boot:spigot-boot-annotation-processor-bungee` | compile | supplies the source-retained `@BungeePlugin` annotation symbol referenced by `Main` |
| `net.md-5:bungeecord-api:1.21-R0.3` | provided | the proxy runtime supplies this |

Version of every in-repo artifact is `${project.version}`.

### Annotation processors (`maven-compiler-plugin` → `annotationProcessorPaths`)

1. `org.projectlombok:lombok:1.18.36` — `@Data`/`@Getter` on config/services.
2. `tech.guilhermekaua.spigot-boot:spigot-boot-annotation-processor-spigot` — emits the
   `DiscoveryIndex` so this plugin's own `@Component`/`@Config`/`@CommandHandler`/`@Service`
   classes are discoverable at runtime (the discovery mechanism is platform-neutral; the
   "spigot" processor is the generic index generator the Bungee modules themselves consume).
3. `tech.guilhermekaua.spigot-boot:spigot-boot-annotation-processor-bungee` — emits
   `bungee.yml` from `@BungeePlugin`.

### Build plugins (mirror `test-plugin/`)

- `maven-compiler-plugin` 3.11.0: `source`/`target` `1.8`, `-parameters`. No
  `testSource`/`testTarget` (no tests).
- `maven-shade-plugin` 3.5.1 with `<minimizeJar>true</minimizeJar>`, bound to `package`.
  No extra relocations: the relocated javassist already lives (single copy) inside
  `spigot-boot-core`, exactly as for `test-plugin`.
- `maven-jar-plugin` writing to `${project.build.directory}`.
- Properties: `maven.deploy.skip=true`, `skipPublishing=true`,
  `project.build.sourceEncoding=UTF-8`.
- `repositories`: PaperMC public repo (for transitive resolution parity with `test-plugin`).

### Generated / runtime artifacts (no hand-written resources)

- `bungee.yml` — generated from `@BungeePlugin`.
- `config.yml`, `messages.yml` — generated from the `@Config` class defaults on first run
  (`generateDefaults` defaults to `true`).
- No `src/main/resources/` is committed.

## File Inventory

All classes carry the MIT license header used across the repo. Normal comments start
lowercase; public/protected APIs get Javadoc per house style. Each class below maps to a
distinct framework feature.

### `Main.java`
- `@BungeePlugin(name = "NetworkManager", version = "1.0.0", author = "Approximations",
  description = "...")` on a class extending `net.md_5.bungee.api.plugin.Plugin`.
- `onEnable()`: `bootPlugin = new BungeeBootPlugin(this); Context ctx =
  BungeeBoot.initialize(bootPlugin);` then read one bean (e.g. `NetworkConfig`) and
  `getLogger().info(...)` a field to prove DI is live — mirrors `test-plugin`'s `Main`.
- `onDisable()`: `BungeeBoot.onDisable(bootPlugin);`
- `@Getter private BungeeBootPlugin bootPlugin;`

### `config/NetworkConfig.java`
- `@Config("config.yml")`, Lombok `@Data`.
- `@Comment` on each field. Fields: `String defaultServer = "lobby";`
  `@Range(min = 1, max = 1000) int maxNetworkPlayers = 500;` a nested
  `AnnouncementsConfig announcements = new AnnouncementsConfig();`
- Nested `AnnouncementsConfig` (`@Data`, plain POJO — mirrors test-plugin's nested
  `DatabaseConfig`): `boolean enabled = true;` `Duration interval = Duration.ofMinutes(5);`
  `List<String> messages = ...` (a couple of defaults).

### `config/MessagesConfig.java`
- `@Config("messages.yml")`, `@Data`, `@Comment`s.
- `String prefix = "&7[&bNetwork&7] ";`
- `String welcome`, `String disconnect`, `String serverSwitch` (templates with placeholders
  like `%player%`, `%server%`).
- A `Duration` field is already present via `NetworkConfig.AnnouncementsConfig.interval`, which
  is the canonical `DurationSerializer` demonstration; `MessagesConfig` stays string-only to
  keep responsibilities clean.

### `service/BroadcastService.java`
- `@Service`. Constructor-injects `Plugin` (reaches the proxy via `plugin.getProxy()`) and
  `MessagesConfig`. `ProxyServer` itself is **not** an injectable bean — `BungeeCoreModule`
  registers only `Plugin` and `TaskScheduler`, so the codebase idiom is to inject `Plugin` and
  call `getProxy()` (the same pattern the Bungee argument resolvers use).
- `broadcast(String message)`: prefixes with `MessagesConfig.prefix`, sends to all proxied
  players (color-translated).
- `@OnConfigReload(MessagesConfig.class) void onMessagesReload()` — logs that messages
  reloaded; demonstrates live-reload firing.

### `service/ConnectionService.java`
- `@Service`. Constructor-injects `Plugin` (for `getProxy()`) and `NetworkConfig`.
- `boolean send(ProxiedPlayer player, ServerInfo server)` — `player.connect(server)`.
- `Optional<ServerInfo> locate(String playerName)` — looks up a player and returns the server
  they are on (used by `/server find`).
- `ServerInfo defaultServer()` — resolves `NetworkConfig.defaultServer` to a `ServerInfo`
  (used by `/lobby`).

### `command/ServerCommands.java`
- `@CommandHandler @RootCommand(value = "server", aliases = "srv")`.
- `@DefaultCommand info(@Sender CommandSender sender)` — prints usage.
- `@Command("send <target> <server>") @Permission("network.server.send")
  void send(@Sender CommandSender sender, @Completion("onlinePlayers") ProxiedPlayer target,
  @Completion("servers") ServerInfo server)` — delegates to `ConnectionService.send`, replies
  using `MessagesConfig`.
- `@Command("find <target>") void find(@Sender CommandSender sender,
  @Completion("onlinePlayers") ProxiedPlayer target)` — uses `ConnectionService.locate`.
- `@Command("list") void list(@Sender CommandSender sender)` — lists configured servers.
- Constructor-injects `ConnectionService` + `MessagesConfig`.

### `command/LobbyCommand.java`
- `@CommandHandler @RootCommand("lobby")`.
- `@DefaultCommand void lobby(@Sender ProxiedPlayer sender)` — sends the caller to
  `ConnectionService.defaultServer()` (driven by `NetworkConfig.defaultServer`).
- Demonstrates a config-driven command + injecting only the services it needs.

### `listener/ConnectionListener.java`
- `@Component implements net.md_5.bungee.api.plugin.Listener`.
- `@EventHandler void onLogin(PostLoginEvent event)` — sends the welcome message from
  `MessagesConfig`.
- `@EventHandler void onDisconnect(PlayerDisconnectEvent event)` — broadcasts a leave message
  via `BroadcastService`.
- Constructor-injects `BroadcastService` + `MessagesConfig`. Auto-registered by
  `BungeeListenerAutoRegistrar` on context-ready.

### `listener/ServerSwitchListener.java`
- `@Component implements Listener`.
- `@EventHandler void onSwitch(ServerSwitchEvent event)` — announces a player's server change
  via `BroadcastService`.

### `task/AnnouncementScheduler.java`
- `@Component implements ContextReadyListener` (the public context-ready hook).
- Constructor-injects the native `TaskScheduler` (registered by `BungeeCoreModule`), `Plugin`,
  `NetworkConfig`, and `BroadcastService`.
- On context-ready: if `announcements.enabled`, schedules a repeating task at
  `announcements.interval` that cycles through `announcements.messages`, broadcasting each.
- The one element beyond `test-plugin`'s surface; included specifically to showcase
  `TaskScheduler` DI, which is proxy-idiomatic.

## Feature Coverage Matrix

| Framework capability | Demonstrated by |
|----------------------|-----------------|
| `@BungeePlugin` → generated `bungee.yml` | `Main` |
| Bootstrap lifecycle (`BungeeBoot.initialize`/`onDisable`, `BungeeBootPlugin`) | `Main` |
| `@Config` + `@Comment` + `@Range` + nested config | `NetworkConfig` |
| `Duration` serialization (shared `DurationSerializer`) | `NetworkConfig.AnnouncementsConfig` |
| Multiple `@Config` files | `NetworkConfig`, `MessagesConfig` |
| `@OnConfigReload` live-reload | `BroadcastService` |
| `@Service` + constructor DI + inter-bean DI | `BroadcastService`, `ConnectionService` |
| `@CommandHandler`/`@RootCommand`/`@Command`/`@DefaultCommand` | `ServerCommands`, `LobbyCommand` |
| `@Permission` enforcement | `ServerCommands.send` |
| `ProxiedPlayer` + `ServerInfo` argument resolvers | `ServerCommands` |
| `@Completion("onlinePlayers")` / `@Completion("servers")` | `ServerCommands` |
| `@Sender` (`CommandSender` / `ProxiedPlayer`) | all commands |
| Auto-registered Bungee `Listener` `@Component` beans | `ConnectionListener`, `ServerSwitchListener` |
| Native `TaskScheduler` injection | `AnnouncementScheduler` |
| `ContextReadyListener` extension point | `AnnouncementScheduler` |

## Verification

1. **Build:** `mvnw.cmd -pl test-plugin-bungee -am package` (JDK 21 per the build constraint).
   Asserts: compiles against the real `bungeecord-api`; `bungee.yml` + `DiscoveryIndex` are
   generated; shading succeeds. The reactor build (`mvn test -B`) stays green.
2. **Manual on-proxy (documented, not automated):** drop the shaded jar onto a BungeeCord 1.21
   proxy with at least one backend server. Expected: plugin loads from generated `bungee.yml`;
   `config.yml`/`messages.yml` are written with the annotated defaults; `/server`, `/lobby`
   commands register with working tab-completion; join/leave/switch events fire messages;
   editing `messages.yml` + a reload triggers `@OnConfigReload`; announcements broadcast on the
   configured interval.

## Open Questions / Risks

- **Discovery-index processor requirement:** the plugin's own components must appear in a
  generated `DiscoveryIndex`. The implementation must confirm the `annotation-processor-spigot`
  path produces the index for this module (the same processor the Bungee framework modules
  use). If the build shows components are not discovered, that is the first thing to check.
- **`@BungeePlugin` symbol availability:** `@BungeePlugin` is `SOURCE`-retained and lives in
  `annotation-processor-bungee`; it must be on the compile classpath (compile-scope dependency),
  not only on `annotationProcessorPaths`.
- **Java level:** `source`/`target` `1.8` matches `test-plugin` and the framework's bytecode
  floor; `bungeecord-api` 1.21 is consumed at provided scope and is Java 8-compatible.
