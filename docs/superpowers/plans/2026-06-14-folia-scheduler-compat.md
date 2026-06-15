# Folia scheduler & region-correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the spigot-boot runtime Folia-safe — a platform-conditional scheduler abstraction, region-correct inventory views, Folia-correct `Utils` helpers, and a cross-version `Sound` serializer — without regressing legacy Spigot/Paper support.

**Architecture:** A new `PlatformScheduler` abstraction in `core-spigot` with two lazily-isolated impls (legacy `BukkitScheduler`; Folia/modern-Paper region/entity/global schedulers), selected once at startup like the existing `InventoryApiNMS` title-updater. inventory-api injects it and routes all scheduling + region-ownership checks through it. `Utils` helpers are redesigned to require a target. `SoundSerializer` is re-enabled with runtime branching for the enum→interface ABI change.

**Tech Stack:** Java 8 source level, Maven (build with JDK 21 — `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10`), paper-api 1.20.1 (Folia scheduler API present), JUnit 5 + Mockito + MockBukkit (global test deps), animal-sniffer 1.8.8 signature check.

**Spec:** `docs/superpowers/specs/2026-06-14-folia-scheduler-compat-design.md`

**Build/test commands (run from repo root):**
- Single module test: `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl platform-spigot/core-spigot -am test -Danimal.sniffer.skip=true`
- Package test-plugin: `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl test-plugin -am package -Danimal.sniffer.skip=true`
- Sniffer check (CI parity, run before final commit): drop `-Danimal.sniffer.skip=true`.

**Branch:** `feat/folia-scheduler-compat` (already created; the prior load-time fixes are uncommitted in the working tree — commit them in Increment 0).

---

## File Structure

**New (Increment 1):** under `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/`
- `PlatformScheduler.java` — interface (no version-specific refs)
- `PlatformTask.java` — cancellable handle interface
- `BukkitPlatformScheduler.java` — legacy `BukkitScheduler` impl
- `BukkitPlatformTask.java` — wraps `org.bukkit.scheduler.BukkitTask`
- `FoliaPlatformScheduler.java` — Folia/modern-Paper impl (reflective accessors + typed scheduler calls)
- `FoliaPlatformTask.java` — wraps `io.papermc.paper.threadedregions.scheduler.ScheduledTask`
- `PlatformSchedulers.java` — detection + factory
- config: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/SpigotSchedulerConfiguration.java` — `@Bean`
- test: `platform-spigot/core-spigot/src/test/java/.../scheduler/PlatformSchedulersTest.java`
- modify: `platform-spigot/core-spigot/pom.xml` (animal-sniffer ignores)

**Modified (Increment 2):** inventory-api/api — `ViewEngine`, `FirstRenderPhase`, `FlushCoordinator`, `ViewSession`, `PageRequest`, `AsyncPageSource`, `BukkitSettleDispatcher`, `ThreadUtils`, `InventoryApiAutoConfiguration` (+ wherever `PageRequest` is constructed).

**Modified (Increment 3):** `core-spigot` `Utils.java` (+ a `SoundCompat` helper).

**Modified (Increment 4):** config-spigot `SoundSerializer.java`, `BukkitSerializers.java`.

---

## Increment 0: Commit the load-time fixes

### Task 0: Commit the already-validated load-time fixes

**Files:** (already changed in working tree) `Plugin.java`, `PluginAnnotationProcessor.java`, `Main.java`, `InventoryApiNMS.java`, `InventoryApiNMSTest.java`.

- [ ] **Step 1: Confirm tree state**

Run: `git status --short`
Expected: shows the 5 modified/added files above (plus untracked scratch logs in the Folia server dir, which are outside the repo).

- [ ] **Step 2: Stage and commit only the source changes**

```bash
git add platform-spigot/annotation-processor/src/main/java/tech/guilhermekaua/spigotboot/spigot/annotationprocessor/annotations/Plugin.java \
        platform-spigot/annotation-processor/src/main/java/tech/guilhermekaua/spigotboot/spigot/annotationprocessor/plugin/PluginAnnotationProcessor.java \
        test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/Main.java \
        platform-spigot/inventory-api/nms/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/nms/InventoryApiNMS.java \
        platform-spigot/inventory-api/nms/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/nms/InventoryApiNMSTest.java
git commit -m "fix(folia): generate folia-supported and select title updater on un-versioned servers"
```

---

## Increment 1: PlatformScheduler abstraction

### Task 1: `PlatformTask` and `PlatformScheduler` interfaces

**Files:**
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformTask.java`
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformScheduler.java`

- [ ] **Step 1: Create `PlatformTask`**

```java
/* <MIT license header — copy verbatim from any existing core-spigot file> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

/**
 * A cancellable handle to a task scheduled through a {@link PlatformScheduler}. Abstracts over
 * the legacy {@code BukkitTask} and the Folia {@code ScheduledTask} so callers can cancel a
 * repeating task without referencing a platform-specific type.
 */
public interface PlatformTask {

    /** Cancels the task; a no-op if it has already run or been cancelled. */
    void cancel();

    /**
     * @return {@code true} if this task has been cancelled.
     */
    boolean isCancelled();
}
```

- [ ] **Step 2: Create `PlatformScheduler`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral scheduling. On Folia every task is bound to a region (an entity's, a
 * location's, or the global region); on legacy Spigot/Paper all variants fall back to the
 * single main thread. Obtain the configured instance by injecting this type.
 *
 * <p>Times are in server ticks (20 per second), matching the legacy scheduler.
 */
public interface PlatformScheduler {

    /**
     * Runs a task on the thread owning the entity's region (Folia) or the main thread (legacy).
     *
     * @param entity  the entity whose region owns the task
     * @param task    the work to run
     * @param retired run instead of {@code task} if the entity is removed before it fires
     *                (Folia only); ignored on legacy
     * @return a cancellable handle
     */
    @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks);

    @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks);

    @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task);

    @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks);

    @NotNull PlatformTask runGlobal(@NotNull Runnable task);

    @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks);

    /**
     * @return {@code true} if the current thread owns the entity's region (Folia) or is the
     * main thread (legacy) — i.e. it is safe to touch the entity now.
     */
    boolean ownsRegion(@NotNull Entity entity);

    boolean ownsRegion(@NotNull Location location);
}
```

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl platform-spigot/core-spigot -am compile -Danimal.sniffer.skip=true -o`
Expected: BUILD SUCCESS (if `-o` offline fails on a missing artifact, drop `-o`).

- [ ] **Step 4: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformTask.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformScheduler.java
git commit -m "feat(scheduler): add PlatformScheduler and PlatformTask interfaces"
```

### Task 2: Legacy `BukkitPlatformScheduler` + `BukkitPlatformTask`

**Files:**
- Create: `.../scheduler/BukkitPlatformTask.java`
- Create: `.../scheduler/BukkitPlatformScheduler.java`

- [ ] **Step 1: Create `BukkitPlatformTask`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/** {@link PlatformTask} backed by a legacy {@link BukkitTask}. */
final class BukkitPlatformTask implements PlatformTask {

    private final BukkitTask task;

    BukkitPlatformTask(@NotNull BukkitTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }
}
```

- [ ] **Step 2: Create `BukkitPlatformScheduler`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Legacy {@link PlatformScheduler}: every variant maps to {@link org.bukkit.scheduler.BukkitScheduler}
 * and runs on the single main thread. {@code retired} callbacks have no legacy equivalent and are
 * ignored. Selected on Spigot / Paper builds that lack the Folia scheduler API.
 */
public final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public BukkitPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return new BukkitPlatformTask(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        return Bukkit.isPrimaryThread();
    }
}
```

- [ ] **Step 3: Compile** — `JAVA_HOME=… ./mvnw -pl platform-spigot/core-spigot -am compile -Danimal.sniffer.skip=true` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/BukkitPlatformTask.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/BukkitPlatformScheduler.java
git commit -m "feat(scheduler): add legacy BukkitPlatformScheduler"
```

### Task 3: Folia `FoliaPlatformScheduler` + `FoliaPlatformTask` + animal-sniffer ignores

**Files:**
- Create: `.../scheduler/FoliaPlatformTask.java`
- Create: `.../scheduler/FoliaPlatformScheduler.java`
- Modify: `platform-spigot/core-spigot/pom.xml` (add scheduler-package ignores)

**Rationale (read before coding):** the modern scheduler API exists in paper-api 1.20.1, so the scheduler-interface calls are typed. The 5 *accessors* on `org.bukkit.Bukkit`/`Entity` (`getRegionScheduler`, `getGlobalRegionScheduler`, `isOwnedByCurrentRegion(Entity)`, `isOwnedByCurrentRegion(Location)`, `Entity#getScheduler`) are reached via cached reflection so we do NOT have to add broad `org.bukkit.Bukkit`/`org.bukkit.entity.Entity` ignores to animal-sniffer (keeping the 1.8 net intact). Only the genuinely-new scheduler-package classes are ignored. This class is instantiated only when detection passes (Task 4), so legacy servers never load it.

- [ ] **Step 1: Create `FoliaPlatformTask`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

/** {@link PlatformTask} backed by a Folia {@link ScheduledTask}. */
final class FoliaPlatformTask implements PlatformTask {

    private final ScheduledTask task;

    FoliaPlatformTask(@NotNull ScheduledTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }
}
```

- [ ] **Step 2: Create `FoliaPlatformScheduler`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Folia / modern-Paper {@link PlatformScheduler}. Region/entity/global tasks land on the owning
 * region thread. The scheduler accessors on {@link Bukkit}/{@link Entity} are invoked reflectively
 * (so the 1.8.8 animal-sniffer net over {@code org.bukkit.*} stays intact); the scheduler
 * interfaces themselves are used with normal typed calls. Instantiated only when the Folia
 * scheduler API is present (see {@link PlatformSchedulers}).
 */
public final class FoliaPlatformScheduler implements PlatformScheduler {

    private static final Method GET_REGION_SCHEDULER;
    private static final Method GET_GLOBAL_SCHEDULER;
    private static final Method IS_OWNED_ENTITY;
    private static final Method IS_OWNED_LOCATION;
    private static final Method ENTITY_GET_SCHEDULER;

    static {
        try {
            GET_REGION_SCHEDULER = Bukkit.class.getMethod("getRegionScheduler");
            GET_GLOBAL_SCHEDULER = Bukkit.class.getMethod("getGlobalRegionScheduler");
            IS_OWNED_ENTITY = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            IS_OWNED_LOCATION = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            ENTITY_GET_SCHEDULER = Entity.class.getMethod("getScheduler");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Folia scheduler API expected but not found", e);
        }
    }

    private final Plugin plugin;

    public FoliaPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    private EntityScheduler entityScheduler(Entity entity) {
        try {
            return (EntityScheduler) ENTITY_GET_SCHEDULER.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private RegionScheduler regionScheduler() {
        try {
            return (RegionScheduler) GET_REGION_SCHEDULER.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private GlobalRegionScheduler globalScheduler() {
        try {
            return (GlobalRegionScheduler) GET_GLOBAL_SCHEDULER.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return new FoliaPlatformTask(entityScheduler(entity).run(plugin, scheduled -> task.run(), retired));
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return new FoliaPlatformTask(entityScheduler(entity).runDelayed(plugin, scheduled -> task.run(), retired, Math.max(1L, delayTicks)));
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return new FoliaPlatformTask(entityScheduler(entity).runAtFixedRate(plugin, scheduled -> task.run(), retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        return new FoliaPlatformTask(regionScheduler().run(plugin, location, scheduled -> task.run()));
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return new FoliaPlatformTask(regionScheduler().runDelayed(plugin, location, scheduled -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        return new FoliaPlatformTask(globalScheduler().run(plugin, scheduled -> task.run()));
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return new FoliaPlatformTask(globalScheduler().runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        try {
            return (boolean) IS_OWNED_ENTITY.invoke(null, entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        try {
            return (boolean) IS_OWNED_LOCATION.invoke(null, location);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 3: Add animal-sniffer ignores** to `platform-spigot/core-spigot/pom.xml`. Find the existing block and extend it:

Before:
```xml
                    <ignores combine.children="append">
                        <ignore>org.bukkit.UnsafeValues</ignore>
                    </ignores>
```
After:
```xml
                    <ignores combine.children="append">
                        <ignore>org.bukkit.UnsafeValues</ignore>
                        <!-- Folia scheduler API; referenced only by FoliaPlatformScheduler/FoliaPlatformTask,
                             which load only when the API is present at runtime -->
                        <ignore>io.papermc.paper.threadedregions.scheduler.EntityScheduler</ignore>
                        <ignore>io.papermc.paper.threadedregions.scheduler.RegionScheduler</ignore>
                        <ignore>io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler</ignore>
                        <ignore>io.papermc.paper.threadedregions.scheduler.ScheduledTask</ignore>
                    </ignores>
```

- [ ] **Step 4: Compile WITH the sniffer enabled** (proves the ignores are sufficient and no `org.bukkit.*` modern method leaked in typed):

Run: `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl platform-spigot/core-spigot -am verify -DskipTests`
Expected: BUILD SUCCESS, animal-sniffer passes. (If it flags an `org.bukkit.*` method, a typed modern call leaked — move it to a reflective accessor.)

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/FoliaPlatformTask.java \
        platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/FoliaPlatformScheduler.java \
        platform-spigot/core-spigot/pom.xml
git commit -m "feat(scheduler): add Folia PlatformScheduler with reflective accessors"
```

### Task 4: `PlatformSchedulers` factory + detection (TDD)

**Files:**
- Create: `.../scheduler/PlatformSchedulers.java`
- Test: `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformSchedulersTest.java`

- [ ] **Step 1: Write the failing test**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlatformSchedulersTest {

    @Test
    void selects_folia_scheduler_when_api_present() {
        Plugin plugin = Mockito.mock(Plugin.class);
        assertInstanceOf(FoliaPlatformScheduler.class, PlatformSchedulers.create(plugin, true));
    }

    @Test
    void selects_bukkit_scheduler_when_api_absent() {
        Plugin plugin = Mockito.mock(Plugin.class);
        assertInstanceOf(BukkitPlatformScheduler.class, PlatformSchedulers.create(plugin, false));
    }
}
```

- [ ] **Step 2: Run test, verify it fails** — Run: `JAVA_HOME=… ./mvnw -pl platform-spigot/core-spigot -am test -Dtest=PlatformSchedulersTest -Danimal.sniffer.skip=true` → FAIL (`PlatformSchedulers` not found).

- [ ] **Step 3: Create `PlatformSchedulers`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Detects the platform and builds the matching {@link PlatformScheduler}. */
public final class PlatformSchedulers {

    private static final String FOLIA_SCHEDULER_CLASS = "io.papermc.paper.threadedregions.scheduler.RegionScheduler";

    private PlatformSchedulers() {
    }

    /**
     * Builds the scheduler for the running server.
     *
     * @param plugin the owning plugin
     * @return a Folia scheduler when the modern scheduler API is present, else a legacy one
     */
    public static @NotNull PlatformScheduler create(@NotNull Plugin plugin) {
        return create(plugin, isFoliaSchedulerApiPresent());
    }

    // package-private seam so the branch is unit-testable without a live server
    static @NotNull PlatformScheduler create(@NotNull Plugin plugin, boolean foliaApiPresent) {
        Objects.requireNonNull(plugin, "plugin");
        return foliaApiPresent ? new FoliaPlatformScheduler(plugin) : new BukkitPlatformScheduler(plugin);
    }

    static boolean isFoliaSchedulerApiPresent() {
        try {
            Class.forName(FOLIA_SCHEDULER_CLASS, false, PlatformSchedulers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test, verify it passes** — same command as Step 2 → PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformSchedulers.java \
        platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/PlatformSchedulersTest.java
git commit -m "feat(scheduler): add PlatformSchedulers detection factory with tests"
```

### Task 5: DI bean

**Files:**
- Create: `.../scheduler/SpigotSchedulerConfiguration.java`

- [ ] **Step 1: Create the configuration**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.scheduler;

import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

/**
 * Registers the platform {@link PlatformScheduler}. Guarded by {@code @ConditionalOnMissingBean}
 * so a plugin may register its own implementation.
 */
@Configuration
public class SpigotSchedulerConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformScheduler.class)
    public PlatformScheduler platformScheduler(Plugin plugin) {
        return PlatformSchedulers.create(plugin);
    }
}
```

- [ ] **Step 2: Build the whole reactor up to test-plugin** (confirms discovery wiring + nothing else breaks):

Run: `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl test-plugin -am package -Danimal.sniffer.skip=true`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/scheduler/SpigotSchedulerConfiguration.java
git commit -m "feat(scheduler): register PlatformScheduler bean"
```

---

## Increment 2: inventory-api region-correctness

> Inject `PlatformScheduler` into the engine, replace the 5 sync scheduler calls and the `isPrimaryThread`/`assertMainThread` guards. Keep these existing tests green throughout: `UpdateFlushTest`, `SharedStateFlowTest`, `PaginationSettleTest`, `ViewEngineOpenOrderingTest`, `BukkitSettleDispatcherTest`, `DeferredOpsTest`, `ViewServiceEndToEndTest`. Run the module test suite after each task: `JAVA_HOME=… ./mvnw -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true`.

### Task 6: Region-aware `ThreadUtils`

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/util/ThreadUtils.java`

`ThreadUtils.assertMainThread` is static with no player. Add a region-aware overload that takes the scheduler + the entity; keep the old method delegating to `isPrimaryThread` semantics for callers without an entity (none after this increment, but harmless).

- [ ] **Step 1: Add `assertOwnsRegion`**

Add to `ThreadUtils` (keep the existing `assertMainThread`):
```java
import org.bukkit.entity.Entity;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
```
```java
    /**
     * Throws when the current thread does not own the entity's region.
     *
     * @param scheduler the platform scheduler used for the ownership check
     * @param entity    the entity whose region must be owned (the viewer)
     * @param operation the operation name used in the error message
     * @throws IllegalStateException when the current thread does not own the entity's region
     */
    public static void assertOwnsRegion(@NotNull PlatformScheduler scheduler, @NotNull Entity entity, @NotNull String operation) {
        if (!scheduler.ownsRegion(entity)) {
            throw new IllegalStateException(operation + " must be called on the thread owning the viewer's region");
        }
    }
```

Note: `inventory-api/api` must depend on `core-spigot`. Verify in `platform-spigot/inventory-api/api/pom.xml`; if absent, add:
```xml
        <dependency>
            <groupId>tech.guilhermekaua.spigot-boot</groupId>
            <artifactId>spigot-boot-core-spigot</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: Compile** — `JAVA_HOME=… ./mvnw -pl platform-spigot/inventory-api/api -am compile -Danimal.sniffer.skip=true` → SUCCESS.

- [ ] **Step 3: Commit** — `git commit -am "feat(inventory): add region-aware ThreadUtils.assertOwnsRegion"`

### Task 7: Inject `PlatformScheduler` into `ViewEngine`, `FirstRenderPhase`, `FlushCoordinator`

**Files:**
- Modify: `ViewEngine.java` (constructor + fields + pass-through to phases)
- Modify: `FirstRenderPhase.java` (constructor)
- Modify: `FlushCoordinator.java` (constructor)
- Modify: `InventoryApiAutoConfiguration.java` if ViewEngine is built there (it is `@Component`; the container injects the `PlatformScheduler` bean automatically once it's a constructor param)

- [ ] **Step 1: Add the field + constructor param to `ViewEngine`**

In `ViewEngine.java` add import `import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;`, add field `private final PlatformScheduler scheduler;`, and extend the constructor:
```java
    public ViewEngine(@NotNull Plugin plugin, @NotNull ViewRegistry views, @NotNull SessionRegistry sessions,
                      @NotNull SlotPainter painter, @NotNull TitleUpdater titleUpdater,
                      @NotNull PlatformScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.views = Objects.requireNonNull(views, "views");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.closePhase = new ClosePhase(this, sessions);
        this.openPhase = new OpenPhase(this, sessions, painter);
        this.firstRenderPhase = new FirstRenderPhase(this, sessions, painter);
        this.updatePhase = new UpdatePhase(this, painter);
        this.clickRoutingPhase = new ClickRoutingPhase(this);
        this.paginationInitPhase = new PaginationInitPhase(this, sessions);
        this.flushCoordinator = new FlushCoordinator(plugin, sessions, updatePhase, scheduler);
    }
```
Add an accessor `public @NotNull PlatformScheduler scheduler() { return scheduler; }` next to `plugin()`.

(`@Component` constructor injection resolves the new `PlatformScheduler` param from the bean registered in Increment 1, Task 5.)

- [ ] **Step 2: Add scheduler to `FlushCoordinator`** — add field `private final PlatformScheduler scheduler;`, import it, and add the param to the constructor (assign with `Objects.requireNonNull`).

- [ ] **Step 3: Build reactor + run inventory-api tests** — fix any other `new ViewEngine(...)` / `new FlushCoordinator(...)` call sites the compiler flags (test fixtures included) to pass a scheduler. For tests, pass `new BukkitPlatformScheduler(plugin)` or a Mockito mock.

Run: `JAVA_HOME=… ./mvnw -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true`
Expected: compile succeeds; tests pass (after updating fixtures).

- [ ] **Step 4: Commit** — `git commit -am "refactor(inventory): inject PlatformScheduler into engine and flush coordinator"`

### Task 8: Replace the `ViewEngine` scheduler calls + region guards

**Files:** `ViewEngine.java`

- [ ] **Step 1: `open` self-defer (line ~139)** — replace `Bukkit.getScheduler().runTask(plugin, () -> open(player, viewType, arguments));` with:
```java
                scheduler.runOnEntity(player, () -> open(player, viewType, arguments), null);
```
And replace the `ThreadUtils.assertMainThread("ViewEngine.open");` at the top of `open` with `ThreadUtils.assertOwnsRegion(scheduler, player, "ViewEngine.open");`.

- [ ] **Step 2: `defer` drain (line ~345)** — replace `Bukkit.getScheduler().runTask(plugin, () -> drainDeferred(session));` with:
```java
        scheduler.runOnEntity(session.player(), () -> drainDeferred(session), null);
```
And replace `ThreadUtils.assertMainThread("ViewEngine.defer");` with `ThreadUtils.assertOwnsRegion(scheduler, session.player(), "ViewEngine.defer");`.

- [ ] **Step 3: Convert the remaining session-scoped guards** — in `close`, `updateTitle`, `update`, `paginationSettle`, `click`, `drag`, `bukkitClose`, `flushDirty`, replace `ThreadUtils.assertMainThread("ViewEngine.X")` with `ThreadUtils.assertOwnsRegion(scheduler, session.player(), "ViewEngine.X")` (the `session` parameter is in scope in each).

- [ ] **Step 4: `flushShared(View owner)` (no session in scope)** — this entry point operates on all sessions of a view across regions; a single assert is wrong. Remove its `assertMainThread` call and rely on the per-session dispatch added in Task 9 (the actual repaints will be region-checked there). Leave the method body otherwise unchanged for now.

- [ ] **Step 5: Remove the now-unused `Bukkit` import if the compiler flags it** (only if no other `Bukkit.` use remains in the file).

- [ ] **Step 6: Run inventory-api tests** — expect green. If a test asserted the old "must be called on the main server thread" message, update it to the new "must be called on the thread owning the viewer's region" message (search tests for that string).

- [ ] **Step 7: Commit** — `git commit -am "refactor(inventory): route ViewEngine scheduling and guards through PlatformScheduler"`

### Task 9: `FirstRenderPhase` timer + `ViewSession.updateTask` type

**Files:** `FirstRenderPhase.java`, `ViewSession.java`

- [ ] **Step 1: Change the `ViewSession` task type** — in `ViewSession.java` replace the `import org.bukkit.scheduler.BukkitTask;` with `import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;`, change the field `private BukkitTask updateTask;` to `private PlatformTask updateTask;`, and change both accessor signatures from `BukkitTask` to `PlatformTask`. Find where `updateTask()` is cancelled (search `updateTask()` usages, e.g. in `ClosePhase`/session teardown) — `task.cancel()` still compiles via `PlatformTask.cancel()`.

- [ ] **Step 2: Change the timer in `FirstRenderPhase`** — replace the imports `org.bukkit.Bukkit` and `org.bukkit.scheduler.BukkitTask` (remove if now unused) and rewrite `startScheduledUpdates`:
```java
    private void startScheduledUpdates(ViewSession session) {
        long interval = session.effectiveConfig().updateIntervalTicks();
        if (interval <= 0) {
            return;
        }
        PlatformTask task = engine.scheduler().runOnEntityAtFixedRate(session.player(),
                new ViewUpdateTask(engine, session), null, interval, interval);
        session.updateTask(task);
    }
```
Add import `import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;`.

- [ ] **Step 2b: `ViewUpdateTask`** — it currently implements `Runnable` (legacy timer passed it directly). Confirm it is a `Runnable` (the legacy `runTaskTimer` accepted it). `runOnEntityAtFixedRate` takes a `Runnable`, so no change. If `ViewUpdateTask` instead `extends BukkitRunnable`, change it to `implements Runnable` and keep its `run()` body.

- [ ] **Step 3: Run inventory-api tests** — green (update any test that mocked a `BukkitTask` return to use `PlatformTask`).

- [ ] **Step 4: Commit** — `git commit -am "refactor(inventory): schedule view update timer via PlatformTask"`

### Task 10: `FlushCoordinator` per-session region-correct flush

**Files:** `FlushCoordinator.java`

The shared-state flush must repaint each open session of the owner on that session's region. Replace the `isPrimaryThread()` inline/scheduled split (lines ~162–195) and make `flushShared` dispatch per session.

- [ ] **Step 1: Write/adjust the failing test** — extend `SharedStateFlowTest` (or add `FlushCoordinatorFoliaTest`) to inject a fake `PlatformScheduler` that records `runOnEntity` calls, trigger an off-region shared write, and assert one `runOnEntity(viewerPlayer, …)` is scheduled per active session of the owner (not a single global hop). Use the existing test's view/session setup as the template.

- [ ] **Step 2: Rewrite `flushShared(View owner, Set<Integer> tokenIds)`** to dispatch each session on its own region:
```java
    private void flushShared(@NotNull View owner, @Nullable Set<Integer> tokenIds) {
        // snapshot: an onUpdate handler may close a session and mutate the registry
        List<ViewSession> snapshot = new ArrayList<>(sessions.all());
        for (ViewSession session : snapshot) {
            if (session.registered().instance() == owner && session.isActive()) {
                final ViewSession target = session;
                if (scheduler.ownsRegion(target.player())) {
                    updatePhase.update(target, UpdateTrigger.STATE_CHANGE, tokenIds);
                } else {
                    scheduler.runOnEntity(target.player(),
                            () -> updatePhase.update(target, UpdateTrigger.STATE_CHANGE, tokenIds), null);
                }
            }
        }
    }
```

- [ ] **Step 3: Replace the flush-hook `isPrimaryThread` branch** (lines ~162–195). The per-tick coalescing map stays (it dedups token ids per owner); the change is that draining no longer hops to one thread but goes through `flushShared`, which now fans out per session. Rewrite the `flushHook` body:
```java
            shared.flushHook(() -> {
                if (scheduler.ownsRegion-any()) { /* see note */ }
            });
```
Note: there is no "owns any region" concept. Replace the inline-vs-scheduled decision with: always coalesce the token id into `sharedFlushScheduled`, and when first-scheduled for the owner this tick, schedule the drain on the **global** region (it only reads the registry and re-dispatches per session — no world state touched), which then calls `flushShared` (Step 2 fans out per session). Concretely:
```java
            final int tokenIdFinal = tokenId;
            shared.flushHook(() -> {
                boolean[] schedule = {false};
                sharedFlushScheduled.compute(owner, (key, existing) -> {
                    if (existing == null) {
                        Set<Integer> created = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
                        created.add(tokenIdFinal);
                        schedule[0] = true;
                        return created;
                    }
                    existing.add(tokenIdFinal);
                    return existing;
                });
                if (schedule[0]) {
                    scheduler.runGlobal(() -> {
                        Set<Integer> ids = sharedFlushScheduled.remove(owner);
                        if (ids != null) {
                            flushShared(owner, ids);
                        }
                    });
                }
            });
```
This drops the synchronous `sharedFlushPending` re-entrancy path; if `UpdateFlushTest`/`SharedStateFlowTest` depend on synchronous same-tick flush, keep an inline fast-path: `if (scheduler.ownsRegion(<the writing session's player>))` — but the hook has no session handle, so the global-scheduler drain is the uniform correct path. Verify against the tests in Step 4 and adjust their timing expectations (they may need to pump the fake scheduler).

- [ ] **Step 4: Run inventory-api tests** — iterate until green. If `sessions.all()` is not safe to read off the owning region, confine the registry read to the global-region drain (already the case here). Keep `flushDirty` (single-session, called from a region-owning context) unchanged.

- [ ] **Step 5: Commit** — `git commit -am "refactor(inventory): region-correct shared-state flush fan-out"`

### Task 11: `PageRequest` viewer + `BukkitSettleDispatcher`

**Files:** `PageRequest.java`, `BukkitSettleDispatcher.java`, `AsyncPageSource.java` (and any `new PageRequest(...)` call sites)

- [ ] **Step 1: Add a viewer to `PageRequest`** — add `private final Player viewer;` (import `org.bukkit.entity.Player`), add it as the last constructor param, and add accessor:
```java
    public @Nullable Player viewer() {
        return viewer;
    }
```
Update the constructor Javadoc. Then fix every `new PageRequest(...)` call site the compiler flags (search `new PageRequest(`) to pass the viewer (the engine knows `session.player()`; tests may pass `null`).

- [ ] **Step 2: Make `BukkitSettleDispatcher` region-aware** — it must now hop to the viewer's region via a `PlatformScheduler`. Add a constructor taking `PlatformScheduler` (the dispatcher is created by the engine/config — pass the bean). Rewrite `dispatch`:
```java
    private final PlatformScheduler scheduler;

    public BukkitSettleDispatcher(@NotNull PlatformScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        Player viewer = request.viewer();
        if (viewer == null) {
            // engine-external test usage: settle inline on the completing thread
            task.run();
            return;
        }
        if (scheduler.ownsRegion(viewer)) {
            task.run();
            return;
        }
        try {
            scheduler.runOnEntity(viewer, task, null);
        } catch (IllegalPluginAccessException e) {
            // legacy: the owning plugin is disabling and the scheduler rejects new tasks
            LOGGER.log(Level.WARNING, "Dropped a page-load settle: the owning plugin is disabled.", e);
        }
    }
```
Keep the existing `LOGGER`. Remove the old `request.plugin()`/`isPrimaryThread` logic. Update where `BukkitSettleDispatcher` is instantiated to pass the scheduler.

- [ ] **Step 3: Run inventory-api tests** — update `BukkitSettleDispatcherTest` to construct with a fake/real `PlatformScheduler` and a `PageRequest` carrying a viewer; assert inline when `ownsRegion` is true and `runOnEntity` otherwise. Keep the null-viewer inline path test.

- [ ] **Step 4: Commit** — `git commit -am "refactor(inventory): settle page loads on the viewer's region"`

### Task 12: Increment 2 reactor build

- [ ] **Step 1: Full build + tests** — `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl test-plugin -am package -Danimal.sniffer.skip=true` → BUILD SUCCESS, all tests pass.
- [ ] **Step 2: Commit any remaining fixups** — `git commit -am "test(inventory): update fixtures for PlatformScheduler"` (if needed).

---

## Increment 3: Utils redesign

### Task 13: `SoundCompat` cross-version sound resolver

**Files:**
- Create: `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/utils/SoundCompat.java`
- Test: `platform-spigot/core-spigot/src/test/java/tech/guilhermekaua/spigotboot/core/spigot/utils/SoundCompatTest.java`

`org.bukkit.Sound` is an enum on paper-api 1.20.1 (compile) and ≤1.21.2 (runtime) but an interface on ≥1.21.3. Provide one resolver usable by both `Utils.playSound` and `SoundSerializer`.

- [ ] **Step 1: Write the failing test** (enum path — the only one exercisable on the 1.20.1 test classpath):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SoundCompatTest {

    @Test
    void resolves_enum_sound_by_name() {
        Sound sound = SoundCompat.resolve("ENTITY_PLAYER_LEVELUP");
        assertNotNull(sound);
    }

    @Test
    void serializes_enum_sound_to_its_name() {
        Sound sound = SoundCompat.resolve("ENTITY_PLAYER_LEVELUP");
        assertEquals("ENTITY_PLAYER_LEVELUP", SoundCompat.toKey(sound));
    }
}
```

- [ ] **Step 2: Run test, verify it fails** — `JAVA_HOME=… ./mvnw -pl platform-spigot/core-spigot -am test -Dtest=SoundCompatTest -Danimal.sniffer.skip=true` → FAIL (no `SoundCompat`).

- [ ] **Step 3: Implement `SoundCompat`** (reflection; no static refs to `Keyed`/`NamespacedKey`/`Registry` so it loads on 1.8.8):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Cross-version {@link Sound} resolution. {@code Sound} is an enum up to MC 1.21.2 and an
 * interface (registry type) from 1.21.3 on; this resolves a sound from a config string and
 * back without a compile-time assumption about its shape.
 */
public final class SoundCompat {

    private SoundCompat() {
    }

    /**
     * Resolves a sound from a config value — an enum constant name (e.g.
     * {@code ENTITY_PLAYER_LEVELUP}) or a namespaced key (e.g. {@code minecraft:entity.player.levelup}).
     *
     * @param value the config value
     * @return the resolved sound, or {@code null} if unknown
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Sound resolve(@NotNull String value) {
        String trimmed = value.trim();
        if (Sound.class.isEnum()) {
            String name = trimmed.toUpperCase().replace('.', '_').replace(' ', '_').replace(':', '_');
            // strip a namespace prefix like MINECRAFT_ if present
            if (name.startsWith("MINECRAFT_")) {
                name = name.substring("MINECRAFT_".length());
            }
            try {
                return (Sound) Enum.valueOf((Class) Sound.class, name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return resolveFromRegistry(trimmed);
    }

    /**
     * Serializes a sound back to a stable config string: the enum name on enum builds, else the
     * namespaced key (e.g. {@code minecraft:entity.player.levelup}).
     *
     * @param sound the sound
     * @return the config string
     */
    public static @NotNull String toKey(@NotNull Sound sound) {
        if (sound instanceof Enum) {
            return ((Enum<?>) sound).name();
        }
        try {
            Method getKey = sound.getClass().getMethod("getKey");
            Object key = getKey.invoke(sound); // NamespacedKey
            return String.valueOf(key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read sound key", e);
        }
    }

    private static @Nullable Sound resolveFromRegistry(String value) {
        try {
            Class<?> namespacedKey = Class.forName("org.bukkit.NamespacedKey");
            Method fromString = namespacedKey.getMethod("fromString", String.class);
            Object key = fromString.invoke(null, value.toLowerCase());
            if (key == null) {
                return null;
            }
            Method getRegistry = Bukkit.class.getMethod("getRegistry", Class.class);
            Object registry = getRegistry.invoke(null, Sound.class);
            if (registry == null) {
                return null;
            }
            Method get = registry.getClass().getMethod("get", namespacedKey);
            get.setAccessible(true);
            return (Sound) get.invoke(registry, key);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test, verify it passes** — same command as Step 2 → PASS.

- [ ] **Step 5: Commit** — `git add` the two files; `git commit -m "feat(core-spigot): add cross-version SoundCompat resolver"`

### Task 14: Redesign `Utils.sync` / `syncLater` / `playSound`

**Files:** `platform-spigot/core-spigot/src/main/java/tech/guilhermekaua/spigotboot/core/spigot/utils/Utils.java`

- [ ] **Step 1: Replace the three helpers** — remove the no-target `sync(Plugin, Runnable)`, `syncLater(Plugin, Runnable, long)`, and `playSound(Plugin, Player, String)`; add target-aware versions. Remove the now-unused `Bukkit`/`Sound` imports if the compiler flags them; add `PlatformScheduler` import.

```java
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
// remove: import org.bukkit.Bukkit;  (only if no other Bukkit use remains)
```
```java
    /**
     * Runs a task on the thread owning the entity's region.
     *
     * @param scheduler the platform scheduler
     * @param entity    the entity whose region owns the task
     * @param runnable  the work to run
     */
    public static void sync(PlatformScheduler scheduler, Entity entity, Runnable runnable) {
        scheduler.runOnEntity(entity, runnable, null);
    }

    public static void syncLater(PlatformScheduler scheduler, Entity entity, Runnable runnable, long delayTicks) {
        scheduler.runOnEntityLater(entity, runnable, null, delayTicks);
    }

    public static void playSound(PlatformScheduler scheduler, Player player, String name) {
        scheduler.runOnEntity(player, () -> Utils.tryElsePrint(() -> {
            Sound sound = SoundCompat.resolve(name);
            if (sound != null) {
                player.playSound(player.getEyeLocation(), sound, 1.0f, 1.0f);
            }
        }), null);
    }
```
Add imports `org.bukkit.entity.Entity` and (keep) `org.bukkit.entity.Player`, `org.bukkit.Sound`, and `tech.guilhermekaua.spigotboot.core.spigot.utils.SoundCompat` (same package — no import needed).

- [ ] **Step 2: Build core-spigot** — `JAVA_HOME=… ./mvnw -pl platform-spigot/core-spigot -am test -Danimal.sniffer.skip=true` → SUCCESS (no in-repo callers to fix; confirm with `grep -rn "Utils.sync\|Utils.syncLater\|Utils.playSound" --include=*.java platform-spigot core test-plugin`).

- [ ] **Step 3: Commit** — `git commit -am "feat(core-spigot)!: redesign Utils scheduling helpers to require a target"`

---

## Increment 4: cross-version SoundSerializer

### Task 15: Re-enable `SoundSerializer` (cross-version)

**Files:**
- Modify: `platform-spigot/config-spigot/.../serialization/SoundSerializer.java` (replace the fully-commented body)
- Modify: `platform-spigot/config-spigot/.../serialization/BukkitSerializers.java` (uncomment registration)
- Test: `platform-spigot/config-spigot/src/test/java/.../serialization/SoundSerializerTest.java`

- [ ] **Step 1: Write the failing test** (enum path on the 1.20.1 test classpath):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.config.spigot.serialization;

import org.bukkit.Sound;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundSerializerTest {

    private final SoundSerializer serializer = new SoundSerializer();

    @Test
    void deserializes_enum_name() throws Exception {
        ConfigNode node = Mockito.mock(ConfigNode.class);
        when(node.get(String.class)).thenReturn("ENTITY_PLAYER_LEVELUP");
        assertNotNull(serializer.deserialize(node, Sound.class));
    }

    @Test
    void serializes_to_stable_key() throws Exception {
        Sound sound = serializer.deserialize(mockNode("ENTITY_PLAYER_LEVELUP"), Sound.class);
        MutableConfigNode out = Mockito.mock(MutableConfigNode.class);
        serializer.serialize(sound, out);
        verify(out).set("ENTITY_PLAYER_LEVELUP");
    }

    private ConfigNode mockNode(String value) {
        ConfigNode node = Mockito.mock(ConfigNode.class);
        when(node.get(String.class)).thenReturn(value);
        return node;
    }
}
```

- [ ] **Step 2: Run test, verify it fails** — `JAVA_HOME=… ./mvnw -pl platform-spigot/config-spigot -am test -Dtest=SoundSerializerTest -Danimal.sniffer.skip=true` → FAIL (class is commented out).

- [ ] **Step 3: Replace `SoundSerializer.java`** with a cross-version body delegating to `SoundCompat`:

```java
/* <MIT license header — uncomment/restore> */
package tech.guilhermekaua.spigotboot.config.spigot.serialization;

import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.core.spigot.utils.SoundCompat;

/**
 * Cross-version serializer for {@link Sound}. {@code Sound} is an enum up to MC 1.21.2 and an
 * interface from 1.21.3 on; resolution is delegated to {@link SoundCompat} so the same config
 * works on every supported server.
 */
public class SoundSerializer implements TypeSerializer<Sound> {

    @Override
    public Sound deserialize(@NotNull ConfigNode node, @NotNull Class<Sound> type) throws SerializationException {
        String value = node.get(String.class);
        if (value == null || value.isEmpty()) {
            return null;
        }
        Sound sound = SoundCompat.resolve(value);
        if (sound == null) {
            throw new SerializationException("Unknown sound: " + value);
        }
        return sound;
    }

    @Override
    public void serialize(@NotNull Sound value, @NotNull MutableConfigNode node) throws SerializationException {
        node.set(SoundCompat.toKey(value));
    }
}
```

- [ ] **Step 4: Re-register** in `BukkitSerializers.java` — uncomment and add the import:
```java
import org.bukkit.Sound;
```
```java
        registry.register(Sound.class, new SoundSerializer());
```

- [ ] **Step 5: Run test, verify it passes** — same command as Step 2 → PASS. Then run the whole config-spigot suite to confirm no regression.

- [ ] **Step 6: Commit** — `git add` the three files; `git commit -m "feat(config-spigot): re-enable Sound serializer with cross-version resolution"`

---

## Increment 5: Validation

### Task 16: Full reactor build with sniffer (CI parity)

- [ ] **Step 1:** `JAVA_HOME=/c/Users/Guilherme/.jdks/ms-21.0.10 ./mvnw -pl test-plugin -am package` (no `-Danimal.sniffer.skip`) → BUILD SUCCESS (proves the sniffer ignores are correct and nothing leaked a modern `org.bukkit.*` call).
  - If the spigot-api 1.8 signature artifact is missing, first run `JAVA_HOME=… ./mvnw -pl spigot-api-1_8-signature install`.

### Task 17: Folia runtime validation (real server)

- [ ] **Step 1: Add a Sound field to the test-plugin config** so the interface-Sound deserialize path is exercised on Folia 26.x. In `test-plugin` `MainConfig` add a `Sound` field bound to a config key, and add the key (e.g. `levelup-sound: minecraft:entity.player.levelup`) to its config yml. (See `test-plugin/src/main/.../configuration/MainConfig.java`.)
- [ ] **Step 2: Rebuild + deploy** — `JAVA_HOME=… ./mvnw -pl test-plugin -am package -Danimal.sniffer.skip=true` then `cp test-plugin/target/test-plugin-3.2.1-SNAPSHOT-shaded.jar "/c/Users/Guilherme/Desktop/spigot/folia 26.1.2/plugins/test-plugin-3.2.1-SNAPSHOT.jar"`.
- [ ] **Step 3: Boot Folia and capture** (headless, timed stop):
```bash
cd "/c/Users/Guilherme/Desktop/spigot/folia 26.1.2"; rm -f after-run2.log; ( sleep 70; printf 'stop\n' ) | java -Xms1G -Xmx2G -jar folia-26.1.2-8.jar nogui > after-run2.log 2>&1
grep -nE "Enabling TestPlugin|MainConfig|Sound|Error occurred while enabling|Exception|Done \(" after-run2.log
```
Expected: `Enabling TestPlugin`, config loads (incl. the Sound value), `Done (`, no exceptions, clean `Disabling` from the stop.
- [ ] **Step 4 (optional, manual): open a view** — if a test command opens an inventory view, run it from the console/an op and confirm no Folia thread-check error in the log. (Region-correctness of live GUI ops; document the result.)

### Task 18: Finish the branch

- [ ] **Step 1:** Remove scratch logs from the server dir: `rm -f "/c/Users/Guilherme/Desktop/spigot/folia 26.1.2/"*-run*.log`.
- [ ] **Step 2:** Use `superpowers:finishing-a-development-branch` to decide merge/PR. Suggested PR title: `feat: Folia scheduler abstraction + region-correct inventory views`. Summarize the 5 increments and the before/after Folia validation in the PR body.

---

## Self-Review

**Spec coverage:**
- §3 approach A (typed interface, isolated impls, detection) → Tasks 1–4. ✓
- §4.1 method set → Task 1 interface matches spec exactly. ✓
- §4.2 PlatformTask → Task 1. ✓
- §4.3 impls + detection → Tasks 2–4 (Folia uses reflective accessors + typed scheduler calls — a refinement keeping the sniffer net tight, noted in Task 3 rationale; still approach A). ✓
- §4.4 DI bean → Task 5. ✓
- §4.5 inventory-api full region-correctness (5 scheduler sites + guards + per-session flush + PageRequest viewer) → Tasks 6–12. ✓
- §4.6 Utils redesign (require target; fix latent Sound.valueOf via SoundCompat) → Tasks 13–14. ✓
- §4.7 cross-version SoundSerializer → Tasks 13, 15. ✓
- §5 testing → unit tests in Tasks 4, 13, 15; existing inventory tests kept green (Increment 2 header); Folia runtime in Task 17. ✓
- §6 increments order → matches. ✓

**Placeholder scan:** the FlushCoordinator hook rewrite (Task 10, Step 3) is the one area requiring test-driven iteration — concrete code is given, with the existing tests named as the oracle. No "TBD"/"implement later".

**Type consistency:** `PlatformScheduler` method names/signatures are identical across interface (Task 1), impls (Tasks 2–3), callers (Tasks 8–11, 14). `PlatformTask` used consistently for `ViewSession.updateTask` (Task 9). `SoundCompat.resolve`/`toKey` consistent across Tasks 13–15. `PageRequest.viewer()` consistent (Tasks 11). `ThreadUtils.assertOwnsRegion(scheduler, entity, op)` consistent (Tasks 6, 8).

**Open risk:** Task 10 (shared-flush concurrency) is the highest-uncertainty task; if the global-drain approach changes `UpdateFlushTest`/`SharedStateFlowTest` timing, adjust those tests to pump the fake scheduler and keep the per-session-fan-out assertion as the contract.
