# BungeeCord Support: core-bungee (v1) Design

Date: 2026-06-15
Status: Approved design, pending spec review
Target module: `platform-bungee/core-bungee` (new), artifactId `spigot-boot-core-bungee`

## Background

Spigot Boot is a multi-module framework whose heavy logic already lives in
platform-agnostic modules with **no Bukkit dependency**:

- `core` — DI container, context lifecycle, discovery, module system.
- `commands` — all command parsing, dispatch, binding, completion, interceptors, cooldowns.
- `config` — config model and binding.

Each platform supplies a thin **adapter** layer. Today only Spigot/Paper exists, under
`platform-spigot/` (`core-spigot`, `commands-spigot`, `config-spigot`, the annotation
processor, etc.). The adapters implement small seam interfaces defined in the generic
modules; the complex code is never duplicated.

This effort adds **BungeeCord** support. It is decomposed into per-module slices, each
with its own spec -> plan -> implementation cycle. **This spec covers only the first
slice: `core-bungee` v1**, the runtime foundation every later slice depends on. Later
slices (separate specs): the plugin descriptor generator (`bungee.yml`),
`commands-bungee`, and `config-bungee`.

## Target platform decision

**BungeeCord now, Velocity-ready.** We build for classic BungeeCord (the
`net.md_5.bungee` API; Waterfall is API-compatible and comes free). We keep the seam
interfaces in the generic modules free of BungeeCord specifics and lay out each platform
as a self-contained sibling tree, so a future `platform-velocity/` can slot in the same
way.

We do **not** build speculative Velocity abstractions now (YAGNI). Any shared
"proxy-common" logic is extracted **when Velocity actually arrives** (the second proxy
implementation reveals the true shared shape; designing it from one implementation tends
to produce the wrong seam).

## Goals

- A Bungee plugin can boot the Spigot Boot DI container and use its beans.
- The native Bungee `Plugin` is injectable, mirroring how `SpigotCoreModule` makes
  `JavaPlugin` injectable.
- `@Component` Bungee event listeners are auto-registered on context-ready and
  unregistered on shutdown, mirroring `BukkitListenerAutoRegistrar`.
- Bungee users never import a class named "Spigot" in normal usage.

## Non-goals (v1)

- **No scheduler abstraction.** See "Scheduling decision".
- No commands, no config, no `bungee.yml` generator (each a separate later slice).
- No port of any Minecraft-world utility from `core-spigot` (`ItemBuilder`, `SoundCompat`,
  `ColorUtil`, `ItemUtils`, NMS) — these are meaningless on a proxy.
- No renaming of the core `SpigotBoot` bootstrap class (framework-wide breaking change;
  out of scope — addressed locally with a facade, see §4).

## Reuse analysis (grounding)

Verified by reading the seams:

- **Reused unchanged (the complex code):** `core` (DI/context/lifecycle/discovery),
  `commands`, `config`. The bootstrap entry point `SpigotBoot` already lives in `core`
  and operates on `BootPlugin`, not on any Bukkit type
  (`core/src/main/java/.../core/SpigotBoot.java`), so the entire context lifecycle is
  platform-agnostic and reused as-is.
- **New, thin adapters (mechanical):** `BungeeBootPlugin`, `BungeeCoreModule`,
  `BungeeListenerAutoRegistrar`, `BungeeBoot` facade.
- **New, genuinely platform-specific (no Spigot equivalent to copy):** Bungee listener
  registration (different event API), and — handled by decision, not abstraction —
  scheduling.

The Spigot `PlatformScheduler` (`platform-spigot/core-spigot/.../scheduler/`) is **not**
reusable: its entire API is in terms of `org.bukkit.entity.Entity`, `org.bukkit.Location`,
regions, and ticks. A proxy has none of those. It correctly lives in `core-spigot`,
invisible to the generic modules, so not reusing it ripples nowhere.

## Scheduling decision

**v1 introduces no scheduler abstraction.** The Spigot `PlatformScheduler` exists to unify
two genuinely different backends (Folia region threads vs. legacy single main thread)
behind one API. BungeeCord has exactly one scheduler, no main thread, no region threads,
and no entities/locations/ticks for such an API to describe — so an abstraction would wrap
a single concrete implementation with no polymorphism doing any work.

Instead, `BungeeCoreModule` registers the native `TaskScheduler` for injection, so authors
use Bungee's own scheduling API directly (`@Inject TaskScheduler`). None of the generic
modules touch scheduling, so this ripples nowhere. The only thing that could justify a
shared scheduler interface is cross-proxy portability (Bungee + Velocity), and that is
better extracted from two real implementations once Velocity exists. Adding a thin
scheduler later would be purely additive (non-breaking).

## Architecture

### Module & Maven structure (Approach A: mirror platform-spigot)

```
platform-bungee/                 packaging: pom; add to root pom <modules>
  pom.xml
  core-bungee/                   artifactId: spigot-boot-core-bungee
    pom.xml
    src/main/java/...
    src/main/resources/META-INF/spigot-boot/modules/<BungeeCoreModule FQCN>
    src/test/java/...
```

`core-bungee` dependencies:

- `tech.guilhermekaua.spigot-boot:spigot-boot-core` (compile) — the generic module.
- BungeeCord API (`provided`) — exact coordinates and repository pinned at plan time
  (md-5 / Sonatype snapshots).
- The discovery-index annotation processor (same one `core` and `core-spigot` use), so
  `@Component` classes in this module are indexed.

`core-bungee` does **not** declare the `animal-sniffer` 1.8.8 Spigot API check. That
plugin is opt-in per module (declared only by Bukkit-facing modules), so simply not
declaring it is correct — this module targets the Bungee API, not the Bukkit 1.8.8 API.
No `maven-shade-plugin` execution is needed (the module uses no javassist of its own).

### Package

`tech.guilhermekaua.spigotboot.core.bungee` (mirrors `...core.spigot`).

## Components

### `BungeeBootPlugin implements BootPlugin`

A near-mechanical mirror of `SpigotBootPlugin`, wrapping
`net.md_5.bungee.api.plugin.Plugin`:

| `BootPlugin` method | Bungee `Plugin` mapping |
|---|---|
| `getName()` | `plugin.getDescription().getName()` |
| `getLogger()` | `plugin.getLogger()` |
| `getDataFolder()` | `plugin.getDataFolder()` |
| `getResource(path)` | `plugin.getResourceAsStream(path)` |
| `getMainClass()` | `ProxyUtils.getRealClass(plugin)` |
| `getClassLoader()` | `getMainClass().getClassLoader()` |
| `getNativePlugin()` | `plugin` |

`@EqualsAndHashCode(of = "plugin")`, as `SpigotBootPlugin` does for its delegate.

### `BungeeCoreModule implements Module`

`@Order(-1000)`, mirroring `SpigotCoreModule`. On `onInitialize(Context)`:

- Register the native plugin under `net.md_5.bungee.api.plugin.Plugin` and under its real
  class (`ProxyUtils.getRealClass(nativePlugin)`).
- Register `plugin.getProxy().getScheduler()` under
  `net.md_5.bungee.api.scheduler.TaskScheduler`, so authors can `@Inject TaskScheduler`
  and use Bungee's own scheduling API directly. **This is the entire v1 scheduling
  story.**

Discovered via `META-INF/spigot-boot/modules/<FQCN>`, mirroring how `core-spigot` ships
the marker for `SpigotCoreModule`.

### `BungeeListenerAutoRegistrar implements ContextReadyListener`

`@Component`, mirroring `BukkitListenerAutoRegistrar`. On `onContextReady`:

- Resolve the Bungee `Plugin` bean and all `net.md_5.bungee.api.plugin.Listener` beans.
- Register each via `plugin.getProxy().getPluginManager().registerListener(plugin, listener)`.
- Register a shutdown hook that unregisters them
  (`getPluginManager().unregisterListener(listener)`) and clears the tracking list.

### `BungeeBoot` (bootstrap facade)

A thin facade in `core-bungee` delegating to the existing core entry point:

- `BungeeBoot.initialize(BootPlugin)` -> `SpigotBoot.initialize(plugin)`
- `BungeeBoot.onDisable(BootPlugin)` -> `SpigotBoot.onDisable(plugin)`

Rationale: the core bootstrap class is (unfortunately) named `SpigotBoot`. The facade
spares Bungee users from importing a class called "Spigot" and reads consistently with
the rest of the Bungee adapter layer. We deliberately use Bungee-named classes throughout
and reuse none of the Spigot-named adapter classes.

## User-facing usage

Mirrors the Spigot pattern (the user wires it themselves; no magic base class):

```java
public class Main extends net.md_5.bungee.api.plugin.Plugin {
    private BungeeBootPlugin bootPlugin;

    @Override
    public void onEnable() {
        bootPlugin = new BungeeBootPlugin(this);
        Context ctx = BungeeBoot.initialize(bootPlugin);
    }

    @Override
    public void onDisable() {
        BungeeBoot.onDisable(bootPlugin);
    }
}
```

(The plugin still needs a `bungee.yml` descriptor to load. Until the descriptor-generator
slice exists, the test plugin hand-writes one.)

## Testing strategy

Pure Mockito (no MockBukkit equivalent exists for Bungee). Mock `Plugin`, `ProxyServer`,
`PluginManager`, `TaskScheduler`. Coverage:

- `BungeeBootPlugin` maps each of the 7 `BootPlugin` methods to the right Bungee call.
- `BungeeCoreModule` registers the plugin (under the interface and its real class) and the
  `TaskScheduler`.
- `BungeeListenerAutoRegistrar` registers all `Listener` beans on context-ready and
  unregisters them on the shutdown hook.
- A small end-to-end test boots a context with a fake `BootPlugin` and asserts a
  `@Component` listener bean was registered with the Bungee `PluginManager`.

## Open items to pin at plan time (not blockers)

1. **Exact BungeeCord API Maven coordinates + repository** (md-5 / Sonatype snapshot
   group, artifact, version; add the repo to `platform-bungee/pom.xml`).
2. **Proxied listeners.** `core-spigot` has `ProxiedListenerEventBinder` because component
   proxies (javassist subclasses) drop `@EventHandler` off overridden methods, so native
   registration finds no handlers. Bungee's event scan may hit the same problem. v1 plan
   decides: mirror an equivalent binder, or document the limitation if a proxied listener
   cannot occur in v1.
3. **Discovery-index processor wiring** for `core-bungee`'s `@Component`s (confirm the
   same processor path used by `core`/`core-spigot` is sufficient, given the processor
   currently lives under `platform-spigot`).

## Roadmap (subsequent slices, each its own spec)

1. **Descriptor generator** — emit `bungee.yml` (decision: reuse `@Plugin` with a
   Bungee-targeted writer vs. a new `@BungeePlugin` annotation/module).
2. **`commands-bungee`** — implement `CommandPlatformSupport` + `CommandSenderHandle` for
   `net.md_5.bungee.api.CommandSender`; register with Bungee's `PluginManager`.
3. **`config-bungee`** — assess how much of `config-spigot`'s YAML loader is reusable
   (SnakeYAML is available on Bungee) vs. Bungee-specific data-folder glue.
