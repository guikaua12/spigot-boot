# BungeeCord Descriptor Generator: bungee.yml Generation Design

Date: 2026-06-15
Status: Approved design, pending spec review
Target module: `platform-bungee/annotation-processor-bungee` (new), artifactId `spigot-boot-annotation-processor-bungee`

## Background

Spigot Boot generates the Spigot plugin descriptor (`plugin.yml`) at **build time** from a
source annotation, so a Spigot author never hand-writes or hand-maintains the descriptor.
The generator lives in `platform-spigot/annotation-processor`
(artifactId `spigot-boot-annotation-processor-spigot`): the `@Plugin` annotation
(`.../annotations/Plugin.java`) carries the metadata, and `PluginAnnotationProcessor`
(`.../plugin/PluginAnnotationProcessor.java`) hand-rolls the YAML with a `PrintWriter` into
`StandardLocation.CLASS_OUTPUT` (`target/classes/plugin.yml`). The annotated class itself is
taken as the `main` class (`PluginAnnotationProcessor.java:72`: "Assumes the annotated class
is the main class"). The processor is registered via SPI in
`META-INF/services/javax.annotation.processing.Processor` (which also lists the unrelated
`DiscoveryIndexProcessor`).

The BungeeCord adapter slice ("core-bungee v1",
`docs/superpowers/specs/2026-06-15-bungeecord-core-bungee-design.md`) deliberately deferred
descriptor generation: its user-facing example notes "the plugin still needs a `bungee.yml`
descriptor to load… the test plugin hand-writes one", and its Roadmap item #1 frames the
exact decision this spec resolves:

> **Descriptor generator** — emit `bungee.yml` (decision: reuse `@Plugin` with a
> Bungee-targeted writer vs. a new `@BungeePlugin` annotation/module).

**This spec covers that slice.** It adds a build-time generator that emits a valid
`bungee.yml` from a new `@BungeePlugin` annotation, mirroring the Spigot mechanism so a
Bungee author declares descriptor metadata on the main class and the file is produced during
compilation.

## Goals

- A Bungee plugin author annotates the main class with `@BungeePlugin(...)` and a valid
  `bungee.yml` is emitted to the jar root at build time — no hand-written descriptor.
- The generated descriptor uses the exact BungeeCord schema (`depends`/`softDepends`
  camelCase, single `author`, `libraries`), distinct from Spigot's schema.
- The mechanism mirrors the Spigot generator (annotation on the main class → hand-rolled
  YAML → `CLASS_OUTPUT`), so the two platforms stay structurally consistent.
- The work is isolated in its own module so it can be built and reviewed concurrently with
  the sibling `commands-bungee` and `config-bungee` slices.

## Non-goals

- **No reuse of the Spigot `@Plugin` annotation.** Its Spigot-only elements (`load`,
  `apiVersion`, `foliaSupported`, `prefix`, `loadbefore`, `website`, plural `authors`) are
  meaningless on a proxy and its dependency keys (`depend`/`softdepend`) do not match
  Bungee's (`depends`/`softDepends`).
- **No Maven property interpolation** (`${project.version}`). Metadata is hardcoded
  annotation literals, exactly as `test-plugin`'s `@Plugin(version = "1.0.0", ...)` does
  today (`test-plugin/.../Main.java:34-41`).
- **No main-class subtype validation.** The processor does not verify the annotated class
  extends `net.md_5.bungee.api.plugin.Plugin`, mirroring Spigot's no-validation approach
  (and keeping the processor free of any Bungee-API dependency).
- **No changes to `test-plugin`.** It is Spigot-only and stays so; the generator is verified
  by self-contained unit tests (see Testing strategy).
- **No on-server end-to-end validation** or runnable Bungee sample plugin (a later slice,
  consistent with core-bungee v1's deferral).
- No touching `commands-bungee`, `config-bungee`, or the shared Spigot processor.

## Descriptor schema (grounding)

Verified directly against the pinned `net.md-5:bungeecord-api:1.21-R0.3` jar (`javap` on
`net.md_5.bungee.api.plugin.PluginDescription`) and BungeeCord's `PluginManager`:

- BungeeCord's `PluginManager.detectPlugins(File)` reads **`bungee.yml`** from the jar root
  first, falling back to `plugin.yml` if absent, and loads it via SnakeYAML
  `yaml.loadAs(in, PluginDescription.class)` — i.e. YAML keys map to the JavaBean property
  names of `PluginDescription` (case-sensitive). `setSkipMissingProperties(true)` means
  unknown keys are silently ignored.
- `PluginDescription` fields → YAML keys:

| Key | Type | Required | Notes |
|---|---|---|---|
| `name` | String | **yes** (`checkNotNull`) | |
| `main` | String | **yes** (`checkNotNull`) | the main class FQCN |
| `version` | String | no (defaults `null`) | required by `@BungeePlugin` by convention, to avoid a Bungee console warning |
| `author` | String | no | **single** author — there is no plural `authors` key (unlike Spigot) |
| `depends` | Set\<String\> | no (default empty) | **camelCase**; *not* Spigot's `depend` |
| `softDepends` | Set\<String\> | no (default empty) | **camelCase**; *not* Spigot's `softdepend` |
| `description` | String | no | |
| `libraries` | List\<String\> | no (default empty) | BungeeCord 1.21 runtime Maven loader; `group:artifact:version` coordinates |

`file` exists on the POJO but is set at runtime, never a YAML key. Scalars
(`name`/`main`/`version`/`author`/`description`) must be single strings; `depends`,
`softDepends`, and `libraries` must be YAML sequences (SnakeYAML does not coerce a scalar
into a Set/List).

## Design decision: a new isolated module

We add a **new module** `platform-bungee/annotation-processor-bungee`
(artifactId `spigot-boot-annotation-processor-bungee`) rather than extending the shared
`platform-spigot/annotation-processor`.

**Why a new module (recommended and chosen):**

1. **Blast radius.** The shared Spigot processor drives the entire Spigot platform — both
   `plugin.yml` generation **and** the runtime discovery index (`DiscoveryIndexProcessor`,
   registered in the same SPI file). Editing it to add Bungee logic risks the Spigot build
   and tests, which is unacceptable while two sibling Bungee efforts run concurrently.
2. **Single Responsibility / Interface Segregation.** A Bungee-specific annotation and writer
   belong with the Bungee platform tree. A Bungee author should not depend on `@Plugin`
   (whose surface is Folia/`api-version`/`load`/`prefix`/plural-`authors`) to emit a proxy
   descriptor; `@BungeePlugin` exposes only Bungee-relevant elements.
3. **Dependency hygiene & symmetry.** Like the Spigot processor, the Bungee module ships no
   runtime dependencies and carries its own SPI registration. It mirrors how
   `platform-spigot` keeps its processor as a standalone module under the platform tree; a
   future `platform-velocity` would slot in the same way.
4. **Parallelizability.** Keeping the work in its own files limits contention with the
   sibling slices to a single shared line in `platform-bungee/pom.xml` (see Architecture).

**Alternative considered — reuse `@Plugin` with a second Bungee-targeted writer:** rejected.
It forces edits to the high-blast-radius shared module, and `@Plugin`'s semantics do not map
cleanly onto Bungee (it would have to ignore six Spigot-only elements and remap
`depend`→`depends`, `softdepend`→`softDepends`, and collapse plural `authors` into a single
`author`). The mismatch is large enough that a dedicated annotation is both safer and
clearer.

## Architecture

### Module & Maven structure (mirror `platform-spigot/annotation-processor`)

```
platform-bungee/                          packaging: pom (already exists)
  pom.xml                                  <modules> gains: annotation-processor-bungee
  core-bungee/                             (existing, v1)
  annotation-processor-bungee/            artifactId: spigot-boot-annotation-processor-bungee
    pom.xml
    src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/
      annotations/BungeePlugin.java
      plugin/BungeePluginAnnotationProcessor.java
    src/main/resources/META-INF/services/javax.annotation.processing.Processor
    src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/
      plugin/BungeePluginAnnotationProcessorTest.java
```

`annotation-processor-bungee` pom (modeled on `core-bungee`'s, *not* the Spigot processor's,
because this module has tests that must compile on Java 17):

- Parent `spigot-boot-platform-bungee:3.2.1-SNAPSHOT`; packaging `jar`.
- `maven-compiler-plugin`: main `source/target = 1.8`, `testSource/testTarget = 17`, with
  `<proc>none</proc>` so the JDK does not auto-run this module's own processor while
  compiling it (matching `platform-spigot/annotation-processor/pom.xml`).
- **No** dependencies on the Bungee API, the Spigot processor, or `spigot-boot-core` — the
  processor uses only `javax.annotation.processing` and `javax.lang.model`.
- **No** animal-sniffer (this module is not Bukkit-facing) and **no** `maven-shade-plugin`
  (no javassist of its own).
- One **test-scoped** dependency: `com.google.testing.compile:compile-testing` (pulls Truth +
  Guava transitively, test scope only). JUnit 5 is inherited from the root pom.

**Shared merge-conflict point:** `platform-bungee/pom.xml`'s `<modules>` block (today only
`core-bungee`) gains `annotation-processor-bungee`. The sibling `commands-bungee` and
`config-bungee` slices add their own modules to the same block, so this single line is the
expected conflict; the plan calls it out and adds the entry adjacent to `core-bungee` to keep
resolution trivial.

### Package

`tech.guilhermekaua.spigotboot.bungee.annotationprocessor` (mirrors the Spigot
`...spigot.annotationprocessor`), with `annotations` and `plugin` subpackages as above.

## Components

### `@BungeePlugin` (the annotation)

`@Retention(RetentionPolicy.SOURCE)`, `@Target(ElementType.TYPE)` — identical retention/target
to Spigot's `@Plugin`, so it is build-time-only and never ships in the plugin jar. Placed on
the class that extends `net.md_5.bungee.api.plugin.Plugin`; that class becomes `main`.

```java
public @interface BungeePlugin {
    String name();                       // required
    String version();                    // required
    String author()        default "";
    String[] depends()     default {};
    String[] softDepends() default {};
    String description()   default "";
    String[] libraries()   default {};
}
```

Element set is the BungeeCord descriptor schema minus `main` (derived) and `file`
(runtime-only). `name`/`version` have no default (required); the rest default empty/blank and
are omitted from the output when unset.

### `BungeePluginAnnotationProcessor` (the writer)

A near-mechanical mirror of `PluginAnnotationProcessor`:

- `@SupportedAnnotationTypes("tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin")`,
  `@SupportedSourceVersion(SourceVersion.RELEASE_8)`.
- `process(...)` iterates the elements annotated with `@BungeePlugin` and, for each, opens
  `filer.createResource(StandardLocation.CLASS_OUTPUT, "", "bungee.yml", element)` and writes
  with a `PrintWriter` (hand-rolled YAML, no YAML library — matching Spigot).
- `main` is `((TypeElement) element).getQualifiedName().toString()`. No subtype validation.
- On `IOException`, reports `Diagnostic.Kind.ERROR` and returns `false` (mirrors Spigot).

Emission order and rules (the exact bytes the tests pin):

1. `name: <name>`
2. `main: <FQCN of annotated class>`
3. `version: <version>`
4. if `author` non-empty → `author: <author>`
5. if `depends` non-empty → `depends: [a, b]` (flow sequence, bare scalars)
6. if `softDepends` non-empty → `softDepends: [a, b]`
7. if `description` non-empty → `description: <description>`
8. if `libraries` non-empty → `libraries: ['g:a:v', ...]` (each entry single-quoted, matching
   Spigot's `libraries` serialization — safe for the colons in Maven coordinates)

Generated `bungee.yml` for a fully-populated annotation:

```yaml
name: MyProxyPlugin
main: com.example.Main
version: 1.0.0
author: Approximations
depends: [SomeOther]
softDepends: [Optional]
description: A proxy plugin.
libraries: ['com.squareup.okhttp3:okhttp:4.12.0']
```

And for the required-only case `@BungeePlugin(name = "MyProxyPlugin", version = "1.0.0")` on
`com.example.Main`:

```yaml
name: MyProxyPlugin
main: com.example.Main
version: 1.0.0
```

Known limitation (parity with Spigot): scalar values are written unquoted, so a `description`
containing YAML-special syntax could need manual escaping. This matches the Spigot writer and
is acceptable for v1.

### SPI registration

`src/main/resources/META-INF/services/javax.annotation.processing.Processor` contains exactly
one line:

```
tech.guilhermekaua.spigotboot.bungee.annotationprocessor.plugin.BungeePluginAnnotationProcessor
```

Discovery indexing is **not** this module's concern — `core-bungee` already wires the Spigot
`DiscoveryIndexProcessor` for `@Component` indexing, so the Bungee module ships only the
descriptor processor.

## User-facing usage

A Bungee plugin combines the v1 `core-bungee` runtime with this descriptor annotation. The
annotation goes on the same main class shown in the core-bungee spec:

```java
@BungeePlugin(
    name = "MyProxyPlugin",
    version = "1.0.0",
    author = "Approximations",
    depends = {"SomeOther"},
    description = "A proxy plugin."
)
public class Main extends net.md_5.bungee.api.plugin.Plugin {
    private BungeeBootPlugin bootPlugin;

    @Override public void onEnable()  { bootPlugin = new BungeeBootPlugin(this);
                                        BungeeBoot.initialize(bootPlugin); }
    @Override public void onDisable() { BungeeBoot.onDisable(bootPlugin); }
}
```

Build-time wiring (the plugin's own pom): add the descriptor processor to
`annotationProcessorPaths` — build-time only; nothing ships, since `@BungeePlugin` is
`SOURCE`-retained:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot-annotation-processor-bungee</artifactId>
        <version>${project.version}</version>
    </path>
    <!-- plus spigot-boot-annotation-processor-spigot for @Component discovery indexing -->
</annotationProcessorPaths>
```

On `package`, `bungee.yml` lands at the jar root and BungeeCord loads it.

## Testing strategy

Real TDD via `com.google.testing.compile` (the Spigot processor currently has no unit tests;
this slice adds proper coverage). `BungeePluginAnnotationProcessorTest` runs the processor
in-memory over an inline `@BungeePlugin` source and asserts the exact generated `bungee.yml`
bytes — fully self-contained, no `test-plugin` changes:

```java
Compilation c = javac()
    .withProcessors(new BungeePluginAnnotationProcessor())
    .compile(JavaFileObjects.forSourceString("com.example.Main", SOURCE));
assertThat(c).succeeded();
assertThat(c)
    .generatedFile(StandardLocation.CLASS_OUTPUT, "", "bungee.yml")
    .contentsAsUtf8String()
    .isEqualTo("name: MyProxyPlugin\nmain: com.example.Main\nversion: 1.0.0\n...");
```

Coverage:

- **Full descriptor** — every element set → emits all eight lines in order.
- **Required-only** — only `name`/`version` → emits exactly `name`, `main`, `version`
  (optional lines omitted).
- **`main` resolution** — `main` equals the annotated class's fully-qualified name.
- **camelCase dependency keys** — `depends`/`softDepends` emitted (asserts they are *not*
  `depend`/`softdepend`), as flow sequences.
- **`libraries` quoting** — each coordinate single-quoted.

## Open items to pin at plan time (not blockers)

1. **`compile-testing` version** — pin a concrete `com.google.testing.compile:compile-testing`
   version (current line is `0.21.0`) and confirm it resolves from Maven Central and runs
   under the JDK 21 build (the processor uses only public `javax.*` APIs, so no
   `--add-exports` is needed, unlike processors touching `com.sun.source.*`).
2. **`@SupportedSourceVersion(RELEASE_8)` warning** — under JDK 21's in-process `javac`,
   compile-testing may surface a benign "supported source version less than -source" note;
   confirm `succeeded()` still holds (warnings do not fail), or pin `-source 8` compiler
   options in the test if needed.
3. **Module ordering in `platform-bungee/pom.xml`** — add `annotation-processor-bungee`
   immediately after `core-bungee` to minimize conflict churn with the sibling slices.

## Roadmap (sibling and subsequent slices)

This slice and the two other concurrent slices all descend from core-bungee v1's roadmap:

1. **`bungee.yml` descriptor generator** — *this spec.*
2. **`commands-bungee`** — `CommandPlatformSupport` + `CommandSenderHandle` for
   `net.md_5.bungee.api.CommandSender` (separate spec, concurrent).
3. **`config-bungee`** — Bungee config loader/data-folder glue (separate spec, concurrent).
4. **Later:** a runnable Bungee sample/test plugin and on-server end-to-end validation that
   exercises both `core-bungee` and a generated `bungee.yml`.
