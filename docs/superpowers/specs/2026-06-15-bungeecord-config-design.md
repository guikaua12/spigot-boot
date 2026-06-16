# BungeeCord Support: config-bungee (v1) Design

Date: 2026-06-15
Status: Approved design, pending spec review
Target module: `platform-bungee/config-bungee` (new), artifactId `spigot-boot-config-bungee`

## Background

Spigot Boot is a multi-module framework whose heavy logic already lives in
platform-agnostic modules. The configuration framework is one of them: the generic
`config` module (artifactId `spigot-boot-config`) owns **everything that is not
platform-specific** — the annotations (`@Config`, `@FolderConfig`, `@ConfigValue`,
`@OnConfigReload`, `@Comment`, `@NodeKey`, …), the reflection `Binder`, the
`ConfigRef`/`FolderConfigRef`/`FolderConfigSnapshot` interfaces, the `${...}` reference
engine, the `ConfigNode`/`MutableConfigNode` tree abstraction, the
`ConfigLoader`/`ConfigSource` seam, and the `TypeSerializer`/`TypeSerializerRegistry`
registry. It has **no Bukkit dependency, no javassist, and no YAML library** of its own
(verified: a grep for `javassist|Bukkit|snakeyaml` over `config/src/main` returns
nothing).

Each platform supplies a thin **adapter** that plugs into those seams. Today only
Spigot/Paper exists, under `platform-spigot/config-spigot` (artifactId
`spigot-boot-config-spigot`). That adapter is the reference implementation this spec
mirrors.

This effort adds the BungeeCord configuration adapter, **`config-bungee` v1**. It is the
third BungeeCord slice (after `core-bungee`, which this module builds on). Like the other
slices it gets its own spec -> plan -> implementation cycle.

**Scope: full parity with `config-spigot`.** v1 ships the complete feature set —
single `@Config` classes with live-reload proxies, `@ConfigValue` injection,
`@OnConfigReload` callbacks, `@FolderConfig` directory-backed collections, and `${...}`
cross-config references — so config-bungee is a drop-in peer to config-spigot, differing
only in the small platform seam (below) and in dropping the Bukkit-specific serializers.

## YAML backend decision

**config-bungee uses SnakeYAML directly, exactly as config-spigot does — it does
*not* use `net.md_5.bungee.config.*`.** This is the load-bearing decision of the whole
design, because it overturns the intuitive framing ("map the core onto BungeeCord's
`Configuration`/`ConfigurationProvider`").

The intuition is wrong because **config-spigot does not use Bukkit's `FileConfiguration`
either.** Verified by reading every file in `platform-spigot/config-spigot/src/main`:
there are **zero** references to `org.bukkit.configuration.*`. All YAML I/O is done
straight through SnakeYAML (`org.yaml.snakeyaml.Yaml`) in `YamlConfigLoader`, with a
hand-rolled comment-aware writer and a `YamlConfigNode` (a `MutableConfigNode` over plain
`Map`/`List`/scalar values). The platform config API is bypassed entirely on both sides.

So the *true* mirror is to bypass `net.md_5.bungee.config.*` as well and reuse the same
SnakeYAML approach. BungeeCord bundles SnakeYAML 2.2 (it is a transitive dependency of the
runtime via `net.md-5:bungeecord-config`), so `YamlConfigLoader`/`YamlConfigNode` port
across **verbatim** — no new runtime dependency, no behavioral change.

Using `net.md_5.bungee.config.Configuration` instead was considered and rejected: it would
*reduce* reuse (a bespoke `ConfigLoader`/`ConfigNode` over BungeeCord's `Configuration`),
lose YAML comment preservation (BungeeCord's `YamlConfiguration` does not round-trip
comments), force manual handling of its divergences (single `Configuration` type with no
`ConfigurationSection`, no `copyDefaults` merge, a public mutable `self` map), and make
config-bungee structurally *diverge* from config-spigot rather than mirror it.

## Goals

- A Bungee plugin (already booting via `core-bungee`) can declare `@Config`,
  `@FolderConfig`, `@ConfigValue`, and `@OnConfigReload` classes and have them loaded,
  bound, injected, and live-reloaded — identically to config-spigot.
- Directly-injected `@Config` beans transparently reflect reloads, via the same javassist
  `ConfigProxy` mechanism config-spigot uses.
- `${configName:path}` cross-config references resolve, with the same topological reload
  ordering and cycle detection.
- The packaged jar is **relocation-safe**: its javassist references point only at the
  shaded package `tech.guilhermekaua.spigotboot.shaded.javassist`, proven by an automated
  packaging-phase check.
- Bungee users never import a class named "Spigot" in normal usage (config beans are
  injected; the module is auto-discovered).

## Non-goals (v1)

- **No use of `net.md_5.bungee.config.*`.** See "YAML backend decision".
- **No Bukkit-world serializers.** `Material`/`Sound`/`World`/`Location` have no
  BungeeCord equivalent and are dropped. Only the platform-neutral `Duration` serializer
  is ported (see Components → serialization).
- **No extraction of a shared `config-platform-common` module.** Full parity means the
  platform-neutral classes config-spigot keeps locally (binder coordinator, folder impls,
  reference manager, loader/node) are *copied* into config-bungee, not refactored out of
  config-spigot. De-duplication is deferred (see Roadmap); per the effort's ground rule,
  all new code lives in the `platform-bungee/config-bungee/**` subtree and config-spigot
  is left untouched.
- **No new config features.** This is a port, not a redesign; behavior matches
  config-spigot one-for-one (minus the dropped serializers).

## Reuse analysis (grounding)

Verified by reading the seams (`config/src/main`, `platform-spigot/config-spigot/src`,
`platform-bungee/core-bungee/src`):

- **Reused unchanged (the complex code):** the entire `spigot-boot-config` core — all
  annotations, `Binder`/`DefaultBinder`, `NamingStrategy`, `ConfigRef`/`DefaultConfigRef`,
  the folder and reference *interfaces*, `ConfigNode`/`MutableConfigNode`/
  `AbstractValueConfigNode`/`SnapshotConfigNode`, `ConfigLoader`/`ConfigSource` (whose
  built-in `FileConfigSource`/`ResourceConfigSource` are pure `java.nio`/classloader, no
  Bukkit), `TypeSerializer`/`TypeSerializerRegistry`, the exceptions, and the build-time
  discovery (`@SpigotBootDiscoveryCategory`, `DiscoveryIndexReader`, `ClassPathScanner`).
  Also reused: the generic `core` DI seams (`Module`, `@Component`, `@Configuration`,
  `@Bean`, `@Order`, `CustomInjector`/`CustomInjectorRegistryCustomizer`,
  `BeanPostProcessor`/`BeanPostProcessorRegistryCustomizer`, `Context`,
  `DependencyManager`) and `utils` (`ProxyUtils`). All arrive transitively through
  `spigot-boot-config`.
- **Reused from `core-bungee`:** at runtime the `Plugin` bean config-bungee injects is the
  one `BungeeCoreModule` registers (`net.md_5.bungee.api.plugin.Plugin`). config-bungee
  depends on `spigot-boot-core-bungee` so a plugin pulling in config-bungee also gets the
  Bungee bootstrap (ergonomic parity with config-spigot, which pulls in `core-spigot`).
- **New, near-mechanical copies (platform-neutral, but duplicated here per the no-shared-
  module rule):** the YAML loader/node, the proxy, the binding coordinator, the folder
  impls, the reference manager + preprocessor, and the reload subsystem.
- **New, genuinely platform-specific:** the `Plugin` seam (data folder + resource
  loading), the DI `@Configuration` wiring (injects the Bungee `Plugin`), the module +
  marker, the reference lookup (delegates to the Bungee manager), and the Duration-only
  serializer customizer.

### The platform seam (the entire Bungee delta)

config-spigot couples to the platform in exactly four spots; everything else is
format/DI/reflection that is already platform-neutral.

| config-spigot | config-bungee |
|---|---|
| injects `org.bukkit.plugin.Plugin` | injects `net.md_5.bungee.api.plugin.Plugin` (registered by `BungeeCoreModule`) |
| `plugin.getResource(name)` | `plugin.getResourceAsStream(name)` |
| `plugin.getDataFolder()`, `getLogger()`, `getClassLoader()`, `getMainClass()` | identical signatures on the Bungee `Plugin` |
| `serialization/` Bukkit serializers + `core-spigot` `TypeUtil`/`SoundCompat` | dropped; only a ported `DurationSerializer` remains |

## Architecture

### Module & Maven structure (mirror config-spigot under platform-bungee)

```
platform-bungee/                 packaging: pom (existing; add config-bungee to <modules>)
  pom.xml
  config-bungee/                 artifactId: spigot-boot-config-bungee (new)
    pom.xml
    src/main/java/tech/guilhermekaua/spigotboot/config/bungee/...
    src/main/resources/META-INF/spigot-boot/modules/<BungeeConfigModule FQCN>
    src/test/java/...
```

`platform-bungee/pom.xml` is the **only aggregator edited** (add
`<module>config-bungee</module>`). The sibling `commands-bungee` effort edits the same
`<modules>` list, so a trivial merge conflict there is expected and benign. The root
reactor already lists `platform-bungee`, so no root `pom.xml` edit is needed.

### Package

`tech.guilhermekaua.spigotboot.config.bungee` (mirrors `...config.spigot`), with the same
subpackages: `configuration/`, `loader/`, `node/`, `proxy/`, `registry/`, `folder/`,
`injector/`, `reference/`, `reload/`, `serialization/`.

### Dependencies & DI wiring

`config-bungee` dependencies:

- `tech.guilhermekaua.spigot-boot:spigot-boot-config` (compile) — the generic SPI; brings
  `spigot-boot-core` and `utils` transitively.
- `tech.guilhermekaua.spigot-boot:spigot-boot-core-bungee` (compile) — runtime pairing and
  ergonomic parity (a plugin adding config-bungee gets the Bungee bootstrap and the
  `Plugin` bean producer). No direct compile symbol is required from it; the dependency is
  intentional, not accidental.
- `org.javassist:javassist:3.30.2-GA` (**provided**) — `ConfigProxy` uses the javassist
  proxy API directly. Never bundled; the relocated copy is supplied at runtime by
  `spigot-boot-core`, and this module's references are rewritten to the shaded package by
  the shade execution (see "Shading & relocation safety").
- `net.md-5:bungeecord-api:1.21-R0.3` (**provided**, version inherited from the
  `platform-bungee` parent's `dependencyManagement`).
- Lombok + `spigot-boot-annotation-processor-spigot` as annotation processors (provided/
  optional), mirroring config-spigot and core-bungee.
- JUnit 5 + Mockito inherited `test`-scoped from the root pom (do not redeclare).

Compiler: main → Java **1.8**, tests → **17**, `-parameters`. **No `animal-sniffer`
plugin** — config-bungee follows core-bungee (a Bungee module does not target the Spigot
1.8.8 API). The `-Danimal.sniffer.skip=true` flag still appears in build commands because
`-am` pulls in upstream Bukkit-facing modules that do run the check.

DI wiring mirrors config-spigot's `ConfigConfiguration`: a `@Configuration` declares the
`BungeeConfigManager` bean (constructor-injecting the Bungee `Plugin`, an optional
`ConfigReferenceErrorHandler`, and the collected `List<TypeSerializerRegistryCustomizer>`),
registers the three custom injectors via a `CustomInjectorRegistryCustomizer`, registers
the `@OnConfigReload` post-processor via a `BeanPostProcessorRegistryCustomizer`, and
registers the Duration serializer via a `TypeSerializerRegistryCustomizer`.

## Components

Full parity = a one-to-one port of config-spigot's classes into the
`tech.guilhermekaua.spigotboot.config.bungee` package, renaming the `Spigot*` types to
`Bungee*` and applying the platform seam. Classes marked **copy** are platform-neutral and
ported near-verbatim (only the package statement and imports change); classes marked
**seam** carry the Bungee delta.

| config-spigot class | config-bungee class | kind | notes |
|---|---|---|---|
| `SpigotConfigModule` (`@Order(-500)`, `Module`) | `BungeeConfigModule` | seam | same `@Order(-500)`; `onInitialize` runs `ConfigRegistry.registerConfigs`; ships its own empty marker |
| `SpigotConfigManager implements ConfigManager` | `BungeeConfigManager implements ConfigManager` | seam | injects Bungee `Plugin`; `register(...)` resolves `plugin.getDataFolder()` + default-copy via `plugin.getResourceAsStream(...)` |
| `ConfigBindingCoordinator` (pkg-private) | `ConfigBindingCoordinator` | copy | bind/rebind orchestration over `ReferenceKey` |
| `ConfigEntryAccessor` (pkg-private) | `ConfigEntryAccessor` | copy | encapsulation callback |
| `configuration/ConfigConfiguration` | `configuration/ConfigConfiguration` | seam | `@Bean`s wire the manager + injectors + post-processor + Duration serializer |
| `loader/YamlConfigLoader` | `loader/YamlConfigLoader` | copy | SnakeYAML `Yaml.load`/comment-aware writer/atomic temp-move |
| `node/YamlConfigNode` | `node/YamlConfigNode` | copy | `MutableConfigNode` over `Map`/`List`/scalars + comments |
| `proxy/ConfigProxy` | `proxy/ConfigProxy` | copy | javassist `ProxyFactory` + `MethodHandler`; the reason this module shades |
| `registry/ConfigRegistry` (`@Component`) | `registry/ConfigRegistry` | copy | scans `@Config`/`@FolderConfig`, builds proxies, registers DI beans, `initializeAll()` |
| `folder/FolderConfigEntry` | `folder/FolderConfigEntry` | seam | swap `Plugin` type + `getResourceAsStream`; folder scan/bind/reload/save/delete |
| `folder/DefaultFolderConfigRef`/`Editor`/`Snapshot` | same names | copy | folder ref/editor/snapshot impls |
| `injector/ConfigRefInjector`/`FolderConfigInjector`/`ConfigValueInjector`/`ConfigValueResolver` | same names | copy | resolve against `BungeeConfigManager` |
| `reference/ConfigReferenceManager` | `reference/ConfigReferenceManager` | copy | parser/resolver/scanner + dependency graph + reload propagation |
| `reference/ReferenceResolvingPreprocessor` | same name | copy | `ConfigNodePreprocessor` plugged into the `Binder` |
| `reference/SpigotConfigReferenceLookup` | `reference/BungeeConfigReferenceLookup` | seam | delegates to `BungeeConfigManager`/`FolderConfigEntry` for roots/paths/keys |
| `reload/OnConfigReloadProcessor`/`OnConfigReloadBinder`/`OnConfigReloadInvoker` | same names | copy | wires `@OnConfigReload` callbacks (uses `ProxyUtils.getRealClass`) |
| `serialization/BukkitSerializers` + 5 serializers | `serialization/BungeeConfigSerializers` + `DurationSerializer` | seam | **only** Duration survives; Material/Sound/World/Location dropped |
| `META-INF/spigot-boot/modules/...SpigotConfigModule` (empty) | `...BungeeConfigModule` (empty) | seam | filename is the FQCN; `ModuleDiscovery` reads the name |

### `BungeeConfigManager implements ConfigManager`

The heart of the adapter, mirroring `SpigotConfigManager`. It implements every
`ConfigManager` method (`get`/`getRef`/`getFolderConfig`/`register`/`reload`/`reloadAll`/
`save`/`generateDefaults`/`getSerializerRegistry` and the value-access overloads), and
keeps the Spigot-side extras the injectors/registry/reference-lookup rely on
(`registerFolderConfig`, `initializeAll`, `deserializeAt`, `coerceDefault`,
`getConfigName`, `getConfigNode`, …). It constructs a `YamlConfigLoader`, a
`TypeSerializerRegistry.defaults()` with applied customizers, a `Binder` built with the
reference preprocessor as its `ConfigNodePreprocessor`, a `ConfigReferenceManager`, and a
`ConfigBindingCoordinator`. **The only Bungee delta** is the `Plugin` type:
`register(Class)` resolves `plugin.getDataFolder().toPath().resolve(filePath)` and, when
generating defaults, copies from `plugin.getResourceAsStream(resource|value)`.

### `serialization/BungeeConfigSerializers` + `DurationSerializer`

`BungeeConfigSerializers` is the `TypeSerializerRegistryCustomizer` analogue of
`BukkitSerializers`; its `getOrder()` matches (`-100`) and `registerAll` registers only the
ported `DurationSerializer`. `DurationSerializer` is copied from config-spigot unchanged —
it is platform-neutral (`java.time.Duration`, available on Java 8) and currently only
rides into config-spigot via `BukkitSerializers`. The four Bukkit serializers are dropped:
they depend on `org.bukkit.*` and `core-spigot` helpers (`TypeUtil`, `SoundCompat`) that
have no BungeeCord meaning.

### `BungeeConfigModule` + marker

`implements Module`, `@Order(-500)` (after `BungeeCoreModule`'s `-1000`, which registers
the `Plugin` bean, but before default-order modules — so `@Config` beans exist before any
later module resolves a config-dependent component). `onInitialize` calls
`context.getBean(ConfigRegistry.class).registerConfigs(context)` and registers a shutdown
hook. Auto-discovered via the empty marker resource
`META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigModule`,
exactly as config-spigot ships its marker.

## Shading & relocation safety

This is the heaviest part of the module and a first-class concern. `ConfigProxy` imports
`javassist.util.proxy.{ProxyFactory, MethodHandler, ProxyObject}` and uses them directly
(it is the **"creating proxies"** case from CLAUDE.md, not the detection-only case). Inside
a downstream plugin jar the only javassist present is the copy `spigot-boot-core` bundles
relocated to `tech.guilhermekaua.spigotboot.shaded.javassist`; the original `javassist.*`
classes are never shipped. So config-bungee's own bytecode must reference the **shaded**
names, or it throws `NoClassDefFoundError: javassist/util/proxy/...` on a real proxy server
while passing tests (where the original javassist is still on the classpath).

### Shade execution (mirror config-spigot exactly)

A `maven-shade-plugin` execution `relocate-javassist-references`, phase `package`, goal
`shade`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <!-- version managed by the parent pluginManagement -->
    <executions>
        <execution>
            <id>relocate-javassist-references</id>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
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
```

Bundling nothing (the `<artifactSet>` includes only this module's own coordinates) keeps
`core` the sole owner of the bundled relocated javassist; the execution only rewrites this
module's references. javassist stays `provided`.

### Relocation guard (why surefire cannot catch this, and what does)

The `utils` module's `ProxyUtilsTest#proxyUtilsClassMustNotReferenceUnrelocatedJavassistPackage`
guards the *detection* case by asserting the compiled class carries **no** slashed
`javassist/` reference — it works in the `test` phase because `ProxyUtils` never imports
javassist. **That exact test would be wrong for `ConfigProxy`:** `ConfigProxy` *does*
import javassist, so its pre-shade bytecode (in `target/classes`, which is what runs during
`test`) legitimately contains `javassist/`. Only the **shaded jar** (produced in `package`,
after tests) should be clean. A surefire test therefore cannot prove relocation safety.

The guard is a `maven-failsafe-plugin` integration test, **`ConfigProxyRelocationIT`**,
bound to the `verify` phase (after `package`/shade). It:

1. Locates the shaded artifact `target/<finalName>.jar` (the `project.build.directory` and
   `project.build.finalName` are passed in via failsafe `systemPropertyVariables`).
2. Opens the `…/config/bungee/proxy/ConfigProxy.class` zip entry and reads its bytes.
3. Decodes them as ISO-8859-1 (byte-preserving) so constant-pool type descriptors appear
   verbatim as substrings.
4. Asserts the bytes **contain** `tech/guilhermekaua/spigotboot/shaded/javassist/`
   (relocation happened), then **strips that shaded prefix** and asserts the remainder
   contains **no** `javassist/` (no un-relocated reference survives). The strip-first step
   is essential: a naive `contains("javassist/")` would match the shaded form itself, since
   `…/shaded/javassist/…` contains the substring `javassist/`.

The IT reads a jar file and scans bytes only — it needs neither javassist nor
bungeecord-api on its classpath. The plan's Done criteria additionally runs a packaging
build (`mvnw … verify`/`install`) as the human-visible proof.

## User-facing usage

config beans are auto-discovered and injected; the only wiring an author does is what
`core-bungee` already requires. Given a booted context (from `BungeeBoot.initialize(...)`),
a `@Config` class is loaded and injectable:

```java
@Config("messages.yml")
public class Messages {
    private String prefix = "&7[Proxy]";
    private Duration joinCooldown = Duration.ofSeconds(5);
    // getters...
}

@Component
public class JoinHandler {
    private final Messages messages;          // a live-reloading proxy

    @Inject
    public JoinHandler(Messages messages) {
        this.messages = messages;
    }

    @OnConfigReload(Messages.class)
    public void onReload() {
        // called after messages.yml reloads
    }
}
```

`@ConfigValue`, `@FolderConfig`/`FolderConfigRef`, and `${configName:path}` references all
behave exactly as documented for config-spigot.

## Testing strategy

Pure JUnit 5 + Mockito (mock `net.md_5.bungee.api.plugin.Plugin`, `@TempDir` for data
folders) — no MockBukkit (none exists for Bungee, and config-spigot does not use it
either). Port config-spigot's ~15 test classes with the `Plugin` mock swapped to the Bungee
type, covering: manager register/get/reload lifecycle and collision handling; YAML
loader round-trips (tricky scalars, comments); node mutation; proxy
`toString/hashCode/equals` interception + delegation; registry discovery and field-modifier
validation; folder-config load/order/enabled/id-injection/reload/save/delete and
path-traversal guards; the three injectors; the `@OnConfigReload` binder/invoker/processor;
reference resolution + type-mismatch firing; and Duration serialization. Add:

- `BungeeModuleDiscoveryTest` — proves the marker resource resolves `BungeeConfigModule`
  (mirrors core-bungee's `BungeeModuleDiscoveryTest`).
- `DurationSerializerTest` — deserialize/serialize/round-trip.
- `ConfigProxyRelocationIT` — the failsafe packaging-phase relocation guard (above).

## Open items to pin at plan time (not blockers)

1. **javassist coordinates** — declared with an explicit version `3.30.2-GA` at `provided`
   scope (not managed in any parent), exactly as config-spigot declares it.
2. **bungeecord-api repository fallback** — if `1.21-R0.3` does not resolve from Maven
   Central, fall back to the Sonatype snapshot repo + `1.21-R0.1-SNAPSHOT`, carried over
   from the core-bungee plan.
3. **`DurationSerializer` Java-8 cleanliness** — `java.time.Duration` is Java-8-safe; the
   port must avoid any Java 9+ API it might reach for (none expected).
4. **failsafe plugin version** — confirm whether `maven-failsafe-plugin` is managed in the
   root `pluginManagement`; if not, pin a version in the module pom.

## Roadmap (subsequent slices, each its own spec)

1. **`config-platform-common`** — extract the platform-neutral classes now duplicated
   between config-spigot and config-bungee (loader/node, proxy, binding coordinator, folder
   impls, reference manager/preprocessor, reload subsystem) into a shared module both
   adapters depend on. Deferred until the second adapter exists (this one), which reveals
   the true shared shape; doing it now would also mean editing config-spigot, out of scope
   for this effort.
2. **Bungee-specific serializers** — e.g. `ServerInfo`, chat `BaseComponent`/`ChatColor`,
   if demand appears.
3. **On-proxy end-to-end validation** — exercise config-bungee inside a runnable BungeeCord
   sample plugin (depends on the `bungee.yml` descriptor-generator slice).
