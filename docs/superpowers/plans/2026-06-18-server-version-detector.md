# Server Version Detector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable, public `ServerVersion` API to `core-spigot` that reports the running Minecraft server version as a structured, comparable value and detects all versions.

**Architecture:** An immutable `ServerVersion` value object parses `major.minor.patch` (open numeric, no hard-coded version list) plus the optional CraftBukkit `vX_Y_RZ` package suffix from `Bukkit.getBukkitVersion()` and the server package name. A package-private `parse(String, String)` seam keeps the logic unit-testable without a live server. The version is reachable both statically (`ServerVersion.current()`, cached) and as an injectable `@Bean`. Change is purely additive.

**Tech Stack:** Java (main compiled at 1.8, tests at 17), Maven (`mvnw.cmd`), JUnit 5, Mockito, MockBukkit-v1.20, spigot-boot DI annotations (`tech.guilhermekaua.spigotboot.core.context.annotations.*`).

## Global Constraints

- **Module:** all new files live under `platform-spigot/core-spigot`. No other module is touched. Do **not** add a `core-spigot` dependency to `inventory-api/nms`.
- **Build JDK:** build/run tests with **JDK 21**, never the shell-default JDK 25 (Lombok 1.18.36 crashes with `TypeTag :: UNKNOWN` on 25). Ensure `JAVA_HOME` points at a JDK 21 before running Maven.
- **Sandbox:** pass `dangerouslyDisableSandbox: true` on every `mvnw.cmd` invocation — a sandboxed Maven build silently runs against a stale overlay and edits won't take effect.
- **Run Maven from the worktree root:** `C:\Users\Guilherme\IdeaProjects\spigot-boot_worktrees\feat-bukkit-server-version-api`.
- **License header:** every new `.java` file (main and test) starts with the exact MIT header block used across the module (copy verbatim from `PlatformSchedulers.java`), copyright `© 2025 Guilherme Kauã da Silva`.
- **Style:** `public final` class + private constructor for the value object; full Javadoc with `@param`/`@return`/`@throws` on public/protected members; `org.jetbrains.annotations.@NotNull`/`@Nullable` on params and returns; normal comments start lowercase; import classes (no fully-qualified inline types). Test method names are snake_case (house style).
- **animal-sniffer 1.8.8 safety:** the only Bukkit calls are `Bukkit.getBukkitVersion()` (1.8-safe) and `Bukkit.getServer()` assigned to an **`Object`-typed** local before `getClass().getPackage()` (so `getClass()` resolves through the root pom's `java.*` ignore). Do **not** add any pom `<ignores>` entries.
- **Equality/ordering domain:** `equals`, `hashCode`, and `compareTo` are defined over the `(major, minor, patch)` triple only. The NMS package suffix is metadata and is excluded from ordering and equality.

---

## File Structure

- **Create** `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java` — the value object: `parse(String, String)` seam, component accessors, comparisons, value semantics, and the static cached `current()` / `detect()` accessors.
- **Create** `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionConfiguration.java` — `@Configuration` exposing `ServerVersion` as a `@Bean` guarded by `@ConditionalOnMissingBean`.
- **Create** `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionTest.java` — pure parse/comparison unit tests (no MockBukkit).
- **Create** `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionCurrentTest.java` — MockBukkit smoke test for `current()` / `detect()`.

---

## Task 1: `ServerVersion` value object (pure logic)

Builds the immutable value object with the testable `parse` seam, component accessors, comparisons, and value semantics. No live-server access yet.

**Files:**
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java`
- Test: `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces (relied on by later tasks):
  - `static ServerVersion ServerVersion.parse(@Nullable String bukkitVersion, @Nullable String serverPackageName)` (package-private)
  - `int getMajor()`, `int getMinor()`, `int getPatch()`
  - `String getRawVersion()`
  - `Optional<String> getNmsPackageSuffix()`, `boolean hasVersionedNmsPackage()`
  - `boolean isAtLeast(int major, int minor)`, `boolean isAtLeast(int major, int minor, int patch)`, `boolean isOlderThan(int major, int minor)`
  - `int compareTo(ServerVersion other)` (implements `Comparable<ServerVersion>`)
  - `equals`/`hashCode`/`toString` over the `(major, minor, patch)` triple

- [ ] **Step 1: Write the failing test file**

Create `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionTest.java`:

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
package tech.guilhermekaua.spigotboot.core.spigot.version;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionTest {

    @Test
    void parses_full_legacy_versioned_server() {
        ServerVersion version = ServerVersion.parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_19_R3");

        assertEquals(1, version.getMajor());
        assertEquals(19, version.getMinor());
        assertEquals(4, version.getPatch());
        assertEquals("1.19.4-R0.1-SNAPSHOT", version.getRawVersion());
        assertEquals(Optional.of("v1_19_R3"), version.getNmsPackageSuffix());
        assertTrue(version.hasVersionedNmsPackage());
    }

    @Test
    void parses_modern_unversioned_server() {
        ServerVersion version = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(1, version.getMajor());
        assertEquals(21, version.getMinor());
        assertEquals(4, version.getPatch());
        assertFalse(version.getNmsPackageSuffix().isPresent());
        assertFalse(version.hasVersionedNmsPackage());
    }

    @Test
    void missing_patch_defaults_to_zero() {
        ServerVersion version = ServerVersion.parse("1.21-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(1, version.getMajor());
        assertEquals(21, version.getMinor());
        assertEquals(0, version.getPatch());
    }

    @Test
    void parses_renumbered_scheme_faithfully() {
        ServerVersion version = ServerVersion.parse("26.1.2-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(26, version.getMajor());
        assertEquals(1, version.getMinor());
        assertEquals(2, version.getPatch());
        assertFalse(version.hasVersionedNmsPackage());
    }

    @Test
    void unparseable_version_yields_unknown_components_and_never_throws() {
        ServerVersion fromNull = ServerVersion.parse(null, null);
        ServerVersion fromGarbage = ServerVersion.parse("not-a-version", "weird.package");

        assertEquals(-1, fromNull.getMajor());
        assertEquals(-1, fromNull.getMinor());
        assertEquals(-1, fromNull.getPatch());
        assertEquals("", fromNull.getRawVersion());
        assertFalse(fromNull.isAtLeast(1, 8));

        assertEquals(-1, fromGarbage.getMajor());
        assertEquals("not-a-version", fromGarbage.getRawVersion());
        assertFalse(fromGarbage.isAtLeast(1, 8));
    }

    @Test
    void null_package_name_yields_empty_suffix() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", null);

        assertFalse(version.hasVersionedNmsPackage());
        assertFalse(version.getNmsPackageSuffix().isPresent());
    }

    @Test
    void is_at_least_compares_against_major_minor() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isAtLeast(1, 20));
        assertTrue(version.isAtLeast(1, 8));
        assertFalse(version.isAtLeast(1, 21));
        assertFalse(version.isAtLeast(2, 0));
    }

    @Test
    void is_at_least_compares_against_patch() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isAtLeast(1, 20, 1));
        assertTrue(version.isAtLeast(1, 20, 0));
        assertFalse(version.isAtLeast(1, 20, 2));
    }

    @Test
    void is_older_than_is_complement_of_is_at_least() {
        ServerVersion version = ServerVersion.parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isOlderThan(1, 20));
        assertFalse(version.isOlderThan(1, 19));
        assertFalse(version.isOlderThan(1, 8));
    }

    @Test
    void compare_to_orders_by_triple() {
        ServerVersion older = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit");
        ServerVersion newer = ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit");
        ServerVersion newerPatch = ServerVersion.parse("1.20.2", "org.bukkit.craftbukkit");

        assertTrue(older.compareTo(newer) < 0);
        assertTrue(newer.compareTo(older) > 0);
        assertTrue(newer.compareTo(newerPatch) < 0);
        assertEquals(0, newer.compareTo(ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit")));
    }

    @Test
    void equals_and_hash_code_ignore_suffix() {
        ServerVersion versioned = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit.v1_19_R3");
        ServerVersion unversioned = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit");
        ServerVersion different = ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit");

        assertEquals(versioned, unversioned);
        assertEquals(versioned.hashCode(), unversioned.hashCode());
        assertNotEquals(versioned, different);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from worktree root, JDK 21, sandbox disabled):
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test -Dtest=ServerVersionTest -Danimal.sniffer.skip=true
```
Expected: FAIL — compilation error, `cannot find symbol: class ServerVersion`.

- [ ] **Step 3: Create the `ServerVersion` class (pure logic)**

Create `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java`:

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
package tech.guilhermekaua.spigotboot.core.spigot.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable, comparable view of the running Minecraft server's version.
 *
 * <p>The version triple is parsed from {@code Bukkit.getBukkitVersion()} (e.g.
 * {@code "1.21.4-R0.1-SNAPSHOT"}) with an open numeric scheme — every {@code major.minor.patch}
 * is accepted, so no per-release code change is ever needed. The optional legacy CraftBukkit
 * package suffix (e.g. {@code v1_19_R3}) is also captured; it is empty on modern un-versioned
 * servers (Paper 1.20.5+) and on renumbered schemes.
 *
 * <p>Ordering and equality are defined over the {@code (major, minor, patch)} triple only; the
 * package suffix is metadata and does not participate.
 *
 * <p>When the version string cannot be parsed the components are {@code -1} and
 * {@link #isAtLeast(int, int)} always returns {@code false}; construction never throws.
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    private static final Pattern BUKKIT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern PACKAGE_SUFFIX = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");

    /** sentinel for a version component that could not be parsed */
    private static final int UNKNOWN = -1;

    private final int major;
    private final int minor;
    private final int patch;
    private final String rawVersion;
    private final String nmsPackageSuffix;

    private ServerVersion(int major, int minor, int patch, @NotNull String rawVersion,
                          @Nullable String nmsPackageSuffix) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.rawVersion = rawVersion;
        this.nmsPackageSuffix = nmsPackageSuffix;
    }

    /**
     * Parses the server coordinates without touching Bukkit, so the logic is unit-testable
     * without a live server.
     *
     * @param bukkitVersion     the {@code Bukkit.getBukkitVersion()} string, or {@code null}
     * @param serverPackageName the {@code Bukkit.getServer().getClass().getPackage().getName()}
     *                          value, or {@code null}
     * @return the parsed version; components are {@code -1} when {@code bukkitVersion} is null or
     *         unparseable
     */
    static @NotNull ServerVersion parse(@Nullable String bukkitVersion, @Nullable String serverPackageName) {
        String raw = bukkitVersion == null ? "" : bukkitVersion;
        int major = UNKNOWN;
        int minor = UNKNOWN;
        int patch = UNKNOWN;

        if (bukkitVersion != null) {
            Matcher matcher = BUKKIT_VERSION.matcher(bukkitVersion);
            if (matcher.find()) {
                major = parseIntOrUnknown(matcher.group(1));
                minor = parseIntOrUnknown(matcher.group(2));
                String patchGroup = matcher.group(3);
                patch = patchGroup == null ? 0 : parseIntOrUnknown(patchGroup);
            }
        }

        // a partially-parsed triple is meaningless, so collapse it to fully unknown
        if (major == UNKNOWN || minor == UNKNOWN) {
            major = UNKNOWN;
            minor = UNKNOWN;
            patch = UNKNOWN;
        }

        return new ServerVersion(major, minor, patch, raw, parsePackageSuffix(serverPackageName));
    }

    private static @Nullable String parsePackageSuffix(@Nullable String serverPackageName) {
        if (serverPackageName == null) {
            return null;
        }
        int lastDot = serverPackageName.lastIndexOf('.');
        String last = lastDot < 0 ? serverPackageName : serverPackageName.substring(lastDot + 1);
        return PACKAGE_SUFFIX.matcher(last).matches() ? last : null;
    }

    private static int parseIntOrUnknown(@Nullable String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    /**
     * @return the major version component (the {@code 1} in {@code 1.21.4}), or {@code -1} if
     *         the version could not be parsed
     */
    public int getMajor() {
        return major;
    }

    /**
     * @return the minor version component (the {@code 21} in {@code 1.21.4}), or {@code -1} if
     *         the version could not be parsed
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @return the patch version component (the {@code 4} in {@code 1.21.4}; {@code 0} when the
     *         version omits a patch), or {@code -1} if the version could not be parsed
     */
    public int getPatch() {
        return patch;
    }

    /**
     * @return the raw {@code Bukkit.getBukkitVersion()} string this was parsed from (empty string
     *         when the source was {@code null})
     */
    public @NotNull String getRawVersion() {
        return rawVersion;
    }

    /**
     * @return the legacy CraftBukkit package suffix (e.g. {@code v1_19_R3}), or an empty
     *         {@link Optional} on un-versioned modern servers
     */
    public @NotNull Optional<String> getNmsPackageSuffix() {
        return Optional.ofNullable(nmsPackageSuffix);
    }

    /**
     * @return {@code true} when the server relocates CraftBukkit into a versioned
     *         {@code vX_Y_RZ} package (pre-Paper-1.20.5 style)
     */
    public boolean hasVersionedNmsPackage() {
        return nmsPackageSuffix != null;
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @return {@code true} if this version is at least {@code major.minor.0}; always {@code false}
     *         when this version could not be parsed
     */
    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @param patch the patch version to compare against
     * @return {@code true} if this version is at least {@code major.minor.patch}; always
     *         {@code false} when this version could not be parsed
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        if (this.major == UNKNOWN || this.minor == UNKNOWN) {
            return false;
        }
        return compareTriple(this.major, this.minor, this.patch, major, minor, patch) >= 0;
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @return the logical complement of {@link #isAtLeast(int, int)} — {@code true} when this
     *         version is strictly older than {@code major.minor.0} (and when it is unparseable)
     */
    public boolean isOlderThan(int major, int minor) {
        return !isAtLeast(major, minor);
    }

    @Override
    public int compareTo(@NotNull ServerVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTriple(this.major, this.minor, this.patch, other.major, other.minor, other.patch);
    }

    private static int compareTriple(int aMajor, int aMinor, int aPatch,
                                     int bMajor, int bMinor, int bPatch) {
        int cmp = Integer.compare(aMajor, bMajor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(aMinor, bMinor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(aPatch, bPatch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerVersion)) {
            return false;
        }
        ServerVersion that = (ServerVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return "ServerVersion{" + major + "." + minor + "." + patch +
                (nmsPackageSuffix != null ? " (" + nmsPackageSuffix + ")" : "") + "}";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test -Dtest=ServerVersionTest -Danimal.sniffer.skip=true
```
Expected: PASS — all `ServerVersionTest` cases green.

- [ ] **Step 5: Commit**

```
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionTest.java
git commit -m "feat: add ServerVersion value object with version parsing"
```

---

## Task 2: live detection + caching (`detect()` / `current()`)

Adds the live-server entry points: `detect()` reads Bukkit (1.8.8-sniffer-safe), and `current()` returns a lazily computed, cached singleton.

**Files:**
- Modify: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java`
- Test: `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionCurrentTest.java`

**Interfaces:**
- Consumes: `ServerVersion.parse(String, String)` and the accessors from Task 1.
- Produces:
  - `static ServerVersion current()` (public, cached)
  - `static ServerVersion detect()` (package-private, reads Bukkit live, no caching)

- [ ] **Step 1: Write the failing MockBukkit smoke test**

Create `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionCurrentTest.java`:

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
package tech.guilhermekaua.spigotboot.core.spigot.version;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionCurrentTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detect_matches_parse_of_running_server() {
        // tie detect() to the same inputs parse() would receive, so the assertion does not
        // hard-code MockBukkit's reported numbers
        Object server = Bukkit.getServer();
        ServerVersion expected = ServerVersion.parse(
                Bukkit.getBukkitVersion(), server.getClass().getPackage().getName());

        ServerVersion detected = ServerVersion.detect();

        assertNotNull(detected);
        assertEquals(expected, detected);
        assertEquals(Bukkit.getBukkitVersion(), detected.getRawVersion());
        // MockBukkit-v1.20 reports a modern 1.x server
        assertTrue(detected.isAtLeast(1, 8));
    }

    @Test
    void current_returns_cached_singleton() {
        ServerVersion first = ServerVersion.current();
        ServerVersion second = ServerVersion.current();

        assertNotNull(first);
        assertSame(first, second);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test -Dtest=ServerVersionCurrentTest -Danimal.sniffer.skip=true
```
Expected: FAIL — compilation error, `cannot find symbol: method detect()` / `method current()`.

- [ ] **Step 3: Add `detect()` and `current()` to `ServerVersion`**

In `ServerVersion.java`, add the Bukkit import near the other imports:

```java
import org.bukkit.Bukkit;
```

Add the cache field directly below the `UNKNOWN` constant:

```java
    /** lazily computed singleton for the running server; the version is constant per process */
    private static volatile ServerVersion current;
```

Add these two methods immediately above the `parse(...)` method:

```java
    /**
     * @return the version of the running server, computed once and cached. Never throws — an
     *         unparseable version yields a {@link ServerVersion} whose components are {@code -1}.
     */
    public static @NotNull ServerVersion current() {
        ServerVersion result = current;
        if (result == null) {
            synchronized (ServerVersion.class) {
                result = current;
                if (result == null) {
                    result = detect();
                    current = result;
                }
            }
        }
        return result;
    }

    /**
     * Reads the running server's coordinates from Bukkit and parses them. Package-private and
     * un-cached so it can be exercised under a mock server.
     *
     * @return the parsed version of the running server
     */
    static @NotNull ServerVersion detect() {
        // Object-typed on purpose: under the animal-sniffer 1.8.8 signature, getClass() on a
        // Bukkit-typed receiver cannot resolve, so route it through the java.* ignore.
        Object server = Bukkit.getServer();
        String packageName = server == null ? null : server.getClass().getPackage().getName();
        return parse(Bukkit.getBukkitVersion(), packageName);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test -Dtest=ServerVersionCurrentTest -Danimal.sniffer.skip=true
```
Expected: PASS — both smoke-test cases green.

> If `detect().isAtLeast(1, 8)` fails because MockBukkit-v1.20 reports an unexpected `Bukkit.getBukkitVersion()`, do not weaken the `parse`-equality assertion; instead lower the `isAtLeast` bound to match the reported version (the `assertEquals(expected, detected)` assertion is the load-bearing one).

- [ ] **Step 5: Commit**

```
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersion.java platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionCurrentTest.java
git commit -m "feat: add cached ServerVersion.current() live detection"
```

---

## Task 3: injectable `ServerVersion` bean

Exposes `ServerVersion` for dependency injection via a `@Configuration`, guarded so a plugin can override it. Mirrors `SpigotSchedulerConfiguration`.

**Files:**
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionConfiguration.java`

**Interfaces:**
- Consumes: `ServerVersion.current()` from Task 2; the DI annotations `@Configuration`, `@Bean`, `@ConditionalOnMissingBean` from `tech.guilhermekaua.spigotboot.core.context.annotations`.
- Produces: a `ServerVersion` bean injectable into any component.

- [ ] **Step 1: Create the configuration class**

Create `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionConfiguration.java`:

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
package tech.guilhermekaua.spigotboot.core.spigot.version;

import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

/**
 * Registers the running server's {@link ServerVersion}. Guarded by {@code @ConditionalOnMissingBean}
 * so a plugin may register its own instance.
 */
@Configuration
public class ServerVersionConfiguration {

    /**
     * @return the {@link ServerVersion} for the running server
     */
    @Bean
    @ConditionalOnMissingBean(ServerVersion.class)
    public ServerVersion serverVersion() {
        return ServerVersion.current();
    }
}
```

- [ ] **Step 2: Compile the module to verify the configuration is valid**

Run:
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test-compile -Danimal.sniffer.skip=true
```
Expected: BUILD SUCCESS — the new `@Configuration` compiles against the DI annotations.

- [ ] **Step 3: Commit**

```
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/version/ServerVersionConfiguration.java
git commit -m "feat: expose ServerVersion as an injectable bean"
```

---

## Task 4: full-module verification

Confirms the complete feature builds clean with the **animal-sniffer 1.8.8 check active** (no skip) and all tests pass — this is the gate that proves runtime 1.8.8 safety.

**Files:** none (verification only).

- [ ] **Step 1: Run the full module build with the 1.8.8 signature check active**

Run (JDK 21, sandbox disabled, **no** `animal.sniffer.skip`):
```
.\mvnw.cmd -pl platform-spigot/core-spigot -am test
```
Expected: BUILD SUCCESS — `ServerVersionTest` and `ServerVersionCurrentTest` pass and the animal-sniffer 1.8.8 check reports no undefined references for the new code.

> On a fresh clone the 1.8.8 signature may be missing; if the build fails resolving `spigot-api-1_8-signature`, first run `.\mvnw.cmd -pl spigot-api-1_8-signature install` (sandbox disabled), then re-run the command above.

- [ ] **Step 2: Verify no unintended files changed**

Run:
```
git status --short
```
Expected: clean working tree (all new files already committed in Tasks 1-3); only the new `version/` files exist, and no files outside `platform-spigot/core-spigot` were modified.

---

## Self-Review

**Spec coverage:**
- Open numeric `major.minor.patch` detecting all versions → Task 1 (`parse`, `BUKKIT_VERSION` regex keeping all three groups).
- Raw version, NMS suffix, `hasVersionedNmsPackage()` → Task 1 accessors + `PACKAGE_SUFFIX` regex.
- `isAtLeast` / `isOlderThan` / `compareTo` / `equals` / `hashCode` over the triple → Task 1.
- Unparseable → `-1`, non-throwing → Task 1 (`unparseable_version_*` test) and Task 2 (`current()` never throws).
- Renumbered "26.x" parsed faithfully → Task 1 (`parses_renumbered_scheme_faithfully`).
- Static cached `current()` → Task 2; package-private `detect()` seam + Object-typed `getServer()` hoist (1.8.8 safety) → Task 2.
- Injectable `@Bean` via `@Configuration` + `@ConditionalOnMissingBean` → Task 3.
- Pure-logic tests without MockBukkit + MockBukkit smoke test → Task 1 / Task 2.
- animal-sniffer 1.8.8 passes with no new ignores → Task 4 (full build, no skip).
- Purely additive, no other modules touched → enforced by Global Constraints + Task 4 Step 2.

**Placeholder scan:** none — every code and command step is complete.

**Type consistency:** `parse(String, String)`, `detect()`, `current()`, `getMajor/getMinor/getPatch`, `getRawVersion`, `getNmsPackageSuffix`, `hasVersionedNmsPackage`, `isAtLeast(int,int)`, `isAtLeast(int,int,int)`, `isOlderThan(int,int)`, `compareTo(ServerVersion)` are named identically across the value object, its tests, and the configuration bean.
