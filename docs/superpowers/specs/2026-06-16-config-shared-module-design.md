# Config Dedup: Extract the shared config implementation into `config/` Design

Date: 2026-06-16
Status: Approved design, pending spec review
Target modules: `config/` (gains the impl), `platform-spigot/config-spigot` (shrinks), `platform-bungee/config-bungee` (deleted)

## Background

The config framework is split across a platform-neutral core (`config/`, artifactId
`spigot-boot-config`) and per-platform adapters. When `config-bungee` was added as a
full-parity port of `config-spigot`, it duplicated ~20 platform-neutral classes verbatim
(`YamlConfigNode`, `YamlConfigLoader`, `ConfigProxy`, the manager, registry, binding
coordinator, folder impls, injectors, reference engine, reload subsystem, `DurationSerializer`).
The only genuinely platform-specific code is the Bukkit serializers
(`Material`/`Sound`/`World`/`Location`).

The duplication exists only because, when Spigot was the sole platform, the concrete YAML
implementation was placed in `config-spigot` rather than in `config/`. Now that a second
platform (BungeeCord) exists, the shared shape is known, and the implementation can be
hoisted into `config/` so neither platform module duplicates it.

This is the realization of Roadmap item #1 from the config-bungee spec
(`docs/superpowers/specs/2026-06-15-bungeecord-config-design.md`).

## Key decision: maximal extraction onto `BootPlugin`

**The entire config implementation becomes platform-neutral and lives in `config/`.** This is
possible because the platform seam is already abstracted: `core`'s `BootPlugin` interface
exposes `getDataFolder()`, `getResource(String)`, `getClassLoader()`, `getMainClass()`,
`getLogger()`, and the platform `BootPlugin` adapters already map `getResource` onto Bukkit's
`getResource` vs BungeeCord's `getResourceAsStream`. So a manager written against `BootPlugin`
has **no per-platform seam at all**.

`config/` is already the natural home: it ships concrete defaults today (`DefaultBinder`,
`DefaultConfigRef`, `DefaultTypeSerializerRegistry`, the scalar serializers, even a stray
`config.spigot.node.SnapshotConfigNode`). The shared impl follows the same `Default*`
naming.

Consequence: **after extraction the only platform-specific config code is the Bukkit
serializers**, so `config-spigot` shrinks to those, and **`config-bungee` is deleted entirely**
(Bungee has zero config-specific code; Bungee plugins depend on `config/` + `core-bungee`).

### Move vs. delete

- **MOVE** the neutral classes **from `config-spigot`** (the source of truth — original, mature,
  and its `plugin.getResource(...)` calls map 1:1 onto `BootPlugin.getResource(...)`) into
  `config/`, switching their plugin type to `BootPlugin`.
- **DELETE** `config-bungee`'s copies outright — they are pure duplicates of what now lives in
  `config/`, and nothing Bungee-specific remains. The whole module is removed.

## Goals

- Zero duplication of platform-neutral config code: one implementation in `config/`.
- `config-spigot` reduced to its Bukkit serializers + a thin serializer-registration
  `@Configuration`.
- `config-bungee` removed; Bungee consumers use `config/` directly.
- The packaged `config/` jar is relocation-safe (javassist relocated), proven by the
  failsafe IT moved into `config/` — closing a gap (config-spigot never had this guard).
- No behavior change for end users: same annotations, same `ConfigManager`/`ConfigRef`
  surface, same YAML semantics.

## Non-goals

- **No `core` changes.** `context.getPlugin()` already returns `BootPlugin`; nothing in `core`
  needs to change.
- **No new config features or semantic changes.** This is a relocation/dedup refactor.
- **No keeping an empty `config-bungee` module "for symmetry."** It is deleted; re-add it only
  when a genuinely Bungee-specific serializer is needed (YAGNI).
- **No renaming of the user-facing API** (annotations, `ConfigManager`, `ConfigRef`, folder/
  reference interfaces — all already in `config/` and untouched).

## Architecture

### `config/` — the shared module (gains the implementation)

Moved in from `config-spigot` (package `config.spigot.*` → `config.*`), retargeted onto
`BootPlugin`:

| From `config-spigot` | Into `config/` as | Notes |
|---|---|---|
| `SpigotConfigManager` | `DefaultConfigManager` | ctor takes `BootPlugin`; `plugin.getResource(...)` stays (matches `BootPlugin.getResource`) |
| `ConfigBindingCoordinator`, `ConfigEntryAccessor` | same names | pkg rename only |
| `loader/YamlConfigLoader`, `node/YamlConfigNode`, `proxy/ConfigProxy` | same names | pkg rename only |
| `registry/ConfigRegistry` | same name | uses `context.getPlugin()` already |
| `folder/{FolderConfigEntry,DefaultFolderConfigRef,DefaultFolderConfigEditor,DefaultFolderConfigSnapshot}` | same names | `FolderConfigEntry` ctor takes `BootPlugin` |
| `injector/{ConfigRefInjector,FolderConfigInjector,ConfigValueInjector,ConfigValueResolver}` | same names | reference `DefaultConfigManager` |
| `reference/{ConfigReferenceManager,ReferenceResolvingPreprocessor}` | same names | pkg rename only |
| `reference/SpigotConfigReferenceLookup` | `DefaultConfigReferenceLookup` | references `DefaultConfigManager` |
| `reload/{OnConfigReloadProcessor,OnConfigReloadBinder,OnConfigReloadInvoker}` | same names | pkg rename only |
| `serialization/DurationSerializer` | same name | **also registered in the default registry** (see below) |
| `SpigotConfigModule` | `ConfigModule` | `@Order(-500)`; + a single `META-INF/spigot-boot/modules/…ConfigModule` marker shipped by `config/` |
| `configuration/ConfigConfiguration` | `configuration/ConfigConfiguration` | **modified**: builds the manager from `context.getPlugin()`; registers injectors + reload post-processor; collects serializer customizers; **no** platform-serializer registration |

`DurationSerializer` is added to `TypeSerializerRegistry.defaults()` (alongside the existing
primitive + UUID serializers), so every platform gets it without a per-platform registrar.

`config/` build changes: add `javassist` (`provided`) and `snakeyaml` (`provided`); add the
`maven-shade-plugin` `relocate-javassist-references` execution (artifactSet =
`tech.guilhermekaua.spigot-boot:spigot-boot-config`); add `maven-failsafe-plugin` for the
relocation IT; ensure the compiler's `annotationProcessorPaths` include Lombok and
`spigot-boot-annotation-processor-spigot` (so the new `@Component`/`@Configuration` are
discovery-indexed). All moved tests come along (Plugin mocks → `BootPlugin` mocks), plus the
relocation IT.

### `config-spigot` — shrinks to the Bukkit serializers

Keeps: `serialization/{MaterialSerializer,SoundSerializer,WorldSerializer,LocationSerializer}`,
`serialization/BukkitSerializers` (now registers only the four Bukkit serializers — Duration is
a `config/` default), and a slim `@Configuration` exposing a `TypeSerializerRegistryCustomizer`
bean that calls `BukkitSerializers.registerAll`. Keeps the Bukkit serializer tests
(`SoundSerializerTest`, `MaterialSerializerBindingTest`). Depends on `config/`, `core-spigot`,
`paper-api` (provided). **Loses** the `javassist` dependency and the shade execution (now in
`config/`). Everything else is deleted (it now lives in `config/`).

### `config-bungee` — deleted

The entire `platform-bungee/config-bungee` module is removed and its `<module>` entry stripped
from `platform-bungee/pom.xml`. Bungee plugins depend on `config/` + `core-bungee`.

### Wiring (the `BootPlugin` seam)

- `DefaultConfigManager(BootPlugin plugin, @Nullable ConfigReferenceErrorHandler errorHandler,
  List<TypeSerializerRegistryCustomizer> serializerCustomizers)`.
- Shared `ConfigConfiguration`'s manager `@Bean` injects `Context` and calls
  `context.getPlugin()` to obtain the `BootPlugin` — no native plugin bean needed, no `core`
  change. It also registers the three injectors and the `@OnConfigReload` post-processor.
- `config-spigot`'s slim `@Configuration` contributes the Bukkit serializer customizer, which
  the shared `ConfigConfiguration` collects via its `List<TypeSerializerRegistryCustomizer>`.

### Discovery

`config/` ships the `ConfigModule` marker, so the module auto-discovers for any consumer
(Spigot or Bungee). `ConfigRegistry` (`@Component`) and `ConfigConfiguration`
(`@Configuration`) are discovery-indexed by the annotation processor now added to `config/`.

## Testing strategy

This is a move, so the bar is "prove nothing regressed":
- All moved tests run in `config/` (Plugin mocks replaced with `BootPlugin` mocks; manager
  tests reference `DefaultConfigManager`). `config/` gets `snakeyaml`/`javassist` on its test
  classpath via the `provided` deps.
- `ConfigConfigurationTest` is split: the injector/reload-processor assertions move to `config/`
  (testing the shared `ConfigConfiguration`); a small test for the Bukkit serializer
  `@Configuration` stays in `config-spigot`.
- The relocation IT (`ConfigProxyRelocationIT`) moves to `config/` and inspects `config/`'s
  shaded jar; its teeth-check (remove `<relocations>` → IT fails) still applies.
- A small `ConfigModuleDiscoveryTest` is added in `config/` (mirrors core-bungee's discovery
  test) proving the marker resolves `ConfigModule`.
- `config-spigot` keeps only its Bukkit serializer tests.
- Whole-reactor build green; `git grep org.bukkit` over `config/src` returns nothing.

## Sequencing & starting state

The plan assumes a working tree where `config-spigot` exists (always) and `config-bungee`
**may** exist (from PR #75). It moves `config-spigot` → `config/` and **deletes `config-bungee`
if present** (handled defensively — if it's absent, that step is a no-op).

Recommended: run after `config-bungee` (PR #75) is merged into the bungee integration branch,
so the deletion is meaningful and the dedup is real. If PR #75 is not merged, this refactor
supersedes it (Bungee will consume `config/` directly) and #75 can be closed.

Because it edits `config/` + `config-spigot` (shipped modules) and removes `config-bungee`,
this is its own change/PR, sequenced with the other in-flight bungee branches (it should land
first, with the platform branches rebasing onto it, or after all of them merge).

## Risks

- `SpigotConfigManager`/`SpigotConfigReferenceLookup`/`SpigotConfigModule` are renamed/relocated
  — acceptable, they are internal; the public surface (annotations, `ConfigManager`/`ConfigRef`
  interfaces) is unchanged and already in `config/`.
- `config/` gains `provided` `snakeyaml`/`javassist` and a shade — must mirror config-spigot's
  proven shade config exactly (relocate `javassist` → `tech.guilhermekaua.spigotboot.shaded.javassist`,
  artifactSet = own module only, javassist `provided`) so the single bundled copy stays owned by
  `core`.
- The annotation processor must be wired into `config/`'s compiler or the new
  `@Component`/`@Configuration` won't be indexed (would fall back to classpath scanning, but the
  index path should be preserved).
- `FolderConfigEntry` loads default resources two ways: `plugin.getResource(...)` (maps cleanly
  to `BootPlugin.getResource(...)`) **and** a `…getClassLoader().getResource(...)` call that
  currently uses the native plugin instance's classloader. The move must map that to
  `bootPlugin.getClassLoader()` (which already resolves to the plugin's real classloader), NOT to
  `bootPlugin.getClass().getClassLoader()` (the wrapper's). The plan pins the exact call-sites.

## Roadmap (later, separate)

- Re-introduce a `config-bungee` module if/when a Bungee-specific serializer (e.g. `ServerInfo`,
  chat `BaseComponent`) is actually needed.
- Consider hoisting the stray `config.spigot.node.SnapshotConfigNode` package in `config/` to a
  non-`spigot` package name for cleanliness (out of scope here).
