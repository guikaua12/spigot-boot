# Bukkit Server Version Detector — Design

- **Date:** 2026-06-18
- **Module:** `platform-spigot/core-spigot` (`spigot-boot-core-spigot`)
- **Status:** Approved (design)
- **Branch:** `worktree/feat-bukkit-server-version-api`

## Goal

Provide a reusable, public API in `core-spigot` that reports the running Minecraft
server version as a structured, comparable value. The repository already parses the
server version in `InventoryApiNMS` (sibling module `inventory-api/nms`), but that
logic exposes only the **minor** component plus the raw CraftBukkit package suffix and
is welded to title-updater selection. This design lifts that parsing approach into a
standalone, general-purpose detector that exposes the full `major.minor.patch` triple
and version comparisons, and detects **all** versions (open numeric parsing, no
hard-coded version list).

## Non-goals

This feature is a **pure version detector**. It explicitly does **not**:

- Detect server platform/flavor (CraftBukkit / Spigot / Paper / Folia).
- Modify `PlatformSchedulers` or its Folia `Class.forName` probe.
- Rewire the feature-detection utilities (`SoundCompat`, `ItemUtils`, `TypeUtil`),
  whose type/shape-based checks are more fork-robust than version-number checks.
- Change `InventoryApiNMS` or add a `core-spigot` dependency to `inventory-api/nms`.

The change is purely additive.

## Approaches considered

- **A — Open numeric value object (chosen).** Parse an arbitrary `major.minor.patch`
  into an immutable `ServerVersion` with `isAtLeast(...)` and `Comparable`. Works for
  any release, present or future, with no per-version code changes.
- **B — Enum of known versions.** A closed set, like the existing NMS `switch`, that
  must be extended for every MC release. Rejected: a general detector should not need
  editing for each new version.
- **C — Loose static methods only.** No value object. Rejected: cannot be passed
  around or injected as a bean, which the access-pattern decision requires.

## Architecture

New package `tech.guilhermekaua.spigotboot.core.spigot.version` (mirrors the existing
`.scheduler` package), containing two classes:

1. **`ServerVersion`** — `public final` immutable value object,
   `implements Comparable<ServerVersion>`. Follows the newer house style seen in
   `SoundCompat` / `PlatformSchedulers`: MIT license header, private constructor, full
   Javadoc with `@param`/`@return`/`@throws`, `@NotNull`/`@Nullable` annotations,
   `Objects.requireNonNull` where appropriate.
2. **`ServerVersionConfiguration`** — `@Configuration` class exposing `ServerVersion`
   as an injectable `@Bean` guarded by `@ConditionalOnMissingBean`, mirroring
   `SpigotSchedulerConfiguration`. Component-scanned the same way (no extra
   registration needed).

### Access patterns

Both a static accessor and an injectable bean are provided:

- **Static:** `ServerVersion.current()` returns a lazily computed, cached singleton
  (the running server's version never changes mid-process). Matches the static usage
  of `InventoryApiNMS` / `PlatformSchedulers`.
- **Bean:** `ServerVersionConfiguration#serverVersion()` returns `ServerVersion.current()`,
  guarded by `@ConditionalOnMissingBean(ServerVersion.class)` so a plugin may register
  its own override.

```java
@Configuration
public class ServerVersionConfiguration {
    @Bean
    @ConditionalOnMissingBean(ServerVersion.class)
    public ServerVersion serverVersion() {
        return ServerVersion.current();
    }
}
```

## `ServerVersion` API surface

```java
// ---- access ----
public static ServerVersion current();                  // cached singleton; reads Bukkit once
static ServerVersion parse(String bukkitVersion, String serverPackageName); // pkg-private test seam

// ---- components ----
public int getMajor();                                   // e.g. 1
public int getMinor();                                   // e.g. 21
public int getPatch();                                   // e.g. 4 (0 when absent, e.g. "1.21")
public String getRawVersion();                           // original Bukkit.getBukkitVersion()
public Optional<String> getNmsPackageSuffix();           // "v1_19_R3"; empty on un-versioned servers
public boolean hasVersionedNmsPackage();                 // true when the CraftBukkit pkg is vX_Y_RZ

// ---- comparisons ----
public boolean isAtLeast(int major, int minor);
public boolean isAtLeast(int major, int minor, int patch);
public boolean isOlderThan(int major, int minor);
public int compareTo(ServerVersion other);               // orders by (major, minor, patch)

// equals / hashCode / toString
```

`compareTo`, `equals`, and `hashCode` are defined over the `(major, minor, patch)`
triple. The NMS package suffix is metadata and is **not** part of ordering or equality.

## Parsing rules

Reuses the regexes and approach proven in `InventoryApiNMS`:

- **Version triple:** apply `^(\d+)\.(\d+)(?:\.(\d+))?` to `Bukkit.getBukkitVersion()`
  (e.g. `"1.21.4-R0.1-SNAPSHOT"`). Unlike the existing code, keep **all three** groups;
  a missing patch group defaults to `0`.
- **NMS suffix:** apply `v(\d+)_(\d+)_R(\d+)` to the last segment of
  `Bukkit.getServer().getClass().getPackage().getName()`. Returns the suffix
  (e.g. `"v1_19_R3"`) as a present `Optional`, or empty when un-versioned (Paper
  1.20.5+ and other un-versioned/renumbered packages).

### Edge cases

- **Unparseable version:** `current()` must never throw — bean creation has to succeed.
  When the version string cannot be parsed, `major`/`minor`/`patch` are set to `-1`,
  `isAtLeast(...)` always returns `false`, and `getNmsPackageSuffix()` may still be
  present/empty independently. This mirrors the `-1` sentinel philosophy already in
  `InventoryApiNMS#detectMinorVersion`.
- **Renumbered schemes (the "26.x" case):** parse faithfully — e.g. `"26.1.2"` yields
  `major=26, minor=1, patch=2`. Document that `isAtLeast(1, X)` comparisons are only
  meaningful on the standard `1.x` scheme; the detector reports raw numbers rather than
  guessing intent. `hasVersionedNmsPackage()` is `false` on such schemes.
- **`getNmsPackageSuffix()` empty but version parseable:** normal for modern servers
  (Paper 1.20.5+). Callers needing the legacy NMS revision must handle the empty case.

## animal-sniffer 1.8.8 safety

`core-spigot` runs the managed spigot-api 1.8.8 API check. The detector touches only
1.8-safe APIs:

- `Bukkit.getBukkitVersion()` — present since 1.8.
- `Bukkit.getServer()` — assigned to an **`Object`-typed** local before calling
  `getClass().getPackage()`, exactly as `InventoryApiNMS#detectPackageSuffix` does, so
  `getClass()` resolves through the root pom's `java.*` ignore rather than the 1.8.8
  Bukkit signature.

No changes to the pom `<ignores>` are required.

## Testing

- **`ServerVersionTest`** (JUnit 5, snake_case names, Mockito only — **no MockBukkit**).
  Drives the package-private `parse(bukkitVersion, serverPackageName)` seam with
  hand-fed strings so no live server is needed (mirrors `InventoryApiNMSTest` /
  `PlatformSchedulersTest`). Cases:
  - Standard versioned legacy server: `parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_19_R3")`
    → `1/19/4`, suffix `v1_19_R3`, `hasVersionedNmsPackage()` true.
  - Modern un-versioned server: `parse("1.21.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit")`
    → `1/21/4`, empty suffix, `hasVersionedNmsPackage()` false.
  - Two-component version: `parse("1.21-R0.1-SNAPSHOT", ...)` → patch `0`.
  - Renumbered scheme: `parse("26.1.2-R0.1-SNAPSHOT", "org.bukkit.craftbukkit")`
    → `26/1/2`, empty suffix.
  - `isAtLeast` / `isOlderThan` / `compareTo` ordering across several versions.
  - Unparseable version (`null` or garbage) → `-1/-1/-1`, `isAtLeast` false, no throw.
  - `equals`/`hashCode`/`toString` over the triple.
- **`ServerVersionConfiguration` / `current()` smoke test** (MockBukkit-v1.20): assert
  `ServerVersion.current()` returns a non-null, sane object under the mock server. Note
  MockBukkit reports a modern, un-versioned package, so the legacy `vX_Y_RZ` suffix
  path cannot be exercised this way — it is covered by the `parse` seam tests above.

## Files

New files (under `platform-spigot/core-spigot`):

- `src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java`
- `src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionConfiguration.java`
- `src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionTest.java`

No existing files are modified.

## Verification

- `mvnw.cmd -pl platform-spigot/core-spigot -am test` (build with JDK 21 per project
  constraint; sandbox disabled for the Maven build).
- Confirm the animal-sniffer 1.8.8 check passes for `core-spigot` (no new ignores).
