# BungeeCord Support: commands-bungee Design

Date: 2026-06-15
Status: Approved design, pending spec review
Target module: `platform-bungee/commands-bungee` (new), artifactId `spigot-boot-commands-bungee`

## Background

Spigot Boot keeps its heavy command logic in a platform-agnostic `commands` module with
**no Bukkit dependency**: parsing, routing, binding, dispatch, completion, interceptors,
cooldowns, and the `@Configuration` that wires all the platform-neutral beans. Each
platform supplies a thin **adapter** that implements a small seam and registers commands
with the native API. Today only Spigot/Paper exists, at `platform-spigot/commands-spigot`.

This effort adds the BungeeCord adapter, `commands-bungee`. It is the second slice of the
BungeeCord roadmap, building on the already-merged `core-bungee` v1 runtime foundation
(`BungeeBootPlugin`, `BungeeCoreModule`, `BungeeListenerAutoRegistrar`, `BungeeBoot`; PR
#72). `core-bungee` already makes the native `net.md_5.bungee.api.plugin.Plugin` and its
`TaskScheduler` injectable; `commands-bungee` reuses that wiring.

This spec mirrors `commands-spigot` as closely as the BungeeCord command API allows and
calls out every place the API forces a divergence. It does **not** touch the generic
`commands` module.

## Target platform decision

Inherited from the `core-bungee` spec: **BungeeCord now, Velocity-ready.** We build for
classic BungeeCord (`net.md_5.bungee`, Waterfall is API-compatible). We lay the adapter out
as a self-contained `platform-bungee/commands-bungee` tree so a future
`platform-velocity/commands-velocity` can slot in the same way. We do **not** build
speculative proxy-shared abstractions now (YAGNI); any shared "proxy-command" shape is
extracted when Velocity actually arrives.

## Goals

- A Bungee plugin using the Spigot Boot container can declare `@RootCommand`/`@Command`
  handler beans and have them registered with BungeeCord automatically on context-ready,
  exactly as on Spigot.
- All generic command behavior (parsing, routing, argument binding, `@Permission`,
  `@Completion`, interceptors, cooldowns, error messages) works unchanged on Bungee.
- Tab completion works for command arguments.
- `@Sender ProxiedPlayer` / `@Sender CommandSender` parameters bind the native sender; a
  parsed `ProxiedPlayer` or `ServerInfo` argument resolves by name with tab completion.
- Bungee users never import a class named "Spigot" or "Bukkit" in normal usage.

## Non-goals

- **No `commands-config-bungee`.** The `CommandTextResolver` → config bridge (the analogue
  of `commands-config-spigot`) is a separate, optional, future module that needs a
  `config-bungee` to exist first. See "Roadmap". Explicitly out of scope here.
- **No edit to the generic `commands` module.** In particular, no hoisting of the shared
  pipeline beans (see "Considered alternative").
- **No collision-detection / command-overwrite policy** beyond BungeeCord's native
  last-wins behavior (see Divergence 4).
- **No runnable Bungee sample plugin or on-server end-to-end validation.** There is no
  `bungee.yml` descriptor generator yet (a separate roadmap slice); tests are unit tests.
- No `OfflinePlayer`/`World`/`Material` argument resolvers or completions — these Bukkit
  concepts do not exist on a proxy.

## Reuse analysis (grounding)

Verified by reading the seams and both modules:

- **Reused unchanged (the complex code):** the entire generic `commands` module, including
  `commands/configuration/CommandsConfiguration` (`@Configuration`) which already defines
  **all 13 platform-neutral beans**: `CommandPatternParser`, `CommandHandlerIntrospector`,
  `CommandReplacementRegistry`, `CommandTextResolverChain`, `CommandArgumentResolverRegistry`,
  `CommandCompletionRegistry`, `DefaultCommandMessages`, `CommandMessagesProvider`,
  `FixedCommandCooldownPolicy`, `CommandRouteValidator`, `CompletionResolver`,
  `CommandInterceptorChain`, `CommandInvocationExecutor`. These arrive free through the
  `spigot-boot-commands` dependency and are discovered by the annotation-processor index.
- **Reused from `core-bungee`:** the injectable native `Plugin` bean (registered by
  `BungeeCoreModule`), reached by the adapters via `context.getBean(Plugin.class)` and (for
  resolvers/completions) constructor injection.
- **New, thin adapters (mechanical mirror of `commands-spigot`):** `BungeeCommandPlatformSupport`,
  `BungeeCommandSender`, `BungeeBootCommand`, `BungeeCommandRegistrar`, `RegisteredCommandSet`,
  `BungeeCommandsContextReadyRegistrar`, `BungeeCommandsModule`, and the `@Configuration`
  `BungeeCommandsConfiguration` that re-declares the **8 platform-support-dependent beans**
  (see below) plus the Bungee glue.
- **New, genuinely platform-specific:** the `TabExecutor` bridge, the `Plugin`-injected
  `ProxiedPlayer`/`ServerInfo` resolvers, and the `onlinePlayers`/`servers` completions.
- **Deleted vs. Spigot (no analogue needed):** `BukkitCommandMapAccessor` — Bungee's
  registration API is public, so no reflection wrapper exists.

### The platform seam (exact)

A platform adapter implements two interfaces and registers a `CommandPlatformSupport` bean:

```java
public interface CommandPlatformSupport {
    CommandSenderHandle createSender(Object nativeSender);
    boolean isSenderType(Class<?> type);
}

public interface CommandSenderHandle {
    String getName();
    String getIdentity();
    boolean hasPermission(String permission);
    void sendMessage(String message);
    <T> Optional<T> unwrap(Class<T> type);
}
```

The native command object bridges to the generic dispatcher, which takes a
`CommandSenderHandle` and owns all per-route permission enforcement:

```java
public boolean dispatch(Context context, CompiledRootCommand root, CommandSenderHandle sender, String label, String[] args);
public List<String> complete(Context context, CompiledRootCommand root, CommandSenderHandle sender, String label, String[] args);
```

### The bean split (why `BungeeCommandsConfiguration` re-declares 8 beans)

`CommandsConfiguration` (generic) defines every bean that does **not** depend on
`CommandPlatformSupport`. The 8 beans that **do** depend on it (directly or transitively)
live in the *platform* `@Configuration`, so `commands-bungee` must re-declare them exactly
as `commands-spigot` does, swapping the platform support implementation:

1. `commandPlatformSupport()` → `new BungeeCommandPlatformSupport()`
2. `commandParameterRoleResolver(CommandPlatformSupport)`
3. `commandInvocationFactory(CommandParameterRoleResolver)`
4. `commandRouteFactory(CommandPatternParser, CommandReplacementRegistry, CommandTextResolverChain, CommandInvocationFactory)`
5. `commandRootCompiler(CommandHandlerIntrospector, CommandRouteFactory, CommandRouteValidator, CommandInterceptorChain)`
6. `commandParameterBinder(CommandArgumentResolverRegistry)`
7. `commandDispatcher(CommandParameterBinder, CommandInvocationExecutor, CommandMessagesProvider, CompletionResolver, CommandInterceptorChain)`
8. `commandCooldownInterceptor(CooldownManager, CommandMessagesProvider)`

Plus the Bungee glue beans: `bungeeCommandRegistrar`, `bungeeCommandsContextReadyRegistrar`,
`bungeeCommandCompletionRegistryCustomizer`, `bungeeProxiedPlayerArgumentResolver`,
`bungeeServerArgumentResolver`. The `CommandArgumentResolver<?>` beans are auto-collected
into the generic `CommandArgumentResolverRegistry`; the `CommandCompletionRegistryCustomizer`
bean is auto-collected into the generic `CommandCompletionRegistry`.

## Key decisions (BungeeCord command API)

Verified against `net.md-5:bungeecord-api:1.21-R0.3` sources.

### Permission — base permission is null; the dispatcher owns `@Permission`

`PluginManager.dispatchCommand` calls `command.hasPermission(sender)` **before** `execute`
and, on failure, sends the permission message and skips `execute` (still returning handled).
`Command.hasPermission` returns true when the permission is null/empty. So `BungeeBootCommand`
passes **null** permission to `super(name, null, aliases)`, guaranteeing Bungee always
reaches `execute`/`onTabComplete`, and the generic `CommandDispatcher` enforces per-route
`@Permission` for both dispatch (sends `messages.noPermission`) and completion (skips routes
the sender lacks). This is the same end-state as Spigot's `SpigotBootCommand.getPermission()`
returning null, reached by a different mechanism.

### Tab completion — `implements TabExecutor`

BungeeCord's base `Command` has no tab-complete method. Completion requires also implementing
the separate interface `TabExecutor { Iterable<String> onTabComplete(CommandSender, String[]); }`,
which `dispatchCommand` invokes via `instanceof TabExecutor` (and only once the command line
contains a space — the command-name token itself is completed by the proxy, identical in
effect to Bukkit). `BungeeBootCommand extends Command implements TabExecutor` and delegates
`onTabComplete` to `dispatcher.complete(...)`, whose `List<String>` return is a valid
`Iterable<String>`.

### Threading — synchronous, no thread-hop

BungeeCord has no main thread; `execute` runs synchronously on the caller (Netty connection)
thread. Mirroring Spigot, `commands-bungee` dispatches synchronously with no thread switch.
Authors needing async work use the `TaskScheduler` already injected by `core-bungee`
(`@Inject TaskScheduler` → `runAsync`). No threading machinery in this module.

## Architecture

### Module & Maven structure (mirror `commands-spigot`)

```
platform-bungee/                    packaging: pom (already exists)
  pom.xml                           add <module>commands-bungee</module>  ← shared edit
  core-bungee/                      (exists, PR #72)
  commands-bungee/                  artifactId: spigot-boot-commands-bungee  (new)
    pom.xml
    src/main/java/...
    src/main/resources/META-INF/spigot-boot/modules/<BungeeCommandsModule FQCN>
    src/test/java/...
```

`commands-bungee` dependencies:

- `net.md-5:bungeecord-api` (`provided`) — version inherited from `platform-bungee/pom.xml`
  dependency-management (`1.21-R0.3`).
- `tech.guilhermekaua.spigot-boot:spigot-boot-commands` (compile) — the generic module.
- `tech.guilhermekaua.spigot-boot:spigot-boot-core-bungee` (compile) — supplies the
  injectable native `Plugin` and the `BungeeCoreModule`.
- `tech.guilhermekaua.spigot-boot:spigot-boot-annotation-processor-spigot`
  (`provided`/`optional`, and in `annotationProcessorPaths` alongside Lombok) — indexes this
  module's `@Configuration`/`@Component` and `Module` beans.
- Tests: JUnit 5 + Mockito inherited from the root pom. **No MockBukkit.**

Compiler: `<source>1.8</source><target>1.8</target>`, `testSource`/`testTarget` 17,
`-parameters`. **No `maven-shade-plugin`, no `animal-sniffer-maven-plugin`** (this module
targets the Bungee API, not the Bukkit 1.8.8 API; and it ships no javassist of its own).

### Package

`tech.guilhermekaua.spigotboot.commands.bungee` (mirrors `...commands.spigot`), with a
`bungee/completion/` and `bungee/resolve/` subpackage as on Spigot, and a
`bungee/configuration/` subpackage for `BungeeCommandsConfiguration`.

### javassist / shading determination

`commands-bungee` **creates no javassist proxies and references no javassist API** — it does
not even use `ProxyUtils` (unlike `BungeeListenerAutoRegistrar`, which warns on proxied
listeners). Command dispatch is framework-driven (the dispatcher invokes handler methods
through the generic invocation plan), so the proxied-*listener* caveat does not apply, and
`commands-spigot`'s command registrar likewise ignores proxies. Per the CLAUDE.md
"Shading & javassist" rules this module falls under *neither* javassist case: it neither
creates proxies (no relocation execution) nor inspects them (no `ProxyUtils` reference).
**Conclusion: no `maven-shade-plugin`, no relocation, no javassist dependency at any scope.**

## Components

### `BungeeCommandPlatformSupport implements CommandPlatformSupport`

Mirror of `BukkitCommandPlatformSupport`. `createSender` requires a
`net.md_5.bungee.api.CommandSender` (throws `IllegalArgumentException` otherwise) and wraps
it in `BungeeCommandSender`. `isSenderType(type)` = `type != null && CommandSender.class.isAssignableFrom(type)`
(true for `CommandSender` and `ProxiedPlayer`).

### `BungeeCommandSender implements CommandSenderHandle`

Mirror of `BukkitCommandSender`, wrapping a `CommandSender`:

| method | mapping |
|---|---|
| `getName()` | `sender.getName()` |
| `getIdentity()` | `((ProxiedPlayer) sender).getUniqueId().toString()` if `sender instanceof ProxiedPlayer`, else `sender.getClass().getName() + ":" + getName()` (console identity; there is no console sender *type* in the Bungee API to test against) |
| `hasPermission(p)` | `sender.hasPermission(p)` |
| `sendMessage(m)` | `sender.sendMessage(m)` — Bungee's String overload (deprecated but functional; renders legacy color codes). The `CommandSenderHandle` contract is String-based; mapping framework messages to `TextComponent` is a deliberate non-goal |
| `unwrap(type)` | `type.isInstance(sender) ? Optional.of(type.cast(sender)) : Optional.empty()` |

### `BungeeBootCommand extends net.md_5.bungee.api.plugin.Command implements TabExecutor`

```java
public BungeeBootCommand(Context context, CompiledRootCommand rootCommand,
                         CommandDispatcher dispatcher, CommandPlatformSupport platformSupport) {
    super(rootCommand.getAliases().getPrimary(), null,        // null permission — dispatcher owns @Permission
          rootCommand.getAliases().getAliases().toArray(new String[0]));
    ...
}

@Override public void execute(CommandSender sender, String[] args) {            // Bungee execute() is void
    dispatcher.dispatch(context, rootCommand, platformSupport.createSender(sender), getName(), args);
}

@Override public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
    List<String> completions = dispatcher.complete(context, rootCommand,
            platformSupport.createSender(sender), getName(), args);
    return completions == null ? Collections.emptyList() : completions;
}
```

Notes: Bungee's `execute` is `void`, so the dispatcher's boolean return is ignored. Bungee
passes no label/alias, so `getName()` (the primary) is used as the dispatcher `label` (used
only to build the usage/echo string). `getAliases()` is a `List<String>` and is converted to
the `String...` the Bungee `Command` constructor wants.

### `BungeeCommandRegistrar`

Constructor `(CommandDispatcher dispatcher, CommandPlatformSupport platformSupport)` — **no
`BukkitCommandMapAccessor` analogue**. `register(Context, List<CompiledRootCommand>)`:

```java
Plugin plugin = context.getBean(Plugin.class);
PluginManager pm = plugin.getProxy().getPluginManager();
List<BungeeBootCommand> commands = new ArrayList<>();
for (CompiledRootCommand root : roots) {
    BungeeBootCommand command = new BungeeBootCommand(context, root, dispatcher, platformSupport);
    pm.registerCommand(plugin, command);                      // public API, no reflection
    commands.add(command);
}
return new RegisteredCommandSet(commands);
```

`unregister(RegisteredCommandSet)` resolves the plugin again and calls
`pm.unregisterCommand(command)` for each tracked command (surgical — only our commands, not
`unregisterCommands(plugin)`), mirroring Spigot's `RegisteredCommandSet` tracking.

### `RegisteredCommandSet`

Immutable snapshot `List<BungeeBootCommand>`, mirror of the Spigot value object, carried by
the shutdown hook for unregistration.

### `BungeeCommandsContextReadyRegistrar implements ContextReadyListener, Ordered`

`getOrder()` returns `1000`. `onContextReady` is the exact mirror of
`CommandsContextReadyRegistrar`:

```java
replacementRegistry.register("plugin.name", context.getPlugin().getName());
replacementRegistry.register("plugin", context.getPlugin().getName());
List<CompiledRootCommand> roots = commandRootCompiler.compile(context);
if (roots.isEmpty()) return;
RegisteredCommandSet registered = bungeeCommandRegistrar.register(context, roots);
context.registerShutdownHook(() -> bungeeCommandRegistrar.unregister(registered));
```

### `BungeeProxiedPlayerArgumentResolver implements CommandArgumentResolver<ProxiedPlayer>, Ordered`

Constructor injects `Plugin` (reaches the proxy via `plugin.getProxy()` — cleaner DI than
Spigot's static `Bukkit.*`, and plain-Mockito-testable). `getOrder()` = `1000`. `supports` =
`ProxiedPlayer.class.equals(parameter.getValueType())`. `resolve` = `plugin.getProxy().getPlayer(input)`,
throwing `IllegalArgumentException("Player not found: " + input)` when null.
`defaultCompletionProvider()` returns online player names matching the input prefix.

### `BungeeServerArgumentResolver implements CommandArgumentResolver<ServerInfo>, Ordered`

Constructor injects `Plugin`. `supports` = `ServerInfo.class.equals(parameter.getValueType())`.
`resolve` = `plugin.getProxy().getServerInfo(input)`, throwing
`IllegalArgumentException("Server not found: " + input)` when null. `defaultCompletionProvider()`
returns configured server names matching the input prefix
(`plugin.getProxy().getServers().keySet()`).

### `BungeeCommandCompletionRegistryCustomizer implements CommandCompletionRegistryCustomizer`

Constructor injects `Plugin`. Registers two named completions (referenced from handlers via
`@Completion("onlinePlayers")` / `@Completion("servers")`, keeping command code portable
across platforms):

- `onlinePlayers` → `plugin.getProxy().getPlayers()` names, prefix-filtered.
- `servers` → `plugin.getProxy().getServers().keySet()`, prefix-filtered.

(No `worlds`/`materials`/`offlinePlayers` — no proxy analogue.)

### `BungeeCommandsModule implements Module` + marker

Near-no-op (logs `getLogger().fine("Initializing Bungee commands module.")`), mirroring
`SpigotCommandsModule`. Discovered via the empty marker resource
`META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsModule`.

### `BungeeCommandsConfiguration` (`@Configuration`)

Re-declares the 8 platform-support-dependent beans (see "The bean split") with
`new BungeeCommandPlatformSupport()` as the platform support, plus `bungeeCommandRegistrar`,
`bungeeCommandsContextReadyRegistrar`, `bungeeCommandCompletionRegistryCustomizer`,
`bungeeProxiedPlayerArgumentResolver(Plugin)`, `bungeeServerArgumentResolver(Plugin)`. The
shared collaborators (`CommandPatternParser`, `CommandReplacementRegistry`,
`CommandTextResolverChain`, `CommandHandlerIntrospector`, `CommandRouteValidator`,
`CommandInterceptorChain`, `CommandArgumentResolverRegistry`, `CommandInvocationExecutor`,
`CommandMessagesProvider`, `CompletionResolver`, `CooldownManager`) are injected from the
generic config / core.

## User-facing usage

```java
public final class ExampleCommands {
    @RootCommand(value = "server", aliases = "srv")
    public static class ServerCommand {
        @DefaultCommand
        public void info(@Sender CommandSender sender) {
            sender.sendMessage("Usage: /server send <player> <server>");
        }

        @Command("send <target> <server>")
        @Permission("example.server.send")
        public void send(@Sender CommandSender sender,
                         @Completion("onlinePlayers") ProxiedPlayer target,
                         @Completion("servers") ServerInfo server) {
            target.connect(server);
            sender.sendMessage(target.getName() + " -> " + server.getName());
        }
    }
}
```

A `@Component` (or `@CommandHandler`) bean holding such a `@RootCommand` is compiled and
registered automatically when the context becomes ready — the author writes no registration
code. (The plugin still needs a `bungee.yml` descriptor to load until the descriptor
generator slice exists; the eventual test plugin hand-writes one.)

## Considered alternative (deliberately deferred)

Hoist the 8 platform-support-dependent pipeline beans (role resolver → … → cooldown
interceptor) into the generic `CommandsConfiguration`, injecting `CommandPlatformSupport` as
a parameter, so neither platform config duplicates them. This is attractive now that the
second platform reveals the shared shape, but it **edits the shared `commands` core**, which
(a) creates merge-conflict surface with the concurrent `config-bungee` work, and (b) risks
regressing `commands-spigot`. It is out of scope for this isolated slice and is recorded as a
recommended follow-up refactor, to be done deliberately and reviewably once `commands-bungee`
is merged and green — matching `core-bungee`'s principle of extracting shared structure only
after the second implementation exists.

## Testing strategy

Pure Mockito (no MockBukkit/MockBungee). Mock `Plugin`, `ProxyServer`, `PluginManager`,
`CommandSender`, `ProxiedPlayer`, `ServerInfo`, `Context`, `CommandDispatcher`. Coverage:

- `BungeeCommandPlatformSupportTest` — `createSender` wraps a `CommandSender` and rejects a
  non-sender; `isSenderType` true for `CommandSender`/`ProxiedPlayer`, false otherwise.
- `BungeeCommandSenderTest` — `getIdentity` is the UUID for a `ProxiedPlayer` and the
  console identity for a plain `CommandSender`; `getName`/`hasPermission`/`sendMessage`
  delegate; `unwrap` returns `ProxiedPlayer`/`CommandSender` and empty for mismatches.
- `BungeeBootCommandTest` — `getPermission()` is null; `execute` delegates to
  `dispatcher.dispatch` with the platform-created sender; `onTabComplete` delegates to
  `dispatcher.complete` and is null-safe.
- `BungeeCommandRegistrarTest` — `register` calls `pluginManager.registerCommand(plugin, cmd)`
  for each root and returns a `RegisteredCommandSet`; `unregister` calls
  `unregisterCommand` for each (mock `Plugin → ProxyServer → PluginManager`, the
  `BungeeListenerAutoRegistrarTest` pattern).
- `BungeeCommandsContextReadyRegistrarTest` — compiles via a mocked `CommandRootCompiler`,
  registers, and the captured shutdown hook unregisters; empty roots register nothing.
- `BungeeProxiedPlayerArgumentResolverTest` / `BungeeServerArgumentResolverTest` — resolve
  by name, throw when absent, and complete by prefix (mock `Plugin → ProxyServer`).
- `BungeeCommandCompletionRegistryCustomizerTest` — registers `onlinePlayers` and `servers`
  and the providers prefix-filter.
- `BungeeCommandsModuleDiscoveryTest` — the marker resource makes `BungeeCommandsModule`
  discoverable via `ModuleDiscovery` (mirror of `BungeeModuleDiscoveryTest`).
- `BungeeBootCommandEndToEndTest` — a real `@RootCommand` handler with a `@Command` method
  taking `@Sender CommandSender`, a `ProxiedPlayer`, and a `ServerInfo`, compiled through
  `CommandRootCompiler` and executed through a fully-wired `CommandDispatcher`; asserts the
  handler received the resolved player/server and that tab completion surfaces player and
  server names (the `SpigotBootCommandTest` analogue, the strongest single proof).

## Open items to pin at plan time (not blockers)

1. **`ProxyServer.getInstance()` vs. injected `Plugin`.** Decided: inject `Plugin`
   (verified `Plugin.getProxy()` reaches `getPlayers()/getServers()/getServerInfo()`),
   avoiding the static singleton and keeping resolvers plain-Mockito-testable. Pin the exact
   `@Bean` parameter wiring in the plan.
2. **Console sender identity string.** The Bungee API jar contains no console sender *type*
   (it lives in the proxy runtime), so `getIdentity` falls back to `class:name` for
   non-`ProxiedPlayer` senders. Confirm this matches `BukkitCommandSender`'s non-entity
   branch in the test.
3. **Shared `platform-bungee/pom.xml` edit.** Adding `<module>commands-bungee</module>` is
   also performed by the sibling `config-bungee` effort; the plan notes this as the expected
   trivial merge conflict.

## Roadmap (subsequent slices, each its own spec)

1. **`bungee.yml` descriptor generator** — needed before any runnable Bungee sample/test
   plugin can load.
2. **`config-bungee`** — the YAML config module (SnakeYAML is available on Bungee).
3. **`commands-config-bungee`** — the `CommandTextResolver` → config bridge (mirror of
   `commands-config-spigot`), depending on `commands-bungee` + `config-bungee`. Lets command
   text/messages come from config via `${config.key}` tokens. Out of scope here.
4. **Hoist shared pipeline beans** into the generic `CommandsConfiguration` (the deferred
   refactor above).
